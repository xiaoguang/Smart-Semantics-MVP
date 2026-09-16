package org.sourceanalysis.app.adapter.cli;

import static org.sourceanalysis.app.adapter.cli.SourceAnalysisExecution.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.analysis.discovery.DiscoveryProfile;
import org.sourceanalysis.app.analysis.flow.capsule.CapsuleProjectionProfile;
import org.sourceanalysis.app.analysis.flow.compiler.FlowCompilationProfile;
import org.sourceanalysis.app.analysis.interpretation.activity.ActivityExplanationProfile;
import org.sourceanalysis.app.analysis.interpretation.material.BusinessMaterialProfile;
import org.sourceanalysis.app.analysis.inventory.ProfileView;
import org.sourceanalysis.app.analysis.knowledge.ProcessDiscoveryProfile;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ArtifactStoreLimits;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicyRegistry;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.EffectiveEngineConfiguration;
import org.sourceanalysis.app.runtime.EngineConfigurationLoader;
import org.sourceanalysis.app.runtime.PersistedTechnicalRunConfiguration;

/** Configuration and model-service declarations consumed by the configured execution service. */
enum ModelProviderKind {
  CODEX_SUBSCRIPTION,
  OPENAI_API
}

record ModelJobsConfiguration(
    int maxConcurrentJobs,
    Map<String, ModelJobProviderConfiguration> providers,
    Map<String, List<String>> routing,
    Path journalDirectory,
    Path outputDirectory,
    String canonicalSha256) {

  static final Set<String> ROUTES =
      Set.of("activity", "processGroup", "repositorySummary", "report");

  ModelJobsConfiguration {
    providers = Map.copyOf(providers);
    routing = Map.copyOf(routing);
  }

  static ModelJobsConfiguration load(ObjectNode document, CanonicalJsonCodec canonicalJson) {
    requireFieldsAllowingOptional(
        document,
        Set.of("providers", "routing"),
        Set.of("journalDirectory", "maxConcurrentJobs", "outputDirectory"));
    int globalCap =
        document.has("maxConcurrentJobs") ? positiveInt(document, "maxConcurrentJobs") : 4;
    Path journalDirectory = optionalAbsolutePath(document, "journalDirectory");
    Path outputDirectory = optionalAbsolutePath(document, "outputDirectory");
    if ((journalDirectory == null) != (outputDirectory == null)) {
      throw failure("CONFIGURATION_INVALID");
    }

    ObjectNode providersNode = object(document, "providers");
    if (providersNode.isEmpty()) {
      throw failure("CONFIGURATION_INVALID");
    }
    Map<String, ModelJobProviderConfiguration> providers = new LinkedHashMap<>();
    Set<String> quotaScopes = new java.util.HashSet<>();
    for (java.util.Iterator<Map.Entry<String, JsonNode>> entries = providersNode.fields();
        entries.hasNext(); ) {
      Map.Entry<String, JsonNode> entry = entries.next();
      String providerKey = entry.getKey();
      if (!providerKey.matches("[a-z][a-z0-9-]{0,47}")
          || !(entry.getValue() instanceof ObjectNode value)) {
        throw failure("CONFIGURATION_INVALID");
      }
      ModelJobProviderConfiguration provider = ModelJobProviderConfiguration.load(value);
      if (providers.put(providerKey, provider) != null || !quotaScopes.add(provider.quotaScope())) {
        throw failure("CONFIGURATION_INVALID");
      }
    }

    ObjectNode routingNode = object(document, "routing");
    requireFields(routingNode, ROUTES);
    Map<String, List<String>> routing = new LinkedHashMap<>();
    for (String route : List.of("activity", "processGroup", "repositorySummary", "report")) {
      JsonNode configured = routingNode.get(route);
      if (!(configured instanceof ArrayNode configuredProviders) || configuredProviders.isEmpty()) {
        throw failure("CONFIGURATION_INVALID");
      }
      List<String> providerKeys = new java.util.ArrayList<>(configuredProviders.size());
      for (JsonNode providerKey : configuredProviders) {
        if (!providerKey.isTextual()
            || providerKey.textValue().isBlank()
            || !providers.containsKey(providerKey.textValue())
            || providerKeys.contains(providerKey.textValue())) {
          throw failure("CONFIGURATION_INVALID");
        }
        providerKeys.add(providerKey.textValue());
      }
      routing.put(route, List.copyOf(providerKeys));
    }

    ObjectNode normalized =
        normalizedNonSecretDocument(
            globalCap, journalDirectory, outputDirectory, providers, routing);
    return new ModelJobsConfiguration(
        globalCap,
        providers,
        routing,
        journalDirectory,
        outputDirectory,
        sha256(canonicalJson.encodeCanonical(normalized).copyToByteArray()));
  }

  ModelJobProviderConfiguration provider(String key) {
    ModelJobProviderConfiguration provider = providers.get(key);
    if (provider == null) {
      throw failure("CONFIGURATION_INVALID");
    }
    return provider;
  }

  void validateExecutionEnvironment() {
    requireExistingDirectory(journalDirectory, "CONFIGURATION_INVALID");
    requireExistingDirectory(outputDirectory, "CONFIGURATION_INVALID");
    Set<String> resolvedCredentials = new java.util.HashSet<>();
    for (ModelJobProviderConfiguration provider : providers.values()) {
      provider.validateExecutionEnvironment();
      for (String credential : provider.authentication().resolvedCredentialIdentities()) {
        if (!resolvedCredentials.add(credential)) {
          throw failure("CONFIGURATION_INVALID");
        }
      }
    }
  }

  ModelJobProviderConfiguration requireCurrentSerialCodexProvider() {
    return provider(requireCurrentSerialCodexProviderKey());
  }

  String requireCurrentSerialCodexProviderKey() {
    List<String> activityRoute = routing.get("activity");
    if (activityRoute == null || activityRoute.size() != 1) {
      throw failure("MODEL_ROUTING_UNSUPPORTED");
    }
    String providerKey = activityRoute.get(0);
    for (String route : List.of("processGroup", "repositorySummary", "report")) {
      if (!List.of(providerKey).equals(routing.get(route))) {
        throw failure("MODEL_ROUTING_UNSUPPORTED");
      }
    }
    ModelJobProviderConfiguration configuration = provider(providerKey);
    if (configuration.kind() != ModelProviderKind.CODEX_SUBSCRIPTION) {
      throw failure("MODEL_PROVIDER_UNSUPPORTED");
    }
    return providerKey;
  }

  ObjectNode normalizedNonSecretDocument() {
    return normalizedNonSecretDocument(
        maxConcurrentJobs, journalDirectory, outputDirectory, providers, routing);
  }

  static ObjectNode normalizedNonSecretDocument(
      int globalCap,
      Path journalDirectory,
      Path outputDirectory,
      Map<String, ModelJobProviderConfiguration> providers,
      Map<String, List<String>> routing) {
    ObjectNode normalized = JsonNodeFactory.instance.objectNode();
    normalized.put("maxConcurrentJobs", globalCap);
    if (journalDirectory == null) {
      normalized.putNull("journalDirectory");
    } else {
      normalized.put("journalDirectory", journalDirectory.toString());
    }
    if (outputDirectory == null) {
      normalized.putNull("outputDirectory");
    } else {
      normalized.put("outputDirectory", outputDirectory.toString());
    }
    ObjectNode normalizedProviders = normalized.putObject("providers");
    providers.entrySet().stream()
        .sorted(Map.Entry.comparingByKey())
        .forEach(
            entry ->
                normalizedProviders.set(
                    entry.getKey(), entry.getValue().normalizedNonSecretNode()));
    ObjectNode normalizedRouting = normalized.putObject("routing");
    for (String route : List.of("activity", "processGroup", "repositorySummary", "report")) {
      ArrayNode values = normalizedRouting.putArray(route);
      routing.get(route).forEach(values::add);
    }
    return normalized;
  }
}

