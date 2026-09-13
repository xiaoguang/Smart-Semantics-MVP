package org.sourceanalysis.app.analysis.code.javaparser;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.code.EntryCodeContext;
import org.sourceanalysis.app.analysis.code.EntrySeed;
import org.sourceanalysis.app.analysis.code.JavaCodeEngine;
import org.sourceanalysis.app.analysis.code.JavaCodeSession;
import org.sourceanalysis.app.analysis.code.JavaDeclarationCatalog;
import org.sourceanalysis.app.analysis.code.VerifiedJavaProject;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.runtime.EffectiveEngineConfiguration;
import org.sourceanalysis.app.runtime.JavaCodeEngineFactory;

/** Public-seam baseline for the retained JavaParser engine. */
class JavaParserCodeEngineBaselineTest {

  private static final String SOURCE_PATH = "src/main/java/example/RegistrationController.java";
  private static final String SOURCE =
      """
      package example;

      @Deprecated
      class RegistrationController {
        private RegistrationService service;

        String register(String loginName) {
          if (loginName.isBlank()) {
            throw new IllegalArgumentException("login name");
          }
          return service.register(loginName);
        }
      }

      class RegistrationService {
        String register(String loginName) {
          return loginName.trim();
        }
      }
      """;

  @Test
  void selectsJavaParserWithoutJdtAndReturnsNeutralCatalogAndEntryMaterial() {
    JavaCodeEngine engine =
        new JavaCodeEngineFactory()
            .create(
                new EffectiveEngineConfiguration(EffectiveEngineConfiguration.JAVAPARSER, null));

    assertThat(engine).isInstanceOf(JavaParserCodeEngine.class);
    try (JavaCodeSession session = engine.open(project())) {
      assertThat(session.descriptor().engineId()).isEqualTo("javaparser");
      assertThat(session.descriptor().toolVersions()).containsKey("javaparser-core");
      JavaDeclarationCatalog catalog = session.catalog();
      JavaDeclarationCatalog.MethodDeclarationView entry =
          catalog.methods().stream()
              .filter(method -> method.declaringType().equals("example.RegistrationController"))
              .filter(method -> method.name().equals("register"))
              .findFirst()
              .orElseThrow();

      EntryCodeContext context =
          session.collect(
              new EntrySeed(
                  "entry:registration", entry.methodKey(), entry.sourceRange(), "HTTP POST"));

      assertThat(context.entryMethodKey()).isEqualTo(entry.methodKey());
      assertThat(context.methods())
          .anySatisfy(
              method -> {
                assertThat(method.declaringType()).isEqualTo("example.RegistrationController");
                assertThat(method.source().text()).contains("service.register(loginName)");
                assertThat(method.controls())
                    .extracting(EntryCodeContext.Control::kind)
                    .contains("IF");
                assertThat(method.exits())
                    .extracting(EntryCodeContext.Exit::kind)
                    .contains("THROW", "RETURN");
              });
      assertThat(context.calls())
          .anySatisfy(
              call -> {
                assertThat(call.expression()).contains("service.register(loginName)");
                assertThat(call.actualArguments())
                    .extracting(EntryCodeContext.ActualArgument::expression)
                    .containsExactly("loginName");
              });
      assertThat(context.technicalEnhancements().availability())
          .isEqualTo(EntryCodeContext.Availability.NOT_PRODUCED);
    }
  }

  private static VerifiedJavaProject project() {
    byte[] source = SOURCE.getBytes(StandardCharsets.UTF_8);
    String seed = SOURCE_PATH + ":" + digest(source);
    VerifiedSourceTextDocument document =
        new VerifiedSourceTextDocument(
            new ArtifactId("file:" + digest(SOURCE_PATH)),
            SOURCE_PATH,
            "100644",
            "text/plain",
            source.length,
            new Sha256Digest(digest(source)),
            ImmutableBytes.copyOf(source));
    VerifiedSourceTextSet texts =
        new VerifiedSourceTextSet(
            "snapshot:" + digest(seed),
            "COMPLETE_CAPTURE",
            true,
            reference("capability-profile", seed),
            reference("source-inventory", seed),
            reference("verified-snapshot", seed),
            controls(seed),
            List.of(document));
    return VerifiedJavaProject.fromVerifiedSourceTextSet(
        texts, List.of("src/main/java"), List.of(), "17");
  }

  private static ArtifactReference reference(String prefix, String seed) {
    return new ArtifactReference(
        new ArtifactId(prefix + ":" + digest(seed + prefix)),
        new Sha256Digest(digest(seed + "-sha")));
  }

  private static ArtifactControls controls(String seed) {
    return new ArtifactControls(
        new Sha256Digest(digest(seed + "-toolchain")),
        new Sha256Digest(digest(seed + "-profile")),
        new Sha256Digest(digest(seed + "-schema")),
        null,
        new ArtifactPolicyRegistryReference(
            new ArtifactId("artifact-policy-registry:" + digest(seed + "-registry")),
            new Sha256Digest(digest(seed + "-registry-sha"))));
  }

  private static String digest(String value) {
    return digest(value.getBytes(StandardCharsets.UTF_8));
  }

  private static String digest(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
