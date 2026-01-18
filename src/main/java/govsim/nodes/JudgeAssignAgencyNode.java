package govsim.nodes;

import govsim.agents.AgentContext;
import govsim.agents.JudgeDecision;
import govsim.config.AgentRegistry;
import govsim.core.Node;
import govsim.core.SimulationState;
import govsim.domain.Agency;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

public class JudgeAssignAgencyNode implements Node {
  private final AgentRegistry registry;

  public JudgeAssignAgencyNode(AgentRegistry registry) {
    this.registry = registry;
  }

  @Override
  public String name() { return "JudgeAssignAgency"; }

  @Override
  public void run(SimulationState state) throws Exception {
    if (state.bill == null) {
      throw new IllegalStateException("Missing bill in state");
    }
    System.out.println("[Judge] Selecting agency...");
    AgentContext ctx = new AgentContext(state.bill, state.billOnePager, state.floorSummary, Map.of(), state.vars);
    JudgeDecision decision;
    try {
      decision = registry.judge().decide(ctx, registry.agencies());
    } catch (Exception e) {
      System.out.println("[Judge] LLM decision invalid. Falling back to keyword match.");
      Agency fallback = fallbackAgency(state.bill.rawText());
      decision = new JudgeDecision();
      decision.selectedAgencyId = fallback.id();
      decision.rationale = "Fallback keyword match due to judge error.";
      decision.confidence = 0.3;
    }

    Agency selected = registry.agencyById(decision.selectedAgencyId);
    if (selected == null) {
      selected = fallbackAgency(state.bill.rawText());
      System.out.println("[Judge] LLM returned unknown agency. Falling back to keyword match.");
    }

    state.selectedAgencyId = selected.id();
    state.vars.put("judgeDecision", decision);
    System.out.println("[Judge] Selected agency: " + selected.name() + " (" + selected.id() + ")");
  }

  private Agency fallbackAgency(String billText) {
    String text = billText.toLowerCase();
    List<Agency> agencies = registry.agencies().stream().toList();
    return agencies.stream()
        .max(Comparator.comparingInt(a -> scoreAgency(a, text)))
        .orElseThrow(() -> new IllegalStateException("No agencies configured"));
  }

  private int scoreAgency(Agency agency, String text) {
    int score = 0;
    for (String keyword : agency.scopeKeywords()) {
      if (text.contains(keyword.toLowerCase())) score++;
    }
    return score;
  }
}
