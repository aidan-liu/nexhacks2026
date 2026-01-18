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
    if (name.endsWith(".glb")) return "model/gltf-binary";
    if (name.endsWith(".gltf")) return "model/gltf+json";
    if (name.endsWith(".mov")) return "video/quicktime";
    if (name.endsWith(".mp4")) return "video/mp4";
    if (name.endsWith(".webm")) return "video/webm";
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
  <script type="importmap">
    {
      "imports": {
        "three": "https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.js",
        "three/addons/": "https://cdn.jsdelivr.net/npm/three@0.160.0/examples/jsm/"
      }
    }
  </script>
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
    header { padding: 10px 20px 12px; border-bottom: 1px solid #d6dee9; display:flex; flex-direction: column; align-items:center; gap: 8px; background: #ffffff; }
    .header-top { display:flex; align-items:center; justify-content:center; gap: 18px; width: 100%; }
    .brand { display:flex; align-items:center; gap: 12px; }
    .brand img { height: 32px; width: auto; }
    .title { font-family: "Press Start 2P", "Space Mono", monospace; font-size: 14px; letter-spacing: 1px; text-transform: uppercase; }
    .row { display:flex; align-items:center; gap: 10px; flex-wrap: wrap; }
    .badge { display:inline-block; padding: 4px 10px; border-radius: 999px; background: #eef3f8; color: var(--muted); font-size: 11px; border: 1px solid #d6dee9; }
    @keyframes stage-pulse {
      0%, 100% { box-shadow: 0 0 0 2px rgba(42,199,105,0.25); }
      50% { box-shadow: 0 0 0 4px rgba(42,199,105,0.45); }
    }
    .stage-flow {
      display:flex;
      align-items:center;
      justify-content:center;
      gap: 6px;
      flex-wrap: wrap;
      font-size: 10px;
      color: var(--muted);
    }
    .stage-node {
      padding: 4px 8px;
      border-radius: 999px;
      border: 1px solid #d6dee9;
      background: #f4f7fb;
      text-transform: uppercase;
      letter-spacing: 0.4px;
    }
    .stage-node.active {
      border-color: #2ac769;
      color: #146b3a;
      background: #e7f7ee;
    }
    .stage-node.running {
      animation: stage-pulse 1s ease-in-out infinite;
    }
    .stage-arrow {
      color: #98a2b3;
      font-size: 12px;
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

    .hidden { display: none; }

    #scene { position: relative; min-height: 70vh; border-radius: 16px; overflow: hidden; border: 1px solid #d6dee9;
      background: url('/assets/background.png') center/cover no-repeat;
    }
    #scene.tv-dim { filter: brightness(0.75) saturate(0.8); }
    #scene::before,
    #scene::after {
      content: none;
    }
    #scene-container {
      position: absolute;
      inset: 0;
      z-index: 1;
    }
    #scene-container canvas { display: block; width: 100%; height: 100%; }
    .scene-header { position: absolute; z-index: 6; top: 10px; left: 12px; right: 12px; display:flex; align-items:center; justify-content:flex-end; pointer-events: none; }
    .speaker-stack { display:flex; flex-direction: column; align-items: flex-end; gap: 6px; }
    #currentSpeech {
      background: #ffffff;
      border: 1px solid var(--accent-2);
      border-radius: 18px;
      padding: 8px 10px;
      font-size: 11px;
      width: min(360px, 70vw);
      color: #1f2a3a;
      line-height: 1.35;
      white-space: pre-wrap;
    }
    .scene-title { font-family: "Press Start 2P", "Space Mono", monospace; font-size: 12px; }
    #repTooltip {
      position: absolute;
      top: 12px;
      left: 12px;
      z-index: 5;
      background: #ffffff;
      border: 1px solid #d6dee9;
      border-radius: 10px;
      padding: 8px 10px;
      font-size: 11px;
      color: var(--text);
      pointer-events: none;
      box-shadow: 0 10px 22px rgba(12,20,36,0.14);
      white-space: pre-wrap;
      max-width: 260px;
    }
    #ambientBubbles {
      position: absolute;
      inset: 0;
      z-index: 5;
      pointer-events: none;
    }
    #stageBanner {
      position: absolute;
      top: 10px;
      left: 50%;
      transform: translateX(-50%);
      background: #ffffff;
      border: 2px solid #d6dee9;
      border-radius: 16px;
      padding: 10px 16px;
      font-family: "Press Start 2P", "Space Mono", monospace;
      font-size: 20px;
      color: #101622;
      letter-spacing: 0.6px;
      text-transform: uppercase;
      z-index: 7;
      box-shadow: 0 12px 28px rgba(12,20,36,0.16);
      pointer-events: none;
    }
    .rep-bubble {
      position: absolute;
      transform: translate(-50%, -120%);
      background: #ffffff;
      border: 1px solid #d6dee9;
      border-radius: 14px;
      padding: 6px 8px;
      font-size: 9px;
      color: #1f2a3a;
      max-width: 200px;
      line-height: 1.3;
      box-shadow: 0 8px 18px rgba(12,20,36,0.12);
      white-space: pre-wrap;
    }

    #cutscene {
      position: absolute;
      inset: 0;
      display: flex;
      align-items: center;
      justify-content: center;
      opacity: 0;
      pointer-events: none;
      z-index: 10;
      transition: opacity 0.2s ease;
    }
    #cutscene.active { opacity: 1; pointer-events: auto; }
    #cutsceneShade {
      position: absolute;
      inset: 0;
      background: rgba(0, 0, 0, 0.92);
    }
    #cutsceneFrame {
      position: relative;
      width: min(60vw, 480px);
      aspect-ratio: 16 / 9;
      background: #0b0f14;
      border: 2px solid #d6dee9;
      border-radius: 12px;
      overflow: hidden;
      box-shadow: 0 18px 40px rgba(10, 18, 30, 0.25);
      transform: scaleY(0.02);
      opacity: 0;
    }
    #cutscene.active #cutsceneFrame {
      animation: tv-on 0.35s ease forwards;
    }
    #cutscene.off #cutsceneFrame {
      animation: tv-off 0.3s ease forwards;
    }
    #cutsceneVideo {
      width: 100%;
      height: 100%;
      object-fit: contain;
      background: #0b0f14;
    }
    #liveBadge {
      position: absolute;
      top: 8px;
      left: 8px;
      display: inline-flex;
      align-items: center;
      gap: 6px;
      font-size: 11px;
      font-weight: 700;
      color: #ffffff;
      background: rgba(16, 24, 36, 0.8);
      border: 1px solid rgba(255, 255, 255, 0.2);
      padding: 4px 8px;
      border-radius: 999px;
      letter-spacing: 0.6px;
    }
    #liveDot {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #ff3b3b;
      box-shadow: 0 0 8px rgba(255, 59, 59, 0.7);
      animation: live-pulse 1s ease-in-out infinite;
    }
    @keyframes live-pulse {
      0%, 100% { opacity: 1; }
      50% { opacity: 0.3; }
    }
    @keyframes tv-on {
      0% { transform: scaleY(0.05) scaleX(0.6); opacity: 0; }
      60% { transform: scaleY(1.02) scaleX(1.02); opacity: 1; }
      100% { transform: scaleY(1) scaleX(1); opacity: 1; }
    }
    @keyframes tv-off {
      0% { transform: scaleY(1) scaleX(1); opacity: 1; }
      70% { transform: scaleY(0.08) scaleX(0.6); opacity: 0.7; }
      100% { transform: scaleY(0.01) scaleX(0.2); opacity: 0; }
    }

    #chatLog { height: 180px; overflow: auto; background: #ffffff; border: 1px solid #e1e7f0; border-radius: 8px; padding: 6px; font-size: 11px; }
    .chat-line { display:flex; gap: 6px; margin-bottom: 4px; }
    .chat-name { font-weight: 700; }
    .chat-text { color: #2c3448; }
    #chatInput { display:flex; gap: 6px; margin-top: 6px; }
    #chatMessage { flex:1; min-width: 120px; padding: 6px; border-radius: 6px; border:1px solid #d6dee9; background:#ffffff; color:#1b2433; }

    #ticker {
      position: fixed;
      left: 16px;
      bottom: 16px;
      background: #ffffff;
      border: 1px solid #d6dee9;
      border-radius: 12px;
      padding: 8px 12px;
      display:flex;
      gap: 10px;
      align-items:center;
      font-size: 11px;
      color: #1f2a3a;
      z-index: 20;
      width: min(380px, 60vw);
      box-shadow: 0 10px 24px rgba(12,20,36,0.12);
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
    <div class="header-top">
      <div class="brand">
      <img src="/assets/quorum.png" alt="Quorum" />
      <div class="title">Quorum</div>
      </div>
      <span id="statusBadge" class="badge">Connecting...</span>
    </div>
    <div id="stageFlow" class="stage-flow" aria-label="Simulation flow"></div>
  </header>
  <div id="layout">
    <section id="scene">
      <div id="scene-container"></div>
      <div class="scene-header">
        <div class="speaker-stack">
          <div id="currentSpeaker" class="badge">Waiting for speaker...</div>
          <div id="currentSpeech" class="hidden"></div>
        </div>
      </div>
      <div id="repTooltip" class="hidden"></div>
      <div id="stageBanner"></div>
      <div id="ambientBubbles"></div>
      <div id="cutscene">
        <div id="cutsceneShade"></div>
      <div id="cutsceneFrame">
        <div id="liveBadge"><span id="liveDot"></span>LIVE</div>
          <video id="cutsceneVideo" muted playsinline preload="auto"></video>
      </div>
      </div>
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
  <script type="module">
    import * as THREE from 'https://cdn.jsdelivr.net/npm/three@0.160.0/build/three.module.js';
    import { OrbitControls } from 'https://cdn.jsdelivr.net/npm/three@0.160.0/examples/jsm/controls/OrbitControls.js';
    import { GLTFLoader } from 'https://cdn.jsdelivr.net/npm/three@0.160.0/examples/jsm/loaders/GLTFLoader.js';

    let nextIndex = 0;
    const logEl = document.getElementById('log');
    const statusBadge = document.getElementById('statusBadge');
    const stageFlow = document.getElementById('stageFlow');
    const latestLine = document.getElementById('latestLine');
    const currentSpeaker = document.getElementById('currentSpeaker');
    const currentSpeech = document.getElementById('currentSpeech');
    const voteStats = document.getElementById('voteStats');
    const voteNotice = document.getElementById('voteNotice');
    const yesBtn = document.getElementById('yesBtn');
    const noBtn = document.getElementById('noBtn');
    const billOnePager = document.getElementById('billOnePager');
    const originalBill = document.getElementById('originalBill');
    const revisedSummary = document.getElementById('revisedSummary');
    const revisedChanges = document.getElementById('revisedChanges');
    const revisedText = document.getElementById('revisedText');
    const sceneContainer = document.getElementById('scene-container');
    const repTooltip = document.getElementById('repTooltip');
    const ambientBubbles = document.getElementById('ambientBubbles');
    const stageBanner = document.getElementById('stageBanner');
    const scene = document.getElementById('scene');
    const cutscene = document.getElementById('cutscene');
    const cutsceneVideo = document.getElementById('cutsceneVideo');
    const cutsceneFrame = document.getElementById('cutsceneFrame');
    const celebration = document.getElementById('celebration');
    let celebrated = false;
    let logsPrimed = false;
    let currentOutcome = '';
    let lastOutcomeCutscene = '';
    const cutsceneQueue = [];
    let cutscenePlaying = false;
    let scene3d = null;
    let repsLoaded = false;
    let lastStage = '';
    let lastStageRunning = false;
    let speechToken = 0;
    let speechTimer = null;
    let lastSpeechText = '';
    const ambientLines = [
      'We need a clean funding baseline.',
      'Constituents want accountability.',
      'This reads like a mandate.',
      'I can back this with amendments.',
      'We should cap the cost exposure.',
      'The oversight language is weak.',
      'We need regional equity here.',
      'This is a nonstarter as written.',
      'Let’s tighten the enforcement.',
      'Transparency should be the floor.'
    ];
    const ambientBubbleState = [];
    const STAGE_NAMES = [
      'Pull Bill',
      'Parse Bill',
      'Judge Assign Agency',
      'Committee Deliberation',
      'Primary Floor Debate',
      'Public Forum',
      'Threshold Decision',
      'Revise Failed Bill',
      'Finalize',
      'Complete'
    ];
    const NODE_CUTSCENES = {
      CommitteeDeliberation: '/assets/videos/Committee.mov',
      PrimaryFloorDebate: '/assets/videos/Talking.mov',
      PublicForum: '/assets/videos/Talking.mov'
    };

    async function fetchLogs() {
      try {
        const res = await fetch(`/log?since=${nextIndex}`, { cache: 'no-store' });
        if (!res.ok) throw new Error('log fetch failed');
        const data = await res.json();
        if (data.lines && data.lines.length) {
          data.lines.forEach(line => {
            logEl.textContent += line + "\\n";
            updateOutcomeFromLine(line);
            if (logsPrimed) {
              const match = line.match(/^==> Done: (.+)$/);
              if (match) {
                handleNodeDone(match[1].trim());
              }
            }
          });
          logEl.scrollTop = logEl.scrollHeight;
        }
        if (!logsPrimed) {
          logsPrimed = true;
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
        updateStageFlow(data.currentStage || 'Idle', data.stageRunning);
        if (data.currentStage && data.currentStage !== lastStage) {
          showStageBanner(data.currentStage);
        }
        if (lastStageRunning && !data.stageRunning && data.currentStage) {
          const nodeName = stageDisplayToNode(data.currentStage);
          if (nodeName) {
            handleNodeDone(nodeName);
          }
        }
        lastStage = data.currentStage || '';
        lastStageRunning = !!data.stageRunning;
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
        if (data.finalOutcome) {
          currentOutcome = String(data.finalOutcome || '').trim().toUpperCase();
          if (currentOutcome && currentOutcome !== 'PASS' && currentOutcome !== 'KILLED') {
            lastOutcomeCutscene = '';
          }
        }
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
    let glbFiles = [];
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

    class RepActor {
      constructor(data, url, loader) {
        this.data = data;
        this.url = url;
        this.loader = loader;
        this.group = new THREE.Group();
        this.group.userData.repId = data.id;
        this.basePosition = new THREE.Vector3();
        this.targetPosition = new THREE.Vector3();
        this.facingOffset = -Math.PI / 2;
        this.rotationY = this.facingOffset;
        this.targetRotationY = this.facingOffset;
        this.turnSpeed = 6.0;
        this.bobPhase = Math.random() * Math.PI * 2;
        this.bobSpeed = 1.3 + Math.random() * 0.6;
        this.bobAmount = 0.03;
        this.isSpeaking = false;
        this.mixer = null;
        this.speakingGlow = null;
        this._buildIndicators();
      }

      async load() {
        if (!this.url) {
          this._buildFallback();
          return;
        }
        try {
          const gltf = await new Promise((resolve, reject) => {
            this.loader.load(this.url, resolve, undefined, reject);
          });
          const model = gltf.scene;
          model.traverse((child) => {
            if (child.isMesh) {
              child.castShadow = true;
              child.receiveShadow = true;
              child.userData.repId = this.data.id;
              if (child.isSkinnedMesh) child.frustumCulled = false;
            }
          });

          const box = new THREE.Box3().setFromObject(model);
          const size = box.getSize(new THREE.Vector3());
          const targetHeight = 2.1;
          const scale = size.y > 0 ? targetHeight / size.y : 1;
          model.scale.setScalar(scale);

          const scaledBox = new THREE.Box3().setFromObject(model);
          const center = scaledBox.getCenter(new THREE.Vector3());
          model.position.y = -scaledBox.min.y;
          model.position.x = -center.x;
          model.position.z = -center.z;
          model.rotation.y = Math.PI;
          this.group.add(model);

          if (gltf.animations && gltf.animations.length) {
            this.mixer = new THREE.AnimationMixer(model);
            const action = this.mixer.clipAction(gltf.animations[0]);
            action.play();
          }
        } catch (err) {
          this._buildFallback();
        }
      }

      _buildIndicators() {
        const shadow = new THREE.Mesh(
          new THREE.CircleGeometry(0.6, 24),
          new THREE.MeshBasicMaterial({ color: 0x000000, transparent: true, opacity: 0.35 })
        );
        shadow.rotation.x = -Math.PI / 2;
        shadow.position.y = -0.02;
        shadow.renderOrder = 4;
        this.group.add(shadow);

        const ring = new THREE.Mesh(
          new THREE.RingGeometry(0.35, 0.45, 32),
          new THREE.MeshBasicMaterial({ color: 0xd1d5db, side: THREE.DoubleSide, transparent: true, opacity: 0.8 })
        );
        ring.rotation.x = -Math.PI / 2;
        ring.position.y = 0.02;
        this.group.add(ring);

        const glow = new THREE.Mesh(
          new THREE.RingGeometry(0.5, 0.7, 32),
          new THREE.MeshBasicMaterial({ color: 0xffd9b3, side: THREE.DoubleSide, transparent: true, opacity: 0 })
        );
        glow.rotation.x = -Math.PI / 2;
        glow.position.y = 0.02;
        this.group.add(glow);
        this.speakingGlow = glow;
      }

      _buildFallback() {
        const mesh = new THREE.Mesh(
          new THREE.CylinderGeometry(0.23, 0.23, 1.2, 12),
          new THREE.MeshStandardMaterial({ color: 0xb8b8b8 })
        );
        mesh.castShadow = true;
        mesh.position.y = 0.6;
        mesh.userData.repId = this.data.id;
        this.group.add(mesh);
      }

      setSpeaking(active) {
        this.isSpeaking = Boolean(active);
        if (this.speakingGlow) {
          this.speakingGlow.material.opacity = active ? 0.35 : 0;
        }
      }

      setPosition(x, y, z) {
        this.basePosition.set(x, y, z);
        this.targetPosition.set(x, y, z);
        this.group.position.set(x, y, z);
      }

      moveTo(x, y, z) {
        this.targetPosition.set(x, y, z);
      }

      returnToBase() {
        this.targetPosition.copy(this.basePosition);
      }

      faceTowards(x, z) {
        const dx = x - this.group.position.x;
        const dz = z - this.group.position.z;
        const len = Math.hypot(dx, dz);
        if (len < 1e-5) return;
        const dirX = dx / len;
        const dirZ = dz / len;
        this.targetRotationY = Math.atan2(dirX, dirZ) + Math.PI + this.facingOffset;
      }

      update(delta) {
        if (this.mixer) {
          this.mixer.update(delta);
        }
        this.bobPhase += this.bobSpeed * delta;
        const bobOffset = Math.sin(this.bobPhase) * this.bobAmount;

        const pos = this.group.position;
        const dx = this.targetPosition.x - pos.x;
        const dz = this.targetPosition.z - pos.z;
        const moveSpeed = 2.0 * delta;
        if (Math.abs(dx) > 0.01 || Math.abs(dz) > 0.01) {
          pos.x += dx * moveSpeed;
          pos.z += dz * moveSpeed;
        }
        pos.y = this.targetPosition.y + bobOffset;

        let diff = this.targetRotationY - this.rotationY;
        diff = (diff + Math.PI) % (Math.PI * 2);
        if (diff < 0) diff += Math.PI * 2;
        diff -= Math.PI;
        const maxStep = this.turnSpeed * delta;
        const step = Math.abs(diff) <= maxStep ? diff : Math.sign(diff) * maxStep;
        this.rotationY += step;
        this.group.rotation.y = this.rotationY;
      }
    }

    class ParliamentScene {
      constructor(container) {
        this.container = container;
        this.scene = new THREE.Scene();
        this.camera = null;
        this.renderer = null;
        this.controls = null;
        this.raycaster = new THREE.Raycaster();
        this.mouse = new THREE.Vector2();
        this.clock = new THREE.Clock();
        this.reps = new Map();
        this.pickables = [];
        this.repsGroup = new THREE.Group();
        this.loader = new GLTFLoader();
        this.currentSpeakerId = '';
        this.onRepHover = null;
        this.onRepLeave = null;
        this.onTick = null;
        this.hoveredId = '';
        this.frustumSize = 24;
        this.rowSpacing = 4.2;
        this.seatSpacing = 4.2;
        this.groundY = -7;
        this.zClamp = 0;
        this.ambientEnabled = true;
        this._ambientTime = 0;
      }

      async init() {
        this.scene.background = null;
        this.scene.add(this.repsGroup);
        this._setupCamera();
        this._setupRenderer();
        this._setupLights();
        this._setupFloor();
        this._setupControls();
        this._bindEvents();
        this._animate();
      }

      _setupCamera() {
        const rect = this.container.getBoundingClientRect();
        const aspect = rect.width / rect.height;
        this.camera = new THREE.OrthographicCamera(
          this.frustumSize * aspect / -2,
          this.frustumSize * aspect / 2,
          this.frustumSize / 2,
          this.frustumSize / -2,
          0.1,
          1000
        );
        this.camera.position.set(18, 18, 18);
        this.camera.lookAt(0, -1.2, 0);
        this.camera.updateProjectionMatrix();
      }

      _setupRenderer() {
        this.renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
        this.renderer.setPixelRatio(window.devicePixelRatio);
        this.renderer.setSize(this.container.clientWidth, this.container.clientHeight);
        this.renderer.setClearColor(0x000000, 0);
        this.renderer.shadowMap.enabled = true;
        this.renderer.shadowMap.type = THREE.PCFSoftShadowMap;
        this.container.appendChild(this.renderer.domElement);
      }

      _setupControls() {
        this.controls = new OrbitControls(this.camera, this.renderer.domElement);
        this.controls.enableRotate = false;
        this.controls.enablePan = true;
        this.controls.enableZoom = true;
        this.controls.minZoom = 0.6;
        this.controls.maxZoom = 2.5;
        this.controls.target.set(0, -1.2, 0);
      }

      _setupLights() {
        const ambient = new THREE.AmbientLight(0xffffff, 0.7);
        this.scene.add(ambient);
        const key = new THREE.DirectionalLight(0xffffff, 0.8);
        key.position.set(6, 10, 6);
        key.castShadow = true;
        key.shadow.mapSize.width = 2048;
        key.shadow.mapSize.height = 2048;
        key.shadow.camera.near = 0.5;
        key.shadow.camera.far = 50;
        key.shadow.camera.left = -15;
        key.shadow.camera.right = 15;
        key.shadow.camera.top = 15;
        key.shadow.camera.bottom = -15;
        this.scene.add(key);
      }

      _setupFloor() {
        const size = 12;
        const geometry = new THREE.PlaneGeometry(size, size);
        const material = new THREE.MeshStandardMaterial({
          color: 0xd8dde6,
          roughness: 0.95,
          transparent: true,
          opacity: 0,
          depthWrite: false
        });
        const floor = new THREE.Mesh(geometry, material);
        floor.rotation.x = -Math.PI / 2;
        floor.position.y = this.groundY;
        floor.receiveShadow = true;
        this.scene.add(floor);

        const grid = new THREE.GridHelper(size, 8, 0x9aa6b2, 0xc7d0db);
        grid.position.y = this.groundY + 0.02;
        grid.material.transparent = true;
        grid.material.opacity = 0.45;
        grid.material.depthWrite = false;
        grid.material.depthTest = false;
        grid.renderOrder = 5;
        grid.scale.x = 1.0;
        grid.scale.z = 1.0;
        this.scene.add(grid);
      }

      _bindEvents() {
        this._onPointerMove = this._onPointerMove.bind(this);
        this._onPointerLeave = this._onPointerLeave.bind(this);
        this._onResize = this._onResize.bind(this);
        this.container.addEventListener('mousemove', this._onPointerMove);
        this.container.addEventListener('mouseleave', this._onPointerLeave);
        window.addEventListener('resize', this._onResize);
      }

      async loadRepresentatives(reps, glbUrls) {
        this.reps.clear();
        this.pickables = [];
        while (this.repsGroup.children.length) {
          this.repsGroup.remove(this.repsGroup.children[0]);
        }
        this._ambientTime = 0;
        const sorted = [...reps].sort((a, b) => a.name.localeCompare(b.name));
        await this._arrangeRandom(sorted, glbUrls);
      }

      async _arrangeRandom(reps, glbUrls) {
        const total = reps.length;
        if (!total) return;
        const areaX = Math.max(8, this.seatSpacing * 4.0);
        const areaZ = Math.max(8, this.rowSpacing * 3.0);
        const positions = [];
        const minDist = Math.min(this.seatSpacing, this.rowSpacing) * 0.6;
        this.zClamp = areaZ * 0.45;
        for (let i = 0; i < total; i++) {
          let x = 0;
          let z = 0;
          let attempts = 0;
          do {
            x = (Math.random() - 0.5) * areaX;
            z = (Math.random() - 0.5) * areaZ;
            if (z < -this.zClamp) z = -this.zClamp;
            if (z > this.zClamp) z = this.zClamp;
            attempts++;
          } while (attempts < 40 && positions.some(p => Math.hypot(p.x - x, p.z - z) < minDist));
          positions.push({ x, z });
        }

        for (let i = 0; i < total; i++) {
          const repData = reps[i];
          const pos = positions[i];
          const url = glbUrls.length ? glbUrls[i % glbUrls.length] : '';
          const actor = new RepActor(repData, url, this.loader);
          await actor.load();
          actor.setPosition(pos.x, this.groundY, pos.z);
          actor.group.userData.repId = repData.id;
          actor.group.traverse((child) => {
            if (child.isMesh) child.userData.repId = repData.id;
          });
          this.repsGroup.add(actor.group);
          this.reps.set(repData.id, actor);
          actor._ambient = {
            nextWanderAt: this._ambientTime + 1 + Math.random() * 4
          };
          this._collectPickables(actor.group);
        }
      }

      _collectPickables(group) {
        group.traverse((child) => {
          if (child.isMesh) this.pickables.push(child);
        });
      }

      setCurrentSpeaker(repId) {
        if (this.currentSpeakerId && this.reps.has(this.currentSpeakerId)) {
          this.reps.get(this.currentSpeakerId).setSpeaking(false);
        }
        this.currentSpeakerId = repId || '';
        if (this.currentSpeakerId && this.reps.has(this.currentSpeakerId)) {
          this.reps.get(this.currentSpeakerId).setSpeaking(true);
        }
      }

      getRepScreenPosition(repId) {
        const actor = this.reps.get(repId);
        if (!actor) return null;
        const pos = actor.group.position.clone();
        pos.y -= 0.9;
        return this._project(pos);
      }

      _project(position) {
        const vector = position.clone();
        vector.project(this.camera);
        const rect = this.container.getBoundingClientRect();
        return {
          x: (vector.x * 0.5 + 0.5) * rect.width,
          y: (-vector.y * 0.5 + 0.5) * rect.height
        };
      }

      _onPointerMove(event) {
        const rect = this.container.getBoundingClientRect();
        const x = event.clientX - rect.left;
        const y = event.clientY - rect.top;
        this.mouse.x = (x / rect.width) * 2 - 1;
        this.mouse.y = -(y / rect.height) * 2 + 1;
        this.raycaster.setFromCamera(this.mouse, this.camera);
        const hits = this.raycaster.intersectObjects(this.pickables, true);
        if (!hits.length) {
          if (this.hoveredId && this.onRepLeave) this.onRepLeave();
          this.hoveredId = '';
          return;
        }
        const repId = hits[0].object.userData.repId;
        if (!repId || !this.reps.has(repId)) return;
        const actor = this.reps.get(repId);
        if (this.onRepHover) {
          this.onRepHover(actor.data, { x, y });
        }
        this.hoveredId = repId;
      }

      _onPointerLeave() {
        if (this.hoveredId && this.onRepLeave) this.onRepLeave();
        this.hoveredId = '';
      }

      _onResize() {
        if (!this.camera || !this.renderer) return;
        const rect = this.container.getBoundingClientRect();
        const aspect = rect.width / rect.height;
        this.camera.left = this.frustumSize * aspect / -2;
        this.camera.right = this.frustumSize * aspect / 2;
        this.camera.top = this.frustumSize / 2;
        this.camera.bottom = this.frustumSize / -2;
        this.camera.updateProjectionMatrix();
        this.renderer.setSize(rect.width, rect.height);
      }

      _animate() {
        requestAnimationFrame(() => this._animate());
        const delta = this.clock.getDelta();
        this._ambientTime += delta;
        if (this.ambientEnabled) {
          this._updateAmbientWander();
        }
        for (const actor of this.reps.values()) {
          actor.update(delta);
        }
        if (this.controls) this.controls.update();
        if (this.onTick) this.onTick();
        this.renderer.render(this.scene, this.camera);
      }

      _updateAmbientWander() {
        for (const actor of this.reps.values()) {
          if (!actor?._ambient) continue;
          if (actor.isSpeaking || actor.data.id === this.currentSpeakerId) continue;
          if (this._ambientTime < actor._ambient.nextWanderAt) continue;

          const r = 0.9;
          const angle = Math.random() * Math.PI * 2;
          const radius = Math.random() * r;
          const x = actor.basePosition.x + Math.cos(angle) * radius;
          let z = actor.basePosition.z + Math.sin(angle) * radius;
          const zClamp = typeof this.zClamp === 'number' ? this.zClamp : 0;
          if (zClamp > 0) {
            if (z > zClamp) z = zClamp;
            if (z < -zClamp) z = -zClamp;
          }
          actor.moveTo(x, actor.basePosition.y, z);
          actor.faceTowards(x, z);
          actor._ambient.nextWanderAt = this._ambientTime + 1.8 + Math.random() * 3.2;
        }
      }
    }

    async function initScene() {
      if (scene3d || !sceneContainer) return;
      scene3d = new ParliamentScene(sceneContainer);
      await scene3d.init();
      scene3d.onRepHover = (rep) => showTooltip(rep);
      scene3d.onRepLeave = () => hideTooltip();
      scene3d.onTick = () => syncAmbientBubbles();
    }

    async function fetchReps() {
      try {
        const res = await fetch('/reps', { cache: 'no-store' });
        if (!res.ok) throw new Error('reps fetch failed');
        const reps = await res.json();
        await initScene();
        if (glbFiles.length === 0) {
          glbFiles = await fetchGlbFiles();
        }
        if (!repsLoaded || (scene3d && scene3d.reps.size !== reps.length)) {
          await scene3d.loadRepresentatives(reps, glbFiles);
          repsLoaded = true;
        }
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

    function showTooltip(rep) {
      if (!repTooltip) return;
      repTooltip.innerHTML = `<strong>${rep.name}</strong><br/>Party: ${rep.party}<br/>Agency: ${rep.agency || 'Unassigned'}`;
      repTooltip.classList.remove('hidden');
    }

    function hideTooltip() {
      if (!repTooltip) return;
      repTooltip.classList.add('hidden');
    }

    function spawnAmbientBubble() {
      if (!scene3d || !ambientBubbles) return;
      const reps = Array.from(scene3d.reps.keys());
      if (!reps.length) return;
      const repId = reps[Math.floor(Math.random() * reps.length)];
      const text = ambientLines[Math.floor(Math.random() * ambientLines.length)];
      const el = document.createElement('div');
      el.className = 'rep-bubble';
      el.textContent = text;
      ambientBubbles.appendChild(el);
      ambientBubbleState.push({ repId, el, expiresAt: performance.now() + 2800 });
    }

    function syncAmbientBubbles() {
      if (!scene3d) return;
      const now = performance.now();
      for (let i = ambientBubbleState.length - 1; i >= 0; i--) {
        const bubble = ambientBubbleState[i];
        if (now > bubble.expiresAt) {
          bubble.el.remove();
          ambientBubbleState.splice(i, 1);
          continue;
        }
        const pos = scene3d.getRepScreenPosition(bubble.repId);
        if (!pos) continue;
        bubble.el.style.left = `${pos.x}px`;
        bubble.el.style.top = `${pos.y}px`;
      }
    }

    function updateSpeaker(data) {
      const speakerId = data.currentSpeakerId || '';
      const speakerName = data.currentSpeakerName || 'Waiting for speaker...';
      const speakerText = data.currentSpeakerText || '';
      currentSpeaker.textContent = speakerId ? `${speakerName} speaking` : 'Waiting for speaker...';
      if (currentSpeech) {
        const hasSpeech = !!(speakerId && speakerText);
        currentSpeech.classList.toggle('hidden', !hasSpeech);
        if (!hasSpeech) {
          stopSpeechTyping();
        } else if (speakerText !== lastSpeechText) {
          startSpeechTyping(speakerText);
          lastSpeechText = speakerText;
        }
      }
      currentSpeakerId = speakerId;
      if (scene3d) {
        scene3d.setCurrentSpeaker(speakerId);
      }
    }

    function stopSpeechTyping() {
      speechToken += 1;
      if (speechTimer) {
        clearTimeout(speechTimer);
        speechTimer = null;
      }
      if (currentSpeech) {
        currentSpeech.textContent = '';
      }
      lastSpeechText = '';
    }

    function startSpeechTyping(text) {
      speechToken += 1;
      const token = speechToken;
      if (!currentSpeech) return;
      if (speechTimer) {
        clearTimeout(speechTimer);
        speechTimer = null;
      }
      currentSpeech.textContent = '';
      const words = text.trim().split(/\s+/).filter(Boolean);
      if (!words.length) return;

      const targetDuration = Math.min(8000, Math.max(2000, words.length * 140));
      const baseInterval = Math.max(40, Math.min(220, targetDuration / words.length));
      let index = 0;

      const step = () => {
        if (token !== speechToken) return;
        const word = words[index];
        currentSpeech.textContent += (index === 0 ? '' : ' ') + word;
        index += 1;
        if (index >= words.length) {
          speechTimer = null;
          return;
        }
        let delay = baseInterval;
        if (/[,.!?;:]$/.test(word)) {
          delay += 140;
        }
        speechTimer = setTimeout(step, delay);
      };
      step();
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

    function renderStageFlow() {
      stageFlow.textContent = '';
      STAGE_NAMES.forEach((name, idx) => {
        const node = document.createElement('span');
        node.className = 'stage-node';
        node.dataset.stage = normalizeStage(name);
        node.textContent = name;
        stageFlow.appendChild(node);
        if (idx < STAGE_NAMES.length - 1) {
          const arrow = document.createElement('span');
          arrow.className = 'stage-arrow';
          arrow.textContent = '->';
          stageFlow.appendChild(arrow);
        }
      });
    }

    function normalizeStage(stageText) {
      if (!stageText) return '';
      return stageText.replace(/\\s*Stage$/i, '').trim().toLowerCase();
    }

    function stageDisplayToNode(stageText) {
      if (!stageText) return '';
      const base = stageText.replace(/\\s*Stage$/i, '').trim();
      return base.replace(/\\s+/g, '');
    }

    function showStageBanner(stageText) {
      if (!stageBanner || !stageText) return;
      stageBanner.textContent = stageText;
    }

    function updateStageFlow(stageText, isRunning) {
      const normalized = normalizeStage(stageText);
      stageFlow.querySelectorAll('.stage-node').forEach(node => {
        const isActive = node.dataset.stage === normalized;
        node.classList.toggle('active', isActive);
        node.classList.toggle('running', isActive && isRunning);
      });
    }

    function updateOutcomeFromLine(line) {
      if (!line) return;
      const finalMatch = line.match(/Final outcome after popular vote:\s*([A-Z_]+)/);
      if (finalMatch) {
        currentOutcome = finalMatch[1];
        if (currentOutcome !== 'PASS' && currentOutcome !== 'KILLED') {
          lastOutcomeCutscene = '';
        }
        return;
      }
      const match = line.match(/Outcome:\s*([A-Z_]+)/);
      if (match) {
        currentOutcome = match[1];
        if (currentOutcome !== 'PASS' && currentOutcome !== 'KILLED') {
          lastOutcomeCutscene = '';
        }
      }
    }

    function handleNodeDone(nodeName) {
      const outcomeClip = outcomeClipForNode(nodeName);
      if (outcomeClip) {
        queueCutscene(outcomeClip);
        return;
      }
      const mapped = NODE_CUTSCENES[nodeName];
      if (mapped) {
        queueCutscene(mapped);
        return;
      }
      if (!cutscenePlaying && cutsceneQueue.length === 0) {
        queueCutscene('/assets/videos/Talking.mov');
      }
    }

    function outcomeClipForNode(nodeName) {
      if (nodeName !== 'ThresholdDecision' && nodeName !== 'Finalize') {
        return null;
      }
      if (currentOutcome === 'PASS' && lastOutcomeCutscene !== 'PASS') {
        lastOutcomeCutscene = 'PASS';
        return '/assets/videos/Pass_Bill.mov';
      }
      if (currentOutcome === 'KILLED' && lastOutcomeCutscene !== 'KILLED') {
        lastOutcomeCutscene = 'KILLED';
        return '/assets/videos/Kill_Bill.mov';
      }
      return null;
    }

    function queueCutscene(src) {
      if (!src) return;
      cutsceneQueue.push(src);
      if (!cutscenePlaying) {
        playNextCutscene();
      }
    }

    function playNextCutscene() {
      if (!cutsceneQueue.length) return;
      const next = cutsceneQueue.shift();
      playCutscene(next);
    }

    function playCutscene(src) {
      cutscenePlaying = true;
      scene.classList.add('tv-dim');
      cutscene.classList.remove('off');
      cutscene.classList.add('active');
      cutsceneVideo.pause();
      cutsceneVideo.currentTime = 0;
      cutsceneVideo.muted = true;
      cutsceneVideo.src = src;
      let finished = false;
      let fallback = null;
      const finish = () => {
        if (finished) return;
        finished = true;
        cutscene.classList.add('off');
        cutsceneVideo.pause();
        if (fallback) clearTimeout(fallback);
        setTimeout(() => {
          cutscene.classList.remove('active');
          cutscene.classList.remove('off');
          scene.classList.remove('tv-dim');
          cutsceneVideo.removeAttribute('src');
          cutsceneVideo.load();
          cutscenePlaying = false;
          playNextCutscene();
        }, 320);
      };
      cutsceneVideo.onended = finish;
      cutsceneVideo.onerror = finish;
      cutsceneVideo.onloadedmetadata = () => {
        if (fallback) clearTimeout(fallback);
        const duration = cutsceneVideo.duration;
        const ms = Number.isFinite(duration)
            ? Math.min(60000, Math.max(3000, duration * 1000 + 500))
            : 15000;
        fallback = setTimeout(finish, ms);
      };
      fallback = setTimeout(finish, 15000);
      cutsceneVideo.play();
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
    setInterval(() => {
      if (scene3d && Math.random() < 0.55) {
        spawnAmbientBubble();
      }
    }, 2200);
    // Fetch once; avoid resetting positions during the run.
    fetchLogs();
    fetchStatus();
    fetchChat();
    fetchBill();
    renderStageFlow();
    fetchReps();
  </script>
</body>
</html>
""";
  }
}
