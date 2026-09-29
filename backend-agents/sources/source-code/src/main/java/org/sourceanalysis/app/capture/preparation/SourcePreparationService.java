package org.sourceanalysis.app.capture.preparation;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import org.sourceanalysis.app.analysis.inventory.DirectorySourceOrigin;
import org.sourceanalysis.app.analysis.inventory.GitCommitSourceOrigin;
import org.sourceanalysis.app.analysis.inventory.PreparedSourceReference;
import org.sourceanalysis.app.analysis.inventory.SavedSourcePreparation;
import org.sourceanalysis.app.analysis.inventory.SourceEntry;
import org.sourceanalysis.app.analysis.inventory.SourceEntryExclusion;
import org.sourceanalysis.app.analysis.inventory.SourceEntryInheritance;
import org.sourceanalysis.app.analysis.inventory.SourceIssue;
import org.sourceanalysis.app.analysis.inventory.SourceOrigin;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationOperation;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationPublisher;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationReader;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationRequest;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationRequestValidator;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationResult;
import org.sourceanalysis.app.analysis.inventory.SourcePreparationTarget;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepPublicationReference;
import org.sourceanalysis.app.capture.localgit.ConstrainedGitObjectAccess;
import org.sourceanalysis.app.capture.localgit.FixedGitObjectAccess;

/**
 * Composes new, refresh, and offline exclusion preparations through one immutable publisher path.
 */
public final class SourcePreparationService {

  private static final Comparator<String> UTF8_ORDER = SourcePreparationService::compareUtf8;

  private final Path privateOutputRoot;
  private final SourcePreparationPublisher publisher;
  private final Function<AnalysisStepPublicationReference, SavedSourcePreparation> baseOpener;
  private final PreparedSourceArchive archive;
  private final SourcePreparationToolIdentityFactory identities;
  private final DirectorySourceAccess directoryAccess;
  private final FixedGitObjectAccess gitObjects;
  private final SourcePreparationStagingFactory stagingFactory;

  /**
   * Creates the production composition without accepting a source path in the CLI-facing service
   * API. Git plumbing is created only for a fixed Git origin; directory preparation never probes or
   * resolves Git.
   */
  public static SourcePreparationService production(
      Path privateOutputRoot,
      SourcePreparationPublisher publisher,
      SourcePreparationReader savedReader,
      PreparedSourceArchive archive,
      SourceOrigin origin,
      Path trustedGitExecutable)
      throws IOException {
    Objects.requireNonNull(origin, "source-preparation origin");
    TrustedGitVersionProbe versionProbe;
    FixedGitObjectAccess objects;
    if (origin instanceof GitCommitSourceOrigin) {
      versionProbe = new ConstrainedGitVersionProbe(trustedGitExecutable);
      objects =
          new ConstrainedGitObjectAccess(
              privateOutputRoot.resolve("git-object-access"), trustedGitExecutable);
    } else if (origin instanceof DirectorySourceOrigin) {
      versionProbe =
          () -> {
            throw new IOException("directory source preparation does not use Git");
          };
      objects =
          (repository, commit) -> {
            throw new IOException("directory source preparation does not use Git objects");
          };
    } else {
      throw new IOException("source-preparation origin kind is unsupported");
    }
    return new SourcePreparationService(
        privateOutputRoot,
        publisher,
        savedReader,
        archive,
        new SourcePreparationToolIdentityFactory(SourcePreparationService.class, versionProbe),
        new JdkDirectorySourceAccess(),
        objects,
        SourcePreparationService::openProductionStaging);
  }

  SourcePreparationService(
      Path privateOutputRoot,
      SourcePreparationPublisher publisher,
      SourcePreparationReader savedReader,
      PreparedSourceArchive archive,
      SourcePreparationToolIdentityFactory identities,
      DirectorySourceAccess directoryAccess,
      FixedGitObjectAccess gitObjects,
      SourcePreparationStagingFactory stagingFactory) {
    this(
        privateOutputRoot,
        publisher,
        savedReader,
        archive,
        identities,
        directoryAccess,
        gitObjects,
        stagingFactory,
        Objects.requireNonNull(savedReader, "saved source-preparation reader")::reopen);
  }

