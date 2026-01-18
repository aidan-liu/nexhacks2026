package govsim.agents;

import govsim.llm.LLMClient;
import govsim.llm.PromptBuilder;
import govsim.memory.MemoryStore;

public class PoliticianAgent extends Agent {
  private final PoliticianProfile profile;
  private final LLMClient llm;
  private final PromptBuilder prompts;

  public PoliticianAgent(String id, String name, PoliticianProfile profile,
                         MemoryStore memory, LLMClient llm, PromptBuilder prompts) {
    super(id, name, memory);
    this.profile = profile;
    this.llm = llm;
    this.prompts = prompts;
  }

  @Override
  public AgentOutput act(AgentContext ctx) throws Exception {
    var mem = memory.retrieveRelevant(ctx);
    String prompt = prompts.buildPoliticianTurnPrompt(this, profile, ctx, mem);
    String json = llm.generateJson(prompt);

    AgentOutput out = AgentOutput.fromJson(json);
    memory.updateFromTurn(ctx, out);
    return out;
  }

  public AgentOutput advocate(AgentContext ctx) throws Exception {
    var mem = memory.retrieveRelevant(ctx);
    String prompt = prompts.buildAdvocatePrompt(this, profile, ctx, mem);
    String json = llm.generateJson(prompt);

    AgentOutput out = AgentOutput.fromJson(json);
    memory.updateFromTurn(ctx, out);
    return out;
  }
}
