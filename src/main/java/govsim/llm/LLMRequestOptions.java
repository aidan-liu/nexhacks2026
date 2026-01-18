package govsim.llm;

public class LLMRequestOptions {
  private final Integer numPredict;

  public LLMRequestOptions(Integer numPredict) {
    this.numPredict = numPredict;
  }

  public Integer numPredict() {
    return numPredict;
  }

  public static LLMRequestOptions withNumPredict(int numPredict) {
    return new LLMRequestOptions(numPredict);
  }
}
