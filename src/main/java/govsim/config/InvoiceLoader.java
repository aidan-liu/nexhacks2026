package govsim.config;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class InvoiceLoader {
  private final ObjectMapper mapper = new ObjectMapper();

  public List<Invoice> load(String path) throws IOException {
    if (path == null || path.isBlank()) {
      return List.of();
    }
    Path file = Path.of(path);
    if (!Files.exists(file)) {
      return List.of();
    }
    List<Invoice> invoices = mapper.readValue(Files.readString(file), new TypeReference<>() {});
    return invoices == null ? List.of() : new ArrayList<>(invoices);
  }

  public record Invoice(String id,
                        String vendor,
                        String description,
                        String amountWei,
                        String recipientAddress,
                        String proofUrl) {}
}
