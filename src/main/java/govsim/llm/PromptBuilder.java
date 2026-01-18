package govsim.llm;

import govsim.agents.AgentContext;
import govsim.agents.PoliticianAgent;
import govsim.agents.PoliticianProfile;
import govsim.domain.Agency;

import java.util.Collection;
import java.util.stream.Collectors;

public class PromptBuilder {
  public String buildPoliticianTurnPrompt(PoliticianAgent agent, PoliticianProfile profile,
                                         AgentContext ctx, String memory) {
    String factsPack = factsPack(ctx);
    return """
You are %s, a government representative.

PERSONA:
- Party: %s
- Ideology: %s
- Red lines: %s
- Pet issues: %s
- Speaking style: %s

BILL ONE-PAGER:
%s

FLOOR SUMMARY:
%s

YOUR MEMORY:
%s

FACTS PACK (use at least one statistic if relevant, cite the source):
%s

PEER REASONING (recent statements by other representatives):
%s

DEBATE TARGET (if present, respond directly with a rebuttal or support):
%s

Return STRICT JSON with keys:
speech (string), proposedAmendments (array of strings), stance ("support"|"oppose"|"undecided"),
voteIntent ("YES"|"NO"|"ABSTAIN"), confidence (number 0..1), reasons (array of strings),
targetsToLobby (array of strings). Arrays must contain only strings, not objects.
Keep speech 120-180 words. reasons must have 2-4 items, each 1-2 sentences.
proposedAmendments max 2 items. targetsToLobby max 2 items.
Include at least one concrete statistic in the speech or reasons if possible.
When you use a fact, explicitly mention its source and add 1-2 sentences of context or impact.
When relevant, reference other representatives by name and their stated reasons.
No extra keys. No markdown.
""".formatted(agent.name(), profile.party, profile.ideology, profile.redLines, profile.petIssues, profile.speakingStyle,
              ctx.billOnePager, ctx.floorSummary, memory, factsPack, peerReasoning(ctx), debateTarget(ctx));
  }

  public String buildAdvocatePrompt(PoliticianAgent agent, PoliticianProfile profile,
                                    AgentContext ctx, String memory) {
    String factsPack = factsPack(ctx);
    return """
You are %s, the bill advocate on the primary floor. Your job is to clearly explain the bill,
highlight its strongest benefits, and persuade others to support it.

PERSONA:
- Party: %s
- Ideology: %s
- Red lines: %s
- Pet issues: %s
- Speaking style: %s

BILL ONE-PAGER:
%s

FLOOR SUMMARY:
%s

YOUR MEMORY:
%s

FACTS PACK (use at least one statistic, cite the source):
%s

PEER REASONING (recent statements by other representatives):
%s

DEBATE TARGET (if present, respond directly with a rebuttal or support):
%s

Return STRICT JSON with keys:
speech (string), proposedAmendments (array of strings), stance ("support"|"oppose"|"undecided"),
voteIntent ("YES"|"NO"|"ABSTAIN"), confidence (number 0..1), reasons (array of strings),
targetsToLobby (array of strings). Arrays must contain only strings, not objects.
Keep speech 120-180 words. reasons must have 2-4 items, each 1-2 sentences.
proposedAmendments max 2 items. targetsToLobby max 2 items.
Include at least one concrete statistic in the speech or reasons if possible.
When you use a fact, explicitly mention its source and add 1-2 sentences of context or impact.
When relevant, reference other representatives by name and their stated reasons.
No extra keys. No markdown.
""".formatted(agent.name(), profile.party, profile.ideology, profile.redLines, profile.petIssues, profile.speakingStyle,
        ctx.billOnePager, ctx.floorSummary, memory, factsPack, peerReasoning(ctx), debateTarget(ctx));
  }

  public String buildJudgePrompt(AgentContext ctx, Collection<Agency> agencies) {
    String agencyList = agencies.stream()
        .map(a -> a.id() + ": " + a.name() + " (keywords: " + String.join(", ", a.scopeKeywords()) + ")")
        .collect(Collectors.joining("\n"));

    return """
You are the LLM Judge. Assign this bill to the best agency.
Evaluate every agency before choosing. Score each agency based on fit.

AGENCIES:
%s

BILL:
%s

Return STRICT JSON:
{
  "selectedAgencyId": "...",
  "rationale": "...",
  "confidence": 0.0,
  "scores": { "agencyId": 0.0, "...": 0.0 }
}
- scores must include every agencyId listed above (0.0 to 1.0).
- selectedAgencyId must be the highest score (break ties with best rationale).
- If uncertain, set confidence to 0.5. rationale must be a short string.
- Use selectedAgencyId exactly as listed above. No extra keys.
""".formatted(agencyList, ctx.bill.rawText());
  }

  private String factsPack(AgentContext ctx) {
    Object facts = ctx.runtime.get("factsPack");
    if (facts == null) return "(no facts provided)";
    String text = facts.toString().trim();
    return text.isBlank() ? "(no facts provided)" : text;
  }

  private String peerReasoning(AgentContext ctx) {
    Object log = ctx.runtime.get("peerReasoningLog");
    if (log == null) return "(none)";
    if (log instanceof java.util.List<?> list) {
      int start = Math.max(0, list.size() - 8);
      return list.subList(start, list.size()).stream()
          .map(Object::toString)
          .collect(java.util.stream.Collectors.joining("\n"));
    }
    String text = log.toString().trim();
    return text.isBlank() ? "(none)" : text;
  }

  private String debateTarget(AgentContext ctx) {
    Object target = ctx.runtime.get("debateTarget");
    if (target == null) return "(none)";
    String text = target.toString().trim();
    return text.isBlank() ? "(none)" : text;
  }
}
