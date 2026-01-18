package govsim.config;

import govsim.agents.JudgeAgent;
import govsim.agents.PoliticianAgent;
import govsim.domain.Agency;

import java.util.Collection;
import java.util.Map;

public class AgentRegistry {
  private final Map<String, Agency> agencies;
  private final Map<String, PoliticianAgent> reps;
  private final JudgeAgent judge;

  public AgentRegistry(Map<String, Agency> agencies,
                       Map<String, PoliticianAgent> reps,
                       JudgeAgent judge) {
    this.agencies = agencies;
    this.reps = reps;
    this.judge = judge;
  }

  public Collection<Agency> agencies() { return agencies.values(); }
  public Agency agencyById(String id) { return agencies.get(id); }
  public Collection<PoliticianAgent> allReps() { return reps.values(); }
  public PoliticianAgent repById(String id) { return reps.get(id); }
  public JudgeAgent judge() { return judge; }
}