  SourcePreparationService(
      Path privateOutputRoot,
      SourcePreparationPublisher publisher,
      SourcePreparationReader savedReader,
      PreparedSourceArchive archive,
      SourcePreparationToolIdentityFactory identities,
      DirectorySourceAccess directoryAccess,
      FixedGitObjectAccess gitObjects,
      SourcePreparationStagingFactory stagingFactory,
      Function<AnalysisStepPublicationReference, SavedSourcePreparation> baseOpener) {
    this.privateOutputRoot =
        Objects.requireNonNull(privateOutputRoot, "private source-preparation output root")
            .toAbsolutePath()
            .normalize();
    this.publisher = Objects.requireNonNull(publisher, "source-preparation publisher");
    Objects.requireNonNull(savedReader, "saved source-preparation reader");
    this.baseOpener = Objects.requireNonNull(baseOpener, "saved source-preparation base opener");
    this.archive = Objects.requireNonNull(archive, "prepared-source archive");
    this.identities = Objects.requireNonNull(identities, "source-preparation tool identities");
    this.directoryAccess = Objects.requireNonNull(directoryAccess, "directory source access");
    this.gitObjects = Objects.requireNonNull(gitObjects, "fixed Git object access");
    this.stagingFactory =
        Objects.requireNonNull(stagingFactory, "source-preparation staging factory");
  }

  public SavedSourcePreparation prepare(AnalysisRunId runId, SourcePreparationRequest request)
      throws IOException {
    return prepare(runId, request, null);
  }

  /**
   * Prepares one source version only when its final private controls equal the queued v3 inputs.
   */
  public SavedSourcePreparation prepare(
      AnalysisRunId runId,
      SourcePreparationRequest request,
      PreparedSourceArchive.PreparationInputReferences expectedInputs)
      throws IOException {
    Objects.requireNonNull(runId, "analysis run ID");
    Objects.requireNonNull(request, "source-preparation request");
    if (request.operation() == SourcePreparationOperation.NEW) {
      requireIndependentRoots(request.origin());
      return publishNew(runId, request, expectedInputs);
    }

    SavedSourcePreparation parent = reopenExactBase(request);
    requireIndependentRoots(request.origin());
    SourcePreparationRequestValidator.validateDerived(parent.request(), request);
    validateKnownTargets(parent.result(), request);
    return request.operation() == SourcePreparationOperation.EXCLUDE
        ? publishExcluded(runId, request, parent, expectedInputs)
        : publishRefresh(runId, request, parent, expectedInputs);
  }

  /**
   * Returns the exact private-fact references that this service will use for a queued v3 request.
   * This is deliberately source-byte-free preflight work.
   */
  public PreparedSourceArchive.PreparationInputReferences preparationInputs(
      SourcePreparationRequest request) throws IOException {
    Objects.requireNonNull(request, "source-preparation request");
    return archive.preparationInputs(request, identities.detect(request.origin()));
  }

  private static SourcePreparationStaging openProductionStaging(
      Path privateOutputRoot, AnalysisRunId runId) throws IOException {
    Path runRoot =
        privateOutputRoot
            .resolve("source-preparation-staging")
            .resolve(Objects.requireNonNull(runId, "analysis run ID").value());
    StagedSourcePreparationBlobSink sink = new StagedSourcePreparationBlobSink(runRoot);
    return new SourcePreparationStaging() {
      @Override
      public SourcePreparationBlobSink sink() {
        return sink;
      }

      @Override
      public SourcePreparationBlobReader acceptedBytes() {
        return entry ->
            Files.newInputStream(
                runRoot.resolve("source-preparation-blobs").resolve(entry.relativePath()));
      }

      @Override
      public void close() {
        // The frozen archive validates and copies accepted bytes before this per-run staging
        // closes.
      }
    };
  }

  private SavedSourcePreparation publishNew(
      AnalysisRunId runId,
      SourcePreparationRequest request,
      PreparedSourceArchive.PreparationInputReferences expectedInputs)
      throws IOException {
    try (SourcePreparationStaging staging = stagingFactory.open(privateOutputRoot, runId)) {
      SourcePreparationResult result = readerFor(request.origin()).read(request, staging.sink());
      return publish(runId, request, result, staging.acceptedBytes(), expectedInputs);
    }
  }

