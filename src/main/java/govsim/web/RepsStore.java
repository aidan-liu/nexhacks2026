package govsim.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class RepsStore {
  private final List<RepInfo> reps = new ArrayList<>();

  public synchronized void setReps(List<RepInfo> repList) {
    reps.clear();
    if (repList != null) {
      reps.addAll(repList);
    }
  }

  public synchronized List<RepInfo> snapshot() {
    return Collections.unmodifiableList(new ArrayList<>(reps));
  }

  public static class RepInfo {
    public final String id;
    public final String name;
    public final String party;
    public final String agency;

    public RepInfo(String id, String name, String party, String agency) {
      this.id = id;
      this.name = name;
      this.party = party;
      this.agency = agency;
    }
  }
}
