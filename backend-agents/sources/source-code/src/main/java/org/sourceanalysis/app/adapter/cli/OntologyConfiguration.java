package org.sourceanalysis.app.adapter.cli;

import static org.sourceanalysis.app.adapter.cli.SourceAnalysisExecution.absolutePath;
import static org.sourceanalysis.app.adapter.cli.SourceAnalysisExecution.failure;
import static org.sourceanalysis.app.adapter.cli.SourceAnalysisExecution.object;
import static org.sourceanalysis.app.adapter.cli.SourceAnalysisExecution.positiveInt;
import static org.sourceanalysis.app.adapter.cli.SourceAnalysisExecution.readConfiguration;
import static org.sourceanalysis.app.adapter.cli.SourceAnalysisExecution.requireFields;
import static org.sourceanalysis.app.adapter.cli.SourceAnalysisExecution.requireFieldsAllowingOptional;
import static org.sourceanalysis.app.adapter.cli.SourceAnalysisExecution.requiredText;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.runtime.AnalysisRunRequest.OntologyOperation;

/** Immutable standalone declaration for an ontology run; it never initializes a model provider. */
record OntologyConfiguration(
    Storage storage,
    Reading reading,
    List<Path> schemaSources,
    Map<String, String> prompts,
    ModelDeclarations models,
    ImmutableBytes canonicalConfiguration,
    Set<String> explicitPromptOverrides) {

  private static final String SCHEMA = "ontology-config-v1";
  private static final String PROMPT_ROOT = "/org/sourceanalysis/app/analysis/ontology/";
  private static final List<String> PROMPT_KEYS =
      List.of(
          "survey", "prioritize", "reading", "object", "action", "analytic", "relate", "review");
  private static final List<String> ROUTES = List.of("survey", "extract", "relate");
  private static final List<String> LINK_PROMPT_KEYS = List.of("link", "linkReview");

  OntologyConfiguration {
    storage = Objects.requireNonNull(storage, "ontology storage");
    reading = Objects.requireNonNull(reading, "ontology reading limits");
    schemaSources = List.copyOf(Objects.requireNonNull(schemaSources, "ontology schema sources"));
    prompts = Map.copyOf(Objects.requireNonNull(prompts, "ontology prompts"));
    explicitPromptOverrides =
        Set.copyOf(Objects.requireNonNull(explicitPromptOverrides, "ontology prompt overrides"));
    canonicalConfiguration =
        Objects.requireNonNull(canonicalConfiguration, "ontology canonical configuration");
  }

  static OntologyConfiguration load(Path configurationPath) {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    ObjectNode document = readConfiguration(configurationPath, canonicalJson);
    requireFieldsAllowingOptional(
        document,
        Set.of("reading", "schemaVersion", "storage"),
        Set.of("modelJobs", "prompts", "schemaSources"));
    if (!SCHEMA.equals(requiredText(document, "schemaVersion"))) {
      throw failure("CONFIGURATION_INVALID");
    }

    Storage storage = Storage.load(object(document, "storage"));
    Reading reading = Reading.load(object(document, "reading"));
    List<Path> schemaSources =
        document.has("schemaSources") ? schemaSources(document.get("schemaSources")) : List.of();
    ObjectNode configuredPrompts =
        document.has("prompts")
            ? object(document, "prompts")
            : JsonNodeFactory.instance.objectNode();
    Map<String, String> prompts = prompts(configuredPrompts);
    Set<String> explicitPromptOverrides = new LinkedHashSet<>();
    configuredPrompts.fieldNames().forEachRemaining(explicitPromptOverrides::add);
    ModelDeclarations models =
        document.has("modelJobs") ? ModelDeclarations.load(object(document, "modelJobs")) : null;
    ObjectNode normalized = normalized(storage, reading, schemaSources, prompts, models);
    return new OntologyConfiguration(
        storage,
        reading,
        schemaSources,
        prompts,
        models,
        canonicalJson.encodeCanonical(normalized),
        explicitPromptOverrides);
  }

  /** Selects the new frozen Prompt defaults after the caller admits the exact v3 policy set. */
  OntologyConfiguration forTypedV4() {
    Map<String, String> selected = new LinkedHashMap<>(prompts);
    for (String key : PROMPT_KEYS) {
      if (!explicitPromptOverrides.contains(key)) {
        selected.put(key, defaultPrompt(key, "v2"));
      }
    }
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    return new OntologyConfiguration(
        storage,
        reading,
        schemaSources,
        selected,
        models,
        canonicalJson.encodeCanonical(
            normalized(storage, reading, schemaSources, selected, models)),
        explicitPromptOverrides);
  }

  /** New defaults participate only in the explicitly admitted joint-link production family. */
  OntologyConfiguration forJointLinks() {
    Map<String, String> selected = new LinkedHashMap<>(prompts);
    selected.putIfAbsent("link", defaultPrompt("link"));
    selected.putIfAbsent("linkReview", defaultPrompt("link-review"));
    if (!explicitPromptOverrides.contains("prioritize")) {
      selected.put("prioritize", defaultPrompt("prioritize", "v3"));
    }
    return new OntologyConfiguration(
        storage,
        reading,
        schemaSources,
        selected,
        models,
        new CanonicalJsonCodec()
            .encodeCanonical(normalized(storage, reading, schemaSources, selected, models)),
        explicitPromptOverrides);
  }

  /**
   * Loads the immutable storage declarations needed to inspect already-saved ontology runs.
   *
   * <p>Inspection must not reread a mutable current Prompt override (or initialize a model
   * provider) merely to reopen a run whose stage snapshot already contains its admitted prompt
   * identity.
   */
  static Storage loadStorage(Path configurationPath) {
    CanonicalJsonCodec canonicalJson = new CanonicalJsonCodec();
    ObjectNode document = readConfiguration(configurationPath, canonicalJson);
    requireFieldsAllowingOptional(
        document,
        Set.of("reading", "schemaVersion", "storage"),
        Set.of("modelJobs", "prompts", "schemaSources"));
    if (!SCHEMA.equals(requiredText(document, "schemaVersion"))) {
      throw failure("CONFIGURATION_INVALID");
    }
    return Storage.load(object(document, "storage"));
  }

  /** Checks only the declared bindings needed by the operation; it never reads credentials. */
  void requireModels(OntologyOperation operation) {
    Objects.requireNonNull(operation, "ontology operation");
    String route =
        switch (operation) {
          case PREPARE_ONTOLOGY, PUBLISH_ONTOLOGY -> null;
          case IDENTIFY_ONTOLOGY -> "extract";
          case RELATE_ONTOLOGY -> "relate";
        };
    if (route != null) {
      requireModelRoute(route);
    }
  }

  /**
   * Checks one existing configured route without coupling formal survey work to typed extraction.
   */
  void requireModelRoute(String route) {
    if (!ROUTES.contains(route) || models == null || models.routing().get(route) == null) {
      throw new IllegalStateException("ONTOLOGY_MODEL_CONFIGURATION_REQUIRED");
    }
  }

  private static List<Path> schemaSources(JsonNode configured) {
    if (!(configured instanceof ArrayNode values)) {
      throw failure("CONFIGURATION_INVALID");
    }
    List<Path> paths = new ArrayList<>();
    Set<Path> distinct = new LinkedHashSet<>();
    for (JsonNode value : values) {
      if (!value.isTextual() || value.textValue().isBlank()) {
        throw failure("CONFIGURATION_INVALID");
      }
      Path path;
      try {
        path = Path.of(value.textValue()).normalize();
      } catch (RuntimeException invalid) {
        throw failure("CONFIGURATION_INVALID", invalid);
      }
      if (path.isAbsolute() || path.toString().isBlank() || path.startsWith("..")) {
        throw failure("CONFIGURATION_INVALID");
      }
      if (!distinct.add(path)) {
        throw failure("CONFIGURATION_INVALID");
      }
      paths.add(path);
    }
    return List.copyOf(paths);
  }

  private static Map<String, String> prompts(ObjectNode configured) {
    Set<String> actual = new LinkedHashSet<>();
    configured.fieldNames().forEachRemaining(actual::add);
    Set<String> allowed = new LinkedHashSet<>(PROMPT_KEYS);
    allowed.addAll(LINK_PROMPT_KEYS);
    if (!allowed.containsAll(actual)) {
      throw failure("CONFIGURATION_INVALID");
    }
    Map<String, String> values = new LinkedHashMap<>();
    for (String key : PROMPT_KEYS) {
      values.put(key, defaultPrompt(key));
    }
    for (String key : actual) {
      values.put(key, readPromptOverride(absolutePath(requiredText(configured, key), key)));
    }
    return Map.copyOf(values);
  }

  private static String defaultPrompt(String key) {
    return defaultPrompt(key, "v1");
  }

  private static String defaultPrompt(String key, String version) {
    String resource = PROMPT_ROOT + "formal-" + key + "-" + version + ".txt";
    try (InputStream input = OntologyConfiguration.class.getResourceAsStream(resource)) {
      if (input == null) {
        throw failure("CONFIGURATION_INVALID");
      }
      return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    } catch (IOException invalid) {
      throw failure("CONFIGURATION_INVALID", invalid);
    }
  }

  private static String readPromptOverride(Path path) {
    try {
      if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
        throw failure("CONFIGURATION_INVALID");
      }
      String content = Files.readString(path, StandardCharsets.UTF_8);
      if (content.isBlank()) {
        throw failure("CONFIGURATION_INVALID");
      }
      return content;
    } catch (IOException invalid) {
      throw failure("CONFIGURATION_INVALID", invalid);
    }
  }

  private static ObjectNode normalized(
      Storage storage,
      Reading reading,
      List<Path> schemaSources,
      Map<String, String> prompts,
      ModelDeclarations models) {
    ObjectNode document = JsonNodeFactory.instance.objectNode();
    document.put("schemaVersion", SCHEMA);
    ObjectNode storageNode = document.putObject("storage");
    storageNode.put("root", storage.root().toString());
    storageNode.put("preparedSourceArchive", storage.preparedSourceArchive().toString());
    storageNode.put(
        "sourcePreparationPolicyRegistry", storage.sourcePreparationPolicyRegistry().toString());
    storageNode.put("evidencePolicyRegistry", storage.evidencePolicyRegistry().toString());
    storageNode.put("ontologyPolicyRegistry", storage.ontologyPolicyRegistry().toString());
    if (!storage.upstreamArtifactPolicyRegistries().isEmpty()) {
      ArrayNode historical = storageNode.putArray("upstreamArtifactPolicyRegistries");
      storage.upstreamArtifactPolicyRegistries().stream()
          .map(Path::toString)
          .sorted()
          .forEach(historical::add);
    }
    ObjectNode readingNode = document.putObject("reading");
    readingNode.put("maxUnitBytes", reading.maxUnitBytes());
    readingNode.put("maxRequestBytes", reading.maxRequestBytes());
    readingNode.put("maxOutputBytes", reading.maxOutputBytes());
    readingNode.put("maxOutputTokens", reading.maxOutputTokens());
    readingNode.put("maxRequests", reading.maxRequests());
    readingNode.put("maxReadingRounds", reading.maxReadingRounds());
    readingNode.put("maxActionsPerRound", reading.maxActionsPerRound());
    readingNode.put("maxNavigationEntries", reading.maxNavigationEntries());
    ArrayNode schemaSourcesNode = document.putArray("schemaSources");
    schemaSources.stream().map(Path::toString).sorted().forEach(schemaSourcesNode::add);
    ObjectNode promptsNode = document.putObject("prompts");
    PROMPT_KEYS.forEach(key -> promptsNode.put(key, prompts.get(key)));
    LINK_PROMPT_KEYS.stream()
        .filter(prompts::containsKey)
        .forEach(key -> promptsNode.put(key, prompts.get(key)));
    if (models != null) {
      document.set("modelJobs", models.normalized());
    }
    return document;
  }

  record Storage(
      Path root,
      Path preparedSourceArchive,
      Path sourcePreparationPolicyRegistry,
      Path evidencePolicyRegistry,
      Path ontologyPolicyRegistry,
      List<Path> upstreamArtifactPolicyRegistries) {

    Storage(
        Path root,
        Path preparedSourceArchive,
        Path sourcePreparationPolicyRegistry,
        Path evidencePolicyRegistry,
        Path ontologyPolicyRegistry) {
      this(
          root,
          preparedSourceArchive,
          sourcePreparationPolicyRegistry,
          evidencePolicyRegistry,
          ontologyPolicyRegistry,
          List.of());
    }

    Storage {
      upstreamArtifactPolicyRegistries =
          List.copyOf(
              Objects.requireNonNull(
                  upstreamArtifactPolicyRegistries, "ontology upstream policy registries"));
    }

    static Storage load(ObjectNode document) {
      requireFieldsAllowingOptional(
          document,
          Set.of(
              "root",
              "preparedSourceArchive",
              "sourcePreparationPolicyRegistry",
              "evidencePolicyRegistry",
              "ontologyPolicyRegistry"),
          Set.of("upstreamArtifactPolicyRegistries"));
      return new Storage(
          absolutePath(requiredText(document, "root"), "root"),
          absolutePath(requiredText(document, "preparedSourceArchive"), "preparedSourceArchive"),
          absolutePath(
              requiredText(document, "sourcePreparationPolicyRegistry"),
              "sourcePreparationPolicyRegistry"),
          absolutePath(requiredText(document, "evidencePolicyRegistry"), "evidencePolicyRegistry"),
          absolutePath(requiredText(document, "ontologyPolicyRegistry"), "ontologyPolicyRegistry"),
          upstreamArtifactPolicyRegistries(document.get("upstreamArtifactPolicyRegistries")));
    }

    private static List<Path> upstreamArtifactPolicyRegistries(JsonNode value) {
      if (value == null) {
        return List.of();
      }
      if (!(value instanceof ArrayNode paths)) {
        throw failure("CONFIGURATION_INVALID");
      }
      List<Path> values = new ArrayList<>();
      Set<Path> distinct = new LinkedHashSet<>();
      for (JsonNode path : paths) {
        if (!path.isTextual() || path.asText().isBlank()) {
          throw failure("CONFIGURATION_INVALID");
        }
        Path resolved = absolutePath(path.asText(), "upstreamArtifactPolicyRegistries");
        if (!distinct.add(resolved)) {
          throw failure("CONFIGURATION_INVALID");
        }
        values.add(resolved);
      }
      return List.copyOf(values);
    }
  }

  record Reading(
      int maxUnitBytes,
      int maxRequestBytes,
      int maxOutputBytes,
      int maxOutputTokens,
      int maxRequests,
      int maxReadingRounds,
      int maxActionsPerRound,
      int maxNavigationEntries) {

    static Reading load(ObjectNode document) {
      requireFields(
          document,
          Set.of(
              "maxUnitBytes",
              "maxRequestBytes",
              "maxOutputBytes",
              "maxOutputTokens",
              "maxRequests",
              "maxReadingRounds",
              "maxActionsPerRound",
              "maxNavigationEntries"));
      return new Reading(
          positiveInt(document, "maxUnitBytes"),
          positiveInt(document, "maxRequestBytes"),
          positiveInt(document, "maxOutputBytes"),
          positiveInt(document, "maxOutputTokens"),
          positiveInt(document, "maxRequests"),
          positiveInt(document, "maxReadingRounds"),
          positiveInt(document, "maxActionsPerRound"),
          positiveInt(document, "maxNavigationEntries"));
    }
  }

  record ModelDeclarations(
      int maxConcurrentJobs,
      Map<String, ModelJobProviderConfiguration> providers,
      Map<String, List<String>> routing) {

    ModelDeclarations {
      providers = Map.copyOf(providers);
      Map<String, List<String>> immutableRouting = new LinkedHashMap<>();
      routing.forEach((key, value) -> immutableRouting.put(key, List.copyOf(value)));
      routing = Map.copyOf(immutableRouting);
    }

    static ModelDeclarations load(ObjectNode document) {
      requireFields(document, Set.of("maxConcurrentJobs", "providers", "routing"));
      ObjectNode providersNode = object(document, "providers");
      if (providersNode.isEmpty()) {
        throw failure("CONFIGURATION_INVALID");
      }
      Map<String, ModelJobProviderConfiguration> providers = new LinkedHashMap<>();
      Set<String> quotaScopes = new LinkedHashSet<>();
      for (var fields = providersNode.fields(); fields.hasNext(); ) {
        Map.Entry<String, JsonNode> entry = fields.next();
        if (!entry.getKey().matches("[a-z][a-z0-9-]{0,47}")
            || !(entry.getValue() instanceof ObjectNode providerNode)) {
          throw failure("CONFIGURATION_INVALID");
        }
        ModelJobProviderConfiguration provider = ModelJobProviderConfiguration.load(providerNode);
        if (providers.put(entry.getKey(), provider) != null
            || !quotaScopes.add(provider.quotaScope())) {
          throw failure("CONFIGURATION_INVALID");
        }
      }
      ObjectNode routingNode = object(document, "routing");
      requireFields(routingNode, Set.copyOf(ROUTES));
      Map<String, List<String>> routing = new LinkedHashMap<>();
      for (String route : ROUTES) {
        JsonNode configured = routingNode.get(route);
        if (!(configured instanceof ArrayNode bindings) || bindings.isEmpty()) {
          throw failure("CONFIGURATION_INVALID");
        }
        List<String> values = new ArrayList<>();
        for (JsonNode binding : bindings) {
          if (!binding.isTextual()
              || binding.textValue().isBlank()
              || !providers.containsKey(binding.textValue())
              || values.contains(binding.textValue())) {
            throw failure("CONFIGURATION_INVALID");
          }
          values.add(binding.textValue());
        }
        routing.put(route, List.copyOf(values));
      }
      return new ModelDeclarations(positiveInt(document, "maxConcurrentJobs"), providers, routing);
    }

    ObjectNode normalized() {
      ObjectNode document = JsonNodeFactory.instance.objectNode();
      document.put("maxConcurrentJobs", maxConcurrentJobs);
      ObjectNode providerNode = document.putObject("providers");
      providers.entrySet().stream()
          .sorted(Map.Entry.comparingByKey())
          .forEach(
              entry ->
                  providerNode.set(entry.getKey(), entry.getValue().normalizedNonSecretNode()));
      ObjectNode routingNode = document.putObject("routing");
      for (String route : ROUTES) {
        ArrayNode bindings = routingNode.putArray(route);
        routing.get(route).forEach(bindings::add);
      }
      return document;
    }
  }
}
