package govsim.core;

public final class SimulationLogger {
  private SimulationLogger() {}

  public static void init(Object store) {
    // No-op: System.out is tee'd to the log store.
  }

  public static void log(String line) {
    System.out.println(line);
  }
}
