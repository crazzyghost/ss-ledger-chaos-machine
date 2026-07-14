package com.softspark.chaos.ledgerproxy;

import jakarta.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * An open, size-bounded stream of statement-artifact bytes from the object store.
 *
 * <p>The artifact is <strong>never held whole in memory</strong>: {@link #writeTo(OutputStream)}
 * copies it through a fixed buffer straight to the servlet output stream. Bytes transiting the
 * gateway is the cost ADR-034 accepts, and it accepts it precisely <em>on the condition</em> that
 * they transit rather than accumulate — a {@code readAllBytes()} here would hold a multi-MB
 * statement per concurrent download.
 *
 * <p>The bound is enforced <em>while copying</em>, not only from the upstream {@code Content-Length}
 * — a peer can omit that header or lie about it.
 *
 * @param body the open response body; {@link #writeTo(OutputStream)} closes it
 * @param contentLength the upstream {@code Content-Length}, or {@code null} if it reported none (a
 *     chunked upstream); only a reported length may be echoed to the browser, or the gateway would
 *     be emitting a header it cannot honour
 * @param maxBytes the ceiling from {@code chaos.statements.max-artifact-bytes}
 */
public record ArtifactStream(InputStream body, @Nullable Long contentLength, long maxBytes) {

  private static final int BUFFER_BYTES = 16 * 1024;

  /**
   * Streams the artifact to {@code out} and closes the upstream body.
   *
   * <p>If the artifact turns out to exceed {@link #maxBytes} mid-transfer the copy is
   * <strong>abandoned</strong> with an {@link IOException}. By then the {@code 200} and its headers
   * are already committed, so the browser sees a failed (truncated) transfer rather than a file that
   * silently lost its tail — the honest outcome, and the reason the {@code Content-Length} pre-check
   * in {@link ArtifactFetcher} exists to catch the ordinary case before anything is committed.
   *
   * @param out the servlet output stream to copy to
   * @throws IOException if the transfer fails or the artifact exceeds {@link #maxBytes}
   */
  public void writeTo(OutputStream out) throws IOException {
    try (InputStream in = body) {
      var buffer = new byte[BUFFER_BYTES];
      long written = 0;
      int read;
      while ((read = in.read(buffer)) != -1) {
        written += read;
        if (written > maxBytes) {
          throw new IOException(
              "Statement artifact exceeds the maximum of "
                  + maxBytes
                  + " bytes; transfer abandoned");
        }
        out.write(buffer, 0, read);
      }
      out.flush();
    }
  }
}
