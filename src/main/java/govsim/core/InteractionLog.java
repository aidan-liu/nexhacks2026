package govsim.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InteractionLog {
  private final List<String> entries = new ArrayList<>();

  public void add(String entry) {
    if (entry == null || entry.isBlank()) return;
    entries.add(entry);
  }

  public List<String> entries() {
    return Collections.unmodifiableList(entries);
  }
}