  private SavedSourcePreparation publishRefresh(
      AnalysisRunId runId,
      SourcePreparationRequest request,
      SavedSourcePreparation parent,
      PreparedSourceArchive.PreparationInputReferences expectedInputs)
      throws IOException {
    try (SourcePreparationStaging staging = stagingFactory.open(privateOutputRoot, runId)) {
      SourcePreparationTargetFragment fragment =
          readerFor(request.origin()).readTargets(request, staging.sink());
      SourcePreparationResult result =
          verifyInheritedAcceptedBytes(parent, mergeRefresh(parent, request, fragment));
      return publish(
          runId, request, result, combinedBytes(parent, staging.acceptedBytes()), expectedInputs);
    }
  }

  private SavedSourcePreparation publishExcluded(
      AnalysisRunId runId,
      SourcePreparationRequest request,
      SavedSourcePreparation parent,
      PreparedSourceArchive.PreparationInputReferences expectedInputs)
      throws IOException {
    SourcePreparationResult result = verifyInheritedAcceptedBytes(parent, exclude(parent, request));
    return publish(runId, request, result, combinedBytes(parent, null), expectedInputs);
  }

  private SavedSourcePreparation publish(
      AnalysisRunId runId,
      SourcePreparationRequest request,
      SourcePreparationResult result,
      SourcePreparationBlobReader acceptedBytes,
      PreparedSourceArchive.PreparationInputReferences expectedInputs)
      throws IOException {
    SourcePreparationToolIdentity actualIdentity = identities.detect(request.origin());
    CapturedSourcePreparation capture =
        new CapturedSourcePreparation(request, result, acceptedBytes, actualIdentity);
    if (expectedInputs != null
        && !expectedInputs.equals(archive.preparationInputs(request, actualIdentity))) {
      throw new IOException("SOURCE_PREPARATION_INPUTS_CHANGED");
    }
    return publisher.publish(runId, capture);
  }

  private SavedSourcePreparation reopenExactBase(SourcePreparationRequest request)
      throws IOException {
    PreparedSourceReference requested = request.basePreparation();
    if (requested == null) {
      throw new IOException("derived source preparation requires a complete saved base");
    }
    final SavedSourcePreparation reopened;
    try {
      reopened = baseOpener.apply(requested.publication());
    } catch (IllegalArgumentException invalid) {
      throw new IOException("derived source preparation base cannot be reopened", invalid);
    }
    if (reopened == null || !requested.equals(reopened.sourceVersionReference())) {
      throw new IOException("derived source preparation base reference does not match its receipt");
    }
    return reopened;
  }

  private void requireIndependentRoots(SourceOrigin origin) throws IOException {
    Path sourceRoot = origin.canonicalRoot().toAbsolutePath().normalize();
    if (sourceRoot.startsWith(privateOutputRoot) || privateOutputRoot.startsWith(sourceRoot)) {
      throw new IOException("source root and private source-preparation output root overlap");
    }
  }

  private void validateKnownTargets(
      SourcePreparationResult parent, SourcePreparationRequest request) throws IOException {
    for (SourcePreparationTarget target : request.targets()) {
      SourceEntry exact =
          parent.entries().stream()
              .filter(entry -> entry.relativePath().equals(target.relativePath()))
              .findFirst()
              .orElse(null);
      if (exact != null && exact.entryKind() == SourceEntry.Kind.UNKNOWN) {
        if (request.operation() == SourcePreparationOperation.EXCLUDE) {
          boolean excludesExactUnknownSubtree =
              target.kind() == SourcePreparationTarget.Kind.DIRECTORY
                  && parent.unknownSubtrees().contains(target.relativePath());
          if (!excludesExactUnknownSubtree) {
            throw new IOException("unknown source target exclusion scope has not been decided");
          }
        }
      } else if (exact != null && !matchesTargetKind(exact, target)) {
        throw new IOException(
            "source-preparation target kind does not match the saved source entry");
      }
      if (request.operation() == SourcePreparationOperation.REFRESH
          && request.effectiveExclusions().stream()
              .anyMatch(exclusion -> covers(exclusion, target))) {
        throw new IOException("refresh target is already excluded from the effective source scope");
      }
    }
  }

