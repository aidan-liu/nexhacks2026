package govsim.nodes;

import govsim.core.Node;
import govsim.core.SimulationState;
import govsim.web.AgentStateStore;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class FinalizeNode implements Node {
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public String name() { return "Finalize"; }

  @Override
  public void run(SimulationState state) {
    writeInteractionLog(state);
    writeRelationshipLog(state);
    writeSocialGraphSnapshot(state);
  }

  private void writeInteractionLog(SimulationState state) {
    try {
      String content = String.join("\n", state.interactionLog.entries());
      Files.writeString(Path.of("interaction.log"), content);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write interaction.log", e);
    }
  }

  private void writeRelationshipLog(SimulationState state) {
    try {
      String content = state.relationshipLog == null
          ? ""
          : String.join("\n", state.relationshipLog.entries());
      Files.writeString(Path.of("relationship.log"), content);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write relationship.log", e);
    }
  }

  private void writeSocialGraphSnapshot(SimulationState state) {
    try {
      Object storeObj = state.vars == null ? null : state.vars.get("agentStateStore");
      if (!(storeObj instanceof AgentStateStore store)) return;
      String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(store.snapshot(false));
      Files.writeString(Path.of("social_graph.json"), json);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to write social_graph.json", e);
    }
  }
}
