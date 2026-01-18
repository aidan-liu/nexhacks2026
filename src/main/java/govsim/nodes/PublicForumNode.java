package govsim.nodes;

import govsim.core.Node;
import govsim.core.SimulationState;

public class PublicForumNode implements Node {
  @Override
  public String name() { return "PublicForum"; }

  @Override
  public void run(SimulationState state) {
    state.vars.put("publicForumNote", "Public forum input not simulated yet.");
  }
}