record ModelJobProviderConfiguration(
    ModelProviderKind kind,
    String quotaScope,
    int maxConcurrentJobs,
    String model,
    String reasoningEffort,
    Duration timeout,
    Path executable,
    String endpoint,
    ModelJobAuthentication authentication) {

  static final Set<String> SUPPORTED_REASONING_EFFORTS =
      Set.of("none", "minimal", "low", "medium", "high", "xhigh", "max", "ultra");
  static final String MODEL_PATTERN = "[A-Za-z0-9][A-Za-z0-9._:-]{0,127}";

  static ModelJobProviderConfiguration load(ObjectNode document) {
    String kind = requiredText(document, "kind");
    return switch (kind) {
      case "codexSubscription" -> loadCodex(document);
      case "openaiApi" -> loadOpenAi(document);
      default -> throw failure("CONFIGURATION_INVALID");
    };
  }

  static ModelJobProviderConfiguration loadCodex(ObjectNode document) {
    requireFieldsAllowingOptional(
        document,
        Set.of("auth", "kind", "quotaScope"),
        Set.of("executable", "maxConcurrentJobs", "model", "reasoningEffort", "timeoutSeconds"));
    ModelJobAuthentication authentication =
        ModelJobAuthentication.loadCodex(object(document, "auth"));
    String model = document.has("model") ? requiredText(document, "model") : "gpt-5.6-luna";
    String reasoningEffort =
        document.has("reasoningEffort") ? requiredText(document, "reasoningEffort") : "high";
    requireSupportedModelDeclaration(model, reasoningEffort);
    return new ModelJobProviderConfiguration(
        ModelProviderKind.CODEX_SUBSCRIPTION,
        quotaScope(document),
        document.has("maxConcurrentJobs") ? positiveInt(document, "maxConcurrentJobs") : 4,
        model,
        reasoningEffort,
        timeout(document),
        document.has("executable")
            ? absolutePath(requiredText(document, "executable"), "Codex executable")
            : null,
        null,
        authentication);
  }

  static ModelJobProviderConfiguration loadOpenAi(ObjectNode document) {
    requireFieldsAllowingOptional(
        document,
        Set.of("auth", "kind", "maxConcurrentJobs", "model", "quotaScope", "reasoningEffort"),
        Set.of("endpoint", "timeoutSeconds"));
    String endpoint =
        document.has("endpoint") ? requiredText(document, "endpoint") : "https://api.openai.com/v1";
    requireSupportedHttpsEndpoint(endpoint);
    String model = requiredText(document, "model");
    String reasoningEffort = requiredText(document, "reasoningEffort");
    requireSupportedModelDeclaration(model, reasoningEffort);
    return new ModelJobProviderConfiguration(
        ModelProviderKind.OPENAI_API,
        quotaScope(document),
        positiveInt(document, "maxConcurrentJobs"),
        model,
        reasoningEffort,
        timeout(document),
        null,
        endpoint,
        ModelJobAuthentication.loadApi(object(document, "auth")));
  }

  static String quotaScope(ObjectNode document) {
    String scope = requiredText(document, "quotaScope");
    if (scope.length() > 256) {
      throw failure("CONFIGURATION_INVALID");
    }
    return scope;
  }

  static Duration timeout(ObjectNode document) {
    return Duration.ofSeconds(
        document.has("timeoutSeconds") ? positiveInt(document, "timeoutSeconds") : 600);
  }

  static void requireSupportedModelDeclaration(String model, String reasoningEffort) {
    if (!model.matches(MODEL_PATTERN) || !SUPPORTED_REASONING_EFFORTS.contains(reasoningEffort)) {
      throw failure("CONFIGURATION_INVALID");
    }
  }

  void validateExecutionEnvironment() {
    authentication.validateEnvironment();
    if (kind == ModelProviderKind.CODEX_SUBSCRIPTION) {
      try {
        if (executable == null
            || !Files.isRegularFile(executable, LinkOption.NOFOLLOW_LINKS)
            || Files.isSymbolicLink(executable)
            || !Files.isExecutable(executable)) {
          throw failure("CONFIGURATION_INVALID");
        }
      } catch (SecurityException failure) {
        throw failure("CONFIGURATION_INVALID", failure);
      }
    }
  }

  ObjectNode normalizedNonSecretNode() {
    ObjectNode normalized = JsonNodeFactory.instance.objectNode();
    normalized.put(
        "kind", kind == ModelProviderKind.CODEX_SUBSCRIPTION ? "codexSubscription" : "openaiApi");
    normalized.put("quotaScope", quotaScope);
    normalized.put("maxConcurrentJobs", maxConcurrentJobs);
    normalized.put("model", model);
    normalized.put("reasoningEffort", reasoningEffort);
    normalized.put("timeoutSeconds", timeout.toSeconds());
    if (kind == ModelProviderKind.CODEX_SUBSCRIPTION) {
      if (executable == null) {
        normalized.putNull("executable");
      } else {
        normalized.put("executable", executable.toString());
      }
    } else {
      normalized.put("endpoint", endpoint);
    }
    normalized.set("auth", authentication.identityNode());
    return normalized;
  }
}

