package org.sourceanalysis.app.artifact;

/** The exact producer identity recorded in one canonical module artifact. */
public record ModuleProducer(ModulePublicationAddress address, String moduleVersion) {

  public ModuleProducer {
    if (address == null || moduleVersion == null || moduleVersion.isBlank()) {
      throw new IllegalArgumentException("module producer has invalid required values");
    }
  }
}