  private SourcePreparationResult mergeRefresh(
      SavedSourcePreparation parent,
      SourcePreparationRequest request,
      SourcePreparationTargetFragment fragment) {
    List<SourceEntry> entries = new ArrayList<>();
    for (SourceEntry entry : parent.result().entries()) {
      if (!coveredByAny(entry.relativePath(), request.targets())) {
        entries.add(inherited(entry, parent.sourceVersionReference().sourceVersionId()));
      }
    }
    entries.addAll(fragment.entries());
    entries.sort(Comparator.comparing(SourceEntry::relativePath, UTF8_ORDER));

    List<SourceIssue> issues = new ArrayList<>();
    for (SourceIssue issue : parent.result().issues()) {
      if (!coveredByAny(issue.relativePath(), request.targets())) {
        issues.add(issue);
      }
    }
    issues.addAll(fragment.issues());
    issues.sort(Comparator.comparing(SourceIssue::issueId, UTF8_ORDER));

    List<String> unknownSubtrees = new ArrayList<>();
    for (String path : parent.result().unknownSubtrees()) {
      if (!coveredByAny(path, request.targets())) {
        unknownSubtrees.add(path);
      }
    }
    unknownSubtrees.addAll(fragment.unknownSubtrees());
    unknownSubtrees = distinctSorted(unknownSubtrees);
    return new SourcePreparationResult(
        mergeInspection(parent.result(), fragment),
        enumerationCompleteAfterRefresh(parent.result(), request, fragment, unknownSubtrees),
        entries,
        issues,
        unknownSubtrees,
        unmatchedExclusions(request.effectiveExclusions(), entries));
  }

  private SourcePreparationResult exclude(
      SavedSourcePreparation parent, SourcePreparationRequest request) {
    List<SourceEntry> entries = new ArrayList<>();
    for (SourceEntry entry : parent.result().entries()) {
      SourcePreparationTarget exclusion = coveringTarget(entry.relativePath(), request.targets());
      entries.add(
          exclusion == null
              ? inherited(entry, parent.sourceVersionReference().sourceVersionId())
              : excluded(entry, exclusion));
    }
    entries.sort(Comparator.comparing(SourceEntry::relativePath, UTF8_ORDER));

    List<SourceIssue> issues = new ArrayList<>();
    for (SourceIssue issue : parent.result().issues()) {
      SourcePreparationTarget exclusion =
          issue.relativePath() == null
              ? null
              : coveringTarget(issue.relativePath(), request.targets());
      issues.add(exclusion == null ? issue : resolvedByExclusion(issue));
    }
    issues.sort(Comparator.comparing(SourceIssue::issueId, UTF8_ORDER));
    return new SourcePreparationResult(
        parent.result().inspectionStatus(),
        parent.result().enumerationComplete(),
        entries,
        issues,
        parent.result().unknownSubtrees(),
        unmatchedExclusions(request.effectiveExclusions(), entries));
  }

  private SourcePreparationResult verifyInheritedAcceptedBytes(
      SavedSourcePreparation parent, SourcePreparationResult result) {
    Map<String, SourceEntry> parentEntries = new HashMap<>();
    for (SourceEntry entry : parent.result().entries()) {
      parentEntries.put(entry.relativePath(), entry);
    }
    List<SourceEntry> entries = new ArrayList<>();
    List<SourceIssue> issues = new ArrayList<>(result.issues());
    for (SourceEntry entry : result.entries()) {
      if (!isInheritedAccepted(entry)) {
        entries.add(entry);
        continue;
      }
      SourceEntry parentEntry = parentEntries.get(entry.relativePath());
      try (InputStream ignored =
          parentEntry == null
              ? null
              : archive.open(parent.sourceVersionReference().sourceVersionId(), parentEntry)) {
        if (ignored == null) {
          throw new IOException("inherited source entry is absent from its saved parent");
        }
        entries.add(entry);
      } catch (IOException integrityFailure) {
        SourceIssue issue = inheritedContentIntegrityIssue(entry);
        issues.add(issue);
        entries.add(unavailableInherited(entry, issue.issueId()));
      }
    }
    entries.sort(Comparator.comparing(SourceEntry::relativePath, UTF8_ORDER));
    issues.sort(Comparator.comparing(SourceIssue::issueId, UTF8_ORDER));
    return new SourcePreparationResult(
        result.inspectionStatus(),
        result.enumerationComplete(),
        entries,
        issues,
        result.unknownSubtrees(),
        result.unmatchedExclusions());
  }

