package govsim.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GraphRunner {
  private final List<Node> nodes;
  private final int maxRevisions;

  public GraphRunner(List<Node> nodes, int maxRevisions) {
    this.nodes = nodes;
    this.maxRevisions = Math.max(0, maxRevisions);
  }

  public void run(SimulationState state) throws Exception {
    Map<String, Integer> indexByName = new HashMap<>();
    for (int i = 0; i < nodes.size(); i++) {
      indexByName.put(nodes.get(i).name(), i);
    }

    int revisionCount = 0;
    for (int i = 0; i < nodes.size(); i++) {
      Node n = nodes.get(i);
      SimulationLogger.log("==> Running: " + n.name());
      n.run(state);
      SimulationLogger.log("==> Done: " + n.name());

      Object rerunFrom = state.vars.remove("rerunFromNode");
      if (rerunFrom != null) {
        String target = rerunFrom.toString();
        Integer targetIndex = indexByName.get(target);
        if (targetIndex == null) {
          SimulationLogger.log("[Loop] Unknown node: " + target);
          continue;
        }
        if (revisionCount >= maxRevisions) {
          SimulationLogger.log("[Loop] Max revisions reached. Skipping rerun.");
          continue;
        }
        revisionCount++;
        SimulationLogger.log("[Loop] Restarting at " + target + " (revision " + revisionCount + "/" + maxRevisions + ")");
        i = targetIndex - 1;
      }
    }
  }
}
