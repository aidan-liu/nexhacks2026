package govsim.domain;

import java.util.Map;

public class VoteResult {
  private final Map<String, Vote> votesByRepId;

  public VoteResult(Map<String, Vote> votesByRepId) { this.votesByRepId = votesByRepId; }
  public Map<String, Vote> votesByRepId() { return votesByRepId; }

  public long yesCount() { return votesByRepId.values().stream().filter(v -> v == Vote.YES).count(); }
  public long noCount()  { return votesByRepId.values().stream().filter(v -> v == Vote.NO).count(); }
  public long abstainCount() { return votesByRepId.values().stream().filter(v -> v == Vote.ABSTAIN).count(); }

  @Override
  public String toString() {
    return "VoteResult{yes=" + yesCount() + ", no=" + noCount() + ", abstain=" + abstainCount() + "}";
  }
}
