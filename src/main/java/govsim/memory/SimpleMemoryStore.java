package govsim.memory;

import govsim.agents.AgentContext;
import govsim.agents.AgentOutput;

import java.util.ArrayList;
import java.util.List;

public class SimpleMemoryStore implements MemoryStore {
  private final List<String> longTermFacts = new ArrayList<>();
  private String rollingSummary = "";

  @Override
  public String retrieveRelevant(AgentContext ctx) {
    String facts = String.join("\n", longTermFacts.stream()
        .skip(Math.max(0, longTermFacts.size() - 5))
        .toList());
    return "ROLLING_SUMMARY:\n" + rollingSummary + "\n\nRECENT_FACTS:\n" + facts;
  }

  @Override
  public void updateFromTurn(AgentContext ctx, AgentOutput out) {
    String firstReason = out.reasons.stream().findFirst().orElse("");
    rollingSummary = (rollingSummary + "\n- " + out.stance + ": " + firstReason).trim();
  }

  public void addFact(String fact) { longTermFacts.add(fact); }
}
