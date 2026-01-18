package govsim.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import govsim.agents.JudgeAgent;
import govsim.agents.PoliticianAgent;
import govsim.agents.PoliticianProfile;
import govsim.domain.Agency;
import govsim.llm.LLMClient;
import govsim.llm.PromptBuilder;
import govsim.memory.RelationshipMemoryStore;
import govsim.memory.SimpleMemoryStore;
import govsim.memory.SocialGraph;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AgentFactory {
  public static AgentRegistry buildAllAgents(SimulationConfig config, LLMClient llm) throws IOException {
    return buildAllAgents(config, llm, new PromptBuilder());
  }

  public static AgentRegistry buildAllAgents(SimulationConfig config, LLMClient llm, PromptBuilder prompts) throws IOException {
    ObjectMapper mapper = new ObjectMapper();
    List<AgencyConfig> agencyConfigs = Arrays.asList(mapper.readValue(
        Files.readString(Path.of(config.agenciesPath())), AgencyConfig[].class));
    List<RepresentativeConfig> repConfigs = Arrays.asList(mapper.readValue(
        Files.readString(Path.of(config.repsPath())), RepresentativeConfig[].class));

    Map<String, RepresentativeConfig> repsById = new HashMap<>();
    for (RepresentativeConfig rc : repConfigs) {
      repsById.put(rc.id, rc);
    }

    Map<String, Agency> agencies = new HashMap<>();
    Map<String, PoliticianAgent> repAgents = new HashMap<>();
    Map<String, String> committeeIdByRepId = new HashMap<>();
    SocialGraph socialGraph = new SocialGraph();

    for (AgencyConfig agencyCfg : agencyConfigs) {
      if (agencyCfg.representativeIds == null || agencyCfg.representativeIds.size() != 3) {
        throw new IllegalArgumentException("Agency " + agencyCfg.id + " must have exactly 3 representatives");
      }
      Agency agency = new Agency(
          agencyCfg.id,
          agencyCfg.name,
          Set.copyOf(agencyCfg.scopeKeywords),
          List.copyOf(agencyCfg.representativeIds)
      );
      agencies.put(agency.id(), agency);

      for (String repId : agency.representativeIds()) {
        committeeIdByRepId.put(repId, agency.id());
        if (repAgents.containsKey(repId)) continue;
        RepresentativeConfig rc = repsById.get(repId);
        if (rc == null) {
          throw new IllegalArgumentException("Missing representative config for " + repId);
        }
        PoliticianProfile profile = new PoliticianProfile(
            rc.party,
            rc.ideology,
            rc.redLines,
            rc.petIssues,
            rc.speakingStyle
        );
        PoliticianAgent agent = new PoliticianAgent(
            rc.id,
            rc.name,
            profile,
            new RelationshipMemoryStore(
                new SimpleMemoryStore(),
                socialGraph,
                rc.id,
                rc.name,
                agency.id(),
                agency.name()
            ),
            llm,
            prompts
        );
        socialGraph.registerRepresentative(rc.id, rc.name, agency.id(), agency.name());
        repAgents.put(repId, agent);
      }
    }

    JudgeAgent judge = new JudgeAgent(llm, prompts);
    return new AgentRegistry(agencies, repAgents, judge, socialGraph, committeeIdByRepId);
  }

  private static class AgencyConfig {
    public String id;
    public String name;
    public List<String> scopeKeywords;
    public List<String> representativeIds;
  }

  private static class RepresentativeConfig {
    public String id;
    public String name;
    public String party;
    public Map<String, Double> ideology;
    public List<String> redLines;
    public List<String> petIssues;
    public String speakingStyle;
  }
}
