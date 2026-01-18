package govsim.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ChatStore {
  private final List<ChatMessage> messages = new ArrayList<>();

  public synchronized void addMessage(String name, String message) {
    String cleanName = sanitize(name, 32);
    String cleanMessage = sanitize(message, 240);
    if (cleanMessage.isBlank()) return;
    if (cleanName.isBlank()) {
      cleanName = "Guest";
    }
    messages.add(new ChatMessage(cleanName, cleanMessage, System.currentTimeMillis()));
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
