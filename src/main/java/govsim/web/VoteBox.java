package govsim.web;

public class VoteBox {
  private boolean open;
  private int yes;
  private int no;
  private final java.util.Set<String> voters = new java.util.HashSet<>();

  public synchronized void open() {
    open = true;
    yes = 0;
    no = 0;
    voters.clear();
  }

  public synchronized void close() {
    open = false;
  }

  public synchronized boolean isOpen() {
    return open;
  }

  public synchronized void voteYes() {
    if (!open) return;
    yes++;
  }

  public synchronized void voteNo() {
    if (!open) return;
    no++;
  }

  public synchronized VoteStatus recordVote(String voterId, boolean yesVote) {
    if (!open) return VoteStatus.CLOSED;
    if (voterId == null || voterId.isBlank()) return VoteStatus.INVALID;
    if (voters.contains(voterId)) return VoteStatus.DUPLICATE;
    voters.add(voterId);
    if (yesVote) {
      yes++;
    } else {
      no++;
    }
    return VoteStatus.OK;
  }

  public synchronized boolean hasVoted(String voterId) {
    if (voterId == null || voterId.isBlank()) return false;
    return voters.contains(voterId);
  }

  public synchronized VoteSnapshot snapshot() {
    return new VoteSnapshot(open, yes, no);
  }

  public static class VoteSnapshot {
    public final boolean open;
    public final int yes;
    public final int no;

    public VoteSnapshot(boolean open, int yes, int no) {
      this.open = open;
      this.yes = yes;
      this.no = no;
    }
  }

  public enum VoteStatus {
    OK,
    CLOSED,
    DUPLICATE,
    INVALID
  }
}
