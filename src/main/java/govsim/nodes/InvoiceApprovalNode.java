package govsim.nodes;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import govsim.config.InvoiceLoader;
import govsim.core.Node;
import govsim.core.SimulationLogger;
import govsim.core.SimulationState;
import govsim.llm.LLMClient;
import govsim.llm.LLMRequestOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class InvoiceApprovalNode implements Node {
  private static final int NUM_PREDICT = 350;
  private final LLMClient llm;
  private final String invoicesPath;
  private final InvoiceLoader loader = new InvoiceLoader();
  private final ObjectMapper mapper = new ObjectMapper();

  public InvoiceApprovalNode(LLMClient llm, String invoicesPath) {
    this.llm = llm;
    this.invoicesPath = invoicesPath;
  }

  @Override
  public String name() { return "InvoiceApproval"; }

  @Override
  public void run(SimulationState state) throws Exception {
    String outcome = String.valueOf(state.vars.getOrDefault("finalOutcome", ""));
    if (!"PASS".equals(outcome)) {
      SimulationLogger.log("[Invoice] Skipping invoice approvals (outcome=" + outcome + ").");
      return;
    }

    List<InvoiceLoader.Invoice> invoices = loader.load(invoicesPath);
    if (invoices.isEmpty()) {
      SimulationLogger.log("[Invoice] No invoices found. Skipping blockchain step.");
      return;
    }

    String billText = resolveBillText(state);
    List<Map<String, Object>> decisions = new ArrayList<>();
    for (InvoiceLoader.Invoice invoice : invoices) {
      Map<String, Object> decision = evaluateInvoice(invoice, billText);
      if (decision == null) {
        continue;
      }
      decisions.add(decision);
      SimulationLogger.log("[Invoice] " + decision.get("invoiceId") + " -> " +
          (Boolean.TRUE.equals(decision.get("approved")) ? "APPROVED" : "REJECTED") +
          " (" + decision.get("rationale") + ")");
    }

    if (decisions.isEmpty()) {
      SimulationLogger.log("[Invoice] No valid invoice decisions produced.");
      return;
    }

    state.vars.put("invoiceDecisions", decisions);
    writeDecisions(state, invoices, decisions);
  }

  private String resolveBillText(SimulationState state) {
    Object revised = state.vars.get("revisedBillText");
    if (revised instanceof String revisedText && !revisedText.isBlank()) {
      return revisedText.trim();
    }
    return state.bill == null ? "" : String.valueOf(state.bill.rawText());
  }

  private Map<String, Object> evaluateInvoice(InvoiceLoader.Invoice invoice, String billText) throws Exception {
    String prompt = """
You are the Treasury Inspector. Use the approved bill to decide if this invoice should be paid.
Only approve if the invoice is clearly within scope and intent of the bill.

APPROVED BILL:
%s

INVOICE:
- id: %s
- vendor: %s
- description: %s
- amountWei: %s
- recipientAddress: %s
- proofUrl: %s

Return STRICT JSON:
{ "invoiceId": "...", "approved": true|false, "rationale": "...", "matchedClauses": ["..."] }
No extra keys. No markdown.
""".formatted(billText, safe(invoice.id()), safe(invoice.vendor()), safe(invoice.description()),
        safe(invoice.amountWei()), safe(invoice.recipientAddress()), safe(invoice.proofUrl()));

    String json = llm.generateJson(prompt, LLMRequestOptions.withNumPredict(NUM_PREDICT));
    JsonNode root;
    try {
      root = mapper.readTree(json);
    } catch (JsonProcessingException e) {
      SimulationLogger.log("[Invoice] Invalid JSON for " + invoice.id() + ". Skipping.");
      return null;
    }

    String invoiceId = root.path("invoiceId").asText(invoice.id());
    boolean approved = root.path("approved").asBoolean(false);
    String rationale = root.path("rationale").asText("").trim();
    List<String> matchedClauses = List.of();
    JsonNode clausesNode = root.get("matchedClauses");
    if (clausesNode != null && clausesNode.isArray()) {
      matchedClauses = mapper.convertValue(clausesNode, new TypeReference<>() {});
    }

    return Map.of(
        "invoiceId", invoiceId,
        "approved", approved,
        "rationale", rationale.isBlank() ? "No rationale provided." : rationale,
        "matchedClauses", matchedClauses,
        "timestamp", Instant.now().toString()
    );
  }

  private void writeDecisions(SimulationState state,
                              List<InvoiceLoader.Invoice> invoices,
                              List<Map<String, Object>> decisions) {
    try {
      Path outDir = Path.of("crypto", "out");
      Files.createDirectories(outDir);
      Path outFile = outDir.resolve("invoice-decisions.json");
      Map<String, Object> payload = Map.of(
          "billId", state.bill == null ? "" : state.bill.id(),
          "billTitle", state.bill == null ? "" : state.bill.title(),
          "decisions", decisions,
          "invoices", invoices
      );
      Files.writeString(outFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(payload));
      SimulationLogger.log("[Invoice] Wrote decisions to " + outFile + ".");
    } catch (IOException e) {
      SimulationLogger.log("[Invoice] Failed to write decision file: " + e.getMessage());
    }
  }

  private String safe(String value) {
    return value == null ? "" : value;
  }
}
