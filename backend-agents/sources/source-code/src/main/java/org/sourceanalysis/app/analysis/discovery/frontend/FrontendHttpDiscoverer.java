package org.sourceanalysis.app.analysis.discovery.frontend;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.HttpMethodCondition;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;

/**
 * Validates finite frontend syntax observations against admitted source text and links static HTTP
 * requests to exact discovered backend entries.
 */
public final class FrontendHttpDiscoverer {

  private static final Comparator<FrontendRequestObservation> OBSERVATION_ORDER =
      Comparator.comparing(FrontendRequestObservation::pagePath)
          .thenComparingInt(observation -> observation.callRange().startOffsetUtf16())
          .thenComparing(FrontendRequestObservation::instanceKey)
          .thenComparing(FrontendRequestObservation::requestId);

  private final FrontendSyntaxTool syntaxTool;

  public FrontendHttpDiscoverer(FrontendSyntaxTool syntaxTool) {
    this.syntaxTool = Objects.requireNonNull(syntaxTool, "frontend syntax tool");
  }

  /**
   * Returns only source-validated request chains. It never evaluates JavaScript or dispatches a
   * backend discovery run.
   */
  public FrontendHttpIndex discover(FrontendHttpDiscoveryRequest request) {
    Objects.requireNonNull(request, "frontend HTTP discovery request");
    FrontendSyntaxScan scan =
        syntaxTool.scan(new FrontendSyntaxInput(request.sourceTexts()), request.configuration());
    if (scan == null) {
      throw new FrontendHttpDiscoveryException("FRONTEND_SYNTAX_SCAN_INVALID");
    }

    Map<String, VerifiedSourceTextDocument> documents = documentsByPath(request);
    List<FrontendSourceFileDisposition> files = validateFileDispositions(request, scan, documents);
    List<FrontendRequestObservation> observations =
        scan.requestObservations().stream().sorted(OBSERVATION_ORDER).toList();
    List<FrontendHttpRequestRecord> requests = new ArrayList<>();
    List<FrontendEntryLinkRecord> entryLinks = new ArrayList<>();
    List<FrontendDiagnosticRecord> diagnostics = new ArrayList<>();
    Map<SupportingUnitIdentity, FrontendSupportingSourceUnit> supportingSourceUnits =
        new HashMap<>();
    for (FrontendDiagnosticRecord diagnostic : scan.diagnostics()) {
      validateSourceIdentity(documents, diagnostic.sourcePath(), diagnostic.sourceSha256());
      diagnostics.add(diagnostic);
    }

    for (FrontendRequestObservation observation : observations) {
      validateSourceIdentity(
          documents, observation.pagePath(), observation.sourceSha256(), observation.callRange());
      for (FrontendWrapperCall wrapper : observation.wrapperPath()) {
        validateSourceIdentity(
            documents, wrapper.sourcePath(), wrapper.sourceSha256(), wrapper.callRange());
        validateSourceIdentity(
            documents, wrapper.sourcePath(), wrapper.sourceSha256(), wrapper.sourceUnitRange());
        requireContains(wrapper.sourceUnitRange(), wrapper.callRange());
      }
      for (FrontendSupportingSourceUnit supportingUnit : observation.supportingSourceUnits()) {
        validateSourceIdentity(
            documents,
            supportingUnit.sourcePath(),
            supportingUnit.sourceSha256(),
            supportingUnit.sourceUnitRange());
        SupportingUnitIdentity identity = SupportingUnitIdentity.from(supportingUnit);
        FrontendSupportingSourceUnit previous =
            supportingSourceUnits.putIfAbsent(identity, supportingUnit);
        if (previous != null && !previous.equals(supportingUnit)) {
          throw new FrontendHttpDiscoveryException("SOURCE_OBSERVATION_SOURCE_MISMATCH");
        }
      }
      requests.add(project(observation));
      entryLinks.add(link(observation, request));
      if (observation.diagnosticCode() != null) {
        diagnostics.add(
            new FrontendDiagnosticRecord(
                observation.diagnosticCode(),
                observation.pagePath(),
                observation.sourceSha256(),
                observation.requestId()));
      }
    }
    List<FrontendSupportingSourceUnit> sortedSupportingUnits =
        supportingSourceUnits.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .map(Map.Entry::getValue)
            .toList();
    return new FrontendHttpIndex(
        files,
        requests,
        entryLinks,
        diagnostics,
        FrontendHttpIndex.Status.ENABLED,
        List.of(),
        sortedSupportingUnits);
  }

