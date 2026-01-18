package govsim.agents;

import java.util.List;
import java.util.Map;

public class PoliticianProfile {
  public final String party;
  public final Map<String, Double> ideology; // e.g. {"econ":0.8,"social":-0.2}
  public final List<String> redLines;
  public final List<String> petIssues;
  public final String speakingStyle;

  public PoliticianProfile(String party,
                           Map<String, Double> ideology,
                           List<String> redLines,
                           List<String> petIssues,
                           String speakingStyle) {
    this.party = party;
    this.ideology = ideology;
    this.redLines = redLines;
    this.petIssues = petIssues;
    this.speakingStyle = speakingStyle;
  }
}
