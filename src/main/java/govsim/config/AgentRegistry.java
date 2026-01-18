package govsim.config;

import govsim.agents.JudgeAgent;
import govsim.agents.PoliticianAgent;
import govsim.domain.Agency;
import govsim.memory.SocialGraph;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class AgentRegistry {
  private final Map<String, Agency> agencies;
  private final Map<String, PoliticianAgent> reps;
  private final JudgeAgent judge;
  private final SocialGraph socialGraph;
  private final Map<String, String> committeeIdByRepId;
  private final Map<String, String> repIdByNormalizedName;

  public AgentRegistry(Map<String, Agency> agencies,
                       Map<String, PoliticianAgent> reps,
                       JudgeAgent judge,
                       SocialGraph socialGraph,
                       Map<String, String> committeeIdByRepId) {
    this.agencies = agencies;
    this.reps = reps;
    this.judge = judge;
    this.socialGraph = socialGraph;
    this.committeeIdByRepId = committeeIdByRepId;
    this.repIdByNormalizedName = buildNameIndex(reps);
  }

  public Collection<Agency> agencies() { return agencies.values(); }
  public Agency agencyById(String id) { return agencies.get(id); }
  public Collection<PoliticianAgent> allReps() { return reps.values(); }
  public PoliticianAgent repById(String id) { return reps.get(id); }
  public JudgeAgent judge() { return judge; }
  public SocialGraph socialGraph() { return socialGraph; }

  public Agency committeeForRep(String repId) {
    String committeeId = committeeIdByRepId.get(repId);
    if (committeeId == null) return null;
    return agencies.get(committeeId);
  }

  public PoliticianAgent repByNameApprox(String name) {
    if (name == null) return null;
    String normalized = normalizeName(name);
    String exact = repIdByNormalizedName.get(normalized);
    if (exact != null) return reps.get(exact);

    // Try last-name match.
    String[] parts = normalized.split("\\s+");
    String last = parts.length == 0 ? normalized : parts[parts.length - 1];
    if (!last.isBlank()) {
      for (var entry : repIdByNormalizedName.entrySet()) {
        String key = entry.getKey();
        if (key.endsWith(" " + last) || key.equals(last)) {
          return reps.get(entry.getValue());
        }
      }
    }
    return null;
  }

  private static Map<String, String> buildNameIndex(Map<String, PoliticianAgent> reps) {
    Map<String, String> index = new HashMap<>();
    for (var entry : reps.entrySet()) {
      PoliticianAgent rep = entry.getValue();
      if (rep == null) continue;
      index.put(normalizeName(rep.name()), entry.getKey());
    }
    return index;
  }

  private static String normalizeName(String name) {
    return name == null ? "" : name.toLowerCase().trim().replaceAll("\\s+", " ");
  }
}