  private SourcePreparationBlobReader combinedBytes(
      SavedSourcePreparation parent, SourcePreparationBlobReader staged) {
    Map<String, SourceEntry> parentEntries = new HashMap<>();
    for (SourceEntry entry : parent.result().entries()) {
      parentEntries.put(entry.relativePath(), entry);
    }
    return entry -> {
      if (entry.inheritedFrom() != null) {
        SourceEntry parentEntry = parentEntries.get(entry.relativePath());
        if (parentEntry == null) {
          throw new IOException("inherited source entry is absent from its saved parent");
        }
        return archive.open(parent.sourceVersionReference().sourceVersionId(), parentEntry);
      }
      if (staged == null) {
        throw new IOException("current source entry has no staged bytes");
      }
      return staged.open(entry);
    };
  }

  private SourceOriginReader readerFor(SourceOrigin origin) throws IOException {
    if (origin instanceof DirectorySourceOrigin) {
      return new DirectorySourceOriginReader(directoryAccess, privateOutputRoot);
    }
    if (origin instanceof GitCommitSourceOrigin) {
      return new GitSourceOriginReader(gitObjects, privateOutputRoot);
    }
    throw new IOException("source-preparation origin kind is unsupported");
  }

  private static SourcePreparationResult.InspectionStatus mergeInspection(
      SourcePreparationResult parent, SourcePreparationTargetFragment fragment) {
    return parent.inspectionStatus() == SourcePreparationResult.InspectionStatus.ABORTED
            || fragment.inspectionStatus() == SourcePreparationResult.InspectionStatus.ABORTED
        ? SourcePreparationResult.InspectionStatus.ABORTED
        : SourcePreparationResult.InspectionStatus.COMPLETED;
  }

  private static boolean enumerationCompleteAfterRefresh(
      SourcePreparationResult parent,
      SourcePreparationRequest request,
      SourcePreparationTargetFragment fragment,
      List<String> unresolvedUnknownSubtrees) {
    if (!fragment.targetEnumerationComplete() || !unresolvedUnknownSubtrees.isEmpty()) {
      return false;
    }
    if (parent.enumerationComplete()) {
      return true;
    }
    return parent.inspectionStatus() == SourcePreparationResult.InspectionStatus.COMPLETED
        && !parent.unknownSubtrees().isEmpty()
        && parent.unknownSubtrees().stream()
            .allMatch(path -> coveredByAny(path, request.targets()));
  }

  private static SourceEntry inherited(
      SourceEntry entry, org.sourceanalysis.app.artifact.ArtifactId sourceVersionId) {
    return new SourceEntry(
        entry.relativePath(),
        entry.entryKind(),
        entry.disposition(),
        entry.sizeBytes(),
        entry.sha256(),
        entry.blobRef(),
        entry.fileId(),
        entry.textEncoding(),
        entry.originAttributes(),
        entry.observations(),
        entry.issueIds(),
        new SourceEntryInheritance(sourceVersionId, entry.fileId()),
        entry.exclusion());
  }

  private static boolean isInheritedAccepted(SourceEntry entry) {
    return entry.inheritedFrom() != null
        && (entry.disposition() == SourceEntry.Disposition.VERIFIED_TEXT
            || entry.disposition() == SourceEntry.Disposition.VERIFIED_MEDIA);
  }

  private static SourceEntry unavailableInherited(SourceEntry entry, String issueId) {
    List<String> issueIds = new ArrayList<>(entry.issueIds());
    issueIds.add(issueId);
    return new SourceEntry(
        entry.relativePath(),
        entry.entryKind(),
        SourceEntry.Disposition.UNAVAILABLE,
        null,
        null,
        null,
        null,
        null,
        entry.originAttributes(),
        entry.observations(),
        issueIds,
        null,
        null);
  }

