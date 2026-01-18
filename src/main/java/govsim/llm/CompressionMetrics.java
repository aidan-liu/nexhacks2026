package govsim.llm;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Thread-safe metrics tracking for bear-1 compression.
 */
public class CompressionMetrics {
    private final AtomicLong totalCompressions = new AtomicLong();
    private final AtomicLong totalOriginalTokens = new AtomicLong();
    private final AtomicLong totalCompressedTokens = new AtomicLong();
    private final AtomicLong totalLatencyMs = new AtomicLong();
    private final AtomicInteger cacheHits = new AtomicInteger();

    private final ConcurrentHashMap<String, ContextTypeMetrics> byContextType = new ConcurrentHashMap<>();

    /**
     * Metrics for a specific context type (bill, facts, summary, etc.)
     */
    public static class ContextTypeMetrics {
        private final AtomicLong count = new AtomicLong();
        private final AtomicLong originalTokens = new AtomicLong();
        private final AtomicLong compressedTokens = new AtomicLong();

        public void record(long origTokens, long compTokens) {
            count.incrementAndGet();
            originalTokens.addAndGet(origTokens);
            compressedTokens.addAndGet(compTokens);
        }

        public long count() { return count.get(); }
        public long originalTokens() { return originalTokens.get(); }
        public long compressedTokens() { return compressedTokens.get(); }
        public double avgRatio() {
            long orig = originalTokens.get();
            return orig > 0 ? (double) compressedTokens.get() / orig : 0.0;
        }
    }

    /**
     * Record a compression result.
     */
    public void record(Bear1Client.CompressionResult result, String contextType) {
        totalCompressions.incrementAndGet();
        totalOriginalTokens.addAndGet(result.originalTokens());
        totalCompressedTokens.addAndGet(result.compressedTokens());
        totalLatencyMs.addAndGet(result.latencyMs());
        if (result.cacheHit()) {
            cacheHits.incrementAndGet();
        }

        byContextType.computeIfAbsent(contextType, k -> new ContextTypeMetrics())
            .record(result.originalTokens(), result.compressedTokens());
    }

    /**
     * Snapshot of current metrics.
     */
    public record MetricsSnapshot(
        long totalCompressions,
        long totalOriginalTokens,
        long totalCompressedTokens,
        double averageCompressionRatio,
        double averageLatencyMs,
        double cacheHitRate,
        long totalTokensSaved,
        ConcurrentHashMap<String, ContextBreakdown> byContextType
    ) {
        public double costSavingsUsd() {
            // Assuming $0.15 per million input tokens for OpenRouter
            return totalTokensSaved * 0.15 / 1_000_000;
        }
    }

    /**
     * Breakdown for a specific context type.
     */
    public record ContextBreakdown(
        long count,
        long totalOriginalTokens,
        long totalCompressedTokens,
        double avgRatio
    ) {}

    /**
     * Get a snapshot of current metrics.
     */
    public MetricsSnapshot getSnapshot() {
        long total = totalCompressions.get();
        long orig = totalOriginalTokens.get();
        long comp = totalCompressedTokens.get();

        ConcurrentHashMap<String, ContextBreakdown> breakdown = new ConcurrentHashMap<>();
        for (var entry : byContextType.entrySet()) {
            ContextTypeMetrics m = entry.getValue();
            breakdown.put(entry.getKey(), new ContextBreakdown(
                m.count(),
                m.originalTokens(),
                m.compressedTokens(),
                m.avgRatio()
            ));
        }

        return new MetricsSnapshot(
            total,
            orig,
            comp,
            orig > 0 ? (double) comp / orig : 0.0,
            total > 0 ? (double) totalLatencyMs.get() / total : 0.0,
            total > 0 ? (double) cacheHits.get() / total : 0.0,
            orig > 0 ? orig - comp : 0,
            breakdown
        );
    }

    /**
     * Reset all metrics.
     */
    public void reset() {
        totalCompressions.set(0);
        totalOriginalTokens.set(0);
        totalCompressedTokens.set(0);
        totalLatencyMs.set(0);
        cacheHits.set(0);
        byContextType.clear();
    }

    // Getters for individual metrics
    public long totalCompressions() { return totalCompressions.get(); }
    public long totalOriginalTokens() { return totalOriginalTokens.get(); }
    public long totalCompressedTokens() { return totalCompressedTokens.get(); }
    public long totalTokensSaved() {
        long orig = totalOriginalTokens.get();
        long comp = totalCompressedTokens.get();
        return orig > comp ? orig - comp : 0;
    }
}
