package govsim.nodes;

import govsim.core.Node;
import govsim.core.SimulationLogger;
import govsim.core.SimulationState;
import govsim.domain.VoteResult;
import govsim.web.VoteBox;

import java.util.Scanner;

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
    SimulationLogger.log("[Decision] Outcome: " + outcome + " (yes=" + yes + ", no=" + no +
        ", abstain=" + result.abstainCount() + ")");

    if (outcome.equals("PASS") || outcome.equals("POPULAR_VOTE_REQUIRED")) {
      String finalOutcome = runPopularVote(state);
      state.vars.put("finalOutcome", finalOutcome);
      SimulationLogger.log("[Decision] Final outcome after popular vote: " + finalOutcome);
    }
  }

  private String runPopularVote(SimulationState state) {
    VoteBox voteBox = state.voteBox;
    if (voteBox == null) {
      return "KILLED";
    }
    voteBox.open();
    int port = state.pollingServer != null ? state.pollingServer.port() : 8080;
    SimulationLogger.log("[PopularVote] Open. Visit http://localhost:" + port + " to vote.");
    SimulationLogger.log("[PopularVote] Type yes/no to vote, or 'close' to end voting.");

    try (Scanner scanner = new Scanner(System.in)) {
      while (true) {
        String input = scanner.nextLine();
        if (input == null) continue;
        String trimmed = input.trim().toLowerCase();
        if (trimmed.equals("close") || trimmed.equals("done") || trimmed.equals("end")) {
          break;
        }
        if (trimmed.equals("yes") || trimmed.equals("y")) {
          voteBox.voteYes();
          SimulationLogger.log("[PopularVote] Recorded YES vote.");
        } else if (trimmed.equals("no") || trimmed.equals("n")) {
          voteBox.voteNo();
          SimulationLogger.log("[PopularVote] Recorded NO vote.");
        } else {
          SimulationLogger.log("[PopularVote] Enter yes/no or close.");
        }
      }
    }

    voteBox.close();
    VoteBox.VoteSnapshot snap = voteBox.snapshot();
    boolean passed = snap.yes > snap.no;
    SimulationLogger.log("[PopularVote] Results: YES=" + snap.yes + ", NO=" + snap.no + ".");
    return passed ? "PASS" : "KILLED";
  }
}
