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

Return STRICT JSON with keys:
speech (string), proposedAmendments (array of strings), stance ("support"|"oppose"|"undecided"),
voteIntent ("YES"|"NO"|"ABSTAIN"), confidence (number 0..1), reasons (array of strings),
targetsToLobby (array of strings). Arrays must contain only strings, not objects.
Keep speech 120-180 words. reasons must have 2-4 items, each 1-2 sentences.
proposedAmendments max 2 items. targetsToLobby max 2 items.
Include at least one concrete statistic in the speech or reasons if possible.
When you use a fact, explicitly mention its source and add 1-2 sentences of context or impact.
No extra keys. No markdown.
""".formatted(agent.name(), profile.party, profile.ideology, profile.redLines, profile.petIssues, profile.speakingStyle,
              ctx.billOnePager, ctx.floorSummary, memory, factsPack);
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

Return STRICT JSON with keys:
speech (string), proposedAmendments (array of strings), stance ("support"|"oppose"|"undecided"),
voteIntent ("YES"|"NO"|"ABSTAIN"), confidence (number 0..1), reasons (array of strings),
targetsToLobby (array of strings). Arrays must contain only strings, not objects.
Keep speech 120-180 words. reasons must have 2-4 items, each 1-2 sentences.
proposedAmendments max 2 items. targetsToLobby max 2 items.
Include at least one concrete statistic in the speech or reasons if possible.
When you use a fact, explicitly mention its source and add 1-2 sentences of context or impact.
No extra keys. No markdown.
""".formatted(agent.name(), profile.party, profile.ideology, profile.redLines, profile.petIssues, profile.speakingStyle,
        ctx.billOnePager, ctx.floorSummary, memory, factsPack);
  }

  public String buildJudgePrompt(AgentContext ctx, Collection<Agency> agencies) {
    String agencyList = agencies.stream()
        .map(a -> a.id() + ": " + a.name() + " (keywords: " + String.join(", ", a.scopeKeywords()) + ")")
        .collect(Collectors.joining("\n"));

    return """
You are the LLM Judge. Assign this bill to the best agency.

AGENCIES:
%s

BILL:
%s

Return STRICT JSON:
{ "selectedAgencyId": "...", "rationale": "...", "confidence": 0.0 }
If uncertain, set confidence to 0.5. rationale must be a short string.
No extra keys. Use selectedAgencyId exactly as listed above.
""".formatted(agencyList, ctx.bill.rawText());
  }

  private String factsPack(AgentContext ctx) {
    Object facts = ctx.runtime.get("factsPack");
    if (facts == null) return "(no facts provided)";
    String text = facts.toString().trim();
    return text.isBlank() ? "(no facts provided)" : text;
  }
}
