package govsim.memory;

import govsim.agents.AgentContext;
import govsim.agents.AgentOutput;

public interface MemoryStore {
  String retrieveRelevant(AgentContext ctx);
  void updateFromTurn(AgentContext ctx, AgentOutput out);
}
