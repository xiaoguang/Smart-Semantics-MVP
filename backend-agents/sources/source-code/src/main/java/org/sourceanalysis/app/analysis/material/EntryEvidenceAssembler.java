package org.sourceanalysis.app.analysis.material;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.SourceRange;
import org.sourceanalysis.app.analysis.code.publish.JavaCodeIndex;
import org.sourceanalysis.app.analysis.discovery.HttpEntryPoint;
import org.sourceanalysis.app.analysis.discovery.HttpMethodCondition;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendEntryLinkRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpIndex;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendHttpRequestRecord;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendPageContext;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendPageSourceUnit;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSourceUnits;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendSupportingSourceUnit;
import org.sourceanalysis.app.analysis.discovery.frontend.FrontendWrapperCall;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;
import org.sourceanalysis.app.evidence.SourceExcerptV1;

/**
 * Builds entry-owned material entirely from reopened R1/R2/R3 projections.
 *
 * <p>This class deliberately has no parser, JDT, Node, file-system, or provider dependency. It
 * selects complete saved units; it never restarts a tool or removes a method/XML resource to fit a
 * legacy Markdown packet budget.
 */
public final class EntryEvidenceAssembler {

  private static final Comparator<String> UTF8_ORDER = EntryEvidenceAssembler::compareUtf8;
  private static final Pattern UNSUPPORTED_SPRING_CONDITION =
      Pattern.compile("\\b(?:params|headers|consumes)\\s*=");
  private static final Pattern JDT_WORKSPACE_SOURCE_URI =
      Pattern.compile("file://[^\\s]+?#\\d+:\\d+");
  private static final Pattern FILE_URI = Pattern.compile("file://[^\\s]+");
  private static final String UNCONFIRMED_WORKSPACE_SOURCE_IDENTITY =
      "UNCONFIRMED_JDT_WORKSPACE_SOURCE_IDENTITY";

  /** Assembles the complete backend and frontend denominators from already-read typed inputs. */
  public EntryEvidenceSet assemble(EntryEvidenceRequest request) {
    Objects.requireNonNull(request, "entry-evidence request");
    requireInputClosure(request);

    List<HttpEntryPoint> entries =
        request.entries().stream()
            .sorted(Comparator.comparing(value -> value.entryId().value(), UTF8_ORDER))
            .toList();
    if (entries.size() > request.profile().maxEntries()) {
      throw new EntryLimitExceededException(request.profile().maxEntries(), entries.size());
    }
    Map<String, JavaCodeIndex.EntryCollection> collections =
        collectionsByEntry(request.javaCodeIndex());
    Map<String, HttpEntryPoint> httpEntries = httpEntriesById(entries);
    if (!collections.keySet().equals(httpEntries.keySet())) {
      throw new IllegalArgumentException("ENTRY_EVIDENCE_ENTRY_DENOMINATOR_MISMATCH");
    }

    FrontendDisposition frontend =
        frontendDisposition(
            request.frontendIndex(),
            request.frontendSourceUnits(),
            entries,
            request.httpMappings());
    List<EntryEvidenceSet.Entry> result = new ArrayList<>(entries.size());
    for (HttpEntryPoint entry : entries) {
      JavaCodeIndex.EntryCollection collection = collections.get(entry.entryId().value());
      result.add(
          entry(
              entry,
              collection,
              request.javaCodeIndex().catalog().files(),
              request.persistenceIndex(),
              request.frontendIndex(),
              frontend));
    }

    return new EntryEvidenceSet(
        new EntryEvidenceSet.Header(
            request.sourceInventory(),
            request.applicationDiscovery(),
            request.navigationPublication(),
            request.persistencePublication(),
            request.frontendPublication(),
            request.javaCodeIndex().snapshotId(),
            request.frontendIndex().status(),
            request.frontendIndex().files(),
            request.frontendIndex().diagnostics(),
            request.profile()),
        result,
        frontend.coverage(),
        frontend.pageContextCoverage());
  }

  private static void requireInputClosure(EntryEvidenceRequest request) {
    if (!request
            .javaCodeIndex()
            .snapshotId()
            .equals(request.persistenceIndex().header().sourceSnapshotId())
        || !request
            .navigationPublication()
            .equals(request.persistenceIndex().header().navigationPublication())) {
      throw new IllegalArgumentException("ENTRY_EVIDENCE_INPUT_SNAPSHOT_MISMATCH");
    }
    if (request.frontendIndex().status() == FrontendHttpIndex.Status.DISABLED
        && (!request.frontendIndex().requests().isEmpty()
            || !request.frontendIndex().supportingSourceUnits().isEmpty()
            || !request.frontendIndex().pageContexts().isEmpty()
            || !request.frontendSourceUnits().units().isEmpty())) {
      throw new IllegalArgumentException("ENTRY_EVIDENCE_DISABLED_FRONTEND_IS_NOT_EMPTY");
    }
  }

  private static Map<String, JavaCodeIndex.EntryCollection> collectionsByEntry(
      JavaCodeIndex index) {
    Map<String, JavaCodeIndex.EntryCollection> values = new LinkedHashMap<>();
    for (JavaCodeIndex.EntryCollection collection : index.entries()) {
      if (values.putIfAbsent(collection.seed().entryId(), collection) != null) {
        throw new IllegalArgumentException("ENTRY_EVIDENCE_DUPLICATE_JAVA_ENTRY");
      }
    }
    return values;
  }

  private static Map<String, HttpEntryPoint> httpEntriesById(List<HttpEntryPoint> entries) {
    Map<String, HttpEntryPoint> values = new LinkedHashMap<>();
    for (HttpEntryPoint entry : entries) {
      if (values.putIfAbsent(entry.entryId().value(), entry) != null) {
        throw new IllegalArgumentException("ENTRY_EVIDENCE_DUPLICATE_HTTP_ENTRY");
      }
    }
    return values;
  }

