package govsim.llm;

import govsim.agents.AgentContext;
import govsim.agents.PoliticianAgent;
import govsim.agents.PoliticianProfile;
import govsim.domain.Agency;

import java.util.Collection;

/**
 * Prompt builder that selectively compresses large text blocks using bear-1.
 * Keeps instructions and persona intact, only compresses content like bills, facts, summaries.
 */
public class CompressingPromptBuilder extends PromptBuilder {
    private final Bear1Client compressor;
    private final CompressionMetrics metrics;
    private final boolean enabled;

    public CompressingPromptBuilder(
            Bear1Client compressor,
            CompressionMetrics metrics,
            boolean enabled) {
        this.compressor = compressor;
        this.metrics = metrics;
        this.enabled = enabled && compressor != null && compressor.isConfigured();
    }

    /**
     * Build a politician turn prompt with optional compression.
     * Compresses: bill one-pager, floor summary, facts pack
     * Keeps raw: persona, instructions, return format
     */
    @Override
    public String buildPoliticianTurnPrompt(PoliticianAgent agent, PoliticianProfile profile,
                                            AgentContext ctx, String memory) {
        if (!enabled) {
            return super.buildPoliticianTurnPrompt(agent, profile, ctx, memory);
        }

        try {
            // Compress individual sections
            String compressedOnePager = compressIfNeeded(ctx.billOnePager, "bill");
            String compressedFloorSummary = compressIfNeeded(ctx.floorSummary, "summary");
            String compressedMemory = compressIfNeeded(memory, "memory");

            // Build facts pack and compress
            String factsPack = extractFactsPack(ctx);
            String compressedFacts = compressIfNeeded(factsPack, "facts");

            // Build prompt with compressed sections
            return buildCompressedPoliticianPrompt(agent, profile, compressedOnePager,
                    compressedFloorSummary, compressedMemory, compressedFacts);
        } catch (Exception e) {
            // Fall back to uncompressed if compression fails
            System.err.println("[CompressingPromptBuilder] Compression failed: " + e.getMessage());
            return super.buildPoliticianTurnPrompt(agent, profile, ctx, memory);
        }
    }

    /**
     * Build an advocate prompt with optional compression.
     */
    @Override
    public String buildAdvocatePrompt(PoliticianAgent agent, PoliticianProfile profile,
                                      AgentContext ctx, String memory) {
        if (!enabled) {
            return super.buildAdvocatePrompt(agent, profile, ctx, memory);
        }

        try {
            String compressedOnePager = compressIfNeeded(ctx.billOnePager, "bill");
            String compressedFloorSummary = compressIfNeeded(ctx.floorSummary, "summary");
            String compressedMemory = compressIfNeeded(memory, "memory");
            String factsPack = extractFactsPack(ctx);
            String compressedFacts = compressIfNeeded(factsPack, "facts");

            return buildCompressedAdvocatePrompt(agent, profile, compressedOnePager,
                    compressedFloorSummary, compressedMemory, compressedFacts);
        } catch (Exception e) {
            System.err.println("[CompressingPromptBuilder] Compression failed: " + e.getMessage());
            return super.buildAdvocatePrompt(agent, profile, ctx, memory);
        }
    }

    /**
     * Build a judge prompt with optional compression.
     * Compresses the bill text (largest content block).
     */
    @Override
    public String buildJudgePrompt(AgentContext ctx, Collection<Agency> agencies) {
        if (!enabled) {
            return super.buildJudgePrompt(ctx, agencies);
        }

        try {
            // Build agency list
            String agencyList = agencies.stream()
                    .map(a -> a.id() + ": " + a.name() + " (keywords: " + String.join(", ", a.scopeKeywords()) + ")")
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");

            // Compress bill text (the large content block)
            String compressedBill = compressIfNeeded(ctx.bill.rawText(), "bill");

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
""".formatted(agencyList, compressedBill);
        } catch (Exception e) {
            System.err.println("[CompressingPromptBuilder] Compression failed: " + e.getMessage());
            return super.buildJudgePrompt(ctx, agencies);
        }
    }

    /**
     * Compress text if it's large enough to benefit from compression.
     * Threshold: ~500 characters (roughly 125 tokens).
     */
    private String compressIfNeeded(String text, String contextType) {
        if (text == null || text.isBlank()) {
            return text;
        }
        // Only compress if text is longer than ~500 chars
        if (text.length() < 500) {
            return text;
        }
        try {
            Bear1Client.CompressionResult result = compressor.compress(text, contextType);
            metrics.record(result, contextType);
            return result.compressedText();
        } catch (Exception e) {
            // Log and return original if compression fails
            System.err.println("[CompressingPromptBuilder] Compression failed for " + contextType + ": " + e.getMessage());
            return text;
        }
    }

    private String extractFactsPack(AgentContext ctx) {
        Object facts = ctx.runtime.get("factsPack");
        if (facts == null) return "(no facts provided)";
        String text = facts.toString().trim();
        return text.isBlank() ? "(no facts provided)" : text;
    }

    private String buildCompressedPoliticianPrompt(
            PoliticianAgent agent, PoliticianProfile profile,
            String onePager, String floorSummary, String memory, String factsPack) {

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
                onePager, floorSummary, memory, factsPack);
    }

    private String buildCompressedAdvocatePrompt(
            PoliticianAgent agent, PoliticianProfile profile,
            String onePager, String floorSummary, String memory, String factsPack) {

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
                onePager, floorSummary, memory, factsPack);
    }

    /**
     * Check if compression is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Get the underlying metrics tracker.
     */
    public CompressionMetrics metrics() {
        return metrics;
    }
}
