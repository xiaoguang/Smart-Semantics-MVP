package org.sourceanalysis.app.artifact;

import java.util.List;

/** The fresh-reopened and verified contents of one module publication. */
public record ReopenedModulePublication(
    ModulePublicationReference reference,
    ModuleReceipt receipt,
    List<VerifiedCanonicalPayload> payloads) {

  public ReopenedModulePublication {
    payloads = List.copyOf(payloads);
  }
}
