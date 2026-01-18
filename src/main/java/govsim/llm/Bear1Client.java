package govsim.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Client for the Bear-1 compression sidecar service.
 * Follows the same pattern as OpenRouterClient.
 */
public class Bear1Client {
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final String baseUrl;
    private final String apiKey;

    public Bear1Client(String baseUrl, String apiKey) {
        this.http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.mapper = new ObjectMapper();
        this.baseUrl = baseUrl.endsWith("/") ?
            baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey;
    }

    /**
     * Result of a compression operation.
     */
    public record CompressionResult(
        String compressedText,
        int originalTokens,
        int compressedTokens,
        double compressionRatio,
        long latencyMs,
        boolean cacheHit
    ) {}

    /**
     * Compress text using the bear-1 sidecar service.
     */
    public CompressionResult compress(String text, String contextType) throws Exception {
        if (text == null || text.isBlank()) {
            return new CompressionResult("", 0, 0, 0, 0, false);
        }

        long start = System.currentTimeMillis();

        // Build request body
        ObjectNode body = mapper.createObjectNode();
        body.put("text", text);
        body.put("context_type", contextType);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/compress"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Bear-1 error " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = mapper.readTree(response.body());

        String compressedText = root.path("compressed_text").asText("");
        int originalTokens = root.path("original_tokens").asInt(0);
        int compressedTokens = root.path("compressed_tokens").asInt(0);
        double compressionRatio = root.path("compression_ratio").asDouble(0.0);
        boolean cacheHit = root.path("cache_hit").asBoolean(false);

        long latencyMs = System.currentTimeMillis() - start;

        return new CompressionResult(
            compressedText,
            originalTokens,
            compressedTokens,
            compressionRatio,
            latencyMs,
            cacheHit
        );
    }

    /**
     * Expand compressed text (for accuracy verification).
     */
    public String expand(String compressedText, String contextType) throws Exception {
        if (compressedText == null || compressedText.isBlank()) {
            return "";
        }

        ObjectNode body = mapper.createObjectNode();
        body.put("compressed_text", compressedText);
        body.put("context_type", contextType);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/expand"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(30))
            .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Bear-1 expand error " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = mapper.readTree(response.body());
        return root.path("expanded_text").asText("");
    }

    /**
     * Get metrics from the sidecar service.
     */
    public String getMetrics() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/metrics"))
            .header("Content-Type", "application/json")
            .timeout(Duration.ofSeconds(10))
            .GET()
            .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("Bear-1 metrics error " + response.statusCode() + ": " + response.body());
        }

        return response.body();
    }

    /**
     * Health check for the sidecar service.
     */
    public boolean isHealthy() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/health"))
                .timeout(Duration.ofSeconds(5))
                .GET()
                .build();

            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Check if this client is configured (has valid base URL).
     */
    public boolean isConfigured() {
        return baseUrl != null && !baseUrl.isBlank();
    }
}
