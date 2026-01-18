package govsim.agents;

import govsim.llm.LLMClient;
import govsim.llm.LLMRequestOptions;
import govsim.llm.PromptBuilder;
import govsim.memory.MemoryStore;
import govsim.core.SimulationLogger;

public class PoliticianAgent extends Agent {
  private final PoliticianProfile profile;
  private final LLMClient llm;
  private final PromptBuilder prompts;
  private static final int NUM_PREDICT_DEFAULT = 500;
  private static final int NUM_PREDICT_RETRY = 800;

  public PoliticianAgent(String id, String name, PoliticianProfile profile,
                         MemoryStore memory, LLMClient llm, PromptBuilder prompts) {
    super(id, name, memory);
    this.profile = profile;
    this.llm = llm;
    this.prompts = prompts;
  }

  @Override
  public AgentOutput act(AgentContext ctx) throws Exception {
    return runTurn(ctx, false);
  }

  public AgentOutput advocate(AgentContext ctx) throws Exception {
    return runTurn(ctx, true);
  }

  private AgentOutput runTurn(AgentContext ctx, boolean advocateMode) throws Exception {
    var mem = memory.retrieveRelevant(ctx);
    String prompt = advocateMode
        ? prompts.buildAdvocatePrompt(this, profile, ctx, mem)
        : prompts.buildPoliticianTurnPrompt(this, profile, ctx, mem);

    String json = llm.generateJson(prompt, LLMRequestOptions.withNumPredict(NUM_PREDICT_DEFAULT));
    try {
      AgentOutput out = AgentOutput.fromJson(json);
      memory.updateFromTurn(ctx, out);
      return out;
    } catch (Exception e) {
      SimulationLogger.log("[LLM] Invalid JSON from " + name + ". Retrying...");
      String retryPrompt = prompt + "\nReturn compact JSON only. No extra text.";
      String retryJson = llm.generateJson(retryPrompt, LLMRequestOptions.withNumPredict(NUM_PREDICT_RETRY));
      AgentOutput out = AgentOutput.fromJson(retryJson);
      memory.updateFromTurn(ctx, out);
      return out;
    }
  }
}
