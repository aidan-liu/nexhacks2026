package govsim;

import govsim.config.AgentFactory;
import govsim.config.AgentRegistry;
import govsim.config.BillLoader;
import govsim.config.FactsLoader;
import govsim.config.SimulationConfig;
import govsim.core.GraphRunner;
import govsim.core.SimulationLogger;
import govsim.core.SimulationState;
import govsim.llm.LLMClient;
import govsim.llm.OllamaClient;
import govsim.nodes.CommitteeDeliberationNode;
import govsim.nodes.FinalizeNode;
import govsim.nodes.InvoiceApprovalNode;
import govsim.nodes.JudgeAssignAgencyNode;
import govsim.nodes.ParseBillNode;
import govsim.nodes.PrimaryFloorDebateNode;
import govsim.nodes.PublicForumNode;
import govsim.nodes.PullBillNode;
import govsim.nodes.ReviseFailedBillNode;
import govsim.nodes.ThresholdDecisionNode;
import govsim.web.ChatStore;
import govsim.web.BillStore;
import govsim.web.LogStore;
import govsim.web.LogTeeOutputStream;
import govsim.web.PollingServer;
import govsim.web.RepsStore;
import govsim.web.StatusStore;
import govsim.web.VoteBox;

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
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

    LLMClient llm = new OllamaClient(config.ollamaUrl(), config.model(), config.numPredict());
    AgentRegistry registry = AgentFactory.buildAllAgents(config, llm);

    VoteBox voteBox = new VoteBox();
    ChatStore chatStore = new ChatStore();
    BillStore billStore = new BillStore();
    RepsStore repsStore = new RepsStore();
    StatusStore statusStore = new StatusStore();
    repsStore.setReps(buildRepInfos(registry));
    PollingServer pollingServer = new PollingServer(config.serverPort(), logStore, voteBox, chatStore, billStore, repsStore, statusStore);
    pollingServer.start();
    SimulationLogger.log("[Server] Live feed at http://localhost:" + pollingServer.port());

    SimulationState state = new SimulationState();
    state.bill = BillLoader.fromFile(config.billPath());
    state.logStore = logStore;
    state.voteBox = voteBox;
    state.pollingServer = pollingServer;
    state.chatStore = chatStore;
    state.vars.put("billStore", billStore);
    state.vars.put("repsStore", repsStore);
    state.vars.put("statusStore", statusStore);
    state.vars.put("billPath", config.billPath());
    state.vars.put("invoicesPath", config.invoicesPath());
    billStore.setOriginalText(state.bill.rawText());
    FactsLoader factsLoader = new FactsLoader();
    state.vars.put("factsPack", FactsLoader.toPromptBlock(factsLoader.load(config.factsPath())));

    GraphRunner runner = new GraphRunner(List.of(
        new PullBillNode(),
        new ParseBillNode(llm),
        new JudgeAssignAgencyNode(registry),
        new CommitteeDeliberationNode(registry),
        new PrimaryFloorDebateNode(registry),
        new PublicForumNode(),
        new ThresholdDecisionNode(),
        new InvoiceApprovalNode(llm, config.invoicesPath()),
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

  private static List<RepsStore.RepInfo> buildRepInfos(AgentRegistry registry) {
    List<RepsStore.RepInfo> infos = new ArrayList<>();
    for (var rep : registry.allReps()) {
      String party = rep.profile() == null ? "Unknown" : rep.profile().party;
      String agencyName = "";
      for (var agency : registry.agencies()) {
        if (agency.representativeIds().contains(rep.id())) {
          agencyName = agency.name();
          break;
        }
      }
      infos.add(new RepsStore.RepInfo(rep.id(), rep.name(), party, agencyName));
    }
    return infos;
  }
}
