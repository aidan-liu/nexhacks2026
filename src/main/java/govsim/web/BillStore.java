package govsim.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class BillStore {
  private String originalText = "";
  private String onePager = "";
  private String revisedText = "";
  private String revisedSummary = "";
  private List<String> revisedChanges = List.of();

  public synchronized void setOriginalText(String text) {
    if (text == null) return;
    this.originalText = text.trim();
  }

  public synchronized void setOnePager(String text) {
    if (text == null) return;
    this.onePager = text.trim();
  }

  public synchronized void setRevised(String text, String summary, List<String> changes) {
    this.revisedText = text == null ? "" : text.trim();
    this.revisedSummary = summary == null ? "" : summary.trim();
    if (changes == null) {
      this.revisedChanges = List.of();
    } else {
      this.revisedChanges = new ArrayList<>(changes);
    }
  }

  public synchronized BillSnapshot snapshot() {
    return new BillSnapshot(originalText, onePager, revisedText, revisedSummary,
        revisedChanges == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(revisedChanges)));
  }

  public static class BillSnapshot {
    public final String originalText;
    public final String onePager;
    public final String revisedText;
    public final String revisedSummary;
    public final List<String> revisedChanges;

    public BillSnapshot(String originalText, String onePager, String revisedText,
                        String revisedSummary, List<String> revisedChanges) {
      this.originalText = originalText;
      this.onePager = onePager;
      this.revisedText = revisedText;
      this.revisedSummary = revisedSummary;
      this.revisedChanges = revisedChanges;
    }
  }
}
