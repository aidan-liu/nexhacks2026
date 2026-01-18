package govsim.config;

import govsim.domain.Bill;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

public class BillLoader {
  public static Bill fromFile(String path) throws IOException {
    Path billPath = Path.of(path);
    String raw = Files.readString(billPath);
    String title = Arrays.stream(raw.split("\\R"))
        .map(String::trim)
        .filter(s -> !s.isEmpty())
        .findFirst()
        .orElse("Untitled Bill");
    String filename = billPath.getFileName().toString();
    String id = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
    return new Bill(id, title, raw.trim());
  }
}
