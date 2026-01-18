package govsim.web;

public class StatusStore {
  private String currentStage = "";
  private boolean stageRunning;
  private String currentSpeakerId = "";
  private String currentSpeakerName = "";
  private String currentSpeakerText = "";
  private String finalOutcome = "";

  public synchronized void setStage(String stage, boolean running) {
    this.currentStage = stage == null ? "" : stage;
    this.stageRunning = running;
  }

  public synchronized void setSpeaker(String id, String name, String text) {
    this.currentSpeakerId = id == null ? "" : id;
    this.currentSpeakerName = name == null ? "" : name;
    this.currentSpeakerText = text == null ? "" : text;
  }

  public synchronized StatusSnapshot snapshot() {
    return new StatusSnapshot(currentStage, stageRunning, currentSpeakerId, currentSpeakerName, currentSpeakerText, finalOutcome);
  }

  public synchronized void setFinalOutcome(String outcome) {
    this.finalOutcome = outcome == null ? "" : outcome;
  }

  public static class StatusSnapshot {
    public final String currentStage;
    public final boolean stageRunning;
    public final String currentSpeakerId;
    public final String currentSpeakerName;
    public final String currentSpeakerText;
    public final String finalOutcome;

    public StatusSnapshot(String currentStage, boolean stageRunning, String currentSpeakerId, String currentSpeakerName,
                          String currentSpeakerText, String finalOutcome) {
      this.currentStage = currentStage;
      this.stageRunning = stageRunning;
      this.currentSpeakerId = currentSpeakerId;
      this.currentSpeakerName = currentSpeakerName;
      this.currentSpeakerText = currentSpeakerText;
      this.finalOutcome = finalOutcome;
    }
  }
}