  private static EntryEvidenceSet.Entry entry(
      HttpEntryPoint entry,
      JavaCodeIndex.EntryCollection collection,
      List<String> catalogFiles,
      PersistenceMaterialIndex persistenceIndex,
      FrontendHttpIndex frontendIndex,
      FrontendDisposition frontend) {
    String entryId = entry.entryId().value();
    FrontendSelection selection = frontend.byEntry().get(entryId);
    if (selection == null) {
      throw new IllegalArgumentException("ENTRY_EVIDENCE_FRONTEND_DENOMINATOR_MISMATCH");
    }
    if (collection.context() == null) {
      EntryEvidenceSet.Limitation limitation =
          new EntryEvidenceSet.Limitation("JAVA_ENTRY_NOT_COLLECTED", entryId, collection.reason());
      List<EntryEvidenceSet.Limitation> limitations = new ArrayList<>(selection.limitations());
      limitations.add(limitation);
      return new EntryEvidenceSet.Entry(
          entryId,
          entry,
          EntryEvidenceSet.AssemblyStatus.NOT_ASSEMBLED,
          new EntryEvidenceSet.Coverage(
              false, selection.status(), persistenceIndex.header().status()),
          selection.frontend(),
          emptyJava(),
          emptyPersistence(),
          sourceReferences(entry, null, selection.frontend(), emptyPersistence()),
          List.copyOf(limitations));
    }

    EntryCodeContext context = collection.context();
    if (!entry.methodKey().equals(context.entryMethodKey())) {
      throw new IllegalArgumentException("ENTRY_EVIDENCE_ENTRY_METHOD_MISMATCH");
    }
    WorkspaceSourceIdentityMapper identities = new WorkspaceSourceIdentityMapper(catalogFiles);
    List<EntryEvidenceSet.Limitation> limitations = new ArrayList<>(selection.limitations());
    context
        .limitations()
        .forEach(
            value ->
                limitations.add(
                    new EntryEvidenceSet.Limitation(
                        value.code(), entryId, identities.projectText(value.detail()))));
    List<EntryCodeContext.CallSite> calls =
        context.calls().stream().map(identities::projectCall).toList();
    List<EntryEvidenceSet.CallObservation> observations =
        callObservationIndex(context.calls(), identities);
    for (EntryCodeContext.CallSite call : calls) {
      if ("QUERY_FAILED".equals(call.resolution())
          || "NAVIGATION_CONFLICT".equals(call.resolution())) {
        limitations.add(
            new EntryEvidenceSet.Limitation(
                call.resolution(), call.callKey(), call.resolutionDetail()));
      }
    }
    EntryEvidenceSet.Persistence persistence =
        persistenceIndex.header().status() == PersistenceMaterialIndex.Status.DISABLED
            ? emptyPersistence()
            : persistenceFor(context.methods(), persistenceIndex);
    if (persistenceIndex.header().status() == PersistenceMaterialIndex.Status.DISABLED) {
      limitations.add(
          new EntryEvidenceSet.Limitation(
              "PERSISTENCE_ANALYSIS_DISABLED",
              entryId,
              "the saved R3 persistence analysis is disabled"));
    }
    if (identities.hasUnconfirmedWorkspaceIdentity()) {
      limitations.add(
          new EntryEvidenceSet.Limitation(
              "JDT_WORKSPACE_SOURCE_IDENTITY_UNCONFIRMED",
              entryId,
              "a JDT workspace source identity did not map uniquely to a saved catalog file"));
    }
    EntryEvidenceSet.AssemblyStatus status =
        limitations.isEmpty()
            ? EntryEvidenceSet.AssemblyStatus.ASSEMBLED
            : EntryEvidenceSet.AssemblyStatus.ASSEMBLED_WITH_LIMITATIONS;
    return new EntryEvidenceSet.Entry(
        entryId,
        entry,
        status,
        new EntryEvidenceSet.Coverage(true, selection.status(), persistenceIndex.header().status()),
        selection.frontend(),
        new EntryEvidenceSet.Java(
            context.methods(),
            List.copyOf(calls),
            observations,
            context.supportingSources(),
            context.technicalEnhancements()),
        persistence,
        sourceReferences(entry, context, selection.frontend(), persistence),
        List.copyOf(limitations));
  }

  /**
   * Adds a compact, entry-local lookup over the complete observations kept on each physical call.
   * The nested values remain the source of truth; this index intentionally does not duplicate their
   * ranges, bindings, or candidate identity.
   */
  private static List<EntryEvidenceSet.CallObservation> callObservationIndex(
      List<EntryCodeContext.CallSite> calls, WorkspaceSourceIdentityMapper identities) {
    List<EntryEvidenceSet.CallObservation> index = new ArrayList<>();
    for (EntryCodeContext.CallSite call : calls) {
      for (int ordinal = 0; ordinal < call.observations().size(); ordinal++) {
        EntryCodeContext.CallObservation observation = call.observations().get(ordinal);
        index.add(
            new EntryEvidenceSet.CallObservation(
                call.callKey() + "#observation-" + ordinal,
                call.callKey(),
                observation.code(),
                identities.projectText(observation.detail())));
      }
    }
    return List.copyOf(index);
  }

  /**
   * Projects only JDT's temporary workspace URI metadata, never method/source bodies or logical
   * identities. A source path becomes portable only when the saved catalog names it exactly once.
   */
  private static final class WorkspaceSourceIdentityMapper {
    private final Map<String, Integer> catalogPathCounts;
    private boolean unconfirmed;

    private WorkspaceSourceIdentityMapper(List<String> catalogFiles) {
      catalogPathCounts = new HashMap<>();
      for (String path : catalogFiles) {
        if (snapshotRelative(path)) {
          catalogPathCounts.merge(path, 1, Integer::sum);
        }
      }
    }

