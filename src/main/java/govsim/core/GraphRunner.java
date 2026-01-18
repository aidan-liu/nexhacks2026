package govsim.core;

import java.util.List;

public class GraphRunner {
  private final List<Node> nodes;

  public GraphRunner(List<Node> nodes) { this.nodes = nodes; }

  public void run(SimulationState state) throws Exception {
    for (Node n : nodes) {
      SimulationLogger.log("==> Running: " + n.name());
      n.run(state);
      SimulationLogger.log("==> Done: " + n.name());
    }
  }
}
