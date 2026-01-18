package govsim.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatStore {
  private final List<ChatMessage> messages = new ArrayList<>();
  private final java.util.Map<String, String> voterNames = new java.util.HashMap<>();
  private static final List<String> ANIMALS = List.of(
      "Tiger", "Lion", "Panther", "Fox", "Wolf", "Hawk", "Otter", "Bear", "Eagle", "Cobra",
      "Falcon", "Raven", "Leopard", "Jaguar", "Puma", "Dolphin", "Orca", "Mantis", "Koala", "Moose"
  );

  public synchronized void addMessage(String voterId, String message) {
    String cleanMessage = sanitize(message, 240);
    if (cleanMessage.isBlank()) return;
    String displayName = displayNameFor(voterId);
    messages.add(new ChatMessage(displayName, cleanMessage, System.currentTimeMillis()));
  }

  public synchronized ChatSnapshot snapshotFrom(int startIndex) {
    int safeStart = Math.max(0, Math.min(startIndex, messages.size()));
    List<ChatMessage> slice = new ArrayList<>(messages.subList(safeStart, messages.size()));
    return new ChatSnapshot(Collections.unmodifiableList(slice), messages.size());
  }

  private String sanitize(String value, int maxLen) {
    if (value == null) return "";
    String trimmed = value.replace("\r", " ").replace("\n", " ").trim();
    if (trimmed.length() > maxLen) {
      return trimmed.substring(0, maxLen);
    }
    return trimmed;
  }

  private String displayNameFor(String voterId) {
    String key = voterId == null ? "" : voterId.trim();
    if (key.isBlank()) {
      return "Anonymous Guest";
    }
    String existing = voterNames.get(key);
    if (existing != null) return existing;
    int index = Math.abs(key.hashCode()) % ANIMALS.size();
    String base = "Anonymous " + ANIMALS.get(index);
    String name = base;
    int suffix = 2;
    while (voterNames.containsValue(name)) {
      name = base + " " + suffix;
      suffix++;
    }
    voterNames.put(key, name);
    return name;
  }

  public static class ChatMessage {
    public final String name;
    public final String message;
    public final long timestamp;

    public ChatMessage(String name, String message, long timestamp) {
      this.name = name;
      this.message = message;
      this.timestamp = timestamp;
    }
  }

  public static class ChatSnapshot {
    public final List<ChatMessage> messages;
    public final int nextIndex;

    public ChatSnapshot(List<ChatMessage> messages, int nextIndex) {
      this.messages = messages;
      this.nextIndex = nextIndex;
    }
  }
}
