package govsim.nodes;

import govsim.config.AgentRegistry;
import govsim.core.Node;
import govsim.core.SimulationLogger;
import govsim.core.SimulationState;
import govsim.domain.Agency;
import govsim.memory.SocialGraph;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * SessionMingleNode creates early-session 1:1 introductions where reps exchange committee info.
 * This seeds the shared SocialGraph so later lobbying can be targeted.
 */
public class SessionMingleNode implements Node {
  private final AgentRegistry registry;

  public SessionMingleNode(AgentRegistry registry) {
    this.registry = registry;
  }

  @Override
  public String name() { return "SessionMingle"; }

  @Override
  public void run(SimulationState state) {
    List<String> repIds = registry.allReps().stream()
        .map(r -> r.id())
        .sorted(Comparator.naturalOrder())
        .toList();
    if (repIds.size() < 2) return;

    SimulationLogger.log("== SESSION: NETWORKING ==");

    Random rng = new Random(seedFromState(state));
    Set<String> seenPairs = new HashSet<>();

    // A couple of quick rounds so reps learn more than one committee.
    int rounds = Math.min(2, Math.max(1, repIds.size() - 1));
    for (int round = 0; round < rounds; round++) {
      List<String> shuffled = new ArrayList<>(repIds);
      for (int i = shuffled.size() - 1; i > 0; i--) {
        int j = rng.nextInt(i + 1);
        String tmp = shuffled.get(i);
        shuffled.set(i, shuffled.get(j));
        shuffled.set(j, tmp);
      }

      for (int i = 0; i + 1 < shuffled.size(); i += 2) {
        String aId = shuffled.get(i);
        String bId = shuffled.get(i + 1);
        String key = aId.compareTo(bId) < 0 ? (aId + "|" + bId) : (bId + "|" + aId);
        if (!seenPairs.add(key)) continue;

        var a = registry.repById(aId);
        var b = registry.repById(bId);
        if (a == null || b == null) continue;

        Agency aCommittee = registry.committeeForRep(aId);
        Agency bCommittee = registry.committeeForRep(bId);
        String aC = aCommittee == null ? "(unknown committee)" : aCommittee.name();
        String bC = bCommittee == null ? "(unknown committee)" : bCommittee.name();

        registry.socialGraph().recordMeet(aId, bId);
        String line = "[Meet] " + a.name() + " meets " + b.name() + " (committees: " + aC + " / " + bC + ")";
        SimulationLogger.log(line);
        state.interactionLog.add(line);

        // Relationship diagnostics log
        state.relationshipLog.add(line);
        SocialGraph.Relationship aToB = registry.socialGraph().relationshipFor(aId, bId);
        SocialGraph.Relationship bToA = registry.socialGraph().relationshipFor(bId, aId);
        if (aToB != null) {
          state.relationshipLog.add("[Graph] " + a.name() + " knows " + b.name() +
              " committee=" + (aToB.otherCommitteeName == null || aToB.otherCommitteeName.isBlank() ? "(unknown)" : aToB.otherCommitteeName) +
              " met=" + aToB.timesMet + " lobbied=" + aToB.timesLobbied);
        }
        if (bToA != null) {
          state.relationshipLog.add("[Graph] " + b.name() + " knows " + a.name() +
              " committee=" + (bToA.otherCommitteeName == null || bToA.otherCommitteeName.isBlank() ? "(unknown)" : bToA.otherCommitteeName) +
              " met=" + bToA.timesMet + " lobbied=" + bToA.timesLobbied);
        }
      }
    }
  }

  private long seedFromState(SimulationState state) {
    if (state.bill == null || state.bill.id() == null) return 0L;
    return state.bill.id().hashCode();
  }
}
