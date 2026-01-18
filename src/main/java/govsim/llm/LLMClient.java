package govsim.llm;

public interface LLMClient {
  String generateJson(String prompt) throws Exception;

  default String generateJson(String prompt, LLMRequestOptions options) throws Exception {
    return generateJson(prompt);
  }
}
