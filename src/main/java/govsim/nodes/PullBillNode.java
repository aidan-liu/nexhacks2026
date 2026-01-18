package govsim.nodes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import govsim.core.Node;
import govsim.core.SimulationLogger;
import govsim.core.SimulationState;
import govsim.domain.Bill;
import govsim.web.BillStore;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

public class PullBillNode implements Node {
  private static final int MAX_BILLS = 20;
  private static final int MIN_TEXT_LEN = 500;
  private static final String DEFAULT_BASE = "https://api.congress.gov/v3";
  private static final String BILL_ENDPOINT = "/bill/119";
  private final HttpClient http = HttpClient.newBuilder()
      .connectTimeout(Duration.ofSeconds(10))
      .build();
  private final ObjectMapper mapper = new ObjectMapper();

  @Override
  public String name() { return "PullBill"; }

  @Override
  public void run(SimulationState state) {
    SimulationLogger.log("[PullBill] Pulling new bill...");
    String apiKey = readEnv("CONGRESS_API_KEY");
    if (apiKey == null || apiKey.isBlank()) {
      SimulationLogger.log("[PullBill] Missing CONGRESS_API_KEY. Using existing bill.");
      return;
    }
    String baseUrl = readEnv("CONGRESS_API_BASE");
    if (baseUrl == null || baseUrl.isBlank()) {
      baseUrl = DEFAULT_BASE;
    }

    try {
      Bill bill = fetchBill(baseUrl, apiKey);
      if (bill == null) {
        SimulationLogger.log("[PullBill] No bill with usable text found. Using existing bill.");
        return;
      }
      state.bill = bill;
      writeBillText(state, bill.rawText());
      Object storeObj = state.vars.get("billStore");
      if (storeObj instanceof BillStore store) {
        store.setOriginalText(bill.rawText());
        store.setOnePager("");
      }
      SimulationLogger.log("[PullBill] Loaded bill: " + bill.title());
    } catch (Exception e) {
      SimulationLogger.log("[PullBill] Failed to pull bill. Using existing bill.");
    }
  }

  private Bill fetchBill(String baseUrl, String apiKey) throws Exception {
    String listUrl = baseUrl + BILL_ENDPOINT + "?limit=" + MAX_BILLS + "&offset=0&format=json&api_key=" + apiKey;
    JsonNode listRoot = getJson(listUrl);
    JsonNode bills = listRoot.path("bills");
    if (!bills.isArray()) return null;

    for (JsonNode billNode : bills) {
      String congress = billNode.path("congress").asText("");
      String type = billNode.path("type").asText("");
      String number = billNode.path("number").asText("");
      String title = billNode.path("title").asText("");
      if (congress.isBlank() || type.isBlank() || number.isBlank() || title.isBlank()) continue;

      String textUrl = fetchTextUrl(baseUrl, apiKey, congress, type, number);
      if (textUrl == null) continue;
      String text = downloadAndStrip(textUrl);
      if (text.length() < MIN_TEXT_LEN) continue;

      String id = congress + "-" + type.toLowerCase() + "-" + number;
      return new Bill(id, title, text);
    }
    return null;
  }

  private String fetchTextUrl(String baseUrl, String apiKey, String congress, String type, String number) throws Exception {
    String url = baseUrl + "/bill/" + congress + "/" + type.toLowerCase() + "/" + number +
        "/text?format=json&api_key=" + apiKey;
    JsonNode root = getJson(url);
    JsonNode versions = root.path("textVersions");
    if (!versions.isArray() || versions.isEmpty()) return null;
    JsonNode latest = versions.get(0);
    JsonNode formats = latest.path("formats");
    if (!formats.isArray()) return null;

    String html = null;
    String xml = null;
    for (JsonNode fmt : formats) {
      String fmtType = fmt.path("type").asText("");
      String fmtUrl = fmt.path("url").asText("");
      if (fmtUrl.isBlank()) continue;
      if ("Formatted Text (HTML)".equals(fmtType)) html = fmtUrl;
      if ("Formatted XML".equals(fmtType)) xml = fmtUrl;
    }
    if (html != null) return html;
    if (xml != null) return xml;
    for (JsonNode fmt : formats) {
      String fmtUrl = fmt.path("url").asText("");
      if (!fmtUrl.isBlank()) return fmtUrl;
    }
    return null;
  }

  private JsonNode getJson(String url) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(30))
        .GET()
        .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("HTTP " + response.statusCode());
    }
    return mapper.readTree(response.body());
  }

  private String downloadAndStrip(String url) throws Exception {
    HttpRequest request = HttpRequest.newBuilder()
        .uri(URI.create(url))
        .timeout(Duration.ofSeconds(60))
        .GET()
        .build();
    HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    if (response.statusCode() < 200 || response.statusCode() >= 300) {
      throw new IllegalStateException("HTTP " + response.statusCode());
    }
    String body = response.body();
    String cleaned = body
        .replaceAll("(?is)<script.*?>.*?</script>", " ")
        .replaceAll("(?is)<style.*?>.*?</style>", " ")
        .replaceAll("<[^>]+>", " ")
        .replaceAll("\\s+", " ")
        .trim();
    return cleaned;
  }

  private void writeBillText(SimulationState state, String text) throws IOException {
    String path = String.valueOf(state.vars.getOrDefault("billPath", "config/bill.txt"));
    if (text == null || text.isBlank()) return;
    Files.writeString(Path.of(path), text, StandardCharsets.UTF_8);
  }

  private String readEnv(String key) {
    String env = System.getenv(key);
    if (env != null && !env.isBlank()) return env;
    String val = readEnvFile(Path.of(".env"), key);
    if (val != null) return val;
    return readEnvFile(Path.of(".env.local"), key);
  }

  private String readEnvFile(Path path, String key) {
    if (!Files.exists(path)) return null;
    try {
      for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
        String trimmed = line.trim();
        if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
        int idx = trimmed.indexOf('=');
        if (idx <= 0) continue;
        String k = trimmed.substring(0, idx).trim();
        if (!k.equals(key)) continue;
        return trimmed.substring(idx + 1).trim();
      }
    } catch (IOException ignored) {
    }
    return null;
  }
}
