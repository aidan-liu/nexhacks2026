package govsim.web;

import govsim.memory.SocialGraph;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * AgentStateStore is a lightweight in-memory diagnostics store for:
 * - last prompt sent to each agent
 * - last rendered memory block for each agent
 * - the shared SocialGraph (who knows whom / committees)
 *
 * This is meant for UI/inspection and intentionally avoids persistence/bloat.
 */
public class AgentStateStore {
  private static final int MAX_TEXT_CHARS = 12_000;

  private final SocialGraph socialGraph;
  private final Map<String, String> lastPromptById = new HashMap<>();
  private final Map<String, String> lastMemoryById = new HashMap<>();

  public AgentStateStore(SocialGraph socialGraph) {
    this.socialGraph = socialGraph;
  }

  public void recordPrompt(String agentId, String prompt) {
    if (agentId == null || agentId.isBlank()) return;
    lastPromptById.put(agentId, truncate(prompt));
  }

  public void recordMemory(String agentId, String memory) {
    if (agentId == null || agentId.isBlank()) return;
    lastMemoryById.put(agentId, truncate(memory));
  }

  public Map<String, Object> snapshot(boolean includePrompts) {
    Map<String, Object> payload = new HashMap<>();
    List<Object> agents = new ArrayList<>();

    for (SocialGraph.RepInfo rep : socialGraph.allReps().values()) {
      Map<String, Object> a = new HashMap<>();
      a.put("id", rep.id());
      a.put("name", rep.name());
      a.put("committeeId", rep.committeeId());
      a.put("committeeName", rep.committeeName());

      String mem = lastMemoryById.get(rep.id());
      if (mem != null) a.put("lastMemory", mem);
      if (includePrompts) {
        String prompt = lastPromptById.get(rep.id());
        if (prompt != null) a.put("lastPrompt", prompt);
      }

      List<Object> known = new ArrayList<>();
      for (SocialGraph.Relationship r : socialGraph.relationshipsFor(rep.id())) {
        Map<String, Object> k = new HashMap<>();
        k.put("id", r.otherId);
        k.put("name", r.otherName);
        k.put("committeeId", r.otherCommitteeId);
        k.put("committeeName", r.otherCommitteeName);
        k.put("timesMet", r.timesMet);
        k.put("timesLobbied", r.timesLobbied);
        k.put("lastInteractionType", r.lastInteractionType);
        k.put("lastNote", r.lastNote);
        known.add(k);
      }
      a.put("known", known);

      agents.add(a);
    }

    payload.put("agents", agents);
    return payload;
  }

  private static String truncate(String s) {
    if (s == null) return null;
    String trimmed = s.trim();
    if (trimmed.length() <= MAX_TEXT_CHARS) return trimmed;
    return trimmed.substring(0, MAX_TEXT_CHARS) + "\n...(truncated)";
  }
}

