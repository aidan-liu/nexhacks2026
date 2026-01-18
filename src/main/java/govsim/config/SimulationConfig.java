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
  private final String billPath;
  private final String agenciesPath;
  private final String repsPath;

  public SimulationConfig(String ollamaUrl, String model, int numPredict, String billPath, String agenciesPath, String repsPath) {
    this.ollamaUrl = ollamaUrl;
    this.model = model;
    this.numPredict = numPredict;
    this.billPath = billPath;
    this.agenciesPath = agenciesPath;
    this.repsPath = repsPath;
  }

  public String ollamaUrl() { return ollamaUrl; }
  public String model() { return model; }
  public int numPredict() { return numPredict; }
  public String billPath() { return billPath; }
  public String agenciesPath() { return agenciesPath; }
  public String repsPath() { return repsPath; }

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
    int numPredict = getIntValue(props, "num_predict", "SIM_NUM_PREDICT", 200);
    String billPath = getValue(props, "bill.path", "SIM_BILL_PATH", "config/bill.txt");
    String agenciesPath = getValue(props, "agencies.path", "SIM_AGENCIES_PATH", "config/agencies.json");
    String repsPath = getValue(props, "reps.path", "SIM_REPS_PATH", "config/representatives.json");

    return new SimulationConfig(ollamaUrl, model, numPredict, billPath, agenciesPath, repsPath);
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
