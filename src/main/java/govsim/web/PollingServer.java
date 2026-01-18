package govsim.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
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
  private final BillStore billStore;
  private final RepsStore repsStore;
  private final StatusStore statusStore;
  private final ObjectMapper mapper = new ObjectMapper();

  public PollingServer(int port, LogStore logStore, VoteBox voteBox, ChatStore chatStore, BillStore billStore,
                       RepsStore repsStore, StatusStore statusStore) throws IOException {
    this.logStore = logStore;
    this.voteBox = voteBox;
    this.chatStore = chatStore;
    this.billStore = billStore;
    this.repsStore = repsStore;
    this.statusStore = statusStore;
    this.server = HttpServer.create(new InetSocketAddress(port), 0);
    this.server.setExecutor(Executors.newCachedThreadPool());
    this.server.createContext("/", this::handleIndex);
    this.server.createContext("/log", this::handleLog);
    this.server.createContext("/status", this::handleStatus);
    this.server.createContext("/vote", this::handleVote);
    this.server.createContext("/chat", this::handleChat);
    this.server.createContext("/bill", this::handleBill);
    this.server.createContext("/reps", this::handleReps);
    this.server.createContext("/glb", this::handleGlbList);
    this.server.createContext("/assets/", this::handleAsset);
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
    ensureVoterCookie(exchange);
    String html = buildIndexHtml();
    byte[] body = html.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
    exchange.getResponseHeaders().add("Cache-Control", "no-store");
    exchange.sendResponseHeaders(200, body.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(body);
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
    StatusStore.StatusSnapshot statusSnap = statusStore.snapshot();
    Map<String, Object> payload = new HashMap<>();
    payload.put("open", snap.open);
    payload.put("yes", snap.yes);
    payload.put("no", snap.no);
    payload.put("total", snap.yes + snap.no);
    payload.put("alreadyVoted", voteBox.hasVoted(voterId));
    payload.put("currentStage", statusSnap.currentStage);
    payload.put("stageRunning", statusSnap.stageRunning);
    payload.put("currentSpeakerId", statusSnap.currentSpeakerId);
    payload.put("currentSpeakerName", statusSnap.currentSpeakerName);
    payload.put("currentSpeakerText", statusSnap.currentSpeakerText);
    payload.put("finalOutcome", statusSnap.finalOutcome);
    payload.put("lastLine", logStore == null ? "" : logStore.lastLine());
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

  private void handleBill(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    ensureVoterCookie(exchange);
    BillStore.BillSnapshot snap = billStore.snapshot();
    Map<String, Object> payload = new HashMap<>();
    payload.put("originalText", snap.originalText);
    payload.put("onePager", snap.onePager);
    payload.put("revisedText", snap.revisedText);
    payload.put("revisedSummary", snap.revisedSummary);
    payload.put("revisedChanges", snap.revisedChanges);
    writeJson(exchange, payload);
  }

  private void handleReps(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    ensureVoterCookie(exchange);
    writeJson(exchange, repsStore.snapshot());
  }

  private void handleGlbList(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    ensureVoterCookie(exchange);
    java.nio.file.Path folder = java.nio.file.Path.of("assets", "GLB format");
    if (!java.nio.file.Files.exists(folder)) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }
    List<String> files;
    try (var stream = java.nio.file.Files.list(folder)) {
      files = stream
          .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".glb"))
          .map(path -> path.getFileName().toString())
          .sorted()
          .toList();
    }
    writeJson(exchange, files);
  }

  private void handleAsset(HttpExchange exchange) throws IOException {
    if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
      exchange.sendResponseHeaders(405, -1);
      return;
    }
    String path = exchange.getRequestURI().getPath();
    if (path == null || !path.startsWith("/assets/")) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }
    String relative = path.substring("/assets/".length());
    try {
      relative = java.net.URLDecoder.decode(relative, java.nio.charset.StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      exchange.sendResponseHeaders(400, -1);
      return;
    }
    if (relative.isBlank() || relative.contains("..")) {
      exchange.sendResponseHeaders(400, -1);
      return;
    }
    java.nio.file.Path filePath = java.nio.file.Path.of("assets").resolve(relative).normalize();
    if (!java.nio.file.Files.exists(filePath)) {
      exchange.sendResponseHeaders(404, -1);
      return;
    }
    byte[] content = java.nio.file.Files.readAllBytes(filePath);
    exchange.getResponseHeaders().add("Content-Type", contentTypeFor(filePath));
    exchange.getResponseHeaders().add("Cache-Control", "no-store");
    exchange.sendResponseHeaders(200, content.length);
    try (OutputStream out = exchange.getResponseBody()) {
      out.write(content);
    }
  }

  private String contentTypeFor(java.nio.file.Path path) {
    String name = path.getFileName().toString().toLowerCase();
    if (name.endsWith(".png")) return "image/png";
    if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
    if (name.endsWith(".webp")) return "image/webp";
    return "application/octet-stream";
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
  <title>Quorum</title>
  <script type="module" src="https://unpkg.com/@google/model-viewer/dist/model-viewer.min.js"></script>
  <style>
    @import url('https://fonts.googleapis.com/css2?family=Press+Start+2P&family=Space+Mono:wght@400;700&display=swap');
    :root {
      --bg:#f6f8fb; --card:#ffffff; --text:#101622; --muted:#667085; --accent:#10b9c9;
      --accent-2:#f4a338; --red:#e15252; --blue:#2f6fe5; --ind:#1f9c6b;
      --scene:#f0f4fb; --scene-dark:#e6edf6;
    }
    * { box-sizing: border-box; }
    html, body { height: 100%; }
    body { margin:0; font-family: "Space Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, "Liberation Mono", "Courier New", monospace; background: var(--bg); color: var(--text); overflow: hidden; }
    header { padding: 12px 20px; border-bottom: 1px solid #d6dee9; display:flex; align-items:center; justify-content:center; gap: 14px; background: #ffffff; }
    .brand { display:flex; align-items:center; gap: 12px; }
    .brand img { height: 32px; width: auto; }
    .title { font-family: "Press Start 2P", "Space Mono", monospace; font-size: 14px; letter-spacing: 1px; text-transform: uppercase; }
    .row { display:flex; align-items:center; gap: 10px; flex-wrap: wrap; }
    .badge { display:inline-block; padding: 4px 10px; border-radius: 999px; background: #eef3f8; color: var(--muted); font-size: 11px; border: 1px solid #d6dee9; }
    #stageTitle.stage-running { border-color: #2ac769; color: #146b3a; box-shadow: 0 0 0 2px rgba(42,199,105,0.25); animation: stage-pulse 1s ease-in-out infinite; }
    @keyframes stage-pulse {
      0%, 100% { box-shadow: 0 0 0 2px rgba(42,199,105,0.25); }
      50% { box-shadow: 0 0 0 4px rgba(42,199,105,0.45); }
    }
    button { background: var(--accent); color: #0b1018; border: 0; padding: 9px 12px; border-radius: 8px; font-weight: 700; cursor: pointer; }
    button.secondary { background: #eef3f8; color: var(--text); border: 1px solid #d6dee9; }
    button[disabled] { background: #e4eaf2; color: #8a96a5; cursor: not-allowed; }
    #layout { display:grid; grid-template-columns: 1fr 360px; gap: 14px; padding: 12px 16px 52px; transition: all 0.3s ease; height: calc(100vh - 64px); }
    body.sidebar-collapsed #layout { grid-template-columns: 1fr 0px; }
    #sidebar { background: var(--card); border: 1px solid #d6dee9; border-radius: 12px; padding: 10px; overflow: hidden; transition: transform 0.3s ease, opacity 0.3s ease; max-height: 100%; display: grid; grid-template-rows: auto auto auto 1fr; gap: 8px; box-shadow: 0 8px 24px rgba(12,20,36,0.08); }
    body.sidebar-collapsed #sidebar { transform: translateX(110%); opacity: 0; pointer-events: none; }
    .panel { border: 1px solid #e1e7f0; border-radius: 10px; padding: 8px; margin: 0; background: #f9fbfe; overflow: hidden; }
    .panel h3 { margin: 0 0 8px 0; font-size: 12px; letter-spacing: 0.6px; text-transform: uppercase; color: var(--muted); }
    #log { height: 130px; overflow: auto; white-space: pre-wrap; font-size: 11px; background: #ffffff; border: 1px solid #e1e7f0; border-radius: 8px; padding: 6px; }
    .bill-text { white-space: pre-wrap; font-size: 12px; color: #283248; line-height: 1.4; }
    .bill-section { margin-top: 6px; }
    details summary { cursor: pointer; color: var(--muted); font-size: 12px; margin-top: 6px; }

    #scene { position: relative; min-height: 70vh; border-radius: 16px; overflow: hidden; border: 1px solid #d6dee9;
      background: url('/assets/background.png') center/cover no-repeat;
    }
    #scene::before,
    #scene::after {
      content: none;
    }
    .scene-header { position: absolute; z-index: 3; top: 10px; left: 12px; right: 12px; display:flex; align-items:center; justify-content:flex-end; pointer-events: none; }
    .scene-title { font-family: "Press Start 2P", "Space Mono", monospace; font-size: 12px; }
    #repGrid {
      position: absolute;
      inset: 0;
      z-index: 2;
      width: 100%;
      height: 100%;
      padding: 0;
    }
    .rep {
      position: absolute;
      width: 140px;
      height: 170px;
      display:flex;
      align-items:center;
      justify-content:center;
    }
    .rep model-viewer {
      width: 140px;
      height: 170px;
      background: transparent;
    }
    .rep .name-tag {
      position: absolute;
      bottom: -18px;
      font-size: 10px;
      color: var(--muted);
      text-align:center;
      width: 100%;
      opacity: 0.8;
    }
    .rep .tooltip {
      position: absolute;
      bottom: 64px;
      left: 50%;
      transform: translateX(-50%);
      background: #ffffff;
      border: 1px solid #d6dee9;
      border-radius: 8px;
      padding: 8px;
      font-size: 11px;
      width: 160px;
      opacity: 0;
      pointer-events: none;
      transition: opacity 0.2s ease, transform 0.2s ease;
      color: var(--text);
      z-index: 5;
    }
    .rep:hover .tooltip { opacity: 1; transform: translateX(-50%) translateY(-4px); }
    .rep .speech {
      position: absolute;
      bottom: 150px;
      left: 50%;
      transform: translateX(-50%);
      background: #ffffff;
      border: 1px solid var(--accent-2);
      border-radius: 10px;
      padding: 6px 8px;
      font-size: 10px;
      width: 240px;
      color: #1f2a3a;
      opacity: 0;
      pointer-events: none;
      transition: opacity 0.2s ease;
      z-index: 6;
    }
    .rep.speaking .speech { opacity: 1; }
    .party-dem { --rep-color: var(--blue); }
    .party-gop { --rep-color: var(--red); }
    .party-ind { --rep-color: var(--ind); }

    #chatLog { height: 180px; overflow: auto; background: #ffffff; border: 1px solid #e1e7f0; border-radius: 8px; padding: 6px; font-size: 11px; }
    .chat-line { display:flex; gap: 6px; margin-bottom: 4px; }
    .chat-name { font-weight: 700; }
    .chat-text { color: #2c3448; }
    #chatInput { display:flex; gap: 6px; margin-top: 6px; }
    #chatMessage { flex:1; min-width: 120px; padding: 6px; border-radius: 6px; border:1px solid #d6dee9; background:#ffffff; color:#1b2433; }

    #ticker {
      position: fixed;
      left: 0; right: 0; bottom: 0;
      background: #ffffff;
      border-top: 1px solid #d6dee9;
      padding: 6px 12px;
      display:flex;
      gap: 10px;
      align-items:center;
      font-size: 11px;
      color: #1f2a3a;
      z-index: 20;
    }
    #latestLine { color: #2f6fe5; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }

    .bill-text { max-height: 120px; overflow: auto; }
    .bill-section { margin-top: 6px; }
    details summary { margin-top: 6px; }
    #billPanel { transition: transform 0.35s ease, box-shadow 0.35s ease; }
    body.celebrate #billPanel {
      transform: scale(1.03);
      box-shadow: 0 0 24px rgba(16,185,201,0.35);
      border-color: #66d4df;
    }
    #celebration {
      position: fixed;
      inset: 0;
      display: none;
      align-items: center;
      justify-content: center;
      background: rgba(242,246,252,0.8);
      z-index: 50;
      overflow: hidden;
    }
    #celebration.active { display: flex; }
    #celebration h1 {
      font-family: "Press Start 2P", "Space Mono", monospace;
      font-size: 18px;
      color: #101622;
      text-align: center;
      text-shadow: 0 4px 12px rgba(12,20,36,0.15);
      background: rgba(255,255,255,0.95);
      border: 1px solid #d6dee9;
      padding: 16px 18px;
      border-radius: 12px;
    }
    .confetti {
      position: absolute;
      width: 8px;
      height: 12px;
      opacity: 0.8;
      animation: confetti-fall 2.6s linear forwards;
    }
    @keyframes confetti-fall {
      0% { transform: translateY(-10vh) rotate(0deg); opacity: 1; }
      100% { transform: translateY(110vh) rotate(360deg); opacity: 0; }
    }
    @media (max-width: 1100px) {
      #layout { grid-template-columns: 1fr; height: auto; }
      body { overflow: auto; }
      #sidebar { transform: none; opacity: 1; max-height: none; display: block; }
      #log, #chatLog, .bill-text { overflow: auto; }
    }
  </style>
</head>
<body>
  <header>
    <span id="stageTitle" class="badge">Loading Stage</span>
    <div class="brand">
      <img src="/assets/quorum.png" alt="Quorum" />
      <div class="title">Quorum</div>
    </div>
    <span id="statusBadge" class="badge">Connecting...</span>
  </header>
  <div id="layout">
    <section id="scene">
      <div class="scene-header">
        <div id="currentSpeaker" class="badge">Waiting for speaker...</div>
      </div>
      <div id="repGrid"></div>
    </section>
    <aside id="sidebar">
      <div class="panel">
        <h3>Popular Vote</h3>
        <div class="row" style="margin-bottom:8px;">
          <button id="yesBtn">Vote YES</button>
          <button id="noBtn">Vote NO</button>
        </div>
        <div id="voteStats" class="badge">Waiting...</div>
        <div id="voteNotice" class="badge" style="margin-top:8px;">One vote per device.</div>
      </div>
      <div class="panel">
        <h3>Console Output</h3>
        <div id="log"></div>
      </div>
      <div class="panel" id="billPanel">
        <h3>Bill</h3>
        <div class="bill-section">
          <div class="badge">Current One-Pager</div>
          <div id="billOnePager" class="bill-text" style="margin-top:6px;"></div>
          <details>
            <summary>Original Bill</summary>
            <div id="originalBill" class="bill-text" style="margin-top:6px;"></div>
          </details>
        </div>
        <div class="bill-section">
          <div class="badge">Revised Bill</div>
          <div id="revisedSummary" class="bill-text" style="margin-top:6px;"></div>
          <div id="revisedChanges" class="bill-text" style="margin-top:6px;"></div>
          <div id="revisedText" class="bill-text" style="margin-top:6px;"></div>
        </div>
      </div>
      <div class="panel">
        <h3>Public Comments</h3>
        <div id="chatLog"></div>
        <div id="chatInput">
          <input id="chatMessage" placeholder="Comment" />
          <button id="chatSend">Send</button>
        </div>
        <div class="badge" style="margin-top:8px;">Names are assigned automatically.</div>
      </div>
    </aside>
  </div>
  <div id="celebration">
    <h1>Bill Passed</h1>
  </div>
  <div id="ticker">
    <span class="badge">Latest</span>
    <span id="latestLine"></span>
  </div>
  <script>
    let nextIndex = 0;
    const logEl = document.getElementById('log');
    const statusBadge = document.getElementById('statusBadge');
    const stageTitle = document.getElementById('stageTitle');
    const latestLine = document.getElementById('latestLine');
    const currentSpeaker = document.getElementById('currentSpeaker');
    const voteStats = document.getElementById('voteStats');
    const voteNotice = document.getElementById('voteNotice');
    const yesBtn = document.getElementById('yesBtn');
    const noBtn = document.getElementById('noBtn');
    const billOnePager = document.getElementById('billOnePager');
    const originalBill = document.getElementById('originalBill');
    const revisedSummary = document.getElementById('revisedSummary');
    const revisedChanges = document.getElementById('revisedChanges');
    const revisedText = document.getElementById('revisedText');
    const repGrid = document.getElementById('repGrid');
    const celebration = document.getElementById('celebration');
    let celebrated = false;

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
        stageTitle.textContent = data.currentStage || 'Idle';
        if (data.stageRunning) {
          stageTitle.classList.add('stage-running');
        } else {
          stageTitle.classList.remove('stage-running');
        }
        latestLine.textContent = data.lastLine || '';
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
        updateSpeaker(data);
        maybeCelebrate(data.finalOutcome);
      } catch (err) {
        statusBadge.textContent = 'Disconnected';
      }
    }

    async function fetchBill() {
      try {
        const res = await fetch('/bill', { cache: 'no-store' });
        if (!res.ok) throw new Error('bill fetch failed');
        const data = await res.json();
        const onePagerText = (data.onePager || '').trim();
        const originalText = (data.originalText || '').trim();
        billOnePager.textContent = onePagerText || originalText || 'Bill not loaded yet.';
        originalBill.textContent = originalText || 'No original bill available.';

        const summaryText = (data.revisedSummary || '').trim();
        const revisedTextValue = (data.revisedText || '').trim();
        const changes = Array.isArray(data.revisedChanges) ? data.revisedChanges.filter(Boolean) : [];
        revisedSummary.textContent = summaryText ? `Summary: ${summaryText}` : 'No revisions yet.';
        revisedChanges.textContent = changes.length ? `Key changes: ${changes.join('; ')}` : '';
        revisedText.textContent = revisedTextValue;
      } catch (err) {
        // Keep last shown values.
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
    const repMap = new Map();
    let glbFiles = [];
    const REP_W = 140;
    const REP_H = 170;
    const BASE_PITCH = 0;
    const BASE_ROLL = 0;
    const BASE_YAW_OFFSET = 180;
    let currentSpeakerId = '';

    async function fetchChat() {
      try {
        const res = await fetch(`/chat?since=${chatIndex}`, { cache: 'no-store' });
        if (!res.ok) throw new Error('chat fetch failed');
        const data = await res.json();
        if (data.messages && data.messages.length) {
          data.messages.forEach(msg => {
            appendChatLine(msg.name, msg.message);
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

    async function fetchReps() {
      try {
        const res = await fetch('/reps', { cache: 'no-store' });
        if (!res.ok) throw new Error('reps fetch failed');
        const reps = await res.json();
        if (glbFiles.length === 0) {
          glbFiles = await fetchGlbFiles();
        }
        repGrid.textContent = '';
        repPositions.length = 0;
        const bounds = repGrid.getBoundingClientRect();
        reps.sort((a, b) => a.name.localeCompare(b.name));
        reps.forEach((rep, idx) => {
          const partyClass = partyToClass(rep.party);
          const card = document.createElement('div');
          card.className = `rep ${partyClass}`;
          card.dataset.id = rep.id;
          card.style.animationDelay = `${(idx % 10) * 0.1}s`;
          const pos = randomSpot(idx, bounds);
          repPositions.push(pos);
          card.style.left = `${pos.x}px`;
          card.style.top = `${pos.y}px`;
          const model = document.createElement('model-viewer');
          model.setAttribute('loading', 'lazy');
          model.setAttribute('interaction-prompt', 'none');
          model.setAttribute('bounds', 'tight');
          model.setAttribute('camera-orbit', '0deg 75deg 3.8m');
          model.setAttribute('camera-target', '0m 1m 0m');
          model.setAttribute('min-camera-orbit', '0deg 75deg 3.8m');
          model.setAttribute('max-camera-orbit', '0deg 75deg 3.8m');
          model.setAttribute('field-of-view', '28deg');
          model.setAttribute('environment-image', 'neutral');
          model.setAttribute('shadow-intensity', '0.8');
          model.setAttribute('exposure', '1.1');
          const glb = pickGlb(idx);
          if (glb) {
            model.setAttribute('src', glb);
            model.setAttribute('autoplay', '');
          }
          model.addEventListener('load', () => {
            const animations = model.availableAnimations || [];
            const walk = animations.find(name => /walk|run|move/i.test(name)) || '';
            const idle = animations.find(name => /idle|stand|rest/i.test(name)) || '';
            if (walk) model.dataset.walkAnim = walk;
            if (idle) model.dataset.idleAnim = idle;
            const initial = walk || idle || (animations[0] || '');
            if (initial) {
              model.setAttribute('animation-name', initial);
              model.setAttribute('autoplay', '');
            }
          });
          const nameTag = document.createElement('div');
          nameTag.className = 'name-tag';
          nameTag.textContent = rep.name.split(' ').slice(-1)[0];
          const tooltip = document.createElement('div');
          tooltip.className = 'tooltip';
          tooltip.innerHTML = `<strong>${rep.name}</strong><br/>Party: ${rep.party}<br/>Agency: ${rep.agency || 'Unassigned'}`;
          const speech = document.createElement('div');
          speech.className = 'speech';
          speech.textContent = '';
          card.appendChild(model);
          card.appendChild(nameTag);
          card.appendChild(tooltip);
          card.appendChild(speech);
          repGrid.appendChild(card);
          repMap.set(rep.id, card);
        });
      } catch (err) {
        statusBadge.textContent = 'Disconnected';
      }
    }

    async function fetchGlbFiles() {
      try {
        const res = await fetch('/glb', { cache: 'no-store' });
        if (!res.ok) throw new Error('glb fetch failed');
        const files = await res.json();
        if (!Array.isArray(files)) return [];
        return files.map(name => `/assets/GLB%20format/${encodeURIComponent(name)}`);
      } catch (err) {
        return [];
      }
    }

    function pickGlb(idx) {
      if (!glbFiles.length) return '';
      return glbFiles[idx % glbFiles.length];
    }

    const repPositions = [];

    function randomSpot(seed, bounds) {
      const width = Math.max(0, bounds.width - REP_W);
      const height = Math.max(0, bounds.height - REP_H);
      const minY = Math.max(0, Math.floor(bounds.height * 0.5));
      const maxY = Math.max(minY, height);
      const x = Math.floor(Math.random() * Math.max(1, width));
      const y = minY + Math.floor(Math.random() * Math.max(1, maxY - minY));
      const speed = 10 + Math.random() * 20;
      const angle = Math.random() * Math.PI * 2;
      const vx = Math.cos(angle) * speed;
      const vy = Math.sin(angle) * speed;
      const heading = headingFromVelocity(vx, vy);
      return {
        x,
        y,
        seed,
        vx,
        vy,
        heading,
        mode: 'move',
        modeUntil: 0
      };
    }

    let lastFrame = 0;
    function animateWander(ts) {
      if (!lastFrame) lastFrame = ts;
      const dt = Math.min(0.05, (ts - lastFrame) / 1000);
      lastFrame = ts;
      const bounds = repGrid.getBoundingClientRect();
      const width = Math.max(0, bounds.width - REP_W);
      const height = Math.max(0, bounds.height - REP_H);
      const minY = Math.max(0, Math.floor(bounds.height * 0.5));
      const maxY = Math.max(minY, height);
      repPositions.forEach((pos, idx) => {
        if (!pos.modeUntil || ts > pos.modeUntil) {
          const willIdle = Math.random() < 0.6;
          if (willIdle) {
            pos.mode = 'idle';
            pos.vx = 0;
            pos.vy = 0;
            pos.modeUntil = ts + (3500 + Math.random() * 3500);
          } else {
            pos.mode = 'move';
            const angle = Math.random() * Math.PI * 2;
            const speed = 14 + Math.random() * 22;
            pos.vx = Math.cos(angle) * speed;
            pos.vy = Math.sin(angle) * speed;
            pos.modeUntil = ts + (2500 + Math.random() * 2500);
          }
        }

        const isIdle = pos.mode === 'idle';
        let nx = pos.x;
        let ny = pos.y;
        if (!isIdle) {
          nx = pos.x + pos.vx * dt;
          ny = pos.y + pos.vy * dt;
          if (nx <= 0 || nx >= width) {
            pos.vx *= -1;
            nx = Math.max(0, Math.min(width, nx));
          }
          if (ny <= minY || ny >= maxY) {
            pos.vy *= -1;
            ny = Math.max(minY, Math.min(maxY, ny));
          }
          const targetHeading = headingFromVelocity(pos.vx, pos.vy);
          pos.heading = smoothAngle(pos.heading, targetHeading, 0.15);
        }
        pos.x = nx;
        pos.y = ny;
        const card = repGrid.children[idx];
        if (card) {
          card.style.left = `${nx}px`;
          card.style.top = `${ny}px`;
          const model = card.querySelector('model-viewer');
          if (model) {
            const heading = pos.heading || 0;
            model.setAttribute('orientation', `${BASE_PITCH}deg ${heading}deg ${BASE_ROLL}deg`);
            const speed = Math.hypot(pos.vx, pos.vy);
            const walk = model.dataset.walkAnim || '';
            const idle = model.dataset.idleAnim || '';
            const desired = isIdle || speed < 1 ? idle : walk;
            if (desired && model.getAttribute('animation-name') !== desired) {
              model.setAttribute('animation-name', desired);
            }
            if (isIdle || speed < 1) {
              if (idle) {
                model.play();
              } else {
                model.pause();
              }
            } else {
              model.play();
            }
          }
        }
      });
      requestAnimationFrame(animateWander);
    }

    function smoothAngle(current, target, factor) {
      if (Number.isNaN(current)) return target;
      let diff = ((target - current + 540) % 360) - 180;
      return current + diff * factor;
    }

    function headingFromVelocity(vx, vy) {
      if (vx === 0 && vy === 0) return 0;
      const raw = Math.atan2(vx, -vy) * (180 / Math.PI);
      return (raw + BASE_YAW_OFFSET + 360) % 360;
    }

    function partyToClass(party) {
      const text = (party || '').toLowerCase();
      if (text.includes('dem')) return 'party-dem';
      if (text.includes('rep')) return 'party-gop';
      if (text.includes('ind')) return 'party-ind';
      return 'party-ind';
    }

    function updateSpeaker(data) {
      const speakerId = data.currentSpeakerId || '';
      const speakerName = data.currentSpeakerName || 'Waiting for speaker...';
      const speakerText = data.currentSpeakerText || '';
      currentSpeaker.textContent = speakerId ? `${speakerName} speaking` : 'Waiting for speaker...';
      if (currentSpeakerId && repMap.has(currentSpeakerId)) {
        const prev = repMap.get(currentSpeakerId);
        prev.classList.remove('speaking');
        prev.querySelector('.speech').textContent = '';
      }
      currentSpeakerId = speakerId;
      if (speakerId && repMap.has(speakerId)) {
        const next = repMap.get(speakerId);
        next.classList.add('speaking');
        next.querySelector('.speech').textContent = speakerText || `${speakerName} speaking`;
      }
    }

    function maybeCelebrate(outcome) {
      if (celebrated) return;
      if (outcome !== 'PASS') return;
      celebrated = true;
      document.body.classList.add('celebrate');
      celebration.classList.add('active');
      launchConfetti();
      setTimeout(() => {
        celebration.classList.remove('active');
      }, 4000);
    }

    function launchConfetti() {
      const colors = ['#4dd0e1', '#f3b24a', '#5ec18f', '#f05b5b', '#4d9be6'];
      for (let i = 0; i < 120; i++) {
        const piece = document.createElement('div');
        piece.className = 'confetti';
        piece.style.left = `${Math.random() * 100}%`;
        piece.style.background = colors[Math.floor(Math.random() * colors.length)];
        piece.style.animationDelay = `${Math.random() * 0.8}s`;
        piece.style.transform = `translateY(-10vh) rotate(${Math.random() * 360}deg)`;
        celebration.appendChild(piece);
        setTimeout(() => piece.remove(), 3500);
      }
    }

    function appendChatLine(name, message) {
      const line = document.createElement('div');
      line.className = 'chat-line';
      const nameSpan = document.createElement('span');
      nameSpan.className = 'chat-name';
      nameSpan.style.color = colorForName(name);
      nameSpan.textContent = name;
      const textSpan = document.createElement('span');
      textSpan.className = 'chat-text';
      textSpan.textContent = message;
      line.appendChild(nameSpan);
      line.appendChild(textSpan);
      chatLog.appendChild(line);
    }

    function colorForName(name) {
      let hash = 0;
      for (let i = 0; i < name.length; i++) {
        hash = name.charCodeAt(i) + ((hash << 5) - hash);
      }
      const hue = Math.abs(hash) % 360;
      return `hsl(${hue}, 65%, 70%)`;
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
    setInterval(fetchBill, 2000);
    // Fetch once; avoid resetting positions during the run.
    fetchLogs();
    fetchStatus();
    fetchChat();
    fetchBill();
    fetchReps();
    requestAnimationFrame(animateWander);
  </script>
</body>
</html>
""";
  }
}
