package govsim.nodes;

import govsim.agents.AgentContext;
import govsim.agents.AgentOutput;
import govsim.config.AgentRegistry;
import govsim.core.Node;
import govsim.core.SimulationLogger;
import govsim.core.SimulationState;
import govsim.domain.Agency;
import govsim.domain.Vote;
import govsim.domain.VoteResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class PrimaryFloorDebateNode implements Node {
  private static final int MAX_SUMMARY_LINES = 12;
  private static final int AGENCIES_TO_VOTE = 7;
  private final AgentRegistry registry;

  public PrimaryFloorDebateNode(AgentRegistry registry) {
    this.registry = registry;
  }

  @Override
  public String name() { return "PrimaryFloorDebate"; }

  @Override
  public void run(SimulationState state) throws Exception {
    String summary = String.valueOf(state.vars.getOrDefault("committeeSummary", ""));

    Random rng = new Random(seedFromState(state));
    AgentOutput advocateOutput = null;
    String advocateId = chooseAdvocateId(state, rng);
    if (advocateId != null) {
      var advocate = registry.repById(advocateId);
      if (advocate != null) {
        SimulationLogger.log("[Advocate] " + advocate.name() + " is advocating for the bill...");
        AgentContext advocateCtx = new AgentContext(state.bill, state.billOnePager, summary, Map.of(), state.vars);
        advocateOutput = advocate.advocate(advocateCtx);
        SimulationLogger.log("[Advocate] " + advocate.name() + ": " + advocateOutput.speech);
        String reason = advocateOutput.reasons.stream().findFirst().orElse("");
        if (!reason.isBlank()) {
          String voteLabel = voteLabel(advocateOutput.voteIntent);
          SimulationLogger.log("[Advocate] Reason (" + voteLabel + "): " + reason);
        }
        state.interactionLog.add("[Advocate] " + advocate.name() + " speaks: " + advocateOutput.stance);
        logLobbyTargets(state, advocate.name(), advocateOutput);
        summary = appendSummary(summary, advocate.name(), advocateOutput);
      }
    }

    List<Agency> agencies = registry.agencies().stream()
        .sorted(Comparator.comparing(Agency::id))
        .toList();
    List<Agency> selectedAgencies = pickAgencies(agencies, AGENCIES_TO_VOTE, rng);
    SimulationLogger.log("[Floor] Agencies voting: " + selectedAgencies.stream().map(Agency::name).toList());

    Map<String, AgentOutput> outputs = new LinkedHashMap<>();
    for (Agency agency : selectedAgencies) {
      String repId = pickRepresentative(agency, rng);
      if (repId == null) continue;
      var rep = registry.repById(repId);
      if (rep == null) continue;
      SimulationLogger.log("[Floor] " + agency.name() + " -> " + rep.name() + " is taking the floor...");
      AgentContext ctx = new AgentContext(state.bill, state.billOnePager, summary, Map.of(), state.vars);
      AgentOutput out;
      if (repId.equals(advocateId) && advocateOutput != null) {
        out = advocateOutput;
      } else {
        out = rep.act(ctx);
      }
      String reason = out.reasons.stream().findFirst().orElse("");
      if (!reason.isBlank()) {
        String voteLabel = voteLabel(out.voteIntent);
        SimulationLogger.log("[Floor] Reason (" + voteLabel + "): " + reason);
      }
      outputs.put(repId, out);
      summary = appendSummary(summary, rep.name(), out);
      String logLine = "[Floor] " + rep.name() + " speaks: " + out.stance + " (vote " + out.voteIntent + ")";
      state.interactionLog.add(logLine);
      logLobbyTargets(state, rep.name(), out);
    }

    state.floorSummary = summary;
    state.lastTurnOutputs = outputs;
    state.voteResult = new VoteResult(toVotes(outputs));
  }

  private long seedFromState(SimulationState state) {
    if (state.bill == null || state.bill.id() == null) return 0L;
    return state.bill.id().hashCode();
  }

  private String chooseAdvocateId(SimulationState state, Random rng) {
    if (state.selectedAgencyId != null) {
      Agency agency = registry.agencyById(state.selectedAgencyId);
      if (agency != null && !agency.representativeIds().isEmpty()) {
        return pickFromList(agency.representativeIds(), rng);
      }
    }
    List<String> repIds = registry.allReps().stream()
        .map(r -> r.id())
        .sorted(Comparator.naturalOrder())
        .toList();
    return repIds.isEmpty() ? null : pickFromList(repIds, rng);
  }

  private List<Agency> pickAgencies(List<Agency> agencies, int count, Random rng) {
    List<Agency> copy = new ArrayList<>(agencies);
    for (int i = copy.size() - 1; i > 0; i--) {
      int j = rng.nextInt(i + 1);
      Agency tmp = copy.get(i);
      copy.set(i, copy.get(j));
      copy.set(j, tmp);
    }
    int limit = Math.min(count, copy.size());
    return copy.subList(0, limit);
  }

  private String pickRepresentative(Agency agency, Random rng) {
    List<String> reps = agency.representativeIds();
    return pickFromList(reps, rng);
  }

  private String pickFromList(List<String> items, Random rng) {
    if (items == null || items.isEmpty()) return null;
    return items.get(rng.nextInt(items.size()));
  }

  private Map<String, Vote> toVotes(Map<String, AgentOutput> outputs) {
    Map<String, Vote> votes = new LinkedHashMap<>();
    for (var entry : outputs.entrySet()) {
      Vote vote = entry.getValue().voteIntent == null ? Vote.ABSTAIN : entry.getValue().voteIntent;
      votes.put(entry.getKey(), vote);
    }
    return votes;
  }

  private String appendSummary(String existing, String repName, AgentOutput out) {
    String reason = out.reasons.stream().findFirst().orElse("");
    String line = repName + ": " + out.stance + ", vote " + out.voteIntent +
        (reason.isBlank() ? "" : " - " + reason);

    String combined = existing == null || existing.isBlank()
        ? line
        : existing + "\n" + line;

    List<String> lines = combined.lines().toList();
    if (lines.size() <= MAX_SUMMARY_LINES) return combined;

    return String.join("\n", lines.subList(lines.size() - MAX_SUMMARY_LINES, lines.size()));
  }

  private void logLobbyTargets(SimulationState state, String repName, AgentOutput out) {
    if (out.targetsToLobby == null || out.targetsToLobby.isEmpty()) return;
    for (String target : out.targetsToLobby) {
      state.interactionLog.add("[Lobby] " + repName + " -> " + target);
    }
  }

  private String voteLabel(Vote vote) {
    if (vote == null) return "abstain";
    return switch (vote) {
      case YES -> "pass";
      case NO -> "fail";
      case ABSTAIN -> "abstain";
    };
  }
}
