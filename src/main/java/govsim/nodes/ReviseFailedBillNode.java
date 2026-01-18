package govsim.nodes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import govsim.config.AgentRegistry;
import govsim.core.Node;
import govsim.core.SimulationLogger;
import govsim.core.SimulationState;
import govsim.domain.Agency;
import govsim.llm.LLMClient;
import govsim.llm.LLMRequestOptions;

import java.util.List;
import java.util.Map;

public class ReviseFailedBillNode implements Node {
  private static final int NUM_PREDICT_REVISE = 600;
  private final AgentRegistry registry;
  private final LLMClient llm;
  private final ObjectMapper mapper = new ObjectMapper();

  public ReviseFailedBillNode(AgentRegistry registry, LLMClient llm) {
    this.registry = registry;
    this.llm = llm;
  }

  @Override
  public String name() { return "ReviseFailedBill"; }

  @Override
  public void run(SimulationState state) throws Exception {
    String outcome = String.valueOf(state.vars.getOrDefault("finalOutcome", ""));
    if (!"KILLED".equals(outcome)) {
      return;
    }
    if (state.selectedAgencyId == null) {
      SimulationLogger.log("[Revise] No agency assigned. Skipping revisions.");
      return;
    }
    Agency agency = registry.agencyById(state.selectedAgencyId);
    if (agency == null) {
      SimulationLogger.log("[Revise] Unknown agency. Skipping revisions.");
      return;
    }

    String committeeSummary = String.valueOf(state.vars.getOrDefault("committeeSummary", ""));
    String floorSummary = state.floorSummary == null ? "" : state.floorSummary;

    String prompt = """
You are the drafting team for the %s. The bill failed. Revise the bill to address likely objections
while staying within the agency's scope (%s).

BILL:
%s

COMMITTEE SUMMARY:
%s

FLOOR SUMMARY:
%s

Return STRICT JSON with keys:
- revisedBillText: a concise revised bill (<= 200 words)
- summary: 1-2 sentence summary of the changes
- keyChanges: array of 3-5 short strings

No extra keys. No markdown.
""".formatted(agency.name(), String.join(", ", agency.scopeKeywords()), state.bill.rawText(),
        committeeSummary, floorSummary);

    String json = llm.generateJson(prompt, LLMRequestOptions.withNumPredict(NUM_PREDICT_REVISE));
    JsonNode root;
    try {
      root = mapper.readTree(json);
    } catch (JsonProcessingException e) {
      SimulationLogger.log("[Revise] Invalid JSON from LLM. Skipping revisions.");
      return;
    }

    String revisedText = root.path("revisedBillText").asText("").trim();
    String summary = root.path("summary").asText("").trim();
    List<String> keyChanges = List.of();
    JsonNode changesNode = root.get("keyChanges");
    if (changesNode != null && changesNode.isArray()) {
      keyChanges = mapper.convertValue(changesNode, new TypeReference<>() {});
    }

    if (revisedText.isBlank()) {
      SimulationLogger.log("[Revise] Revised bill text is empty. Skipping revisions.");
      return;
    }

    state.vars.put("revisedBillText", revisedText);
    state.vars.put("revisedBillSummary", summary);
    state.vars.put("revisedBillChanges", keyChanges);

    SimulationLogger.log("[Revise] Drafted revisions by " + agency.name() + ".");
    if (!summary.isBlank()) {
      SimulationLogger.log("[Revise] Summary: " + summary);
    }
    if (!keyChanges.isEmpty()) {
      SimulationLogger.log("[Revise] Key changes: " + String.join("; ", keyChanges));
    }

    state.billOnePager = buildRevisedOnePager(summary, keyChanges, revisedText);
    state.floorSummary = "";
    state.lastTurnOutputs.clear();
    state.voteResult = null;
    state.vars.put("finalOutcome", "REVISED_PENDING");
    state.vars.put("rerunFromNode", "CommitteeDeliberation");
  }

  private String buildRevisedOnePager(String summary, List<String> keyChanges, String revisedText) {
    StringBuilder sb = new StringBuilder();
    if (!summary.isBlank()) {
      sb.append(summary.trim());
    }
    if (keyChanges != null && !keyChanges.isEmpty()) {
      if (sb.length() > 0) sb.append("\n");
      sb.append("Key changes: ").append(String.join("; ", keyChanges));
    }
    if (sb.length() == 0 && revisedText != null) {
      String trimmed = revisedText.trim();
      sb.append(trimmed.length() > 600 ? trimmed.substring(0, 600) + "..." : trimmed);
    }
    return sb.toString().trim();
  }
}