    private EntryCodeContext.CallSite projectCall(EntryCodeContext.CallSite call) {
      List<EntryCodeContext.CallTarget> targets =
          call.targets().stream().map(this::projectTarget).toList();
      List<EntryCodeContext.CallObservation> observations =
          projectObservations(call.observations());
      return new EntryCodeContext.CallSite(
          call.callKey(),
          call.callerMethodKey(),
          call.kind(),
          call.site(),
          call.navigationSite(),
          call.expression(),
          call.receiverExpression(),
          call.actualArguments(),
          call.enclosingControlIndexes(),
          call.deferred(),
          targets,
          call.resolution(),
          projectText(call.resolutionDetail()),
          observations);
    }

    /**
     * A temporary JDT workspace path can be the only distinguishing part of two observations on the
     * same physical call. Preserve both observations when their portable source identities would
     * otherwise collide, without retaining a machine path or inventing a source path.
     */
    private List<EntryCodeContext.CallObservation> projectObservations(
        List<EntryCodeContext.CallObservation> observations) {
      List<ProjectedCallObservation> projected =
          observations.stream().map(this::projectObservation).toList();
      Map<String, Integer> projectedIdentityCounts = new HashMap<>();
      for (ProjectedCallObservation observation : projected) {
        if (observation.value().displayIdentity() != null) {
          projectedIdentityCounts.merge(observation.value().displayIdentity(), 1, Integer::sum);
        }
      }

      List<EntryCodeContext.CallObservation> result = new ArrayList<>(projected.size());
      for (int ordinal = 0; ordinal < projected.size(); ordinal++) {
        ProjectedCallObservation observation = projected.get(ordinal);
        EntryCodeContext.CallObservation value = observation.value();
        if (observation.displayIdentityChanged()
            && projectedIdentityCounts.getOrDefault(value.displayIdentity(), 0) > 1) {
          result.add(withDisplayIdentity(value, workspaceObservationIdentity(ordinal)));
        } else {
          result.add(value);
        }
      }
      return List.copyOf(result);
    }

    private static EntryCodeContext.CallObservation withDisplayIdentity(
        EntryCodeContext.CallObservation observation, String displayIdentity) {
      return new EntryCodeContext.CallObservation(
          observation.code(),
          observation.operation(),
          observation.uriKind(),
          observation.sourceRange(),
          observation.association(),
          observation.declarationKey(),
          observation.declaringTypeKey(),
          observation.typeOrigin(),
          displayIdentity,
          observation.detail());
    }

    private static String workspaceObservationIdentity(int ordinal) {
      return "JDT_WORKSPACE_SOURCE_OBSERVATION_" + ordinal;
    }

    private EntryCodeContext.CallTarget projectTarget(EntryCodeContext.CallTarget target) {
      return new EntryCodeContext.CallTarget(
          target.methodKey(),
          target.roles(),
          projectText(target.displayName()),
          target.navigationKinds(),
          target.expansion(),
          target.reason(),
          target.argumentAssociations());
    }

    private ProjectedCallObservation projectObservation(
        EntryCodeContext.CallObservation observation) {
      String displayIdentity = projectText(observation.displayIdentity());
      String detail = projectText(observation.detail());
      if (displayIdentity != null
          && !Objects.equals(displayIdentity, observation.displayIdentity())
          && !detail.contains(displayIdentity)) {
        detail = detail + " at " + displayIdentity;
      }
      return new ProjectedCallObservation(
          new EntryCodeContext.CallObservation(
              observation.code(),
              observation.operation(),
              observation.uriKind(),
              observation.sourceRange(),
              observation.association(),
              observation.declarationKey(),
              observation.declaringTypeKey(),
              observation.typeOrigin(),
              displayIdentity,
              detail),
          !Objects.equals(displayIdentity, observation.displayIdentity()));
    }

    private String projectText(String value) {
      if (value == null) {
        return null;
      }
      Matcher workspaceUris = JDT_WORKSPACE_SOURCE_URI.matcher(value);
      StringBuffer projected = new StringBuffer();
      boolean foundWorkspaceUri = false;
      while (workspaceUris.find()) {
        foundWorkspaceUri = true;
        workspaceUris.appendReplacement(
            projected, Matcher.quoteReplacement(projectUri(workspaceUris.group())));
      }
      if (foundWorkspaceUri) {
        workspaceUris.appendTail(projected);
        value = projected.toString();
      }
      Matcher remainingFileUris = FILE_URI.matcher(value);
      StringBuffer redacted = new StringBuffer();
      boolean foundFileUri = false;
      while (remainingFileUris.find()) {
        foundFileUri = true;
        unconfirmed = true;
        remainingFileUris.appendReplacement(
            redacted, Matcher.quoteReplacement(UNCONFIRMED_WORKSPACE_SOURCE_IDENTITY));
      }
      if (foundFileUri) {
        remainingFileUris.appendTail(redacted);
        return redacted.toString();
      }
      return value;
    }

    private String projectUri(String workspaceUri) {
      int rangeStart = workspaceUri.lastIndexOf('#');
      if (rangeStart < 0) {
        return unconfirmed();
      }
      String sourceUri = workspaceUri.substring(0, rangeStart);
      Set<String> matches = new LinkedHashSet<>();
      int marker = sourceUri.indexOf("/projects/");
      while (marker >= 0) {
        String candidate = sourceUri.substring(marker + "/projects/".length());
        if (catalogPathCounts.getOrDefault(candidate, 0) == 1) {
          matches.add(candidate);
        }
        String generatedProjectSourcePath = sourcePathAfterGeneratedProject(candidate);
        if (generatedProjectSourcePath != null
            && catalogPathCounts.getOrDefault(generatedProjectSourcePath, 0) == 1) {
          matches.add(generatedProjectSourcePath);
        }
        marker = sourceUri.indexOf("/projects/", marker + 1);
      }
      if (matches.size() == 1) {
        return matches.iterator().next() + workspaceUri.substring(rangeStart);
      }
      return unconfirmed();
    }

