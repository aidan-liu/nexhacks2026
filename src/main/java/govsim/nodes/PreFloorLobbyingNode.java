package govsim.nodes;

import govsim.agents.AgentOutput;
import govsim.config.AgentRegistry;
import govsim.core.Node;
import govsim.core.SimulationLogger;
import govsim.core.SimulationState;
import govsim.domain.Agency;
import govsim.domain.Vote;
import govsim.memory.SocialGraph;

import java.util.HashMap;
import java.util.Map;

/**
 * PreFloorLobbyingNode turns targetsToLobby into 1:1 direct messages before the floor debate.
 * It is intentionally lightweight: it does not run extra LLM calls; it just summarizes the lobbyist's reasons.
 */
public class PreFloorLobbyingNode implements Node {
  private final AgentRegistry registry;

  public PreFloorLobbyingNode(AgentRegistry registry) {
    this.registry = registry;
  }

  @Override
  public String name() { return "PreFloorLobbying"; }

  @Override
  public void run(SimulationState state) {
    if (state.lastTurnOutputs == null || state.lastTurnOutputs.isEmpty()) return;

    SimulationLogger.log("== SESSION: PRE-FLOOR LOBBYING ==");

    for (var entry : state.lastTurnOutputs.entrySet()) {
      String lobbyistId = entry.getKey();
      AgentOutput out = entry.getValue();
      if (out == null || out.targetsToLobby == null || out.targetsToLobby.isEmpty()) continue;

      var lobbyist = registry.repById(lobbyistId);
      if (lobbyist == null) continue;

      for (String targetRaw : out.targetsToLobby) {
        var target = registry.repByNameApprox(targetRaw);
        if (target == null) continue;
        if (target.id().equals(lobbyistId)) continue;

        String note = buildLobbyMessage(lobbyistId, lobbyist.name(), target.name(), out);
        state.directMessagesByRepId.computeIfAbsent(target.id(), ignored -> new HashMap<>())
            .put(lobbyist.name(), note);

        registry.socialGraph().recordLobby(lobbyistId, target.id(), note);

        String line = "[" + lobbyist.name() + "] persuades [" + target.name() + "]: " + note;
        SimulationLogger.log(line);
        state.interactionLog.add(line);

        // Relationship diagnostics log
        state.relationshipLog.add("[DM] " + lobbyist.name() + " -> " + target.name() + ": " + note);
        SocialGraph.Relationship rel = registry.socialGraph().relationshipFor(lobbyistId, target.id());
        if (rel != null) {
          state.relationshipLog.add("[Graph] " + lobbyist.name() + " knows " + target.name() +
              " committee=" + (rel.otherCommitteeName == null || rel.otherCommitteeName.isBlank() ? "(unknown)" : rel.otherCommitteeName) +
              " met=" + rel.timesMet + " lobbied=" + rel.timesLobbied);
        }
      }
    }
  }

  private String buildLobbyMessage(String lobbyistId, String lobbyistName, String targetName, AgentOutput out) {
    Agency committee = registry.committeeForRep(lobbyistId);
    String committeeName = committee == null ? "" : committee.name();

    String reason = (out.reasons == null || out.reasons.isEmpty()) ? "" : String.valueOf(out.reasons.get(0)).trim();
    if (reason.length() > 220) reason = reason.substring(0, 220) + "...";

    String vote = voteLabel(out.voteIntent);
    StringBuilder sb = new StringBuilder();
    sb.append("I’m ").append(out.stance == null ? "weighing this" : out.stance).append(" (").append(vote).append("). ");
    if (!committeeName.isBlank()) {
      sb.append("From ").append(committeeName).append(", ");
    }
    if (!reason.isBlank()) {
      sb.append(reason);
    } else {
      sb.append("I think this is the pragmatic path given our constraints.");
    }
    return sb.toString().trim();
  }

  private String voteLabel(Vote vote) {
    if (vote == null) return "vote ABSTAIN";
    return switch (vote) {
      case YES -> "vote YES";
      case NO -> "vote NO";
      case ABSTAIN -> "vote ABSTAIN";
    };
  }
}
