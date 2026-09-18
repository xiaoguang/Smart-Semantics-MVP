package org.sourceanalysis.app.runtime;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import org.sourceanalysis.app.analysis.code.CodeEngineException;

/** Strictly loads local-only engine tooling configuration at the composition root. */
public final class EngineConfigurationLoader {

  private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration QUERY_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration SHUTDOWN_TIMEOUT = Duration.ofSeconds(10);

  private final ObjectMapper yaml;

  public EngineConfigurationLoader() {
    YAMLFactory factory = new YAMLFactory();
    factory.enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    yaml =
        new ObjectMapper(factory)
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
  }

  /** Loads one complete UTF-8 YAML document with no environment or CLI override layer. */
  public EffectiveEngineConfiguration load(byte[] yamlBytes) {
    if (yamlBytes == null || yamlBytes.length == 0) {
      throw invalid("engine configuration bytes are required", null);
    }
    try {
      ConfigurationDocument document = yaml.readValue(yamlBytes, ConfigurationDocument.class);
      if (document == null || document.sourceAnalysis() == null) {
        throw invalid("sourceAnalysis configuration is required", null);
      }
      SourceAnalysisDocument sourceAnalysis = document.sourceAnalysis();
      String engine = sourceAnalysis.javaEngine();
      if (!EffectiveEngineConfiguration.JDT.equals(engine)) {
        throw invalid("sourceAnalysis.javaEngine must be exactly jdt", null);
      }
      return new EffectiveEngineConfiguration(
          engine, selectedJdtConfiguration(sourceAnalysis.jdt()));
    } catch (CodeEngineException failure) {
      throw failure;
    } catch (IOException | IllegalArgumentException failure) {
      throw invalid("engine configuration YAML is invalid", failure);
    }
  }

  private static EffectiveEngineConfiguration.JdtConfiguration selectedJdtConfiguration(
      JdtDocument jdt) {
    if (jdt == null || blank(jdt.installation()) || blank(jdt.javaHome())) {
      throw invalid("JDT installation and Java home are required", null);
    }
    try {
      return new EffectiveEngineConfiguration.JdtConfiguration(
          Path.of(jdt.installation()),
          Path.of(jdt.javaHome()),
          STARTUP_TIMEOUT,
          QUERY_TIMEOUT,
          SHUTDOWN_TIMEOUT);
    } catch (InvalidPathException failure) {
      throw invalid("selected JDT paths are invalid", failure);
    }
  }

  private static boolean blank(String value) {
    return value == null || value.isBlank();
  }

  private static CodeEngineException invalid(String detail, Throwable cause) {
    return new CodeEngineException(CodeEngineException.ENGINE_CONFIGURATION_INVALID, detail, cause);
  }

  private record ConfigurationDocument(SourceAnalysisDocument sourceAnalysis) {}

  private record SourceAnalysisDocument(String javaEngine, JdtDocument jdt) {}

  private record JdtDocument(String installation, String javaHome) {}
}