  private static Map<String, VerifiedSourceTextDocument> documentsByPath(
      FrontendHttpDiscoveryRequest request) {
    Map<String, VerifiedSourceTextDocument> documents = new HashMap<>();
    for (VerifiedSourceTextDocument document : request.sourceTexts().documents()) {
      if (documents.put(document.path(), document) != null) {
        throw new FrontendHttpDiscoveryException("SOURCE_OBSERVATION_SOURCE_MISMATCH");
      }
    }
    return documents;
  }

  private static void validateSourceIdentity(
      Map<String, VerifiedSourceTextDocument> documents, String sourcePath, String sourceSha256) {
    VerifiedSourceTextDocument document = documents.get(sourcePath);
    if (document == null || !document.sha256().value().equals(sourceSha256)) {
      throw new FrontendHttpDiscoveryException("SOURCE_OBSERVATION_SOURCE_MISMATCH");
    }
  }

  private static void validateSourceIdentity(
      Map<String, VerifiedSourceTextDocument> documents,
      String sourcePath,
      String sourceSha256,
      SourceRange range) {
    validateSourceIdentity(documents, sourcePath, sourceSha256);
    VerifiedSourceTextDocument document = documents.get(sourcePath);
    String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    long end = (long) range.startOffsetUtf16() + range.lengthUtf16();
    if (end > source.length()) {
      throw new FrontendHttpDiscoveryException("SOURCE_OBSERVATION_RANGE_INVALID");
    }
  }

  private static void requireContains(SourceRange container, SourceRange contained) {
    long containerEnd = (long) container.startOffsetUtf16() + container.lengthUtf16();
    long containedEnd = (long) contained.startOffsetUtf16() + contained.lengthUtf16();
    if (container.startOffsetUtf16() > contained.startOffsetUtf16()
        || containerEnd < containedEnd) {
      throw new FrontendHttpDiscoveryException("SOURCE_OBSERVATION_RANGE_INVALID");
    }
  }

  private static List<FrontendSourceFileDisposition> validateFileDispositions(
      FrontendHttpDiscoveryRequest request,
      FrontendSyntaxScan scan,
      Map<String, VerifiedSourceTextDocument> documents) {
    Map<String, VerifiedSourceTextDocument> expected = selectedFrontendFiles(request, documents);
    List<FrontendSourceFileDisposition> supplied = scan.fileDispositions();
    Map<String, FrontendSourceFileDisposition> byPath = new HashMap<>();
    for (FrontendSourceFileDisposition disposition : supplied) {
      VerifiedSourceTextDocument document = expected.get(disposition.path());
      if (document == null
          || !document.sha256().value().equals(disposition.sourceSha256())
          || byPath.put(disposition.path(), disposition) != null) {
        throw new FrontendHttpDiscoveryException("FRONTEND_FILE_DISPOSITION_INVALID");
      }
    }
    if (!byPath.keySet().equals(expected.keySet())) {
      throw new FrontendHttpDiscoveryException("FRONTEND_FILE_DISPOSITION_INVALID");
    }

    List<String> parsedPaths =
        byPath.values().stream()
            .filter(
                disposition -> disposition.status() == FrontendSourceFileDisposition.Status.PARSED)
            .map(FrontendSourceFileDisposition::path)
            .sorted()
            .toList();
    if (!scan.parsedSourcePaths().equals(parsedPaths)
        || new HashSet<>(scan.parsedSourcePaths()).size() != scan.parsedSourcePaths().size()) {
      throw new FrontendHttpDiscoveryException("FRONTEND_FILE_DISPOSITION_INVALID");
    }
    return byPath.values().stream()
        .sorted(Comparator.comparing(FrontendSourceFileDisposition::path))
        .toList();
  }

  private static Map<String, VerifiedSourceTextDocument> selectedFrontendFiles(
      FrontendHttpDiscoveryRequest request, Map<String, VerifiedSourceTextDocument> documents) {
    Map<String, VerifiedSourceTextDocument> selected = new HashMap<>();
    for (VerifiedSourceTextDocument document : documents.values()) {
      if (isSelectedFrontendPath(document.path(), request.configuration().sourceRoots())) {
        selected.put(document.path(), document);
      }
    }
    return selected;
  }

