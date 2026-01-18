package govsim.nodes;

import govsim.core.Node;
import govsim.core.SimulationState;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FinalizeNode implements Node {
  @Override
  public String name() { return "Finalize"; }

  @Override
  public void run(SimulationState state) {
    writeInteractionLog(state);
  }

  private void writeInteractionLog(SimulationState state) {
    try {
      String content = String.join("\n", state.interactionLog.entries());
      Files.writeString(Path.of("interaction.log"), content);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write interaction.log", e);
    }
  }
}
