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

Return STRICT JSON with keys:
speech (string), proposedAmendments (array of strings), stance ("support"|"oppose"|"undecided"),
voteIntent ("YES"|"NO"|"ABSTAIN"), confidence (number 0..1), reasons (array of strings),
targetsToLobby (array of strings). Arrays must contain only strings, not objects.
Keep speech under 80 words. reasons must have 1-2 short items. proposedAmendments max 2 items. targetsToLobby max 2 items.
No extra keys. No markdown.
""".formatted(agent.name(), profile.party, profile.ideology, profile.redLines, profile.petIssues, profile.speakingStyle,
              ctx.billOnePager, ctx.floorSummary, memory);
  }

  public String buildAdvocatePrompt(PoliticianAgent agent, PoliticianProfile profile,
                                    AgentContext ctx, String memory) {
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

Return STRICT JSON with keys:
speech (string), proposedAmendments (array of strings), stance ("support"|"oppose"|"undecided"),
voteIntent ("YES"|"NO"|"ABSTAIN"), confidence (number 0..1), reasons (array of strings),
targetsToLobby (array of strings). Arrays must contain only strings, not objects.
Keep speech under 80 words. reasons must have 1-2 short items. proposedAmendments max 2 items. targetsToLobby max 2 items.
No extra keys. No markdown.
""".formatted(agent.name(), profile.party, profile.ideology, profile.redLines, profile.petIssues, profile.speakingStyle,
        ctx.billOnePager, ctx.floorSummary, memory);
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
}
