package govsim.core;

import java.util.List;

public class GraphRunner {
  private final List<Node> nodes;

  public GraphRunner(List<Node> nodes) { this.nodes = nodes; }

  public void run(SimulationState state) throws Exception {
    for (Node n : nodes) {
      System.out.println("==> Running: " + n.name());
      n.run(state);
      System.out.println("==> Done: " + n.name());
    }
  }
}
