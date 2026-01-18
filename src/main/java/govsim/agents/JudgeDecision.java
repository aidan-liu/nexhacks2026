package govsim.agents;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

public class JudgeDecision {
  public String selectedAgencyId;
  public String rationale;
  public Double confidence;

  public static JudgeDecision fromJson(String json) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);

    JsonNode root = mapper.readTree(json);
    if (root == null || !root.isObject()) {
      throw new IllegalArgumentException("Expected JSON object for JudgeDecision");
    }
    ObjectNode obj = (ObjectNode) root;

    JudgeDecision out = new JudgeDecision();
    out.selectedAgencyId = firstText(obj, "selectedAgencyId", "agencyId", "selectedAgency");
    out.rationale = firstText(obj, "rationale", "reason");
    out.confidence = firstNumber(obj, "confidence", "score");

    if (out.selectedAgencyId == null || out.selectedAgencyId.isBlank()) {
      throw new IllegalArgumentException("Missing required fields: selectedAgencyId");
    }
    if (out.rationale == null || out.rationale.isBlank()) {
      out.rationale = "No rationale provided.";
    }
    if (out.confidence == null) {
      out.confidence = 0.5;
    }
    if (out.confidence < 0 || out.confidence > 1) {
      out.confidence = Math.max(0, Math.min(1, out.confidence));
    }
    return out;
  }

  private static String firstText(ObjectNode obj, String... keys) {
    for (String key : keys) {
      JsonNode node = obj.get(key);
      if (node != null && node.isTextual() && !node.asText().isBlank()) {
        return node.asText().trim();
      }
    }
    return null;
  }

  private static Double firstNumber(ObjectNode obj, String... keys) {
    for (String key : keys) {
      JsonNode node = obj.get(key);
      if (node == null || node.isNull()) continue;
      if (node.isNumber()) return node.asDouble();
      if (node.isTextual()) {
        try {
          return Double.parseDouble(node.asText().trim());
        } catch (NumberFormatException ignored) {
          return null;
        }
      }
    }
    return null;
  }
}
