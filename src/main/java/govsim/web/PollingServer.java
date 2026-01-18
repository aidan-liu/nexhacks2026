package govsim.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import govsim.llm.CompressionMetrics;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

public class PollingServer {
  private final HttpServer server;
  private final LogStore logStore;
  private final VoteBox voteBox;
  private final ChatStore chatStore;
  private final AgentStateStore agentStateStore;
  private final CompressionMetrics compressionMetrics;
  private final ObjectMapper mapper = new ObjectMapper();

  public PollingServer(int port, LogStore logStore, VoteBox voteBox, ChatStore chatStore) throws IOException {
    this(port, logStore, voteBox, chatStore, null, null);
  }

  public PollingServer(int port, LogStore logStore, VoteBox voteBox, ChatStore chatStore, AgentStateStore agentStateStore)
      throws IOException {
    this(port, logStore, voteBox, chatStore, agentStateStore, null);
  }

  public PollingServer(int port, LogStore logStore, VoteBox voteBox, ChatStore chatStore,
                       AgentStateStore agentStateStore, CompressionMetrics compressionMetrics)
      throws IOException {
    this.logStore = logStore;
    this.voteBox = voteBox;
    this.chatStore = chatStore;
    this.agentStateStore = agentStateStore;
    this.compressionMetrics = compressionMetrics;
    this.server = HttpServer.create(new InetSocketAddress(port), 0);
    this.server.setExecutor(Executors.newCachedThreadPool());
    this.server.createContext("/", this::handleIndex);
    this.server.createContext("/static", this::handleStatic);
    this.server.createContext("/representatives", this::handleRepresentatives);
    this.server.createContext("/log", this::handleLog);
    this.server.createContext("/status", this::handleStatus);
    this.server.createContext("/vote", this::handleVote);
    this.server.createContext("/chat", this::handleChat);
    this.server.createContext("/agent-state", this::handleAgentState);
    this.server.createContext("/compression-metrics", this::handleCompressionMetrics);
  }

  public void start() {
    server.start();
  }

  public void stop() {
    server.stop(0);
  }

  public int port() {
    return server.getAddress().getPort();
  }

  private void handleIndex(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    // Redirect to the static frontend
    ensureVoterCookie(exchange);
    exchange.getResponseHeaders().add("Location", "/static/index.html");
    exchange.sendResponseHeaders(302, -1);
  }

  private void handleStatic(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    String path = exchange.getRequestURI().getPath();
    // Remove /static prefix
    String resourcePath = path.substring("/static".length());
    if (resourcePath.isEmpty() || resourcePath.equals("/")) {
      resourcePath = "/index.html";
    }

    InputStream is = null;

    // First try loading from filesystem (for development)
    String fsPath = "src/main/resources/static" + resourcePath;
    File file = new File(fsPath);
    if (file.exists() && file.isFile()) {
      is = new FileInputStream(file);
    } else {
      // Fall back to classpath (for production)
      String classpathResource = "/static" + resourcePath;
      is = getClass().getResourceAsStream(classpathResource);
    }

    if (is == null) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }

    byte[] body = is.readAllBytes();
    is.close();

    // Determine content type
    String contentType = "application/octet-stream";
    if (resourcePath.endsWith(".html")) contentType = "text/html; charset=utf-8";
    else if (resourcePath.endsWith(".css")) contentType = "text/css; charset=utf-8";
    else if (resourcePath.endsWith(".js")) contentType = "application/javascript; charset=utf-8";
    else if (resourcePath.endsWith(".png")) contentType = "image/png";
    else if (resourcePath.endsWith(".svg")) contentType = "image/svg+xml";
    else if (resourcePath.endsWith(".json")) contentType = "application/json; charset=utf-8";
    else if (resourcePath.endsWith(".glb")) contentType = "model/gltf-binary";