record ModelJobAuthentication(String mode, List<String> environmentNames) {

  static ModelJobAuthentication loadCodex(ObjectNode document) {
    requireFields(document, Set.of("codexHomeEnv", "mode"));
    requireText(document, "mode", "chatgpt");
    return new ModelJobAuthentication(
        "chatgpt", List.of(environmentName(document, "codexHomeEnv")));
  }

  static ModelJobAuthentication loadApi(ObjectNode document) {
    requireFields(document, Set.of("apiKeyEnvs", "mode"));
    requireText(document, "mode", "apiKey");
    JsonNode configured = document.get("apiKeyEnvs");
    if (!(configured instanceof ArrayNode names) || names.isEmpty()) {
      throw failure("CONFIGURATION_INVALID");
    }
    List<String> environmentNames = new java.util.ArrayList<>(names.size());
    for (JsonNode name : names) {
      if (!name.isTextual()
          || !name.textValue().matches("[A-Z_][A-Z0-9_]*")
          || environmentNames.contains(name.textValue())) {
        throw failure("CONFIGURATION_INVALID");
      }
      environmentNames.add(name.textValue());
    }
    return new ModelJobAuthentication("apiKey", List.copyOf(environmentNames));
  }

  static String environmentName(ObjectNode document, String field) {
    String name = requiredText(document, field);
    if (!name.matches("[A-Z_][A-Z0-9_]*")) {
      throw failure("CONFIGURATION_INVALID");
    }
    return name;
  }

