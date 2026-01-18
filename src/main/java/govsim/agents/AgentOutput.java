package govsim.agents;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import govsim.domain.Vote;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class AgentOutput {
  public String speech;
  public List<String> proposedAmendments = new ArrayList<>();
  public String stance;          // support|oppose|undecided
  public Vote voteIntent;        // YES|NO|ABSTAIN
  public Double confidence;      // 0..1
  public List<String> reasons = new ArrayList<>();
  public List<String> targetsToLobby = new ArrayList<>();

  public static AgentOutput fromJson(String json) throws Exception {
    ObjectMapper mapper = new ObjectMapper();
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
    JsonNode root = mapper.readTree(json);
    if (root == null || !root.isObject()) {
      throw new IllegalArgumentException("Expected JSON object for AgentOutput");
    }
    ObjectNode normalized = ((ObjectNode) root).deepCopy();
    normalizeStringArray(normalized, "proposedAmendments");
    normalizeStringArray(normalized, "reasons");
    normalizeStringArray(normalized, "targetsToLobby");
    normalizeTextField(normalized, "stance", true);
    normalizeTextField(normalized, "voteIntent", false);
    normalizeNumericField(normalized, "confidence");

    AgentOutput out = mapper.treeToValue(normalized, AgentOutput.class);

    List<String> missing = new ArrayList<>();
    if (out.speech == null || out.speech.isBlank()) missing.add("speech");
    if (out.stance == null || out.stance.isBlank()) missing.add("stance");
    if (out.voteIntent == null) missing.add("voteIntent");
    if (out.confidence == null) missing.add("confidence");
    if (out.reasons == null) out.reasons = new ArrayList<>();
    if (out.proposedAmendments == null) out.proposedAmendments = new ArrayList<>();
    if (out.targetsToLobby == null) out.targetsToLobby = new ArrayList<>();

    if (!missing.isEmpty()) {
      throw new IllegalArgumentException("Missing required fields: " + String.join(", ", missing));
    }
    if (out.confidence < 0 || out.confidence > 1) {
      throw new IllegalArgumentException("confidence out of range: " + out.confidence);
    }
    Set<String> allowedStances = new HashSet<>(List.of("support", "oppose", "undecided"));
    if (!allowedStances.contains(out.stance)) {
      throw new IllegalArgumentException("Invalid stance: " + out.stance);
    }
    return out;
  }

  private static void normalizeStringArray(ObjectNode root, String field) {
    JsonNode node = root.get(field);
    ArrayNode array = root.arrayNode();
    if (node == null || node.isNull()) {
      root.set(field, array);
      return;
    }
    if (node.isArray()) {
      for (JsonNode item : node) {
        array.add(extractString(item));
      }
      root.set(field, array);
      return;
    }
    array.add(extractString(node));
    root.set(field, array);
  }

  private static void normalizeTextField(ObjectNode root, String field, boolean lowerCase) {
    JsonNode node = root.get(field);
    if (node == null || node.isNull()) return;
    if (node.isTextual()) {
      String text = node.asText().trim();
      root.put(field, lowerCase ? text.toLowerCase() : text.toUpperCase());
    }
  }

  private static void normalizeNumericField(ObjectNode root, String field) {
    JsonNode node = root.get(field);
    if (node == null || node.isNull()) return;
    if (node.isTextual()) {
      try {
        double value = Double.parseDouble(node.asText().trim());
        root.put(field, value);
      } catch (NumberFormatException ignored) {
        // Keep as-is; validation will fail.
      }
    }
  }

  private static String extractString(JsonNode node) {
    if (node == null || node.isNull()) return "";
    if (node.isTextual() || node.isNumber() || node.isBoolean()) {
      return node.asText();
    }
    if (node.isObject()) {
      ObjectNode obj = (ObjectNode) node;
      for (String key : List.of("text", "title", "label", "name", "summary", "amendment", "proposal", "target")) {
        JsonNode val = obj.get(key);
        if (val != null && val.isTextual() && !val.asText().isBlank()) {
          return val.asText();
        }
      }
    }
    return node.toString();
  }
}
