package org.sourceanalysis.app.analysis.discovery;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceInventoryReference;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextReader;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.evidence.SourceExcerptV1;
import org.sourceanalysis.app.evidence.SourceLocatorV1;

/**
 * Deterministically detects the static Java/Spring MVC/MyBatis profile of verified source bytes.
 */
public final class ApplicationProfileDetector {

  private static final Set<String> SPRING_MVC_ARTIFACTS =
      Set.of("spring-webmvc", "spring-boot-starter-web");
  private static final Set<String> MYBATIS_ARTIFACTS =
      Set.of("mybatis-spring", "mybatis-spring-boot-starter", "mybatis-plus-boot-starter");
  private static final Pattern MAPPER_LOCATIONS =
      Pattern.compile("(?m)^\\s*mapper-locations\\s*:\\s*([^\\r\\n#]+)");
  private static final CanonicalJsonCodec CANONICAL_JSON = new CanonicalJsonCodec();

  private final VerifiedSourceTextReader sourceReader;

  ApplicationProfileDetector(VerifiedSourceTextReader sourceReader) {
    this.sourceReader = java.util.Objects.requireNonNull(sourceReader, "verified source reader");
  }

  /** Reads only the supplied verified-source handle and returns static capability signals. */
  public ApplicationProfile detect(
      VerifiedSourceInventoryReference frozenSource, DiscoveryProfile discoveryProfile) {
    if (frozenSource == null || discoveryProfile == null) {
      throw new IllegalArgumentException("application discovery request is invalid");
    }
    VerifiedSourceTextSet source = sourceReader.reopen(frozenSource);
    List<FrameworkSignal> frameworkSignals = frameworkSignals(source.documents());
    List<ConfigSignal> configSignals = configSignals(source.documents());
    Integer languageVersion = languageVersion(source.documents());
    return new ApplicationProfile(
        applicationProfileId(
            source, discoveryProfile, languageVersion, frameworkSignals, configSignals),
        source.snapshotId(),
        source.inventoryScopeKind(),
        source.repositoryCompletionEligible(),
        ApplicationLanguage.JAVA,
        languageVersion,
        frameworkSignals,
        configSignals,
        source.capabilityProfileRef(),
        source.sourceInventoryRef(),
        source.verifiedSnapshotRef(),
        source.controls());
  }

  private static List<FrameworkSignal> frameworkSignals(
      List<VerifiedSourceTextDocument> documents) {
    List<FrameworkSignal> candidates = new ArrayList<>();
    for (VerifiedSourceTextDocument document : documents) {
      if (!isMavenModelPath(document.path())) {
        continue;
      }
      Model model = parsePom(document);
      for (Dependency dependency : model.getDependencies()) {
        FrameworkSignalKind kind = frameworkKind(dependency.getArtifactId());
        if (kind != null) {
          candidates.add(
              new FrameworkSignal(
                  kind,
                  excerpt(document, dependency.getArtifactId()),
                  SignalDisposition.SUPPORTED,
                  null));
        }
      }
    }
    candidates.sort(
        Comparator.comparing((FrameworkSignal signal) -> signal.kind().name())
            .thenComparing(signal -> signal.sourceExcerpt().locator().path())
            .thenComparingLong(signal -> signal.sourceExcerpt().locator().startByte()));
    Map<FrameworkSignalKind, FrameworkSignal> firstByKind = new LinkedHashMap<>();
    for (FrameworkSignal candidate : candidates) {
      firstByKind.putIfAbsent(candidate.kind(), candidate);
    }
    return List.copyOf(firstByKind.values());
  }

  private static FrameworkSignalKind frameworkKind(String artifactId) {
    if (SPRING_MVC_ARTIFACTS.contains(artifactId)) {
      return FrameworkSignalKind.SPRING_MVC;
    }
    if (MYBATIS_ARTIFACTS.contains(artifactId)) {
      return FrameworkSignalKind.MYBATIS;
    }
    return null;
  }

  private static List<ConfigSignal> configSignals(List<VerifiedSourceTextDocument> documents) {
    List<ConfigSignal> signals = new ArrayList<>();
    for (VerifiedSourceTextDocument document : documents) {
      if (!document.path().endsWith(".yml") && !document.path().endsWith(".yaml")) {
        continue;
      }
      String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
      Matcher matcher = MAPPER_LOCATIONS.matcher(source);
      while (matcher.find()) {
        if (!isMyBatisChildMapping(source, matcher.start())) {
          continue;
        }
        signals.add(
            new ConfigSignal(
                ConfigSignalKind.MYBATIS_MAPPER_LOCATION,
                excerpt(document, matcher.start(1), matcher.end(1)),
                matcher.group(1).trim(),
                SignalDisposition.SUPPORTED,
                null));
      }
    }
    signals.sort(
        Comparator.comparing((ConfigSignal signal) -> signal.kind().name())
            .thenComparing(signal -> signal.sourceExcerpt().locator().path()));
    return List.copyOf(signals);
  }

