package govsim.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class OllamaClient implements LLMClient {
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();
  private final ObjectMapper mapper = new ObjectMapper();
  private final String baseUrl; // e.g. http://localhost:11434
  private final String model;   // e.g. "gemma2:2b"
  private final int numPredict;

  public OllamaClient(String baseUrl, String model, int numPredict) {
    this.baseUrl = baseUrl;
    this.model = model;
    this.numPredict = numPredict;
  }

  @Override
  public String generateJson(String prompt) throws Exception {
    return generateJson(prompt, null);
  }

  @Override
  public String generateJson(String prompt, LLMRequestOptions optionsOverride) throws Exception {
    int effectiveNumPredict = numPredict;
    if (optionsOverride != null && optionsOverride.numPredict() != null) {
      effectiveNumPredict = optionsOverride.numPredict();
    }

    ObjectNode body = mapper.createObjectNode();
    body.put("model", model);
    body.put("prompt", prompt);
    body.put("stream", false);
    body.put("format", "json");
    if (effectiveNumPredict > 0) {
      ObjectNode options = body.putObject("options");
      options.put("num_predict", effectiveNumPredict);
    }

    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(baseUrl + "/api/generate"))
        .header("Content-Type", "application/json")
        .timeout(Duration.ofSeconds(300))
        .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
        .build();

    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("Ollama error " + response.statusCode() + ": " + response.body());
    }
    JsonNode root = mapper.readTree(response.body());
    JsonNode respNode = root.get("response");
    if (respNode == null || respNode.isNull()) {
      throw new IllegalStateException("Missing response field from Ollama");
    }
    return respNode.asText();
  }
}
