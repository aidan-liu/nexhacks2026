package govsim.domain;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Bill {
  private final String id;
  private final String title;
  private final String rawText;

  // Filled by parser:
  private List<String> topics = new ArrayList<>();
  private double estimatedCost = 0.0;
  private Map<String, Object> attributes = new HashMap<>();

  public Bill(String id, String title, String rawText) {
    this.id = id;
    this.title = title;
    this.rawText = rawText;
  }

  public String id() { return id; }
  public String title() { return title; }
  public String rawText() { return rawText; }

  public List<String> topics() { return topics; }
  public void setTopics(List<String> topics) { this.topics = topics; }

  public double estimatedCost() { return estimatedCost; }
  public void setEstimatedCost(double estimatedCost) { this.estimatedCost = estimatedCost; }

  public Map<String, Object> attributes() { return attributes; }
  public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }
}