    /**
     * The JDT workspace creates exactly one {@code source-analysis-<id>} project segment before the
     * customer repository path. Only remove that controlled segment; the remaining path must still
     * be an exact catalog key before {@link #projectUri(String)} accepts it.
     */
    private static String sourcePathAfterGeneratedProject(String candidate) {
      int separator = candidate.indexOf('/');
      if (separator < 1) {
        return null;
      }
      String projectSegment = candidate.substring(0, separator);
      if (!projectSegment.startsWith("source-analysis-")) {
        return null;
      }
      String identifier = projectSegment.substring("source-analysis-".length());
      if (identifier.isEmpty() || !identifier.chars().allMatch(Character::isLetterOrDigit)) {
        return null;
      }
      return candidate.substring(separator + 1);
    }

    private String unconfirmed() {
      unconfirmed = true;
      return UNCONFIRMED_WORKSPACE_SOURCE_IDENTITY;
    }

    private boolean hasUnconfirmedWorkspaceIdentity() {
      return unconfirmed;
    }

    private static boolean snapshotRelative(String path) {
      return path != null
          && !path.isBlank()
          && !path.startsWith("/")
          && !path.contains("\\")
          && !path.contains("..");
    }

    private record ProjectedCallObservation(
        EntryCodeContext.CallObservation value, boolean displayIdentityChanged) {}
  }

  private static List<EntryEvidenceSet.SourceReference> sourceReferences(
      HttpEntryPoint entry,
      EntryCodeContext context,
      EntryEvidenceSet.Frontend frontend,
      EntryEvidenceSet.Persistence persistence) {
    Map<String, EntryEvidenceSet.SourceReference> references = new LinkedHashMap<>();
    for (int index = 0; index < entry.routeSourceExcerpts().size(); index++) {
      SourceExcerptV1 excerpt = entry.routeSourceExcerpts().get(index);
      String reference = "http-route:" + index;
      references.put(
          reference,
          new EntryEvidenceSet.SourceReference(
              reference,
              "HTTP_ROUTE",
              excerpt.locator().path(),
              null,
              null,
              excerpt.locator().fileId().value()));
    }
    if (context != null) {
      for (EntryCodeContext.MethodCode method : context.methods()) {
        EntryCodeContext.SourceSource source = method.source();
        putReference(
            references,
            new EntryEvidenceSet.SourceReference(
                "java-method:" + method.methodKey(),
                "JAVA_METHOD",
                source.path(),
                new SourceRange(
                    source.startOffsetUtf16(),
                    source.lengthUtf16(),
                    source.startLine(),
                    source.endLine()),
                null,
                null));
      }
      for (int index = 0; index < context.supportingSources().size(); index++) {
        EntryCodeContext.SupportingSource supporting = context.supportingSources().get(index);
        EntryCodeContext.SourceSource source = supporting.source();
        putReference(
            references,
            new EntryEvidenceSet.SourceReference(
                "java-support:" + index,
                "JAVA_SUPPORT",
                source.path(),
                new SourceRange(
                    source.startOffsetUtf16(),
                    source.lengthUtf16(),
                    source.startLine(),
                    source.endLine()),
                null,
                null));
      }
    }
    for (FrontendSourceUnits.Unit unit : frontend.units()) {
      putReference(
          references,
          new EntryEvidenceSet.SourceReference(
              "frontend-unit:" + unit.sourceUnitId(),
              "FRONTEND_UNIT",
              unit.path(),
              unit.sourceUnitRange(),
              unit.sourceSha256(),
              null));
    }
    for (PersistenceMaterialIndex.Resource resource : persistence.resources()) {
      putReference(
          references,
          new EntryEvidenceSet.SourceReference(
              "mapper-resource:" + resource.resourcePath(),
              "MAPPER_RESOURCE",
              resource.resourcePath(),
              null,
              sha256(resource.rawSource()),
              null));
    }
    return references.values().stream()
        .sorted(Comparator.comparing(EntryEvidenceSet.SourceReference::reference, UTF8_ORDER))
        .toList();
  }

  private static void putReference(
      Map<String, EntryEvidenceSet.SourceReference> references,
      EntryEvidenceSet.SourceReference reference) {
    EntryEvidenceSet.SourceReference prior =
        references.putIfAbsent(reference.reference(), reference);
    if (prior != null && !prior.equals(reference)) {
      throw new IllegalArgumentException("ENTRY_EVIDENCE_SOURCE_REFERENCE_CONFLICT");
    }
  }

