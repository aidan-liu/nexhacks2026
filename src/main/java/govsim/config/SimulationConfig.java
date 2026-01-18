package govsim.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class SimulationConfig {
  private final String ollamaUrl;
  private final String model;
  private final int numPredict;
  private final int serverPort;
  private final int maxRevisions;
  private final String factsPath;
  private final String billPath;
  private final String agenciesPath;
  private final String repsPath;
  private final String invoicesPath;

  public SimulationConfig(String ollamaUrl, String model, int numPredict, int serverPort, int maxRevisions, String factsPath,
                          String billPath, String agenciesPath, String repsPath, String invoicesPath) {
    this.ollamaUrl = ollamaUrl;
    this.model = model;
    this.numPredict = numPredict;
    this.serverPort = serverPort;
    this.maxRevisions = maxRevisions;
    this.factsPath = factsPath;
    this.billPath = billPath;
    this.agenciesPath = agenciesPath;
    this.repsPath = repsPath;
    this.invoicesPath = invoicesPath;
  }

  public String ollamaUrl() { return ollamaUrl; }
  public String model() { return model; }
  public int numPredict() { return numPredict; }
  public int serverPort() { return serverPort; }
  public int maxRevisions() { return maxRevisions; }
  public String factsPath() { return factsPath; }
  public String billPath() { return billPath; }
  public String agenciesPath() { return agenciesPath; }
  public String repsPath() { return repsPath; }
  public String invoicesPath() { return invoicesPath; }

  public static SimulationConfig load() throws IOException {
    Properties props = new Properties();
    Path path = Path.of("config.properties");
    if (Files.exists(path)) {
      try (InputStream in = Files.newInputStream(path)) {
        props.load(in);
      }
    }

    String ollamaUrl = getValue(props, "ollama.url", "SIM_OLLAMA_URL", "http://localhost:11434");
    String model = getValue(props, "model", "SIM_MODEL", "gemma2:2b");
    int numPredict = getIntValue(props, "num_predict", "SIM_NUM_PREDICT", 600);
    int serverPort = getIntValue(props, "server.port", "SIM_SERVER_PORT", 8080);
    int maxRevisions = getIntValue(props, "max_revisions", "SIM_MAX_REVISIONS", 1);
    String factsPath = getValue(props, "facts.path", "SIM_FACTS_PATH", "config/facts.json");
    String billPath = getValue(props, "bill.path", "SIM_BILL_PATH", "config/bill.txt");
    String agenciesPath = getValue(props, "agencies.path", "SIM_AGENCIES_PATH", "config/agencies.json");
    String repsPath = getValue(props, "reps.path", "SIM_REPS_PATH", "config/representatives.json");
    String invoicesPath = getValue(props, "invoices.path", "SIM_INVOICES_PATH", "config/invoices.json");

    return new SimulationConfig(ollamaUrl, model, numPredict, serverPort, maxRevisions, factsPath,
        billPath, agenciesPath, repsPath, invoicesPath);
  }

  private static String getValue(Properties props, String key, String envKey, String defaultValue) {
    String env = System.getenv(envKey);
    if (env != null && !env.isBlank()) return env;
    String value = props.getProperty(key);
    if (value != null && !value.isBlank()) return value;
    return defaultValue;
  }

  private static int getIntValue(Properties props, String key, String envKey, int defaultValue) {
    String env = System.getenv(envKey);
    if (env != null && !env.isBlank()) {
      try {
        return Integer.parseInt(env);
      } catch (NumberFormatException ignored) {
        return defaultValue;
      }
    }
    String value = props.getProperty(key);
    if (value != null && !value.isBlank()) {
      try {
        return Integer.parseInt(value);
      } catch (NumberFormatException ignored) {
        return defaultValue;
      }
    }
    return defaultValue;
  }
}
