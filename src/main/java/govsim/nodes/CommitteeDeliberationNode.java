package govsim.nodes;

import govsim.agents.AgentContext;
import govsim.agents.AgentOutput;
import govsim.config.AgentRegistry;
import govsim.core.Node;
import govsim.core.SimulationLogger;
import govsim.core.SimulationState;
import govsim.domain.Agency;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

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
    Random rng = new Random(seedFromState(state));
    for (String repId : agency.representativeIds()) {
      var rep = registry.repById(repId);
      SimulationLogger.log("[Committee] " + agency.name() + " -> " + rep.name());
      Map<String, Object> runtime = new java.util.HashMap<>(state.vars);
      String debateTarget = pickDebateTarget(runtime, rng);
      if (debateTarget != null) {
        runtime.put("debateTarget", debateTarget);
      }
      AgentContext ctx = new AgentContext(state.bill, state.billOnePager, state.floorSummary,
          Map.of(), runtime);
      AgentOutput out = rep.act(ctx);
      updateSpeaker(state, rep.id(), rep.name(), out.speech);
      String reason = out.reasons.stream().findFirst().orElse("");
      if (!reason.isBlank()) {
        String voteLabel = voteLabel(out.voteIntent);
        SimulationLogger.log("[Committee] Reason (" + voteLabel + "): " + reason);
      }
      outputs.put(repId, out);
      addPeerReasoning(state, agency.name(), rep.name(), out);
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

  private void updateSpeaker(SimulationState state, String id, String name, String speech) {
    Object storeObj = state.vars.get("statusStore");
    if (storeObj instanceof govsim.web.StatusStore store) {
      String text = speech == null ? "" : speech.trim();
      if (text.length() > 180) {
        text = text.substring(0, 180) + "...";
      }
      store.setSpeaker(id, name, text);
    }
  }

  private long seedFromState(SimulationState state) {
    if (state.bill == null || state.bill.id() == null) return 0L;
    return state.bill.id().hashCode();
  }

  private String pickDebateTarget(Map<String, Object> runtime, Random rng) {
    Object existing = runtime.get("peerReasoningLog");
    if (!(existing instanceof List<?> list) || list.isEmpty()) {
      return null;
    }
    int idx = rng.nextInt(list.size());
    return list.get(idx).toString();
  }

  @SuppressWarnings("unchecked")
  private void addPeerReasoning(SimulationState state, String agencyName, String repName, AgentOutput out) {
    Object existing = state.vars.get("peerReasoningLog");
    List<String> log;
    if (existing instanceof List<?>) {
      log = (List<String>) existing;
    } else {
      log = new ArrayList<>();
      state.vars.put("peerReasoningLog", log);
    }
    String reasons = out.reasons == null ? "" : String.join(" | ", out.reasons);
    String line = repName + " (" + agencyName + "): " + out.stance + " (vote " + out.voteIntent + ")"
        + (reasons.isBlank() ? "" : " - " + reasons);
    log.add(line);
    if (log.size() > 30) {
      log.remove(0);
    }
  }
}
