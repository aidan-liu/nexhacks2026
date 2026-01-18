package govsim.config;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class FactsLoader {
  private final ObjectMapper mapper = new ObjectMapper();

  public List<Fact> load(String path) throws IOException {
    Path factsPath = Path.of(path);
    if (!Files.exists(factsPath)) {
      return Collections.emptyList();
    }
    String raw = Files.readString(factsPath).trim();
    if (raw.isEmpty()) {
      return Collections.emptyList();
    }
    Fact[] facts = mapper.readValue(raw, Fact[].class);
    List<Fact> list = new ArrayList<>();
    if (facts != null) {
      Collections.addAll(list, facts);
    }
    return list;
  }

  public static String toPromptBlock(List<Fact> facts) {
    if (facts == null || facts.isEmpty()) {
      return "(no facts provided)";
    }
    StringBuilder sb = new StringBuilder();
    for (Fact fact : facts) {
      if (fact == null) continue;
      String id = safe(fact.id);
      String source = safe(fact.source);
      String text = safe(fact.text);
      if (text.isBlank()) continue;
      sb.append("- ");
      if (!id.isBlank()) sb.append("[").append(id).append("] ");
      if (!source.isBlank()) sb.append("(").append(source).append(") ");
      sb.append(text).append("\n");
    }
    String result = sb.toString().trim();
    return result.isBlank() ? "(no facts provided)" : result;
  }

  private static String safe(String value) {
    return value == null ? "" : value.trim();
  }

  public static class Fact {
    public String id;
    public String source;
    public String text;
  }
}
