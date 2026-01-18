package govsim.web;

import java.io.IOException;
import java.io.OutputStream;

public class LogTeeOutputStream extends OutputStream {
  private final OutputStream primary;
  private final LogStore logStore;
  private final StringBuilder buffer = new StringBuilder();

  public LogTeeOutputStream(OutputStream primary, LogStore logStore) {
    this.primary = primary;
    this.logStore = logStore;
  }

  @Override
  public void write(int b) throws IOException {
    primary.write(b);
    char ch = (char) b;
    if (ch == '\r') {
      return;
    }
    if (ch == '\n') {
      flushLine();
    } else {
      buffer.append(ch);
    }
  }

  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    primary.write(b, off, len);
    for (int i = off; i < off + len; i++) {
      char ch = (char) b[i];
      if (ch == '\r') continue;
      if (ch == '\n') {
        flushLine();
      } else {
        buffer.append(ch);
      }
    }
  }

  @Override
  public void flush() throws IOException {
    primary.flush();
  }

  @Override
  public void close() throws IOException {
    flushLine();
    primary.close();
  }

  private void flushLine() {
    if (buffer.length() == 0) {
      logStore.addLine("");
      return;
    }
    String line = buffer.toString();
    buffer.setLength(0);
    logStore.addLine(line);
  }
}
