package govsim.agents;

import govsim.domain.Bill;

import java.util.Map;

public class AgentContext {
  public final Bill bill;
  public final String billOnePager;
  public final String floorSummary;
  public final Map<String, String> directMessages; // from other reps
  public final Map<String, Object> runtime; // anything else

  public AgentContext(Bill bill, String billOnePager, String floorSummary,
                      Map<String, String> directMessages, Map<String, Object> runtime) {
    this.bill = bill;
    this.billOnePager = billOnePager;
    this.floorSummary = floorSummary;
    this.directMessages = directMessages;
    this.runtime = runtime;
  }
}
