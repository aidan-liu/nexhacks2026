package govsim;

import govsim.config.AgentFactory;
import govsim.config.AgentRegistry;
import govsim.config.BillLoader;
import govsim.config.FactsLoader;
import govsim.config.SimulationConfig;
import govsim.core.GraphRunner;
import govsim.core.SimulationLogger;
import govsim.core.SimulationState;
import govsim.llm.Bear1Client;
import govsim.llm.CompressingPromptBuilder;
import govsim.llm.CompressionMetrics;
import govsim.llm.LLMClient;
import govsim.llm.OpenRouterClient;
import govsim.llm.PromptBuilder;
import govsim.nodes.CommitteeDeliberationNode;
import govsim.nodes.FinalizeNode;
import govsim.nodes.JudgeAssignAgencyNode;
import govsim.nodes.ParseBillNode;
import govsim.nodes.PreFloorLobbyingNode;
import govsim.nodes.PrimaryFloorDebateNode;
import govsim.nodes.PublicForumNode;
import govsim.nodes.ReviseFailedBillNode;
import govsim.nodes.SessionMingleNode;
import govsim.nodes.ThresholdDecisionNode;
import govsim.web.AgentStateStore;
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

    LLMClient llm = new OpenRouterClient(
        config.openRouterApiKey(),
        config.openRouterBaseUrl(),
        config.model(),
        config.numPredict(),
        config.openRouterHttpReferer(),
        config.openRouterXTitle()
    );

    // Initialize Bear-1 compression if enabled
    Bear1Client bear1Client = null;
    CompressionMetrics compressionMetrics = new CompressionMetrics();
    PromptBuilder promptBuilder;

    if (config.compressionEnabled() && !config.bear1ApiKey().isBlank()) {
      bear1Client = new Bear1Client(config.bear1SidecarUrl(), config.bear1ApiKey());
      SimulationLogger.log("[Compression] Bear-1 compression enabled at " + config.bear1SidecarUrl());
      if (bear1Client.isHealthy()) {
        SimulationLogger.log("[Compression] Bear-1 sidecar is healthy");
      } else {
        SimulationLogger.log("[Compression] WARNING: Bear-1 sidecar is not responding - compression will fail silently");
      }
      promptBuilder = new CompressingPromptBuilder(
          bear1Client,
          compressionMetrics,
          config.compressionEnabled()
      );
      SimulationLogger.log("[Compression] CompressingPromptBuilder initialized");
    } else {
      promptBuilder = new PromptBuilder();
      if (config.compressionEnabled()) {
        SimulationLogger.log("[Compression] Compression enabled but no API key provided - using uncompressed prompts");
      }
    }

    AgentRegistry registry = AgentFactory.buildAllAgents(config, llm, promptBuilder);
    AgentStateStore agentStateStore = new AgentStateStore(registry.socialGraph());

    PollingServer pollingServer = new PollingServer(config.serverPort(), logStore, voteBox, chatStore, agentStateStore, compressionMetrics);
    pollingServer.start();
    SimulationLogger.log("[Server] Live feed at http://localhost:" + pollingServer.port());

    SimulationState state = new SimulationState();
    state.bill = BillLoader.fromFile(config.billPath());
    state.logStore = logStore;
    state.voteBox = voteBox;
    state.pollingServer = pollingServer;
    state.chatStore = chatStore;
    FactsLoader factsLoader = new FactsLoader();
    state.vars.put("factsPack", FactsLoader.toPromptBlock(factsLoader.load(config.factsPath())));
    state.vars.put("agentStateStore", agentStateStore);

    GraphRunner runner = new GraphRunner(List.of(
        new SessionMingleNode(registry),
        new ParseBillNode(llm),
        new JudgeAssignAgencyNode(registry),
        new CommitteeDeliberationNode(registry),
        new PreFloorLobbyingNode(registry),
        new PrimaryFloorDebateNode(registry),
        new PublicForumNode(llm, bear1Client, chatStore, compressionMetrics),
        new ThresholdDecisionNode(),
        new ReviseFailedBillNode(registry, llm),
        new FinalizeNode()
    ), config.maxRevisions());

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
