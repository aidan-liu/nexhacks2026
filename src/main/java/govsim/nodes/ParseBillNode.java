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
      throw new IllegalArgumentException("ParseBillNode missing required fields in JSON");
    }

    List<String> topics = mapper.convertValue(topicsNode, new TypeReference<>() {});
    Map<String, Object> attributes = mapper.convertValue(attributesNode, new TypeReference<>() {});
    double estimatedCost = costNode.asDouble();
    String onePager = onePagerNode.asText();

    state.bill.setTopics(topics);
    state.bill.setEstimatedCost(estimatedCost);
    state.bill.setAttributes(attributes);
    state.billOnePager = onePager;
    SimulationLogger.log("[ParseBill] Analysis complete.");
  }
}
