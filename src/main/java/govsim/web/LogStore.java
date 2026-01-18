package govsim.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LogStore {
  private final List<String> lines = new ArrayList<>();

  public synchronized void addLine(String line) {
    if (line == null) return;
    lines.add(line);
  }

  public synchronized LogSnapshot snapshotFrom(int startIndex) {
    int safeStart = Math.max(0, Math.min(startIndex, lines.size()));
    List<String> slice = new ArrayList<>(lines.subList(safeStart, lines.size()));
    return new LogSnapshot(Collections.unmodifiableList(slice), lines.size());
  }

  public synchronized String lastLine() {
    if (lines.isEmpty()) return "";
    return lines.get(lines.size() - 1);
  }

  public static class LogSnapshot {
    public final List<String> lines;
    public final int nextIndex;

    public LogSnapshot(List<String> lines, int nextIndex) {
      this.lines = lines;
      this.nextIndex = nextIndex;
    }
  }
}
