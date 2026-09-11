package org.sourceanalysis.app.analysis.document;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.CompletedReportRenderer;
import org.sourceanalysis.app.runtime.RenderedDocumentReference;

/** Adapter that proves a saved report can be deterministically rerendered without a Provider. */
public final class BusinessReportCheckpointRenderer implements CompletedReportRenderer {

  private final BusinessReportCheckpointReader reader;

  public BusinessReportCheckpointRenderer(CanonicalModuleArtifactStore artifacts) {
    reader =
        new BusinessReportCheckpointReader(
            Objects.requireNonNull(artifacts, "module artifact store"));
  }

  @Override
  public RenderedDocumentReference render(
      AnalysisRunId runId, ModulePublicationReference reportCheckpoint) {
    BusinessReportPublication publication = reader.reopen(reportCheckpoint);
    String rerendered = reader.rerender(publication);
    if (!rerendered.equals(publication.documentMarkdown())) {
      throw new IllegalStateException("BUSINESS_REPORT_RERENDER_MISMATCH");
    }
    byte[] bytes = rerendered.getBytes(StandardCharsets.UTF_8);
    return new RenderedDocumentReference(runId, reportCheckpoint, sha256(bytes), bytes.length);
  }

  private static Sha256Digest sha256(byte[] bytes) {
    try {
      return new Sha256Digest(
          HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }
}