  private static SourceIssue inheritedContentIntegrityIssue(SourceEntry entry) {
    String message = "inherited saved source bytes cannot be verified";
    return new SourceIssue(
        "source-issue:"
            + sha256(
                SourceIssue.Code.SOURCE_HASH_MISMATCH.name()
                    + "|"
                    + SourceIssue.Category.SAVED_CONTENT_INTEGRITY.name()
                    + "|"
                    + SourceIssue.Scope.FILE.name()
                    + "|"
                    + entry.relativePath()
                    + "|"
                    + SourceIssue.Operation.READ_SAVED_CONTENT.name()
                    + "|"
                    + SourceIssue.Resolution.OPEN.name()
                    + "|"
                    + message),
        SourceIssue.Code.SOURCE_HASH_MISMATCH,
        SourceIssue.Category.SAVED_CONTENT_INTEGRITY,
        SourceIssue.Scope.FILE,
        entry.relativePath(),
        SourceIssue.Operation.READ_SAVED_CONTENT,
        message,
        null,
        null,
        SourceIssue.Resolution.OPEN,
        Set.of(SourceIssue.AllowedAction.REFRESH_FILE, SourceIssue.AllowedAction.EXCLUDE_FILE),
        null);
  }

  private static SourceEntry excluded(SourceEntry entry, SourcePreparationTarget target) {
    return new SourceEntry(
        entry.relativePath(),
        entry.entryKind(),
        SourceEntry.Disposition.EXCLUDED_BY_USER,
        entry.sizeBytes(),
        entry.sha256(),
        entry.blobRef(),
        entry.fileId(),
        null,
        entry.originAttributes(),
        entry.observations(),
        entry.issueIds(),
        null,
        new SourceEntryExclusion(
            "USER_DECLARED", SourcePreparationOperation.EXCLUDE, target.relativePath()));
  }

  private static SourceIssue resolvedByExclusion(SourceIssue issue) {
    if (issue.resolution() != SourceIssue.Resolution.OPEN) {
      return issue;
    }
    return new SourceIssue(
        issue.issueId(),
        issue.code(),
        issue.category(),
        issue.scope(),
        issue.relativePath(),
        issue.operation(),
        issue.message(),
        issue.expected(),
        issue.observed(),
        SourceIssue.Resolution.RESOLVED_BY_EXCLUSION,
        Set.of(),
        issue.diagnosticRef());
  }

  private static List<SourcePreparationTarget> unmatchedExclusions(
      List<SourcePreparationTarget> exclusions, List<SourceEntry> entries) {
    return exclusions.stream()
        .filter(
            target ->
                entries.stream()
                    .noneMatch(
                        entry ->
                            entry.relativePath().equals(target.relativePath())
                                && matchesTargetKind(entry, target)))
        .sorted(
            Comparator.comparing(SourcePreparationTarget::relativePath, UTF8_ORDER)
                .thenComparing(target -> target.kind().name(), UTF8_ORDER))
        .toList();
  }

  private static boolean matchesTargetKind(SourceEntry entry, SourcePreparationTarget target) {
    return (target.kind() == SourcePreparationTarget.Kind.FILE
            && entry.entryKind() == SourceEntry.Kind.REGULAR_FILE)
        || (target.kind() == SourcePreparationTarget.Kind.DIRECTORY
            && entry.entryKind() == SourceEntry.Kind.DIRECTORY);
  }

  private static boolean coveredByAny(String path, List<SourcePreparationTarget> targets) {
    return coveringTarget(path, targets) != null;
  }

  private static SourcePreparationTarget coveringTarget(
      String path, List<SourcePreparationTarget> targets) {
    if (path == null) {
      return null;
    }
    return targets.stream().filter(target -> covers(target, path)).findFirst().orElse(null);
  }

  private static boolean covers(SourcePreparationTarget target, SourcePreparationTarget candidate) {
    return covers(target, candidate.relativePath());
  }

  private static boolean covers(SourcePreparationTarget target, String path) {
    return target.relativePath().equals(path)
        || (target.kind() == SourcePreparationTarget.Kind.DIRECTORY
            && path.startsWith(target.relativePath() + "/"));
  }

  private static List<String> distinctSorted(List<String> paths) {
    return paths.stream().filter(Objects::nonNull).distinct().sorted(UTF8_ORDER).toList();
  }

  private static int compareUtf8(String first, String second) {
    byte[] left = first.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    byte[] right = second.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(left.length, right.length);
  }

  private static String sha256(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 is unavailable", unavailable);
    }
  }
}
