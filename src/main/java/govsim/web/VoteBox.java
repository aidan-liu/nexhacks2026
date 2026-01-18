package govsim.web;

public class VoteBox {
  private boolean open;
  private int yes;
  private int no;

  public synchronized void open() {
    open = true;
    yes = 0;
    no = 0;
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
}
