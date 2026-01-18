package govsim.nodes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import govsim.core.Node;
import govsim.core.SimulationLogger;
import govsim.core.SimulationState;
import govsim.llm.Bear1Client;
import govsim.llm.CompressionMetrics;
import govsim.llm.LLMClient;
import govsim.web.ChatStore;

import java.util.List;

/**
 * Public Forum Node with Bear-1 Compression Demo.
 *
 * This innovative feature demonstrates the value of compression by processing
 * up to 100 public comments (which would normally exceed context limits).
 * Comments are compressed using bear-1, then analyzed to extract sentiment,
 * key arguments, and representative quotes.
 */
public class PublicForumNode implements Node {
    private final LLMClient llm;
    private final Bear1Client compressor;
    private final ChatStore chatStore;
    private final CompressionMetrics metrics;
    private final ObjectMapper mapper = new ObjectMapper();

    // Up to 100 public comments (expanded from original ~10)
    private static final int MAX_PUBLIC_COMMENTS = 100;

    /**
     * Create a PublicForumNode without compression support.
     */
    public PublicForumNode() {
        this.llm = null;
        this.compressor = null;
        this.chatStore = null;
        this.metrics = null;
    }

    /**
     * Create a PublicForumNode with full compression support.
     */
    public PublicForumNode(LLMClient llm, Bear1Client compressor, ChatStore chatStore, CompressionMetrics metrics) {
        this.llm = llm;
        this.compressor = compressor;
        this.chatStore = chatStore;
        this.metrics = metrics;
    }

    @Override
    public String name() {
        return "PublicForum";
    }

    @Override
    public void run(SimulationState state) throws Exception {
        // Check if we have compression support
        if (compressor == null || llm == null || chatStore == null) {
            SimulationLogger.log("[PublicForum] Compression not available. Skipping public forum analysis.");
            state.vars.put("publicForumNote", "Public forum requires compression service.");
            state.vars.put("publicForumCommentCount", 0);
            state.vars.put("publicForumCompressionRatio", 0.0);
            return;
        }

        // Gather all public comments from chat
        ChatStore.ChatSnapshot snapshot = chatStore.snapshotFrom(0);
        List<ChatStore.ChatMessage> messages = snapshot.messages;
        int commentCount = Math.min(messages.size(), MAX_PUBLIC_COMMENTS);

        if (commentCount == 0) {
            SimulationLogger.log("[PublicForum] No public comments received.");
            state.vars.put("publicForumNote", "No public comments to process.");
            state.vars.put("publicForumCommentCount", 0);
            state.vars.put("publicForumCompressionRatio", 0.0);
            return;
        }

        // Build full comments string
        StringBuilder commentsBuilder = new StringBuilder();
        commentsBuilder.append("PUBLIC COMMENTS (").append(commentCount).append(" total):\n\n");

        for (int i = 0; i < commentCount; i++) {
            ChatStore.ChatMessage msg = messages.get(i);
            // Generate a readable name from the voter ID
            String displayName = generateDisplayName(i);
            commentsBuilder.append(String.format("[%d] %s: %s\n",
                i + 1, displayName, msg.message));
        }

        String fullComments = commentsBuilder.toString();
        int originalEstTokens = estimateTokens(fullComments);

        SimulationLogger.log("[PublicForum] Processing " + commentCount + " comments (~" +
            originalEstTokens + " tokens)...");

        // COMPRESS: Use bear-1 to compress all comments
        long compressStart = System.currentTimeMillis();
        Bear1Client.CompressionResult compression;
        try {
            compression = compressor.compress(fullComments, "public_comments");
        } catch (Exception e) {
            SimulationLogger.log("[PublicForum] Compression failed: " + e.getMessage());
            state.vars.put("publicForumNote", "Compression service unavailable.");
            return;
        }
        long compressLatency = System.currentTimeMillis() - compressStart;

        // Record metrics
        if (metrics != null) {
            metrics.record(compression, "public_comments");
        }

        double percentReduction = 100 * (1 - compression.compressionRatio());
        SimulationLogger.log("[PublicForum] Compressed: " +
            compression.originalTokens() + " -> " +
            compression.compressedTokens() + " tokens (" +
            String.format("%.1f", percentReduction) + "% reduction)");

        // Send compressed comments + bill to LLM for analysis
        String prompt = buildAnalysisPrompt(state.billOnePager, commentCount,
            compression.compressedText(), percentReduction);

        String json;
        try {
            json = llm.generateJson(prompt);
        } catch (Exception e) {
            SimulationLogger.log("[PublicForum] LLM analysis failed: " + e.getMessage());
            state.vars.put("publicForumNote", "LLM analysis failed.");
            state.vars.put("publicForumCommentCount", commentCount);
            state.vars.put("publicForumCompressionRatio", compression.compressionRatio());
            return;
        }

        // Parse analysis
        try {
            JsonNode root = mapper.readTree(json);
            String overallSentiment = root.path("overallSentiment").asText("unknown");
            String summary = root.path("summary").asText("");

            // Extract key arguments
            List<String> keyArguments = List.of();
            JsonNode argsNode = root.get("keyArguments");
            if (argsNode != null && argsNode.isArray()) {
                keyArguments = mapper.convertValue(argsNode, new TypeReference<List<String>>() {});
            }

            // Extract notable quotes
            List<String> notableQuotes = List.of();
            JsonNode quotesNode = root.get("notableQuotes");
            if (quotesNode != null && quotesNode.isArray()) {
                notableQuotes = mapper.convertValue(quotesNode, new TypeReference<List<String>>() {});
            }

            // Store results
            state.vars.put("publicForumAnalysis", json);
            state.vars.put("publicForumSentiment", overallSentiment);
            state.vars.put("publicForumSummary", summary);
            state.vars.put("publicForumArguments", keyArguments);
            state.vars.put("publicForumQuotes", notableQuotes);
            state.vars.put("publicForumCommentCount", commentCount);
            state.vars.put("publicForumCompressionRatio", compression.compressionRatio());
            state.vars.put("publicForumNote", "Analyzed " + commentCount + " public comments with compression.");

            SimulationLogger.log("[PublicForum] Analysis complete:");
            SimulationLogger.log("[PublicForum]   Sentiment: " + overallSentiment);
            if (!summary.isBlank()) {
                SimulationLogger.log("[PublicForum]   Summary: " + summary);
            }
            if (!keyArguments.isEmpty()) {
                SimulationLogger.log("[PublicForum]   Key arguments: " + String.join("; ", keyArguments));
            }

        } catch (JsonProcessingException e) {
            SimulationLogger.log("[PublicForum] Failed to parse analysis: " + e.getMessage());
            state.vars.put("publicForumAnalysis", json);
            state.vars.put("publicForumCommentCount", commentCount);
            state.vars.put("publicForumCompressionRatio", compression.compressionRatio());
        }
    }

