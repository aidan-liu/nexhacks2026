package govsim.domain;

import java.util.List;
import java.util.Set;

public class Agency {
  private final String id;
  private final String name;
  private final Set<String> scopeKeywords;
  private final List<String> representativeIds; // 3 reps

  public Agency(String id, String name, Set<String> scopeKeywords, List<String> representativeIds) {
    this.id = id;
    this.name = name;
    this.scopeKeywords = scopeKeywords;
    this.representativeIds = representativeIds;
  }

  public String id() { return id; }
  public String name() { return name; }
  public Set<String> scopeKeywords() { return scopeKeywords; }
  public List<String> representativeIds() { return representativeIds; }
}
