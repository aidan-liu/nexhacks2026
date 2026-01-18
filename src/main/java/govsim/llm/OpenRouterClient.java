package govsim.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class OpenRouterClient implements LLMClient {
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();
  private final ObjectMapper mapper = new ObjectMapper();
  private final String apiKey;
  private final String baseUrl;
  private final String model;
  private final int maxTokens;
  private final String httpReferer;
  private final String xTitle;

  public OpenRouterClient(String apiKey, String baseUrl, String model, int maxTokens, String httpReferer, String xTitle) {
    if (apiKey == null || apiKey.isBlank()) {
      throw new IllegalArgumentException("Missing OpenRouter API key");
    }
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalArgumentException("Missing OpenRouter base URL");
    }
    if (model == null || model.isBlank()) {
      throw new IllegalArgumentException("Missing OpenRouter model");
    }
    this.apiKey = apiKey;
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.model = model;
    this.maxTokens = maxTokens;
    this.httpReferer = httpReferer == null ? "" : httpReferer.trim();
    this.xTitle = xTitle == null ? "" : xTitle.trim();
  }

  @Override
  public String generateJson(String prompt) throws Exception {
    return generateJson(prompt, null);
  }

  @Override
  public String generateJson(String prompt, LLMRequestOptions optionsOverride) throws Exception {
    int effectiveMaxTokens = maxTokens;
    if (optionsOverride != null && optionsOverride.numPredict() != null) {
      effectiveMaxTokens = optionsOverride.numPredict();
    }
    if (effectiveMaxTokens <= 0) {
      effectiveMaxTokens = 600;
    }

    try {
      return extractJsonPayload(chatCompletions(prompt, effectiveMaxTokens, true));
    } catch (IllegalStateException e) {
      String msg = e.getMessage() == null ? "" : e.getMessage();
      if (msg.contains("OpenRouter error 4") || msg.contains("response_format")) {
        return extractJsonPayload(chatCompletions(prompt, effectiveMaxTokens, false));
      }
      throw e;
    }
  }

  private String chatCompletions(String prompt, int effectiveMaxTokens, boolean includeResponseFormat) throws Exception {
    ObjectNode body = mapper.createObjectNode();
    body.put("model", model);
    body.putArray("messages")
        .addObject()
        .put("role", "user")
        .put("content", prompt);
    body.put("temperature", 0.2);
    body.put("max_tokens", effectiveMaxTokens);
    if (includeResponseFormat) {
      body.putObject("response_format").put("type", "json_object");
    }

    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/chat/completions"))
        .header("Authorization", "Bearer " + apiKey)
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(300))
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));

    if (!httpReferer.isBlank()) {
      builder.header("HTTP-Referer", httpReferer);
    }
    if (!xTitle.isBlank()) {
      builder.header("X-Title", xTitle);
    }

    HttpResponse<String> response = http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("OpenRouter error " + response.statusCode() + ": " + response.body());
    }

    JsonNode root = mapper.readTree(response.body());
    JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
    if (contentNode.isMissingNode() || contentNode.isNull()) {
      throw new IllegalStateException("Missing choices[0].message.content from OpenRouter");
    }
    return contentNode.asText();
  }

  private String extractJsonPayload(String text) {
    if (text == null) return "";
    String trimmed = text.trim();

    if (trimmed.startsWith("```")) {
      int firstNewline = trimmed.indexOf('\n');
      if (firstNewline >= 0) {
        trimmed = trimmed.substring(firstNewline + 1);
      }
      int lastFence = trimmed.lastIndexOf("```");
      if (lastFence >= 0) {
        trimmed = trimmed.substring(0, lastFence);
      }
      trimmed = trimmed.trim();
    }

    int firstObj = trimmed.indexOf('{');
    int lastObj = trimmed.lastIndexOf('}');
    if (firstObj >= 0 && lastObj > firstObj) {
      return trimmed.substring(firstObj, lastObj + 1).trim();
    }
    int firstArr = trimmed.indexOf('[');
    int lastArr = trimmed.lastIndexOf(']');
    if (firstArr >= 0 && lastArr > firstArr) {
      return trimmed.substring(firstArr, lastArr + 1).trim();
    }
    return trimmed;
  }
}
