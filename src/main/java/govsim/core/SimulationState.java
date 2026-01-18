package govsim.core;

import govsim.agents.AgentOutput;
import govsim.domain.Bill;
import govsim.domain.VoteResult;

import java.util.HashMap;
import java.util.Map;

public class SimulationState {
  public Bill bill;

  public String selectedAgencyId;
  public String billOnePager = "";
  public String floorSummary = "";

  public Map<String, AgentOutput> lastTurnOutputs = new HashMap<>();
  public VoteResult voteResult;

  public InteractionLog interactionLog = new InteractionLog();
  public Map<String, Object> vars = new HashMap<>();
}