    exchange.getResponseHeaders().add("Content-Type", contentType);
    exchange.getResponseHeaders().add("Cache-Control", "no-store");
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  private void handleRepresentatives(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    // Read representatives.json from config/ and enrich with committee membership derived from agencies.json.
    Path repsPath = Path.of("config/representatives.json");
    if (!Files.exists(repsPath)) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }
    Path agenciesPath = Path.of("config/agencies.json");
    try {
      List<Map<String, Object>> reps = mapper.readValue(Files.readString(repsPath), List.class);
      Map<String, Map<String, String>> committeeByRepId = new HashMap<>();
      if (Files.exists(agenciesPath)) {
        List<Map<String, Object>> agencies = mapper.readValue(Files.readString(agenciesPath), List.class);
        for (Map<String, Object> agency : agencies) {
          String committeeId = String.valueOf(agency.getOrDefault("id", "")).trim();
          String committeeName = String.valueOf(agency.getOrDefault("name", "")).trim();
          Object repIds = agency.get("representativeIds");
          if (!(repIds instanceof List<?> list)) continue;
          for (Object rid : list) {
            if (rid == null) continue;
            committeeByRepId.put(String.valueOf(rid), Map.of(
                "committeeId", committeeId,
                "committeeName", committeeName
            ));
          }
        }
      }

      for (Map<String, Object> rep : reps) {
        Object id = rep.get("id");
        if (id == null) continue;
        Map<String, String> committee = committeeByRepId.get(String.valueOf(id));
        if (committee == null) continue;
        rep.putAll(committee);
      }

      writeJson(exchange, reps);
    } catch (Exception e) {
      // Fallback to raw file if enrichment fails.
      byte[] body = Files.readAllBytes(repsPath);
      exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
      exchange.getResponseHeaders().add("Cache-Control", "no-store");
      exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
      exchange.sendResponseHeaders(200, body.length);
      try (OutputStream out = exchange.getResponseBody()) {
        out.write(body);
      }
    }
  }

  private void handleLog(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    ensureVoterCookie(exchange);
    URI uri = exchange.getRequestURI();
    int since = parseIntParam(uri.getQuery(), "since", 0);
    LogStore.LogSnapshot snap = logStore.snapshotFrom(since);

    Map<String, Object> payload = new HashMap<>();
    payload.put("lines", snap.lines);
    payload.put("nextIndex", snap.nextIndex);
    writeJson(exchange, payload);
  }

  private void handleStatus(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    String voterId = ensureVoterCookie(exchange);
    VoteBox.VoteSnapshot snap = voteBox.snapshot();
    Map<String, Object> payload = new HashMap<>();
    payload.put("open", snap.open);
    payload.put("yes", snap.yes);
    payload.put("no", snap.no);
    payload.put("total", snap.yes + snap.no);
    payload.put("alreadyVoted", voteBox.hasVoted(voterId));
    writeJson(exchange, payload);
  }

  private void handleVote(HttpExchange exchange) throws IOException {
    if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    URI uri = exchange.getRequestURI();
    String choice = getParam(uri.getQuery(), "choice");
    if (choice == null) {
      exchange.sendResponseHeaders(400, -1);
      return;
    }
    String voterId = ensureVoterCookie(exchange);
    VoteBox.VoteStatus status;
    if ("yes".equalsIgnoreCase(choice)) {
      status = voteBox.recordVote(voterId, true);
    } else if ("no".equalsIgnoreCase(choice)) {
      status = voteBox.recordVote(voterId, false);
    } else {
      exchange.sendResponseHeaders(400, -1);
      return;
    }

    if (status == VoteBox.VoteStatus.OK) {
      exchange.sendResponseHeaders(204, -1);
    } else if (status == VoteBox.VoteStatus.DUPLICATE) {
      exchange.sendResponseHeaders(409, -1);
    } else if (status == VoteBox.VoteStatus.CLOSED) {
      exchange.sendResponseHeaders(403, -1);
    } else {
      exchange.sendResponseHeaders(400, -1);
    }
  }

  private void handleChat(HttpExchange exchange) throws IOException {
    if ("GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      ensureVoterCookie(exchange);
      URI uri = exchange.getRequestURI();
      int since = parseIntParam(uri.getQuery(), "since", 0);
      ChatStore.ChatSnapshot snap = chatStore.snapshotFrom(since);
      Map<String, Object> payload = new HashMap<>();
      payload.put("messages", snap.messages);
      payload.put("nextIndex", snap.nextIndex);
      writeJson(exchange, payload);
      return;
    }

    if ("POST".equalsIgnoreCase(exchange.getRequestMethod())) {
      ensureVoterCookie(exchange);
      byte[] body = exchange.getRequestBody().readAllBytes();
      Map<?, ?> payload = mapper.readValue(body, Map.class);
      Object message = payload.get("message");
      String voterId = ensureVoterCookie(exchange);
      chatStore.addMessage(voterId, message == null ? "" : message.toString());
      exchange.sendResponseHeaders(204, -1);
      return;
    }

    exchange.sendResponseHeaders(405, -1);
  }

  private void handleAgentState(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    if (agentStateStore == null) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }
    URI uri = exchange.getRequestURI();
    String include = getParam(uri.getQuery(), "includePrompts");
    boolean includePrompts = "1".equals(include) || "true".equalsIgnoreCase(include);
    writeJson(exchange, agentStateStore.snapshot(includePrompts));
  }

  private void handleCompressionMetrics(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    if (compressionMetrics == null) {
      // Return empty metrics if compression is not enabled
      Map<String, Object> emptyMetrics = new HashMap<>();
      emptyMetrics.put("totalCompressions", 0);
      emptyMetrics.put("totalOriginalTokens", 0);
      emptyMetrics.put("totalCompressedTokens", 0);
      emptyMetrics.put("averageCompressionRatio", 0.0);
      emptyMetrics.put("averageLatencyMs", 0.0);
      emptyMetrics.put("cacheHitRate", 0.0);
      emptyMetrics.put("totalTokensSaved", 0);
      emptyMetrics.put("costSavingsUsd", 0.0);
      emptyMetrics.put("byContextType", new HashMap<>());
      writeJson(exchange, emptyMetrics);
      return;
    }
    writeJson(exchange, compressionMetrics.getSnapshot());
  }

  private void writeJson(HttpExchange exchange, Object payload) throws IOException {
    byte[] body = mapper.writeValueAsBytes(payload);
    exchange.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
    exchange.getResponseHeaders().add("Cache-Control", "no-store");
    exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
    }
  }

  private String ensureVoterCookie(HttpExchange exchange) {
    String cookie = getCookieValue(exchange, "govsim_voter");
    if (cookie != null && !cookie.isBlank()) {
      return cookie;
    }
    String id = UUID.randomUUID().toString();
    exchange.getResponseHeaders().add("Set-Cookie", "govsim_voter=" + id + "; Path=/; SameSite=Lax");
    return id;
  }

  private static String getCookieValue(HttpExchange exchange, String name) {
    List<String> cookies = exchange.getRequestHeaders().get("Cookie");
    if (cookies == null) return null;
    for (String header : cookies) {
      String[] parts = header.split(";");
      for (String part : parts) {
        String[] kv = part.trim().split("=", 2);
        if (kv.length == 2 && kv[0].equals(name)) {
          return kv[1];
        }
      }
    }
    return null;
  }

  private static int parseIntParam(String query, String key, int defaultValue) {
    String value = getParam(query, key);
    if (value == null) return defaultValue;
    try {
      return Integer.parseInt(value);
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  private static String getParam(String query, String key) {
    if (query == null || query.isBlank()) return null;
    String[] pairs = query.split("&");
    for (String pair : pairs) {
      String[] parts = pair.split("=", 2);
      if (parts.length == 2 && parts[0].equals(key)) {
        return parts[1];
      }
    }
    return null;
  }

  private String buildIndexHtml() {
    return """
<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>GovSim Live Feed</title>
  <style>
    :root { --bg:#0b0f14; --card:#141a22; --text:#e6edf3; --muted:#9aa4af; --accent:#4dd0e1; }
    body { margin:0; font-family: ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace; background: var(--bg); color: var(--text); }
    header { padding: 16px 20px; border-bottom: 1px solid #1f2a36; }
    main { display: grid; grid-template-columns: 2fr 1fr; gap: 16px; padding: 16px 20px; }
    .card { background: var(--card); border: 1px solid #1f2a36; border-radius: 10px; padding: 12px; }
    #log { height: 65vh; overflow: auto; white-space: pre-wrap; font-size: 13px; }
    .badge { display: inline-block; padding: 2px 8px; border-radius: 999px; background: #1f2a36; color: var(--muted); font-size: 12px; }
    button { background: var(--accent); color: #0b0f14; border: 0; padding: 10px 14px; margin-right: 8px; border-radius: 8px; font-weight: 600; cursor: pointer; }
    button[disabled] { background: #2a3645; color: #7c8794; cursor: not-allowed; }
    .row { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
  </style>
</head>
<body>
  <header>
    <div class="row">
      <h2 style="margin:0;">GovSim Live Feed</h2>
      <span id="statusBadge" class="badge">Connecting...</span>
    </div>
  </header>
  <main>
    <section class="card">
      <h3 style="margin-top:0;">Console Output</h3>
      <div id="log"></div>
    </section>
    <section class="card">
      <h3 style="margin-top:0;">Popular Vote</h3>
      <div class="row" style="margin-bottom:12px;">
        <button id="yesBtn">Vote YES</button>
        <button id="noBtn">Vote NO</button>
      </div>
      <div id="voteStats" class="badge">Waiting...</div>
      <div id="voteNotice" class="badge" style="margin-top:8px;">One vote per device.</div>
      <div style="margin-top:16px;">
        <h4 style="margin:8px 0;">Public Comments</h4>
        <div id="chatLog" style="height:220px; overflow:auto; white-space:pre-wrap; background:#0f141b; border:1px solid #1f2a36; border-radius:8px; padding:8px; font-size:12px;"></div>
        <div class="row" style="margin-top:8px;">
          <input id="chatMessage" placeholder="Comment" style="flex:2; min-width:160px; padding:8px; border-radius:6px; border:1px solid #2a3645; background:#0b0f14; color:#e6edf3;" />
          <button id="chatSend">Send</button>
        </div>
        <div class="badge" style="margin-top:8px;">Names are assigned automatically.</div>
      </div>
    </section>
  </main>
  <script>
    let nextIndex = 0;
    const logEl = document.getElementById('log');
    const statusBadge = document.getElementById('statusBadge');
    const voteStats = document.getElementById('voteStats');
    const voteNotice = document.getElementById('voteNotice');
    const yesBtn = document.getElementById('yesBtn');
    const noBtn = document.getElementById('noBtn');

    async function fetchLogs() {
      try {
        const res = await fetch(`/log?since=${nextIndex}`, { cache: 'no-store' });
        if (!res.ok) throw new Error('log fetch failed');
        const data = await res.json();
        if (data.lines && data.lines.length) {
          data.lines.forEach(line => {
            logEl.textContent += line + "\\n";
          });
          logEl.scrollTop = logEl.scrollHeight;
        }
        nextIndex = data.nextIndex || nextIndex;
      } catch (err) {
        statusBadge.textContent = 'Disconnected';
      }
    }

    async function fetchStatus() {
      try {
        const res = await fetch('/status', { cache: 'no-store' });
        if (!res.ok) throw new Error('status fetch failed');
        const data = await res.json();
        const open = !!data.open;
        statusBadge.textContent = open ? 'Voting Open' : 'Voting Closed';
        voteStats.textContent = `YES ${data.yes} | NO ${data.no} | TOTAL ${data.total}`;
        const alreadyVoted = !!data.alreadyVoted;
        yesBtn.disabled = !open || alreadyVoted;
        noBtn.disabled = !open || alreadyVoted;
        if (alreadyVoted) {
          voteNotice.textContent = 'Already voted.';
        } else if (!open) {
          voteNotice.textContent = 'Voting closed.';
        } else {
          voteNotice.textContent = 'One vote per device.';
        }
      } catch (err) {
        statusBadge.textContent = 'Disconnected';
      }
    }

    async function vote(choice) {
      const res = await fetch(`/vote?choice=${choice}`, { method: 'POST', cache: 'no-store' });
      if (res.status === 409) {
        voteNotice.textContent = 'Already voted.';
        yesBtn.disabled = true;
        noBtn.disabled = true;
      } else if (res.status === 403) {
        voteNotice.textContent = 'Voting closed.';
        yesBtn.disabled = true;
        noBtn.disabled = true;
      } else if (res.ok) {
        voteNotice.textContent = 'Vote recorded.';
        yesBtn.disabled = true;
        noBtn.disabled = true;
      } else {
        voteNotice.textContent = 'Vote failed.';
      }
      fetchStatus();
    }

    let chatIndex = 0;
    const chatLog = document.getElementById('chatLog');
    const chatMessage = document.getElementById('chatMessage');
    const chatSend = document.getElementById('chatSend');

    async function fetchChat() {
      try {
        const res = await fetch(`/chat?since=${chatIndex}`, { cache: 'no-store' });
        if (!res.ok) throw new Error('chat fetch failed');
        const data = await res.json();
        if (data.messages && data.messages.length) {
          data.messages.forEach(msg => {
            const line = `${msg.name}: ${msg.message}`;
            chatLog.textContent += line + "\\n";
          });
          chatLog.scrollTop = chatLog.scrollHeight;
        }
        chatIndex = data.nextIndex || chatIndex;
      } catch (err) {
        statusBadge.textContent = 'Disconnected';
      }
    }

    async function sendChat() {
      const message = chatMessage.value.trim();
      if (!message) return;
      await fetch('/chat', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ message }),
        cache: 'no-store'
      });
      chatMessage.value = '';
      fetchChat();
    }

    yesBtn.addEventListener('click', () => vote('yes'));
    noBtn.addEventListener('click', () => vote('no'));
    chatSend.addEventListener('click', () => sendChat());
    chatMessage.addEventListener('keydown', (e) => {
      if (e.key === 'Enter') sendChat();
    });

    setInterval(fetchLogs, 1000);
    setInterval(fetchStatus, 1000);
    setInterval(fetchChat, 1000);
    fetchLogs();
    fetchStatus();
    fetchChat();
  </script>
</body>
</html>
""";
  }
}

