package govsim;

import govsim.config.AgentFactory;
import govsim.config.AgentRegistry;
import govsim.config.BillLoader;
import govsim.config.SimulationConfig;
import govsim.core.GraphRunner;
import govsim.core.SimulationState;
import govsim.llm.LLMClient;
import govsim.llm.OllamaClient;
import govsim.nodes.CommitteeDeliberationNode;
import govsim.nodes.FinalizeNode;
import govsim.nodes.JudgeAssignAgencyNode;
import govsim.nodes.ParseBillNode;
import govsim.nodes.PrimaryFloorDebateNode;
import govsim.nodes.PublicForumNode;
import govsim.nodes.ThresholdDecisionNode;

import java.util.List;

public class Main {
  public static void main(String[] args) throws Exception {
    SimulationConfig config = SimulationConfig.load();
    LLMClient llm = new OllamaClient(config.ollamaUrl(), config.model(), config.numPredict());
    AgentRegistry registry = AgentFactory.buildAllAgents(config, llm);

    SimulationState state = new SimulationState();
    state.bill = BillLoader.fromFile(config.billPath());

    GraphRunner runner = new GraphRunner(List.of(
        new ParseBillNode(llm),
        new JudgeAssignAgencyNode(registry),
        new CommitteeDeliberationNode(registry),
        new PrimaryFloorDebateNode(registry),
        new PublicForumNode(),
        new ThresholdDecisionNode(),
        new FinalizeNode()
    ));

    runner.run(state);
    System.out.println(state.voteResult);
    System.out.println("Outcome: " + state.vars.get("finalOutcome"));
  }
}
