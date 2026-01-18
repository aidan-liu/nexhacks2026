package govsim.core;

public interface Node {
  String name();
  void run(SimulationState state) throws Exception;
}