  private static boolean isMyBatisChildMapping(String source, int mapperLocationsStart) {
    int currentLineStart = source.lastIndexOf('\n', Math.max(0, mapperLocationsStart - 1)) + 1;
    int childIndent = indentation(source, currentLineStart);
    int lineEnd = currentLineStart - 1;
    while (lineEnd >= 0) {
      int lineStart = source.lastIndexOf('\n', Math.max(0, lineEnd - 1)) + 1;
      String line = source.substring(lineStart, lineEnd + 1);
      String trimmed = line.trim();
      if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
        int parentIndent = indentation(source, lineStart);
        if (parentIndent < childIndent) {
          return trimmed.equals("mybatis:") || trimmed.startsWith("mybatis: #");
        }
      }
      lineEnd = lineStart - 2;
    }
    return false;
  }

  private static int indentation(String source, int lineStart) {
    int index = lineStart;
    while (index < source.length() && source.charAt(index) == ' ') {
      index++;
    }
    return index - lineStart;
  }

  private static Integer languageVersion(List<VerifiedSourceTextDocument> documents) {
    Set<Integer> versions = new java.util.HashSet<>();
    for (VerifiedSourceTextDocument document : documents) {
      if (!isMavenModelPath(document.path())) {
        continue;
      }
      var properties = parsePom(document).getProperties();
      Integer release = languageVersion(properties.getProperty("maven.compiler.release"));
      Integer source = languageVersion(properties.getProperty("maven.compiler.source"));
      if (release != null && source != null && !release.equals(source)) {
        throw new ApplicationDiscoveryException("APPLICATION_PROFILE_CONFLICT");
      }
      if (release != null) {
        versions.add(release);
      } else if (source != null) {
        versions.add(source);
      }
    }
    if (versions.size() > 1) {
      throw new ApplicationDiscoveryException("APPLICATION_PROFILE_CONFLICT");
    }
    return versions.stream().findFirst().orElse(null);
  }

  private static Integer languageVersion(String value) {
    if (value == null) {
      return null;
    }
    if (value.matches("[1-9][0-9]*")) {
      return Integer.parseInt(value);
    }
    if (value.matches("1\\.[1-9][0-9]*")) {
      return Integer.parseInt(value.substring(2));
    }
    return null;
  }

  private static boolean isMavenModelPath(String path) {
    return "pom.xml".equals(path) || path.endsWith("/pom.xml");
  }

  private static Model parsePom(VerifiedSourceTextDocument document) {
    try {
      return new MavenXpp3Reader()
          .read(
              new StringReader(
                  new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8)));
    } catch (Exception invalid) {
      throw new IllegalArgumentException(
          "application profile contains an invalid Maven model", invalid);
    }
  }

  private static ArtifactId applicationProfileId(
      VerifiedSourceTextSet source,
      DiscoveryProfile profile,
      Integer languageVersion,
      List<FrameworkSignal> frameworkSignals,
      List<ConfigSignal> configSignals) {
    ObjectNode material = JsonNodeFactory.instance.objectNode();
    material.put("snapshotId", source.snapshotId());
    material.put("inventoryScopeKind", source.inventoryScopeKind());
    material.put("repositoryCompletionEligible", source.repositoryCompletionEligible());
    material.set("capabilityProfileRef", referenceIdentity(source.capabilityProfileRef()));
    material.set("sourceInventoryRef", referenceIdentity(source.sourceInventoryRef()));
    material.set("verifiedSnapshotRef", referenceIdentity(source.verifiedSnapshotRef()));
    material.set("controls", controlsIdentity(source.controls()));
    material.put("ruleVersion", profile.ruleVersion());
    material.put("language", ApplicationLanguage.JAVA.name());
    if (languageVersion == null) {
      material.putNull("languageVersion");
    } else {
      material.put("languageVersion", languageVersion);
    }
    ArrayNode framework = material.putArray("frameworkSignals");
    for (FrameworkSignal signal : frameworkSignals) {
      ObjectNode item = framework.addObject();
      item.put("kind", signal.kind().name());
      item.put("disposition", signal.disposition().name());
      if (signal.reasonCode() == null) {
        item.putNull("reasonCode");
      } else {
        item.put("reasonCode", signal.reasonCode());
      }
      item.set("sourceExcerpt", excerptIdentity(signal.sourceExcerpt()));
    }
    ArrayNode config = material.putArray("configSignals");
    for (ConfigSignal signal : configSignals) {
      ObjectNode item = config.addObject();
      item.put("kind", signal.kind().name());
      if (signal.value() == null) {
        item.putNull("value");
      } else {
        item.put("value", signal.value());
      }
      item.put("disposition", signal.disposition().name());
      if (signal.reasonCode() == null) {
        item.putNull("reasonCode");
      } else {
        item.put("reasonCode", signal.reasonCode());
      }
      item.set("sourceExcerpt", excerptIdentity(signal.sourceExcerpt()));
    }
    byte[] canonical = CANONICAL_JSON.encodeCanonical(material).copyToByteArray();
    return ArtifactId.parse(
        "application-profile:"
            + sha256(
                concatenate(
                    frame("application-discovery-application-profile-id-v2"), frame(canonical))));
  }

  private static ObjectNode excerptIdentity(SourceExcerptV1 excerpt) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    var locator = excerpt.locator();
    ObjectNode location = value.putObject("locator");
    location.put("fileId", locator.fileId().value());
    location.put("path", locator.path());
    location.put("startByte", locator.startByte());
    location.put("endByteExclusive", locator.endByteExclusive());
    location.put("startLine", locator.startLine());
    location.put("startColumn", locator.startColumn());
    location.put("endLine", locator.endLine());
    location.put("endColumn", locator.endColumn());
    value.put("rawUtf8Sha256", excerpt.rawUtf8Sha256().value());
    return value;
  }

  private static ObjectNode referenceIdentity(ArtifactReference reference) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("artifactId", reference.artifactId().value());
    value.put("sha256", reference.sha256().value());
    return value;
  }

  private static ObjectNode referenceIdentity(ArtifactPolicyRegistryReference reference) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("artifactId", reference.artifactId().value());
    value.put("sha256", reference.sha256().value());
    return value;
  }

  private static ObjectNode controlsIdentity(ArtifactControls controls) {
    ObjectNode value = JsonNodeFactory.instance.objectNode();
    value.put("toolchainSha256", controls.toolchainSha256().value());
    value.put("profileSha256", controls.profileSha256().value());
    value.put("schemaBundleSha256", controls.schemaBundleSha256().value());
    if (controls.promptBundleSha256() == null) {
      value.putNull("promptBundleSha256");
    } else {
      value.put("promptBundleSha256", controls.promptBundleSha256().value());
    }
    value.set("artifactPolicyRegistryRef", referenceIdentity(controls.artifactPolicyRegistryRef()));
    return value;
  }

  private static SourceExcerptV1 excerpt(VerifiedSourceTextDocument document, String token) {
    String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    int characterStart = source.indexOf(token);
    if (characterStart < 0) {
      throw new IllegalArgumentException("application signal is not present in verified source");
    }
    return excerpt(document, characterStart, characterStart + token.length());
  }

  private static SourceExcerptV1 excerpt(
      VerifiedSourceTextDocument document, int characterStart, int characterEndExclusive) {
    String source = new String(document.rawUtf8().copyToByteArray(), StandardCharsets.UTF_8);
    byte[] bytes = document.rawUtf8().copyToByteArray();
    int startByte = source.substring(0, characterStart).getBytes(StandardCharsets.UTF_8).length;
    int endByte =
        source.substring(0, characterEndExclusive).getBytes(StandardCharsets.UTF_8).length;
    return new SourceExcerptV1(
        new SourceLocatorV1(
            document.fileId(),
            document.path(),
            startByte,
            endByte,
            lineAt(source, characterStart),
            columnAt(source, characterStart),
            lineAt(source, characterEndExclusive),
            columnAt(source, characterEndExclusive)),
        ImmutableBytes.copyOf(java.util.Arrays.copyOfRange(bytes, startByte, endByte)),
        org.sourceanalysis.app.artifact.Sha256Digest.parse(
            sha256(java.util.Arrays.copyOfRange(bytes, startByte, endByte))));
  }

  private static int lineAt(String source, int characterOffset) {
    return (int)
            source
                .substring(0, characterOffset)
                .chars()
                .filter(character -> character == '\n')
                .count()
        + 1;
  }

  private static int columnAt(String source, int characterOffset) {
    int lineStart = source.lastIndexOf('\n', Math.max(0, characterOffset - 1));
    return source
            .substring(lineStart + 1, characterOffset)
            .codePointCount(0, characterOffset - lineStart - 1)
        + 1;
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static byte[] frame(String value) {
    return frame(value.getBytes(StandardCharsets.UTF_8));
  }

  private static byte[] frame(byte[] value) {
    return ByteBuffer.allocate(Long.BYTES + value.length)
        .order(ByteOrder.BIG_ENDIAN)
        .putLong(value.length)
        .put(value)
        .array();
  }

  private static byte[] concatenate(byte[]... values) {
    int length = 0;
    for (byte[] value : values) {
      length = Math.addExact(length, value.length);
    }
    byte[] result = new byte[length];
    int offset = 0;
    for (byte[] value : values) {
      System.arraycopy(value, 0, result, offset, value.length);
      offset += value.length;
    }
    return result;
  }
}
