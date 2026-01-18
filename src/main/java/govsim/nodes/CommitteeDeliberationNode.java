package govsim.nodes;

import govsim.agents.AgentContext;
import govsim.agents.AgentOutput;
import govsim.config.AgentRegistry;
import govsim.core.Node;
import govsim.core.SimulationLogger;
import govsim.core.SimulationState;
import govsim.domain.Agency;

import java.util.LinkedHashMap;
import java.util.Map;

public class CommitteeDeliberationNode implements Node {
  private final AgentRegistry registry;

  public CommitteeDeliberationNode(AgentRegistry registry) {
    this.registry = registry;
  }

  @Override
  public String name() { return "CommitteeDeliberation"; }

  @Override
  public void run(SimulationState state) throws Exception {
    if (state.selectedAgencyId == null) {
      throw new IllegalStateException("No selected agency in state");
    }
    Agency agency = registry.agencyById(state.selectedAgencyId);
    if (agency == null) {
      throw new IllegalStateException("Unknown agency: " + state.selectedAgencyId);
    }

    Map<String, AgentOutput> outputs = new LinkedHashMap<>();
    for (String repId : agency.representativeIds()) {
      var rep = registry.repById(repId);
      SimulationLogger.log("[Committee] " + agency.name() + " -> " + rep.name());
      Map<String, String> inbox = state.directMessagesByRepId.getOrDefault(repId, Map.of());
      AgentContext ctx = new AgentContext(state.bill, state.billOnePager, state.floorSummary,
          inbox, state.vars);
      AgentOutput out = rep.act(ctx);
      String reason = out.reasons.stream().findFirst().orElse("");
      if (!reason.isBlank()) {
        String voteLabel = voteLabel(out.voteIntent);
        SimulationLogger.log("[Committee] Reason (" + voteLabel + "): " + reason);
      }
      outputs.put(repId, out);
      String logLine = "[Committee] " + rep.name() + " speaks: " + out.stance + " (vote " + out.voteIntent + ")";
      state.interactionLog.add(logLine);
      logLobbyTargets(state, rep.name(), out);
    }

    state.lastTurnOutputs = outputs;
    String summary = buildCommitteeSummary(agency, outputs);
    state.vars.put("committeeSummary", summary);
    state.floorSummary = summary;
  }

  private String buildCommitteeSummary(Agency agency, Map<String, AgentOutput> outputs) {
    StringBuilder sb = new StringBuilder();
    sb.append("Committee Summary (" + agency.name() + ")\n");
    for (var entry : outputs.entrySet()) {
      String repName = registry.repById(entry.getKey()).name();
      AgentOutput out = entry.getValue();
      String reason = out.reasons.stream().findFirst().orElse("");
      sb.append("- ").append(repName)
          .append(": ").append(out.stance)
          .append(" (vote ").append(out.voteIntent).append(")")
          .append(reason.isBlank() ? "" : " - " + reason)
          .append("\n");
    }
    return sb.toString().trim();
  }

  private void logLobbyTargets(SimulationState state, String repName, AgentOutput out) {
    if (out.targetsToLobby == null || out.targetsToLobby.isEmpty()) return;
    for (String target : out.targetsToLobby) {
      state.interactionLog.add("[Lobby] " + repName + " -> " + target);
    }
  }

  private String voteLabel(govsim.domain.Vote vote) {
    if (vote == null) return "abstain";
    return switch (vote) {
      case YES -> "pass";
      case NO -> "fail";
      case ABSTAIN -> "abstain";
    };
  }
}
