package govsim.agents;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
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
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    mapper.configure(DeserializationFeature.READ_UNKNOWN_ENUM_VALUES_AS_NULL, true);
    mapper.configure(MapperFeature.ACCEPT_CASE_INSENSITIVE_ENUMS, true);
    JsonNode root;
    try {
      root = mapper.readTree(json);
    } catch (Exception e) {
      String extracted = extractJsonObject(json);
      if (extracted == null) {
        return fallbackFromRaw(json);
      }
      try {
        root = mapper.readTree(extracted);
      } catch (Exception nested) {
        return fallbackFromRaw(json);
      }
    }
    if (root == null || !root.isObject()) {
      return fallbackFromRaw(json);
    }
    ObjectNode normalized = ((ObjectNode) root).deepCopy();
    normalizeStringArray(normalized, "proposedAmendments");
    normalizeStringArray(normalized, "reasons");
    normalizeStringArray(normalized, "targetsToLobby");
    normalizeTextField(normalized, "stance", true);
    normalizeTextField(normalized, "voteIntent", false);
    normalizeNumericField(normalized, "confidence");

    AgentOutput out;
    try {
      out = mapper.treeToValue(normalized, AgentOutput.class);
    } catch (Exception e) {
      return fallbackFromRaw(json);
    }

    fillDefaults(out);
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

  private static void fillDefaults(AgentOutput out) {
    if (out == null) return;
    if (out.proposedAmendments == null) out.proposedAmendments = new ArrayList<>();
    if (out.reasons == null) out.reasons = new ArrayList<>();
    if (out.targetsToLobby == null) out.targetsToLobby = new ArrayList<>();

    out.proposedAmendments = sanitizeList(out.proposedAmendments);
    out.reasons = sanitizeList(out.reasons);
    out.targetsToLobby = sanitizeList(out.targetsToLobby);

    if (out.voteIntent == null && out.stance != null && !out.stance.isBlank()) {
      out.voteIntent = voteFromStance(out.stance);
    }
    if (out.stance == null || out.stance.isBlank()) {
      out.stance = stanceFromVote(out.voteIntent);
    }

    Set<String> allowedStances = new HashSet<>(List.of("support", "oppose", "undecided"));
    if (!allowedStances.contains(out.stance)) {
      out.stance = stanceFromVote(out.voteIntent);
    }
    if (out.voteIntent == null) {
      out.voteIntent = Vote.ABSTAIN;
    }

    if (out.confidence == null || out.confidence < 0 || out.confidence > 1) {
      out.confidence = 0.5;
    }
    if (out.speech == null || out.speech.isBlank()) {
      out.speech = "No speech provided.";
    }
    if (out.reasons.isEmpty()) {
      out.reasons = new ArrayList<>(List.of(defaultReason(out.stance)));
    }
  }

  private static String stanceFromVote(Vote vote) {
    if (vote == null) return "undecided";
    return switch (vote) {
      case YES -> "support";
      case NO -> "oppose";
      case ABSTAIN -> "undecided";
    };
  }

  private static Vote voteFromStance(String stance) {
    if (stance == null) return Vote.ABSTAIN;
    return switch (stance.trim().toLowerCase()) {
      case "support" -> Vote.YES;
      case "oppose" -> Vote.NO;
      case "undecided" -> Vote.ABSTAIN;
      default -> Vote.ABSTAIN;
    };
  }

  private static AgentOutput fallbackFromRaw(String json) {
    AgentOutput out = new AgentOutput();
    out.stance = "undecided";
    out.voteIntent = Vote.ABSTAIN;
    out.confidence = 0.5;
    out.speech = "No speech provided.";
    out.reasons = new ArrayList<>(List.of(defaultReason(out.stance)));
    out.proposedAmendments = new ArrayList<>();
    out.targetsToLobby = new ArrayList<>();
    if (json != null) {
      String trimmed = json.strip();
      if (!trimmed.isEmpty()) {
        out.speech = trimmed.length() > 400 ? trimmed.substring(0, 400) + "..." : trimmed;
      }
    }
    return out;
  }
}