  private static boolean isSelectedFrontendPath(String path, List<String> sourceRoots) {
    if (!path.endsWith(".vue") && !path.endsWith(".js")) {
      return false;
    }
    return sourceRoots.stream()
        .map(FrontendHttpDiscoverer::normalizedRoot)
        .anyMatch(root -> path.startsWith(root + "/"));
  }

  private static String normalizedRoot(String root) {
    String normalized = root;
    while (normalized.endsWith("/") && normalized.length() > 1) {
      normalized = normalized.substring(0, normalized.length() - 1);
    }
    return normalized;
  }

  private static FrontendHttpRequestRecord project(FrontendRequestObservation observation) {
    return new FrontendHttpRequestRecord(
        observation.requestId(),
        observation.pagePath(),
        observation.sourceSha256(),
        observation.instanceKey(),
        observation.callRange(),
        observation.httpMethod(),
        observation.rawUrlExpression(),
        observation.resolvedPath(),
        observation.requestOrigin(),
        observation.wrapperPath(),
        observation.supportingSourceUnits(),
        observation.argumentBindings(),
        observation.baseUrlExpression(),
        observation.baseUrlStaticFallback());
  }

  private static FrontendEntryLinkRecord link(
      FrontendRequestObservation observation, FrontendHttpDiscoveryRequest request) {
    if (!request.backendDiscoveryExecuted()) {
      return new FrontendEntryLinkRecord(
          observation.requestId(),
          FrontendEntryLinkRecord.Resolution.BACKEND_DISCOVERY_NOT_RUN,
          List.of());
    }
    if (observation.resolvedPath() == null) {
      return new FrontendEntryLinkRecord(
          observation.requestId(),
          FrontendEntryLinkRecord.Resolution.UNRESOLVED_REQUEST,
          List.of());
    }
    List<HttpEntryPoint> matches =
        request.httpEntries().stream()
            .filter(entry -> entry.route().equals(observation.resolvedPath()))
            .filter(entry -> acceptsExactMethod(entry.methodCondition(), observation.httpMethod()))
            .toList();
    if (matches.isEmpty()) {
      return new FrontendEntryLinkRecord(
          observation.requestId(), FrontendEntryLinkRecord.Resolution.NO_MATCH, List.of());
    }
    return new FrontendEntryLinkRecord(
        observation.requestId(),
        matches.size() == 1
            ? FrontendEntryLinkRecord.Resolution.MATCHED_UNIQUE
            : FrontendEntryLinkRecord.Resolution.MATCHED_MULTIPLE,
        matches.stream().map(HttpEntryPoint::entryId).toList());
  }

  private static boolean acceptsExactMethod(HttpMethodCondition condition, String method) {
    return condition.kind() == HttpMethodCondition.Kind.EXPLICIT
        && condition.methods().contains(method);
  }

  private record SupportingUnitIdentity(
      String sourcePath,
      String sourceSha256,
      int startOffsetUtf16,
      int lengthUtf16,
      FrontendWrapperCall.SourceUnitKind sourceUnitKind)
      implements Comparable<SupportingUnitIdentity> {

    private static SupportingUnitIdentity from(FrontendSupportingSourceUnit unit) {
      return new SupportingUnitIdentity(
          unit.sourcePath(),
          unit.sourceSha256(),
          unit.sourceUnitRange().startOffsetUtf16(),
          unit.sourceUnitRange().lengthUtf16(),
          unit.sourceUnitKind());
    }

    @Override
    public int compareTo(SupportingUnitIdentity other) {
      int compared = sourcePath.compareTo(other.sourcePath);
      if (compared != 0) {
        return compared;
      }
      compared = sourceSha256.compareTo(other.sourceSha256);
      if (compared != 0) {
        return compared;
      }
      compared = Integer.compare(startOffsetUtf16, other.startOffsetUtf16);
      if (compared != 0) {
        return compared;
      }
      compared = Integer.compare(lengthUtf16, other.lengthUtf16);
      if (compared != 0) {
        return compared;
      }
      return sourceUnitKind.compareTo(other.sourceUnitKind);
    }
  }
}
