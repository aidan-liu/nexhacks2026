package govsim.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class SimulationConfig {
  private final String openRouterApiKey;
  private final String openRouterBaseUrl;
  private final String openRouterHttpReferer;
  private final String openRouterXTitle;
  private final String model;
  private final int numPredict;
  private final int serverPort;
  private final int maxRevisions;
  private final String factsPath;
  private final String billPath;
  private final String agenciesPath;
  private final String repsPath;

  // Bear-1 Compression Configuration
  private final String bear1SidecarUrl;
  private final String bear1ApiKey;
  private final boolean compressionEnabled;

  public SimulationConfig(String openRouterApiKey, String openRouterBaseUrl, String openRouterHttpReferer, String openRouterXTitle,
                          String model, int numPredict, int serverPort, int maxRevisions, String factsPath,
                          String billPath, String agenciesPath, String repsPath,
                          String bear1SidecarUrl, String bear1ApiKey, boolean compressionEnabled) {
    this.openRouterApiKey = openRouterApiKey;
    this.openRouterBaseUrl = openRouterBaseUrl;
    this.openRouterHttpReferer = openRouterHttpReferer;
    this.openRouterXTitle = openRouterXTitle;
    this.model = model;
    this.numPredict = numPredict;
    this.serverPort = serverPort;
    this.maxRevisions = maxRevisions;
    this.factsPath = factsPath;
    this.billPath = billPath;
    this.agenciesPath = agenciesPath;
    this.repsPath = repsPath;
    this.bear1SidecarUrl = bear1SidecarUrl;
    this.bear1ApiKey = bear1ApiKey;
    this.compressionEnabled = compressionEnabled;
  }

  public String openRouterApiKey() { return openRouterApiKey; }
  public String openRouterBaseUrl() { return openRouterBaseUrl; }
  public String openRouterHttpReferer() { return openRouterHttpReferer; }
  public String openRouterXTitle() { return openRouterXTitle; }
  public String model() { return model; }
  public int numPredict() { return numPredict; }
  public int serverPort() { return serverPort; }
  public int maxRevisions() { return maxRevisions; }
  public String factsPath() { return factsPath; }
  public String billPath() { return billPath; }
  public String agenciesPath() { return agenciesPath; }
  public String repsPath() { return repsPath; }

  // Bear-1 getters
  public String bear1SidecarUrl() { return bear1SidecarUrl; }
  public String bear1ApiKey() { return bear1ApiKey; }
  public boolean compressionEnabled() { return compressionEnabled; }

  public static SimulationConfig load() throws IOException {
    Properties props = new Properties();
    Path path = Path.of("config.properties");
    if (Files.exists(path)) {
      try (InputStream in = Files.newInputStream(path)) {
        props.load(in);
      }
    }

    String openRouterApiKey = getValue(props, "openrouter.api_key", "", null, "SIM_OPENROUTER_API_KEY", "OPENROUTER_API_KEY");
    String openRouterBaseUrl = getValue(props, "openrouter.base_url", "", "https://openrouter.ai/api/v1", "SIM_OPENROUTER_BASE_URL");
    String openRouterHttpReferer = getValue(props, "openrouter.http_referer", "", "", "SIM_OPENROUTER_HTTP_REFERER");
    String openRouterXTitle = getValue(props, "openrouter.x_title", "", "", "SIM_OPENROUTER_X_TITLE");
    String model = getValue(props, "model", "SIM_MODEL", "x-ai/grok-4.1-fast");

    if (openRouterApiKey.isBlank()) {
      throw new IllegalStateException("Missing OpenRouter API key (set SIM_OPENROUTER_API_KEY or OPENROUTER_API_KEY)");
    }

    int numPredict = getIntValue(props, "num_predict", "SIM_NUM_PREDICT", 600);
    int serverPort = getIntValue(props, "server.port", "SIM_SERVER_PORT", 8080);
    int maxRevisions = getIntValue(props, "max_revisions", "SIM_MAX_REVISIONS", 1);
    String factsPath = getValue(props, "facts.path", "SIM_FACTS_PATH", "config/facts.json");
    String billPath = getValue(props, "bill.path", "SIM_BILL_PATH", "config/bill.txt");
    String agenciesPath = getValue(props, "agencies.path", "SIM_AGENCIES_PATH", "config/agencies.json");
    String repsPath = getValue(props, "reps.path", "SIM_REPS_PATH", "config/representatives.json");

    // Bear-1 Compression Configuration
    String bear1SidecarUrl = getValue(props, "bear1.sidecar_url", "SIM_BEAR1_SIDECAR_URL", "http://localhost:8001");
    String bear1ApiKey = getValue(props, "bear1.api_key", "SIM_BEAR1_API_KEY", "");
    boolean compressionEnabled = getBoolValue(props, "bear1.enabled", "SIM_BEAR1_ENABLED", true);

    return new SimulationConfig(openRouterApiKey, openRouterBaseUrl, openRouterHttpReferer, openRouterXTitle,
        model, numPredict, serverPort, maxRevisions, factsPath,
        billPath, agenciesPath, repsPath,
        bear1SidecarUrl, bear1ApiKey, compressionEnabled);
  }

  private static String getValue(Properties props, String key, String envKey, String defaultValue) {
    String env = System.getenv(envKey);
    if (env != null && !env.isBlank()) return env;
    String value = props.getProperty(key);
    if (value != null && !value.isBlank()) return value;
    return defaultValue;
  }

  private static String getValue(Properties props, String key, String valueWhenUnset, String defaultValue, String... envKeys) {
    for (String envKey : envKeys) {
      if (envKey == null || envKey.isBlank()) continue;
      String env = System.getenv(envKey);
      if (env != null && !env.isBlank()) return env;
    }
    String value = props.getProperty(key);
    if (value != null && !value.isBlank()) return value;
    if (defaultValue != null) return defaultValue;
    return valueWhenUnset;
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

  private static boolean getBoolValue(Properties props, String key, String envKey, boolean defaultValue) {
    String env = System.getenv(envKey);
    if (env != null && !env.isBlank()) {
      return Boolean.parseBoolean(env);
    }
    String value = props.getProperty(key);
    if (value != null && !value.isBlank()) {
      return Boolean.parseBoolean(value);
    }
    return defaultValue;
  }
}