  void validateEnvironment() {
    for (String environmentName : environmentNames) {
      String value = System.getenv(environmentName);
      if (value == null || value.isBlank()) {
        throw failure("CONFIGURATION_INVALID");
      }
      if ("chatgpt".equals(mode)) {
        Path context = absolutePath(value, "Codex home context");
        requireExistingDirectory(context, "CONFIGURATION_INVALID");
      }
    }
  }

  List<String> resolvedCredentialIdentities() {
    List<String> identities = new java.util.ArrayList<>(environmentNames.size());
    for (String environmentName : environmentNames) {
      String value = System.getenv(environmentName);
      if (value == null || value.isBlank()) {
        throw failure("CONFIGURATION_INVALID");
      }
      if ("chatgpt".equals(mode)) {
        try {
          identities.add("chatgpt:" + Path.of(value).toRealPath());
        } catch (IOException | InvalidPathException | SecurityException invalid) {
          throw failure("CONFIGURATION_INVALID", invalid);
        }
      } else {
        identities.add("api-key:" + value);
      }
    }
    return List.copyOf(identities);
  }

  ObjectNode identityNode() {
    ObjectNode identity = JsonNodeFactory.instance.objectNode();
    identity.put("mode", mode);
    if ("chatgpt".equals(mode)) {
      identity.put("codexHomeEnv", environmentNames.get(0));
    } else {
      ArrayNode names = identity.putArray("apiKeyEnvs");
      environmentNames.forEach(names::add);
    }
    return identity;
  }
}

