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
      String stageName = prettyStageName(n.name()) + " Stage";
      updateStage(state, stageName, true);
      SimulationLogger.log("==> Running: " + n.name());
      n.run(state);
      SimulationLogger.log("==> Done: " + n.name());
      updateStage(state, stageName, false);

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
    updateStage(state, "Complete", false);
  }

  private void updateStage(SimulationState state, String stage, boolean running) {
    Object storeObj = state.vars.get("statusStore");
    if (storeObj instanceof govsim.web.StatusStore store) {
      store.setStage(stage, running);
    }
  }

  private String prettyStageName(String raw) {
    if (raw == null || raw.isBlank()) return "Stage";
    String spaced = raw.replaceAll("([a-z])([A-Z])", "$1 $2");
    spaced = spaced.replaceAll("([A-Z])([A-Z][a-z])", "$1 $2");
    return spaced.trim();
  }
}
