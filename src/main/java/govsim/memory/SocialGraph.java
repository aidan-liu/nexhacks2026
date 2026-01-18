package govsim.memory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * SocialGraph is a lightweight shared in-memory store of who knows whom,
 * along with committee membership and a few interaction counters.
 *
 * It intentionally avoids heavy retrieval infrastructure; consumers can render it into prompts.
 */
public class SocialGraph {
  public record RepInfo(String id, String name, String committeeId, String committeeName) {}

  public static final class Relationship {
    public final String otherId;
    public final String otherName;
    public String otherCommitteeId = "";
    public String otherCommitteeName = "";
    public int timesMet = 0;
    public int timesLobbied = 0;
    public long lastInteractionAtMs = 0L;
    public String lastInteractionType = "";
    public String lastNote = "";

    private Relationship(String otherId, String otherName) {
      this.otherId = otherId;
      this.otherName = otherName;
    }
  }

  private final Map<String, RepInfo> repsById = new HashMap<>();
  private final Map<String, Map<String, Relationship>> relationshipsBySelf = new HashMap<>();

  public void registerRepresentative(String repId, String repName, String committeeId, String committeeName) {
    repsById.put(repId, new RepInfo(repId, repName, safe(committeeId), safe(committeeName)));
  }

  public RepInfo repInfo(String repId) {
    return repsById.get(repId);
  }

  public void recordMeet(String aId, String bId) {
    recordInteraction(aId, bId, "meet", "");
    recordInteraction(bId, aId, "meet", "");
    learnCommittee(aId, bId);
    learnCommittee(bId, aId);
  }

  public void recordLobby(String lobbyistId, String targetId, String note) {
    Relationship rel = ensureRelationship(lobbyistId, targetId);
    rel.timesLobbied += 1;
    rel.lastInteractionAtMs = System.currentTimeMillis();
    rel.lastInteractionType = "lobby";
    rel.lastNote = safe(note);
  }

  public void learnCommittee(String selfId, String otherId) {
    RepInfo other = repsById.get(otherId);
    if (other == null) return;
    Relationship rel = ensureRelationship(selfId, otherId);
    rel.otherCommitteeId = safe(other.committeeId());
    rel.otherCommitteeName = safe(other.committeeName());
  }

  public List<Relationship> relationshipsFor(String selfId) {
    Map<String, Relationship> map = relationshipsBySelf.getOrDefault(selfId, Map.of());
    if (map.isEmpty()) return List.of();
    List<Relationship> rels = new ArrayList<>(map.values());
    rels.sort(Comparator.comparingLong((Relationship r) -> r.lastInteractionAtMs).reversed());
    return Collections.unmodifiableList(rels);
  }

  public Relationship relationshipFor(String selfId, String otherId) {
    Map<String, Relationship> map = relationshipsBySelf.get(selfId);
    if (map == null) return null;
    return map.get(otherId);
  }

  public Map<String, RepInfo> allReps() {
    return Collections.unmodifiableMap(repsById);
  }

  private void recordInteraction(String selfId, String otherId, String type, String note) {
    Relationship rel = ensureRelationship(selfId, otherId);
    rel.timesMet += 1;
    rel.lastInteractionAtMs = System.currentTimeMillis();
    rel.lastInteractionType = safe(type);
    if (note != null && !note.isBlank()) {
      rel.lastNote = safe(note);
    }
  }

  private Relationship ensureRelationship(String selfId, String otherId) {
    RepInfo other = repsById.get(otherId);
    if (other == null) {
      throw new IllegalArgumentException("Unknown rep: " + otherId);
    }
    relationshipsBySelf.computeIfAbsent(selfId, ignored -> new HashMap<>());
    Map<String, Relationship> inner = relationshipsBySelf.get(selfId);
    return inner.computeIfAbsent(otherId, ignored -> new Relationship(otherId, other.name()));
  }

  private static String safe(String s) {
    return s == null ? "" : s.trim();
  }
}
