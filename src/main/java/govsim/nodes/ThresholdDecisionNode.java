package govsim.nodes;

import govsim.core.Node;
import govsim.core.SimulationState;
import govsim.domain.VoteResult;

public class ThresholdDecisionNode implements Node {
  @Override
  public String name() { return "ThresholdDecision"; }

  @Override
  public void run(SimulationState state) {
    VoteResult result = state.voteResult;
    if (result == null) {
      state.vars.put("finalOutcome", "UNKNOWN");
      return;
    }

    long yes = result.yesCount();
    long no = result.noCount();
    String outcome;
    if (yes > no) {
      outcome = "PASS";
    } else if (yes == no) {
      outcome = "POPULAR_VOTE_REQUIRED";
    } else {
      outcome = "KILLED";
    }

    state.vars.put("finalOutcome", outcome);
    System.out.println("[Decision] Outcome: " + outcome + " (yes=" + yes + ", no=" + no +
        ", abstain=" + result.abstainCount() + ")");
  }
}