  private static String sha256(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              java.security.MessageDigest.getInstance("SHA-256")
                  .digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 unavailable", unavailable);
    }
  }

  private static EntryEvidenceSet.Java emptyJava() {
    return new EntryEvidenceSet.Java(
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        new EntryCodeContext.TechnicalEnhancements(
            EntryCodeContext.Availability.NOT_PRODUCED,
            "JAVA_ENTRY_NOT_COLLECTED",
            List.of(),
            List.of(),
            null));
  }

  private static EntryEvidenceSet.Persistence emptyPersistence() {
    return new EntryEvidenceSet.Persistence(List.of(), List.of(), List.of(), List.of(), List.of());
  }

  private static EntryEvidenceSet.Persistence persistenceFor(
      List<EntryCodeContext.MethodCode> methods, PersistenceMaterialIndex index) {
    Set<String> methodKeys =
        methods.stream()
            .map(EntryCodeContext.MethodCode::methodKey)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    List<PersistenceMaterialIndex.JavaBinding> bindings =
        index.bindings().stream()
            .filter(binding -> methodKeys.contains(binding.methodKey()))
            .sorted(
                Comparator.comparing(PersistenceMaterialIndex.JavaBinding::methodKey, UTF8_ORDER))
            .toList();
    Set<String> statementRefs = new LinkedHashSet<>();
    for (PersistenceMaterialIndex.JavaBinding binding : bindings) {
      binding.statementRefs().forEach(reference -> statementRefs.add(reference.statementRef()));
    }
    List<PersistenceMaterialIndex.Statement> statements =
        index.statements().stream()
            .filter(statement -> statementRefs.contains(statement.statementRef()))
            .sorted(
                Comparator.comparing(PersistenceMaterialIndex.Statement::statementRef, UTF8_ORDER))
            .toList();
    Set<String> directResourcePaths = new LinkedHashSet<>();
    statements.forEach(statement -> directResourcePaths.add(statement.resourceRef()));
    List<PersistenceMaterialIndex.Resource> resources =
        resourceClosure(directResourcePaths, index.resources());
    Set<String> resourcePaths =
        resources.stream()
            .map(PersistenceMaterialIndex.Resource::resourcePath)
            .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    List<PersistenceMaterialIndex.SqlAnalysis> analyses =
        index.sqlAnalyses().stream()
            .filter(analysis -> statementRefs.contains(analysis.statementRef()))
            .sorted(
                Comparator.comparing(
                    PersistenceMaterialIndex.SqlAnalysis::statementRef, UTF8_ORDER))
            .toList();
    List<PersistenceMaterialIndex.Diagnostic> diagnostics =
        index.diagnostics().stream()
            .filter(
                diagnostic ->
                    methodKeys.contains(diagnostic.subjectRef())
                        || statementRefs.contains(diagnostic.subjectRef())
                        || resourcePaths.contains(diagnostic.subjectRef()))
            .sorted(
                Comparator.comparing(
                    value ->
                        value.code() + "\u0000" + value.subjectRef() + "\u0000" + value.detail(),
                    UTF8_ORDER))
            .toList();
    return new EntryEvidenceSet.Persistence(bindings, statements, resources, analyses, diagnostics);
  }

  private static List<PersistenceMaterialIndex.Resource> resourceClosure(
      Set<String> directResourcePaths, List<PersistenceMaterialIndex.Resource> available) {
    Map<String, PersistenceMaterialIndex.Resource> byPath = new HashMap<>();
    for (PersistenceMaterialIndex.Resource resource : available) {
      if (byPath.putIfAbsent(resource.resourcePath(), resource) != null) {
        throw new IllegalArgumentException("ENTRY_EVIDENCE_DUPLICATE_PERSISTENCE_RESOURCE");
      }
    }
    Set<String> selected = new LinkedHashSet<>();
    List<String> pending = new ArrayList<>(directResourcePaths);
    for (int index = 0; index < pending.size(); index++) {
      String path = pending.get(index);
      if (!selected.add(path)) {
        continue;
      }
      PersistenceMaterialIndex.Resource resource = byPath.get(path);
      if (resource == null) {
        throw new IllegalArgumentException("ENTRY_EVIDENCE_RESOURCE_CLOSURE_MISSING: " + path);
      }
      resource.dependencyResourcePaths().stream()
          .filter(dependency -> !selected.contains(dependency) && !pending.contains(dependency))
          .sorted(UTF8_ORDER)
          .forEach(pending::add);
    }
    return selected.stream()
        .map(byPath::get)
        .sorted(Comparator.comparing(PersistenceMaterialIndex.Resource::resourcePath, UTF8_ORDER))
        .toList();
  }

  private static FrontendDisposition frontendDisposition(
      FrontendHttpIndex index,
      FrontendSourceUnits sourceUnits,
      List<HttpEntryPoint> entries,
      List<EntryEvidenceHttpMapping> mappings) {
    Map<String, FrontendSelection> selections = new LinkedHashMap<>();
    Map<String, FrontendPageContext> pageContextsById = new LinkedHashMap<>();
    for (FrontendPageContext context : index.pageContexts()) {
      if (pageContextsById.putIfAbsent(context.contextId(), context) != null) {
        throw new IllegalArgumentException("ENTRY_EVIDENCE_DUPLICATE_PAGE_CONTEXT");
      }
    }
    for (HttpEntryPoint entry : entries) {
      selections.put(
          entry.entryId().value(),
          new FrontendSelection(
              new ArrayList<>(),
              new ArrayList<>(),
              new LinkedHashMap<>(),
              new LinkedHashMap<>(),
              new ArrayList<>()));
    }
    if (index.status() == FrontendHttpIndex.Status.DISABLED) {
      for (FrontendSelection selection : selections.values()) {
        selection
            .limitations()
            .add(
                new EntryEvidenceSet.Limitation(
                    "FRONTEND_DISCOVERY_DISABLED",
                    "frontend",
                    "frontend discovery was explicitly disabled for this R1"));
      }
      return new FrontendDisposition(
          finalizeSelections(selections, EntryEvidenceSet.FrontendStatus.DISABLED),
          List.of(),
          List.of());
    }

    List<EntryEvidenceSet.FrontendCoverage> coverage = new ArrayList<>();
    for (FrontendHttpRequestRecord request :
        index.requests().stream()
            .sorted(Comparator.comparing(FrontendHttpRequestRecord::requestId, UTF8_ORDER))
            .toList()) {
      Match match = match(request, entries, mappings);
      ResolvedUnits units = sourceUnits(request, sourceUnits);
      List<String> candidateIds =
          match.entries().stream().map(value -> value.entryId().value()).toList();
      String reason =
          units.reason() == null ? match.reason() : joinReasons(match.reason(), units.reason());
      List<String> sourceUnitIds =
          units.units().stream().map(FrontendSourceUnits.Unit::sourceUnitId).toList();
      List<String> pageContextIds =
          index.pageContexts().stream()
              .filter(context -> context.requestIds().contains(request.requestId()))
              .map(FrontendPageContext::contextId)
              .sorted(UTF8_ORDER)
              .toList();
      EntryEvidenceSet.RequestUse use =
          new EntryEvidenceSet.RequestUse(
              request, match.resolution(), candidateIds, sourceUnitIds, pageContextIds, reason);
      List<String> included = new ArrayList<>();
      if (match.resolution() == FrontendEntryLinkRecord.Resolution.MATCHED_UNIQUE
          && reason == null) {
        String entryId = candidateIds.get(0);
        FrontendSelection selection = selections.get(entryId);
        selection.uses().add(use);
        addPageContexts(selection, pageContextIds, pageContextsById, sourceUnits);
        units.units().forEach(unit -> selection.units().putIfAbsent(unit.sourceUnitId(), unit));
        included.add(entryId);
      } else if (match.resolution() == FrontendEntryLinkRecord.Resolution.MATCHED_MULTIPLE
          || (match.resolution() == FrontendEntryLinkRecord.Resolution.UNRESOLVED_REQUEST
              && !candidateIds.isEmpty())) {
        for (String entryId : candidateIds) {
          FrontendSelection selection = selections.get(entryId);
          selection.candidateUses().add(use);
          addPageContexts(selection, pageContextIds, pageContextsById, sourceUnits);
          units.units().forEach(unit -> selection.units().putIfAbsent(unit.sourceUnitId(), unit));
          selection
              .limitations()
              .add(
                  new EntryEvidenceSet.Limitation(
                      "FRONTEND_REQUEST_" + match.resolution().name(),
                      request.requestId(),
                      nonBlank(reason)));
        }
      } else if (reason != null
          && match.resolution() == FrontendEntryLinkRecord.Resolution.MATCHED_UNIQUE) {
        String entryId = candidateIds.get(0);
        FrontendSelection selection = selections.get(entryId);
        selection.candidateUses().add(use);
        addPageContexts(selection, pageContextIds, pageContextsById, sourceUnits);
        units.units().forEach(unit -> selection.units().putIfAbsent(unit.sourceUnitId(), unit));
        selection
            .limitations()
            .add(
                new EntryEvidenceSet.Limitation(
                    "FRONTEND_SOURCE_UNITS_INCOMPLETE", request.requestId(), reason));
      }
      coverage.add(
          new EntryEvidenceSet.FrontendCoverage(
              request.requestId(),
              request,
              match.resolution(),
              candidateIds,
              included,
              units.units(),
              pageContextIds,
              reason));
    }
    return new FrontendDisposition(
        finalizeSelections(selections, null),
        List.copyOf(coverage),
        pageContextCoverage(index, sourceUnits, selections));
  }

  private static List<EntryEvidenceSet.FrontendPageContextCoverage> pageContextCoverage(
      FrontendHttpIndex index,
      FrontendSourceUnits sourceUnits,
      Map<String, FrontendSelection> selections) {
    return index.pageContexts().stream()
        .sorted(Comparator.comparing(FrontendPageContext::contextId, UTF8_ORDER))
        .map(
            context -> {
              List<FrontendSourceUnits.Unit> units = pageContextSourceUnits(context, sourceUnits);
              List<String> includedEntryIds =
                  selections.entrySet().stream()
                      .filter(
                          entry -> entry.getValue().pageContexts().containsKey(context.contextId()))
                      .map(Map.Entry::getKey)
                      .sorted(UTF8_ORDER)
                      .toList();
              boolean requestMembership = !context.requestIds().isEmpty();
              return new EntryEvidenceSet.FrontendPageContextCoverage(
                  context.contextId(),
                  context,
                  units,
                  includedEntryIds,
                  requestMembership
                      ? EntryEvidenceSet.FrontendPageContextCoverage.Disposition.REQUEST_MEMBERSHIP
                      : EntryEvidenceSet.FrontendPageContextCoverage.Disposition
                          .NO_REQUEST_MEMBERSHIP,
                  requestMembership ? null : "no saved HTTP request member");
            })
        .toList();
  }

  private static List<FrontendSourceUnits.Unit> pageContextSourceUnits(
      FrontendPageContext context, FrontendSourceUnits available) {
    Map<String, FrontendSourceUnits.Unit> units = new LinkedHashMap<>();
    for (FrontendPageSourceUnit contextUnit : context.sourceUnits()) {
      FrontendSourceUnits.Unit unit =
          uniqueExact(
              available.units(),
              contextUnit.sourcePath(),
              contextUnit.sourceSha256(),
              contextUnit.sourceUnitRange(),
              contextUnit.sourceUnitKind());
      if (unit == null) {
        throw new IllegalArgumentException(
            "ENTRY_EVIDENCE_PAGE_CONTEXT_SOURCE_UNIT_UNAVAILABLE: " + context.contextId());
      }
      units.putIfAbsent(unit.sourceUnitId(), unit);
    }
    return units.values().stream()
        .sorted(Comparator.comparing(FrontendSourceUnits.Unit::sourceUnitId, UTF8_ORDER))
        .toList();
  }

  private static Map<String, FrontendSelection> finalizeSelections(
      Map<String, FrontendSelection> selections, EntryEvidenceSet.FrontendStatus forced) {
    for (FrontendSelection selection : selections.values()) {
      selection.forcedStatus = forced;
    }
    return selections;
  }

  private static void addPageContexts(
      FrontendSelection selection,
      List<String> contextIds,
      Map<String, FrontendPageContext> pageContextsById,
      FrontendSourceUnits sourceUnits) {
    for (String contextId : contextIds) {
      FrontendPageContext context = pageContextsById.get(contextId);
      if (context == null) {
        throw new IllegalArgumentException("ENTRY_EVIDENCE_PAGE_CONTEXT_MISSING");
      }
      FrontendPageContext prior = selection.pageContexts().putIfAbsent(contextId, context);
      if (prior != null && !prior.equals(context)) {
        throw new IllegalArgumentException("ENTRY_EVIDENCE_PAGE_CONTEXT_CONFLICT");
      }
      for (FrontendSourceUnits.Unit unit : pageContextSourceUnits(context, sourceUnits)) {
        FrontendSourceUnits.Unit previous =
            selection.units().putIfAbsent(unit.sourceUnitId(), unit);
        if (previous != null && !previous.equals(unit)) {
          throw new IllegalArgumentException("ENTRY_EVIDENCE_PAGE_CONTEXT_UNIT_CONFLICT");
        }
      }
    }
  }

  private static Match match(
      FrontendHttpRequestRecord request,
      List<HttpEntryPoint> entries,
      List<EntryEvidenceHttpMapping> mappings) {
    if (request.resolvedPath() == null) {
      return new Match(
          FrontendEntryLinkRecord.Resolution.UNRESOLVED_REQUEST,
          List.of(),
          "request path is not statically resolved");
    }
    MappedPath mapped = mappedPath(request, mappings);
    if (mapped.path() == null) {
      return new Match(
          FrontendEntryLinkRecord.Resolution.UNRESOLVED_REQUEST, List.of(), mapped.reason());
    }
    List<HttpEntryPoint> candidates =
        entries.stream()
            .filter(entry -> entry.route().equals(mapped.path()))
            .filter(entry -> accepts(entry.methodCondition(), request.httpMethod()))
            .toList();
    if (candidates.isEmpty()) {
      return new Match(
          FrontendEntryLinkRecord.Resolution.NO_MATCH,
          List.of(),
          "no saved HTTP entry matches path and method");
    }
    if (mapped.reason() != null) {
      return new Match(
          FrontendEntryLinkRecord.Resolution.UNRESOLVED_REQUEST, candidates, mapped.reason());
    }
    if (candidates.size() > 1) {
      return new Match(
          FrontendEntryLinkRecord.Resolution.MATCHED_MULTIPLE,
          candidates,
          "multiple saved HTTP entries match path and method");
    }
    HttpEntryPoint candidate = candidates.get(0);
    if (hasUnsupportedRouteCondition(candidate)) {
      return new Match(
          FrontendEntryLinkRecord.Resolution.UNRESOLVED_REQUEST,
          candidates,
          "the saved HTTP entry has params, headers, or consumes conditions not represented by the"
              + " request observation");
    }
    return new Match(FrontendEntryLinkRecord.Resolution.MATCHED_UNIQUE, candidates, null);
  }

  private static MappedPath mappedPath(
      FrontendHttpRequestRecord request, List<EntryEvidenceHttpMapping> mappings) {
    List<EntryEvidenceHttpMapping> applicable =
        mappings.stream().filter(mapping -> applies(mapping, request)).toList();
    if (applicable.isEmpty()) {
      if (request.baseUrlExpression() != null) {
        return new MappedPath(
            request.resolvedPath(),
            "the saved request has a runtime base URL expression and no explicit HTTP address"
                + " mapping");
      }
      return new MappedPath(request.resolvedPath(), null);
    }
    if (applicable.size() != 1) {
      return new MappedPath(
          null, "multiple configured HTTP address mappings apply to this request");
    }
    EntryEvidenceHttpMapping mapping = applicable.get(0);
    String path = request.resolvedPath();
    if (mapping.stripPrefix() != null) {
      if (!path.startsWith(mapping.stripPrefix())) {
        return new MappedPath(
            null, "configured HTTP stripPrefix does not match the saved request path");
      }
      path = path.substring(mapping.stripPrefix().length());
      if (path.isEmpty()) path = "/";
    }
    if (mapping.addPrefix() != null) path = joinPath(mapping.addPrefix(), path);
    if (mapping.backendContextPath() != null) path = joinPath(mapping.backendContextPath(), path);
    // R2 currently preserves an application-profile artifact identity, not the configured
    // free-text backendApplicationRef. Do not let an otherwise matching URL turn that unchecked
    // deployment assertion into a confirmed frontend-to-controller edge.
    return new MappedPath(
        path,
        "configured backend application reference '"
            + mapping.backendApplicationRef()
            + "' is not verifiable from the saved R2 application identity");
  }

  private static boolean applies(
      EntryEvidenceHttpMapping mapping, FrontendHttpRequestRecord request) {
    return (mapping.requestOrigin() == null
            || mapping.requestOrigin().equals(request.requestOrigin()))
        && (mapping.requestPathPrefix() == null
            || request.resolvedPath().startsWith(mapping.requestPathPrefix()));
  }

  private static String joinPath(String prefix, String path) {
    String normalizedPrefix =
        prefix.endsWith("/") && prefix.length() > 1
            ? prefix.substring(0, prefix.length() - 1)
            : prefix;
    String normalizedPath = path.startsWith("/") ? path : "/" + path;
    return "/".equals(normalizedPrefix) ? normalizedPath : normalizedPrefix + normalizedPath;
  }

  private static boolean accepts(HttpMethodCondition condition, String method) {
    return condition.kind() == HttpMethodCondition.Kind.UNRESTRICTED
        || condition.methods().contains(method);
  }

  private static boolean hasUnsupportedRouteCondition(HttpEntryPoint entry) {
    for (SourceExcerptV1 excerpt : entry.routeSourceExcerpts()) {
      String source = new String(excerpt.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      if (UNSUPPORTED_SPRING_CONDITION.matcher(source).find()) {
        return true;
      }
    }
    return false;
  }

  private static ResolvedUnits sourceUnits(
      FrontendHttpRequestRecord request, FrontendSourceUnits available) {
    Map<String, FrontendSourceUnits.Unit> selected = new LinkedHashMap<>();
    List<String> missing = new ArrayList<>();
    FrontendSourceUnits.Unit page =
        uniqueContaining(
            available.units(),
            request.pagePath(),
            request.sourceSha256(),
            request.callRange(),
            null);
    if (page != null) {
      selected.put(page.sourceUnitId(), page);
    }
    for (FrontendWrapperCall wrapper : request.wrapperPath()) {
      FrontendSourceUnits.Unit unit =
          uniqueExact(
              available.units(),
              wrapper.sourcePath(),
              wrapper.sourceSha256(),
              wrapper.sourceUnitRange(),
              wrapper.sourceUnitKind());
      if (unit == null) missing.add("wrapper:" + wrapper.sourcePath());
      else selected.put(unit.sourceUnitId(), unit);
    }
    for (FrontendSupportingSourceUnit supporting : request.supportingSourceUnits()) {
      FrontendSourceUnits.Unit unit =
          uniqueExact(
              available.units(),
              supporting.sourcePath(),
              supporting.sourceSha256(),
              supporting.sourceUnitRange(),
              supporting.sourceUnitKind());
      if (unit == null) missing.add("supporting:" + supporting.sourcePath());
      else selected.put(unit.sourceUnitId(), unit);
    }
    if (page == null && request.wrapperPath().isEmpty()) {
      missing.add("request:" + request.pagePath());
    }
    List<FrontendSourceUnits.Unit> units =
        selected.values().stream()
            .sorted(Comparator.comparing(FrontendSourceUnits.Unit::sourceUnitId, UTF8_ORDER))
            .toList();
    return new ResolvedUnits(
        units,
        missing.isEmpty()
            ? null
            : "saved R0 frontend source unit is unavailable: " + String.join(",", missing));
  }

  private static FrontendSourceUnits.Unit uniqueContaining(
      List<FrontendSourceUnits.Unit> units,
      String path,
      String sha256,
      SourceRange range,
      FrontendWrapperCall.SourceUnitKind kind) {
    List<FrontendSourceUnits.Unit> matches =
        units.stream()
            .filter(unit -> path.equals(unit.path()) && sha256.equals(unit.sourceSha256()))
            .filter(unit -> kind == null || kind == unit.sourceUnitKind())
            .filter(unit -> contains(unit.sourceUnitRange(), range))
            .toList();
    return matches.size() == 1 ? matches.get(0) : null;
  }

  private static FrontendSourceUnits.Unit uniqueExact(
      List<FrontendSourceUnits.Unit> units,
      String path,
      String sha256,
      SourceRange range,
      FrontendWrapperCall.SourceUnitKind kind) {
    List<FrontendSourceUnits.Unit> matches =
        units.stream()
            .filter(unit -> path.equals(unit.path()) && sha256.equals(unit.sourceSha256()))
            .filter(unit -> range.equals(unit.sourceUnitRange()) && kind == unit.sourceUnitKind())
            .toList();
    return matches.size() == 1 ? matches.get(0) : null;
  }

  private static boolean contains(SourceRange outer, SourceRange inner) {
    long outerEnd = (long) outer.startOffsetUtf16() + outer.lengthUtf16();
    long innerEnd = (long) inner.startOffsetUtf16() + inner.lengthUtf16();
    return outer.startOffsetUtf16() <= inner.startOffsetUtf16() && innerEnd <= outerEnd;
  }

  private static String joinReasons(String first, String second) {
    if (first == null) return second;
    if (second == null) return first;
    return first + "; " + second;
  }

  private static String nonBlank(String value) {
    return value == null ? "saved frontend match is not confirmed" : value;
  }

  private static int compareUtf8(String first, String second) {
    return java.util.Arrays.compareUnsigned(
        first.getBytes(StandardCharsets.UTF_8), second.getBytes(StandardCharsets.UTF_8));
  }

  private record Match(
      FrontendEntryLinkRecord.Resolution resolution, List<HttpEntryPoint> entries, String reason) {}

  private record MappedPath(String path, String reason) {}

  private record ResolvedUnits(List<FrontendSourceUnits.Unit> units, String reason) {}

  /** A configured entry-count bound that callers can report without installing a partial set. */
  public static final class EntryLimitExceededException extends IllegalArgumentException {
    private final int limit;
    private final int actual;

    private EntryLimitExceededException(int limit, int actual) {
      super("ENTRY_EVIDENCE_ENTRY_COUNT_LIMIT_EXCEEDED");
      this.limit = limit;
      this.actual = actual;
    }

    public int limit() {
      return limit;
    }

    public int actual() {
      return actual;
    }
  }

  private static final class FrontendSelection {
    private final List<EntryEvidenceSet.RequestUse> uses;
    private final List<EntryEvidenceSet.RequestUse> candidateUses;
    private final Map<String, FrontendSourceUnits.Unit> units;
    private final Map<String, FrontendPageContext> pageContexts;
    private final List<EntryEvidenceSet.Limitation> limitations;
    private EntryEvidenceSet.FrontendStatus forcedStatus;

    private FrontendSelection(
        List<EntryEvidenceSet.RequestUse> uses,
        List<EntryEvidenceSet.RequestUse> candidateUses,
        Map<String, FrontendSourceUnits.Unit> units,
        Map<String, FrontendPageContext> pageContexts,
        List<EntryEvidenceSet.Limitation> limitations) {
      this.uses = uses;
      this.candidateUses = candidateUses;
      this.units = units;
      this.pageContexts = pageContexts;
      this.limitations = limitations;
    }

    private List<EntryEvidenceSet.RequestUse> uses() {
      return uses;
    }

    private List<EntryEvidenceSet.RequestUse> candidateUses() {
      return candidateUses;
    }

    private Map<String, FrontendSourceUnits.Unit> units() {
      return units;
    }

    private Map<String, FrontendPageContext> pageContexts() {
      return pageContexts;
    }

    private List<EntryEvidenceSet.Limitation> limitations() {
      return limitations;
    }

    private EntryEvidenceSet.FrontendStatus status() {
      if (forcedStatus != null) return forcedStatus;
      if (!uses.isEmpty()) return EntryEvidenceSet.FrontendStatus.MATCHED;
      if (!candidateUses.isEmpty() || !limitations.isEmpty()) {
        return EntryEvidenceSet.FrontendStatus.MATCHING_LIMITATIONS;
      }
      return EntryEvidenceSet.FrontendStatus.NO_MATCHED_REQUEST;
    }

    private EntryEvidenceSet.Frontend frontend() {
      return new EntryEvidenceSet.Frontend(
          List.copyOf(uses),
          List.copyOf(candidateUses),
          units.values().stream()
              .sorted(Comparator.comparing(FrontendSourceUnits.Unit::sourceUnitId, UTF8_ORDER))
              .toList(),
          pageContexts.values().stream()
              .sorted(Comparator.comparing(FrontendPageContext::contextId, UTF8_ORDER))
              .toList());
    }
  }

  private record FrontendDisposition(
      Map<String, FrontendSelection> byEntry,
      List<EntryEvidenceSet.FrontendCoverage> coverage,
      List<EntryEvidenceSet.FrontendPageContextCoverage> pageContextCoverage) {}
}