record RepositoryRunConfiguration(
    CanonicalJsonCodec canonicalJson,
    Sha256Digest baseConfigurationSha256,
    ModelJobsConfiguration modelJobs,
    CanonicalArtifactPolicyRegistry policyRegistry,
    CanonicalArtifactPolicyRegistry inputPolicyRegistry,
    Path repositoryPath,
    String repositoryIdentity,
    String commitId,
    Path runStore,
    Path captureWorkspace,
    Path stateFile,
    Path gitExecutable,
    EffectiveEngineConfiguration engineConfiguration,
    List<Path> approvedClasspath,
    ArtifactReference capturePolicyRef,
    ArtifactReference candidateSeriesRef,
    ArtifactReference capabilityProfileRef,
    ArtifactReference verificationPolicyRef,
    ArtifactReference profileBundleRef,
    ArtifactReference resourceBudgetRef,
    ArtifactReference toolchainRef,
    ArtifactReference schemaBundleRef,
    ArtifactReference promptBundleRef,
    ArtifactReference graphProfileRef,
    List<String> selectedEntryIds,
    ArtifactReference flowProfileRef,
    ArtifactReference capsuleProfileRef,
    ProfileView inventoryProfile,
    ArtifactStoreLimits storeLimits,
    FlowCompilationProfile flowProfile,
    CapsuleProjectionProfile capsuleProfile,
    BusinessMaterialProfile materialProfile,
    ActivityExplanationProfile activityProfile,
    ProcessDiscoveryProfile processDiscoveryProfile,
    int maxMaterialsToStart) {

  static RepositoryRunConfiguration load(Path configPath) {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    ObjectNode document = readConfiguration(configPath, canonicalJson);
    requireFieldsAllowingOptional(
        document,
        Set.of(
            "business",
            "inputs",
            "paths",
            "policyRegistry",
            "schemaVersion",
            "source",
            "sourceAnalysis",
            "technical"),
        Set.of("inputPolicyRegistry"));
    requireText(document, "schemaVersion", CONFIG_SCHEMA);

    ObjectNode source = object(document, "source");
    requireFields(source, Set.of("commitId", "declaredRepositoryIdentity", "repositoryPath"));
    String commitId = requiredText(source, "commitId");
    if (!commitId.matches("[0-9a-f]{40}")) {
      throw failure("CONFIGURATION_INVALID");
    }
    String repositoryIdentity = requiredText(source, "declaredRepositoryIdentity");
    Path repositoryPath = absolutePath(requiredText(source, "repositoryPath"), "repository path");

    ObjectNode paths = object(document, "paths");
    requireFields(paths, Set.of("captureWorkspace", "gitExecutable", "runStore", "stateFile"));
    Path runStore = absolutePath(requiredText(paths, "runStore"), "run store");
    Path captureWorkspace =
        absolutePath(requiredText(paths, "captureWorkspace"), "capture workspace");
    Path stateFile = absolutePath(requiredText(paths, "stateFile"), "state file");
    Path gitExecutable = absolutePath(requiredText(paths, "gitExecutable"), "Git executable");

    ObjectNode sourceAnalysis = object(document, "sourceAnalysis");
    requireFieldsAllowingOptional(sourceAnalysis, Set.of("javaEngine"), Set.of("jdt", "modelJobs"));
    ObjectNode engineSourceAnalysis = JsonNodeFactory.instance.objectNode();
    engineSourceAnalysis.set("javaEngine", sourceAnalysis.get("javaEngine"));
    if (sourceAnalysis.has("jdt")) {
      engineSourceAnalysis.set("jdt", sourceAnalysis.get("jdt"));
    }
    ObjectNode engineDocument = JsonNodeFactory.instance.objectNode();
    engineDocument.set("sourceAnalysis", engineSourceAnalysis);
    EffectiveEngineConfiguration engine =
        new EngineConfigurationLoader()
            .load(canonicalJson.encodeCanonical(engineDocument).copyToByteArray());
    ModelJobsConfiguration modelJobs =
        sourceAnalysis.has("modelJobs")
            ? ModelJobsConfiguration.load(object(sourceAnalysis, "modelJobs"), canonicalJson)
            : null;

    Path policyPath = resolvePolicyPath(configPath, requiredText(document, "policyRegistry"));
    CanonicalArtifactPolicyRegistry policies = loadPolicies(policyPath, canonicalJson);
    CanonicalArtifactPolicyRegistry inputPolicies =
        document.has("inputPolicyRegistry")
            ? loadPolicies(
                resolvePolicyPath(configPath, requiredText(document, "inputPolicyRegistry")),
                canonicalJson)
            : policies;
    InputReferences inputs = InputReferences.load(object(document, "inputs"), canonicalJson);

    ObjectNode technical = object(document, "technical");
    requireFieldsAllowingOptional(
        technical,
        Set.of("approvedClasspath", "capsule", "flow", "inventory", "store"),
        Set.of("selectedEntryIds"));
    List<String> selectedEntryIds =
        SourceAnalysisExecution.selectedEntryIds(technical.get("selectedEntryIds"));
    ArtifactReference effectiveProfileBundleRef =
        inputs.effectiveProfileBundleRef(selectedEntryIds, canonicalJson);
    ArtifactReference effectiveGraphProfileRef =
        inputs.effectiveGraphProfileRef(selectedEntryIds, canonicalJson);
    List<ApprovedClasspathEntry> approvedClasspath =
        SourceAnalysisExecution.approvedClasspath(technical.get("approvedClasspath"));
    ArtifactReference toolchainRef = inputs.effectiveToolchainRef(approvedClasspath, canonicalJson);
    ObjectNode inventory = object(technical, "inventory");
    requireFields(inventory, Set.of("maxSourceBytes", "maxSourceFiles"));
    ProfileView inventoryProfile =
        new ProfileView(
            effectiveProfileBundleRef,
            inputs.resourceBudgetRef(),
            positiveInt(inventory, "maxSourceFiles"),
            nonnegativeLong(inventory, "maxSourceBytes"));
    requireSameValue(inventory, inputs.resourceBudget(), "maxSourceFiles");
    requireSameValue(inventory, inputs.resourceBudget(), "maxSourceBytes");

    ObjectNode store = object(technical, "store");
    requireFields(
        store,
        Set.of(
            "maxArtifactBytes", "maxDirectoryEntries", "maxPayloadFiles", "maxPublicationBytes"));
    ArtifactStoreLimits storeLimits =
        new ArtifactStoreLimits(
            positiveInt(store, "maxPayloadFiles"),
            positiveLong(store, "maxArtifactBytes"),
            positiveLong(store, "maxPublicationBytes"),
            positiveInt(store, "maxDirectoryEntries"));

    ObjectNode flow = object(technical, "flow");
    requireFields(
        flow,
        Set.of(
            "maxFlowEdges",
            "maxFlowNodes",
            "maxFlows",
            "maxOutcomesPerFlow",
            "maxProcessJoinSignalBasisRefs",
            "maxProcessJoinSignalsPerFlow",
            "maxTraversalDepth"));
    requireProfileFields(flow, inputs.flowProfile());
    FlowCompilationProfile flowProfile =
        new FlowCompilationProfile(
            inputs.flowProfileRef(),
            positiveInt(flow, "maxFlows"),
            positiveInt(flow, "maxOutcomesPerFlow"),
            positiveInt(flow, "maxFlowNodes"),
            positiveInt(flow, "maxFlowEdges"),
            positiveInt(flow, "maxTraversalDepth"),
            positiveInt(flow, "maxProcessJoinSignalsPerFlow"),
            positiveInt(flow, "maxProcessJoinSignalBasisRefs"));

    ObjectNode capsule = object(technical, "capsule");
    requireFields(
        capsule,
        Set.of("maxCapsuleUtf8Bytes", "maxCapsules", "maxSpanBytes", "maxSpansPerCapsule"));
    requireProfileFields(capsule, inputs.capsuleProfile());
    CapsuleProjectionProfile capsuleProfile =
        new CapsuleProjectionProfile(
            inputs.capsuleProfileRef(),
            positiveInt(capsule, "maxCapsules"),
            positiveInt(capsule, "maxSpansPerCapsule"),
            positiveInt(capsule, "maxSpanBytes"),
            positiveInt(capsule, "maxCapsuleUtf8Bytes"));

    ObjectNode business = object(document, "business");
    requireFieldsAllowingOptional(
        business,
        Set.of("activity", "material", "maxMaterialsToStart", "processDiscovery"),
        Set.of());
    BusinessMaterialProfile materialProfile =
        SourceAnalysisExecution.materialProfile(object(business, "material"));
    ActivityExplanationProfile activityProfile =
        SourceAnalysisExecution.activityProfile(object(business, "activity"));
    ProcessDiscoveryProfile processDiscoveryProfile =
        SourceAnalysisExecution.processDiscoveryProfile(object(business, "processDiscovery"));
    int maxMaterialsToStart = positiveInt(business, "maxMaterialsToStart");

    return new RepositoryRunConfiguration(
        canonicalJson,
        SourceAnalysisExecution.baseConfigurationSha256(document, canonicalJson),
        modelJobs,
        policies,
        inputPolicies,
        repositoryPath,
        repositoryIdentity,
        commitId,
        runStore,
        captureWorkspace,
        stateFile,
        gitExecutable,
        engine,
        approvedClasspath.stream().map(ApprovedClasspathEntry::path).toList(),
        inputs.capturePolicyRef(),
        inputs.candidateSeriesRef(),
        inputs.capabilityProfileRef(),
        inputs.verificationPolicyRef(),
        effectiveProfileBundleRef,
        inputs.resourceBudgetRef(),
        toolchainRef,
        inputs.schemaBundleRef(),
        inputs.promptBundleRef(),
        effectiveGraphProfileRef,
        selectedEntryIds,
        inputs.flowProfileRef(),
        inputs.capsuleProfileRef(),
        inventoryProfile,
        storeLimits,
        flowProfile,
        capsuleProfile,
        materialProfile,
        activityProfile,
        processDiscoveryProfile,
        maxMaterialsToStart);
  }

  ModelJobsConfiguration requireModelJobsForExecution() {
    if (modelJobs == null) {
      throw failure("CONFIGURATION_INVALID");
    }
    modelJobs.validateExecutionEnvironment();
    return modelJobs;
  }

  ProcessDiscoveryProfile requireProcessDiscoveryProfile() {
    if (processDiscoveryProfile == null) {
      throw failure("PROCESS_DISCOVERY_PROFILE_REQUIRED");
    }
    return processDiscoveryProfile;
  }

  PersistedTechnicalRunConfiguration technicalConfiguration(ImmutableBytes frozenBytes) {
    return new PersistedTechnicalRunConfiguration(
        frozenBytes,
        verificationPolicyRef,
        capabilityProfileRef,
        inventoryProfile,
        storeLimits,
        DiscoveryProfile.standard(),
        graphProfileRef,
        flowProfile,
        capsuleProfile,
        engineConfiguration,
        approvedClasspath,
        selectedEntryIds);
  }
}

