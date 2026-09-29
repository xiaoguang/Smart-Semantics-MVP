package org.sourceanalysis.app.analysis.discovery.frontend;

import java.util.List;

/** The typed, parser-neutral result of one frontend HTTP discovery pass. */
public record FrontendHttpIndex(
    List<FrontendSourceFileDisposition> files,
    List<FrontendHttpRequestRecord> requests,
    List<FrontendEntryLinkRecord> entryLinks,
    List<FrontendDiagnosticRecord> diagnostics,
    Status status,
    List<FrontendConfigurationFileRecord> configurationFiles,
    List<FrontendSupportingSourceUnit> supportingSourceUnits) {

  /** Distinguishes an enabled scan with no requests from an explicitly disabled frontend scope. */
  public enum Status {
    ENABLED,
    DISABLED
  }

  /** Retains the parser result constructor's historical enabled meaning. */
  public FrontendHttpIndex(
      List<FrontendSourceFileDisposition> files,
      List<FrontendHttpRequestRecord> requests,
      List<FrontendEntryLinkRecord> entryLinks,
      List<FrontendDiagnosticRecord> diagnostics) {
    this(files, requests, entryLinks, diagnostics, Status.ENABLED, List.of());
  }

  /**
   * Retains the first status-aware constructor for indexes without selected configuration files.
   */
  public FrontendHttpIndex(
      List<FrontendSourceFileDisposition> files,
      List<FrontendHttpRequestRecord> requests,
      List<FrontendEntryLinkRecord> entryLinks,
      List<FrontendDiagnosticRecord> diagnostics,
      Status status) {
    this(files, requests, entryLinks, diagnostics, status, List.of());
  }

  /** Retains indexes written before v2 admitted explicit supporting source units. */
  public FrontendHttpIndex(
      List<FrontendSourceFileDisposition> files,
      List<FrontendHttpRequestRecord> requests,
      List<FrontendEntryLinkRecord> entryLinks,
      List<FrontendDiagnosticRecord> diagnostics,
      Status status,
      List<FrontendConfigurationFileRecord> configurationFiles) {
    this(files, requests, entryLinks, diagnostics, status, configurationFiles, List.of());
  }

  public FrontendHttpIndex {
    files = List.copyOf(files);
    requests = List.copyOf(requests);
    entryLinks = List.copyOf(entryLinks);
    diagnostics = List.copyOf(diagnostics);
    configurationFiles = List.copyOf(configurationFiles);
    supportingSourceUnits = List.copyOf(supportingSourceUnits);
    if (status == null) {
      throw new IllegalArgumentException("frontend HTTP index status");
    }
  }

  /**
   * Returns the explicit empty result used when frontend discovery is disabled by configuration.
   */
  public static FrontendHttpIndex disabledIndex() {
    return new FrontendHttpIndex(
        List.of(), List.of(), List.of(), List.of(), Status.DISABLED, List.of());
  }
}
