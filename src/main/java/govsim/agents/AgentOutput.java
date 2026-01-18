package govsim.agents;

import com.fasterxml.jackson.core.JsonProcessingException;
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
    JsonNode root;
    try {
      root = mapper.readTree(json);
    } catch (JsonProcessingException e) {
      String extracted = extractJsonObject(json);
      if (extracted == null) {
        throw e;
      }
      root = mapper.readTree(extracted);
    }
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

    out.reasons = sanitizeList(out.reasons);
    out.proposedAmendments = sanitizeList(out.proposedAmendments);
    out.targetsToLobby = sanitizeList(out.targetsToLobby);

    if (out.reasons.isEmpty()) {
      out.reasons = new ArrayList<>(List.of(defaultReason(out.stance)));
    }

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
        addIfNotBlank(array, extractString(item));
      }
      root.set(field, array);
      return;
    }
    addIfNotBlank(array, extractString(node));
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

  private static String extractJsonObject(String json) {
    if (json == null) return null;
    int start = json.indexOf('{');
    int end = json.lastIndexOf('}');
    if (start < 0 || end <= start) return null;
    return json.substring(start, end + 1);
  }

  private static void addIfNotBlank(ArrayNode array, String value) {
    if (value == null) return;
    String trimmed = value.trim();
    if (!trimmed.isEmpty()) array.add(trimmed);
  }

  private static List<String> sanitizeList(List<String> items) {
    List<String> cleaned = new ArrayList<>();
    for (String item : items) {
      if (item == null) continue;
      String trimmed = item.trim();
      if (!trimmed.isEmpty()) cleaned.add(trimmed);
    }
    return cleaned;
  }

  private static String defaultReason(String stance) {
    if (stance == null) return "No reason provided.";
    return switch (stance) {
      case "support" -> "Believes the bill advances key priorities.";
      case "oppose" -> "Believes the bill conflicts with key priorities.";
      case "undecided" -> "Needs more evidence to decide.";
      default -> "No reason provided.";
    };
  }
}