    private String buildAnalysisPrompt(String billSummary, int commentCount,
                                       String compressedComments, double percentReduction) {
        return String.format("""
You are analyzing public sentiment on a bill.

BILL SUMMARY:
%s

COMPRESSED PUBLIC COMMENTS (%d comments, compressed by %.1f%% using Bear-1):
%s

The above comments have been compressed to fit more public input while preserving key sentiments.
Analyze the compressed input and extract the main themes.

Return STRICT JSON with keys:
- overallSentiment: "support"|"oppose"|"mixed"
- keyArguments: array of up to 5 main arguments from public
- notableQuotes: array of up to 3 representative quotes (paraphrased from compressed)
- summary: 1-2 sentence summary of public opinion

No extra keys. No markdown.
""",
            billSummary,
            commentCount,
            percentReduction,
            compressedComments
        );
    }

    private String generateDisplayName(int index) {
        // Generate friendly names like "Citizen Alpha", "Citizen Beta", etc.
        String[] greekLetters = {
            "Alpha", "Beta", "Gamma", "Delta", "Epsilon", "Zeta", "Eta", "Theta",
            "Iota", "Kappa", "Lambda", "Mu", "Nu", "Xi", "Omicron", "Pi", "Rho",
            "Sigma", "Tau", "Upsilon", "Phi", "Chi", "Psi", "Omega"
        };
        int greekIndex = index % greekLetters.length;
        int repeat = index / greekLetters.length;
        if (repeat == 0) {
            return "Citizen " + greekLetters[greekIndex];
        } else {
            return "Citizen " + greekLetters[greekIndex] + " " + romanNumeral(repeat);
        }
    }

    private String romanNumeral(int n) {
        return switch (n) {
            case 1 -> "II";
            case 2 -> "III";
            case 3 -> "IV";
            case 4 -> "V";
            default -> "" + n;
        };
    }

    private int estimateTokens(String text) {
        // Rough estimation: ~4 chars per token
        return text.length() / 4;
    }
}
