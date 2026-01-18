package govsim.agents;

import govsim.memory.MemoryStore;

public abstract class Agent {
  protected final String id;
  protected final String name;
  protected final MemoryStore memory;

  protected Agent(String id, String name, MemoryStore memory) {
    this.id = id;
    this.name = name;
    this.memory = memory;
  }

  public String id() { return id; }
  public String name() { return name; }

  public abstract AgentOutput act(AgentContext ctx) throws Exception;
}
