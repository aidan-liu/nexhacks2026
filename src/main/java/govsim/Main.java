package govsim;

import govsim.config.AgentFactory;
import govsim.config.AgentRegistry;
import govsim.config.BillLoader;
import govsim.config.SimulationConfig;
import govsim.core.GraphRunner;
import govsim.core.SimulationLogger;
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
import govsim.web.ChatStore;
import govsim.web.LogStore;
import govsim.web.LogTeeOutputStream;
import govsim.web.PollingServer;
import govsim.web.VoteBox;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Main {
  public static void main(String[] args) throws Exception {
    SimulationConfig config = SimulationConfig.load();
    LogStore logStore = new LogStore();
    PrintStream originalOut = System.out;
    System.setOut(new PrintStream(new LogTeeOutputStream(originalOut, logStore), true, StandardCharsets.UTF_8));
    PrintStream originalErr = System.err;
    System.setErr(new PrintStream(new LogTeeOutputStream(originalErr, logStore), true, StandardCharsets.UTF_8));
    SimulationLogger.init(logStore);

    VoteBox voteBox = new VoteBox();
    ChatStore chatStore = new ChatStore();
    PollingServer pollingServer = new PollingServer(config.serverPort(), logStore, voteBox, chatStore);
    pollingServer.start();
    SimulationLogger.log("[Server] Live feed at http://localhost:" + pollingServer.port());

    LLMClient llm = new OllamaClient(config.ollamaUrl(), config.model(), config.numPredict());
    AgentRegistry registry = AgentFactory.buildAllAgents(config, llm);

    SimulationState state = new SimulationState();
    state.bill = BillLoader.fromFile(config.billPath());
    state.logStore = logStore;
    state.voteBox = voteBox;
    state.pollingServer = pollingServer;
    state.chatStore = chatStore;

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
    SimulationLogger.log(String.valueOf(state.voteResult));
    SimulationLogger.log("Outcome: " + state.vars.get("finalOutcome"));

    if (state.pollingServer != null) {
      SimulationLogger.log("[Server] Shutting down.");
      state.pollingServer.stop();
    }
    System.exit(0);
  }
}
