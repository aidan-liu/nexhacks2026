package govsim.memory;

import govsim.agents.AgentContext;
import govsim.agents.AgentOutput;

import java.util.List;

/**
 * RelationshipMemoryStore composes an existing MemoryStore (currently SimpleMemoryStore)
 * with a shared SocialGraph to make committee/relationship knowledge available to the LLM.
 */
public class RelationshipMemoryStore implements MemoryStore {
  private static final int MAX_KNOWN_REPS = 10;

  private final MemoryStore base;
  private final SocialGraph socialGraph;
  private final String selfId;
  private final String selfName;
  private final String selfCommitteeId;
  private final String selfCommitteeName;

  public RelationshipMemoryStore(MemoryStore base, SocialGraph socialGraph,
                                 String selfId, String selfName,
                                 String selfCommitteeId, String selfCommitteeName) {
    this.base = base;
    this.socialGraph = socialGraph;
    this.selfId = selfId;
    this.selfName = selfName;
    this.selfCommitteeId = selfCommitteeId == null ? "" : selfCommitteeId.trim();
    this.selfCommitteeName = selfCommitteeName == null ? "" : selfCommitteeName.trim();
  }

  public SocialGraph socialGraph() { return socialGraph; }
  public String selfId() { return selfId; }
  public String selfName() { return selfName; }
  public String selfCommitteeId() { return selfCommitteeId; }
  public String selfCommitteeName() { return selfCommitteeName; }

  @Override
  public String retrieveRelevant(AgentContext ctx) {
    String baseText = base.retrieveRelevant(ctx);
    StringBuilder sb = new StringBuilder();
    sb.append(baseText == null ? "" : baseText.trim());
    if (sb.length() > 0) sb.append("\n\n");

    sb.append("SOCIAL_MEMORY:\n");
    sb.append("YOUR_COMMITTEE: ").append(selfCommitteeName.isBlank() ? "(unknown)" : selfCommitteeName)
        .append(selfCommitteeId.isBlank() ? "" : " (" + selfCommitteeId + ")")
        .append("\n");

    List<SocialGraph.Relationship> rels = socialGraph.relationshipsFor(selfId);
    if (rels.isEmpty()) {
      sb.append("KNOWN_REPS: (none)\n");
      return sb.toString().trim();
    }

    sb.append("KNOWN_REPS (most recent first):\n");
    int count = 0;
    for (SocialGraph.Relationship r : rels) {
      if (count >= MAX_KNOWN_REPS) break;
      String committee = r.otherCommitteeName == null || r.otherCommitteeName.isBlank()
          ? "(committee unknown)"
          : r.otherCommitteeName;
      sb.append("- ").append(r.otherName)
          .append(" (").append(committee).append(")")
          .append(", met ").append(r.timesMet).append("x")
          .append(", lobbied ").append(r.timesLobbied).append("x")
          .append(r.lastInteractionType == null || r.lastInteractionType.isBlank() ? "" : ", last=" + r.lastInteractionType)
          .append("\n");
      count++;
    }

    return sb.toString().trim();
  }

  @Override
  public void updateFromTurn(AgentContext ctx, AgentOutput out) {
    base.updateFromTurn(ctx, out);
  }
}

