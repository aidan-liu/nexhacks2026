package govsim.agents;

import govsim.domain.Agency;
import govsim.llm.LLMClient;
import govsim.llm.PromptBuilder;

import java.util.Collection;

public class JudgeAgent {
  private final LLMClient llm;
  private final PromptBuilder prompts;

  public JudgeAgent(LLMClient llm, PromptBuilder prompts) {
    this.llm = llm;
    this.prompts = prompts;
  }

  public JudgeDecision decide(AgentContext ctx, Collection<Agency> agencies) throws Exception {
    String prompt = prompts.buildJudgePrompt(ctx, agencies);
    String json = llm.generateJson(prompt);
    return JudgeDecision.fromJson(json);
  }
}
