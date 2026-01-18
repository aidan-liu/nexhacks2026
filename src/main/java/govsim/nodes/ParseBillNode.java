package govsim.nodes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import govsim.core.Node;
import govsim.core.SimulationLogger;
import govsim.core.SimulationState;
import govsim.llm.LLMClient;
import govsim.llm.LLMRequestOptions;
import govsim.web.BillStore;

import java.util.List;
import java.util.Map;

public class ParseBillNode implements Node {
  private final LLMClient llm;
  private final ObjectMapper mapper = new ObjectMapper();
  private static final int NUM_PREDICT_PARSE = 400;
  private static final int NUM_PREDICT_PARSE_RETRY = 600;

  public ParseBillNode(LLMClient llm) {
    this.llm = llm;
  }

  @Override
  public String name() { return "ParseBill"; }

  @Override
  public void run(SimulationState state) throws Exception {
    if (state.bill == null) {
      throw new IllegalStateException("Missing bill in state");
    }
    SimulationLogger.log("[ParseBill] Sending bill to LLM for analysis...");
    String prompt = """
You are a legislative analyst. Extract structured data and a short one-pager.

Return STRICT JSON with keys:
- topics: array of 5 short strings
- estimatedCost: number (USD millions if unsure)
- attributes: object with 3-5 key facts (values string or number)
- onePager: 3 short sentences

BILL:
%s

No extra keys. No markdown.
""".formatted(state.bill.rawText());

    String json = llm.generateJson(prompt, LLMRequestOptions.withNumPredict(NUM_PREDICT_PARSE));
    JsonNode root;
    try {
      root = mapper.readTree(json);
    } catch (JsonProcessingException e) {
      SimulationLogger.log("[ParseBill] Invalid JSON from LLM. Retrying with higher limit...");
      String retryPrompt = prompt + "\nReturn compact JSON only. No extra text.";
      json = llm.generateJson(retryPrompt, LLMRequestOptions.withNumPredict(NUM_PREDICT_PARSE_RETRY));
      root = mapper.readTree(json);
    }

    JsonNode topicsNode = root.get("topics");
    JsonNode costNode = root.get("estimatedCost");
    JsonNode attributesNode = root.get("attributes");
    JsonNode onePagerNode = root.get("onePager");

    if (topicsNode == null || costNode == null || attributesNode == null || onePagerNode == null) {
      SimulationLogger.log("[ParseBill] Missing fields in LLM JSON. Applying fallbacks.");
    }

    List<String> topics = topicsNode == null
        ? deriveTopics(state.bill.title(), state.bill.rawText())
        : mapper.convertValue(topicsNode, new TypeReference<>() {});
    Map<String, Object> attributes = attributesNode == null
        ? Map.of()
        : mapper.convertValue(attributesNode, new TypeReference<>() {});
    double estimatedCost = costNode == null ? 0.0 : costNode.asDouble();
    String onePager = onePagerNode == null ? deriveOnePager(state.bill.rawText()) : onePagerNode.asText();

    state.bill.setTopics(topics);
    state.bill.setEstimatedCost(estimatedCost);
    state.bill.setAttributes(attributes);
    state.billOnePager = onePager;
    Object storeObj = state.vars.get("billStore");
    if (storeObj instanceof BillStore store) {
      store.setOriginalText(state.bill.rawText());
      store.setOnePager(onePager);
    }
    SimulationLogger.log("[ParseBill] Analysis complete.");
  }

  private List<String> deriveTopics(String title, String rawText) {
    String base = (title == null || title.isBlank()) ? rawText : title;
    if (base == null) return List.of("general");
    String[] tokens = base.toLowerCase()
        .replaceAll("[^a-z\\s]", " ")
        .split("\\s+");
    java.util.Set<String> stop = java.util.Set.of(
        "the", "and", "of", "to", "a", "an", "in", "for", "on", "by", "with", "from", "act", "bill");
    java.util.List<String> topics = new java.util.ArrayList<>();
    for (String token : tokens) {
      if (token.isBlank() || stop.contains(token)) continue;
      if (!topics.contains(token)) topics.add(token);
      if (topics.size() >= 5) break;
    }
    while (topics.size() < 5) topics.add("general");
    return topics;
  }

  private String deriveOnePager(String rawText) {
    if (rawText == null || rawText.isBlank()) return "No summary available.";
    String[] parts = rawText.split("(?<=[.!?])\\s+");
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < parts.length && i < 3; i++) {
      if (parts[i].isBlank()) continue;
      if (sb.length() > 0) sb.append(' ');
      sb.append(parts[i].trim());
    }
    String result = sb.toString().trim();
    if (result.isBlank()) {
      result = rawText.length() > 240 ? rawText.substring(0, 240).trim() + "..." : rawText.trim();
    }
    return result.isBlank() ? "No summary available." : result;
  }
}