record InputReferences(
    ArtifactReference capturePolicyRef,
    ArtifactReference candidateSeriesRef,
    ArtifactReference capabilityProfileRef,
    ArtifactReference verificationPolicyRef,
    ObjectNode profileBundle,
    ArtifactReference profileBundleRef,
    ArtifactReference resourceBudgetRef,
    ObjectNode toolchain,
    ArtifactReference schemaBundleRef,
    ArtifactReference promptBundleRef,
    ObjectNode graphProfile,
    ArtifactReference graphProfileRef,
    ArtifactReference flowProfileRef,
    ArtifactReference capsuleProfileRef,
    ObjectNode resourceBudget,
    ObjectNode flowProfile,
    ObjectNode capsuleProfile) {

  static InputReferences load(ObjectNode inputs, CanonicalJsonCodec canonicalJson) {
    requireFields(
        inputs,
        Set.of(
            "candidateSeries",
            "capabilityProfile",
            "capsuleProfile",
            "capturePolicy",
            "flowProfile",
            "graphProfile",
            "profileBundle",
            "promptBundle",
            "resourceBudget",
            "schemaBundle",
            "toolchain",
            "verificationPolicy"));
    ObjectNode resourceBudget = object(inputs, "resourceBudget");
    ObjectNode flowProfile = object(inputs, "flowProfile");
    ObjectNode capsuleProfile = object(inputs, "capsuleProfile");
    ObjectNode profileBundle = object(inputs, "profileBundle");
    ObjectNode graphProfile = object(inputs, "graphProfile");
    return new InputReferences(
        reference("capture-policy", object(inputs, "capturePolicy"), canonicalJson),
        reference("candidate-series", object(inputs, "candidateSeries"), canonicalJson),
        reference("capability-profile", object(inputs, "capabilityProfile"), canonicalJson),
        reference("verification-policy", object(inputs, "verificationPolicy"), canonicalJson),
        profileBundle,
        reference("profile-bundle", profileBundle, canonicalJson),
        reference("resource-budget", resourceBudget, canonicalJson),
        object(inputs, "toolchain"),
        reference("schema-bundle", object(inputs, "schemaBundle"), canonicalJson),
        reference("prompt-bundle", object(inputs, "promptBundle"), canonicalJson),
        graphProfile,
        reference("graph-profile", graphProfile, canonicalJson),
        reference("flow-profile", flowProfile, canonicalJson),
        reference("capsule-profile", capsuleProfile, canonicalJson),
        resourceBudget,
        flowProfile,
        capsuleProfile);
  }

  ArtifactReference effectiveProfileBundleRef(
      List<String> selectedEntryIds, CanonicalJsonCodec canonicalJson) {
    if (selectedEntryIds.isEmpty()) {
      return profileBundleRef;
    }
    ObjectNode effective = JsonNodeFactory.instance.objectNode();
    effective.put("schemaVersion", "repository-run-effective-profile-bundle-v1");
    effective.set("declaredProfileBundle", profileBundle);
    ArrayNode entries = effective.putArray("selectedEntryIds");
    selectedEntryIds.forEach(entries::add);
    return reference("profile-bundle", effective, canonicalJson);
  }

  ArtifactReference effectiveGraphProfileRef(
      List<String> selectedEntryIds, CanonicalJsonCodec canonicalJson) {
    if (graphProfile.has("selectedEntryIds")
        && !selectedEntryIds(graphProfile.get("selectedEntryIds")).equals(selectedEntryIds)) {
      throw failure("CONFIGURATION_PROFILE_DRIFT");
    }
    if (selectedEntryIds.isEmpty()) {
      return graphProfileRef;
    }
    ObjectNode effective = graphProfile.deepCopy();
    ArrayNode entries = effective.putArray("selectedEntryIds");
    selectedEntryIds.forEach(entries::add);
    return reference("graph-profile", effective, canonicalJson);
  }

  ArtifactReference effectiveToolchainRef(
      List<ApprovedClasspathEntry> approvedClasspath, CanonicalJsonCodec canonicalJson) {
    ObjectNode effective = JsonNodeFactory.instance.objectNode();
    effective.put("schemaVersion", "repository-run-effective-toolchain-v1");
    effective.set("declaredToolchain", toolchain);
    ArrayNode entries = effective.putArray("approvedClasspath");
    for (ApprovedClasspathEntry entry : approvedClasspath) {
      entries
          .addObject()
          .put("path", entry.path().toString())
          .put("sha256", entry.sha256().value());
    }
    return reference("toolchain", effective, canonicalJson);
  }
}

record ApprovedClasspathEntry(Path path, Sha256Digest sha256) {

  ApprovedClasspathEntry {
    Objects.requireNonNull(path, "approved classpath path");
    Objects.requireNonNull(sha256, "approved classpath digest");
  }
}
