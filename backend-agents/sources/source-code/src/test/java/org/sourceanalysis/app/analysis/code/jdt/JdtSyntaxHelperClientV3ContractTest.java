package org.sourceanalysis.app.analysis.code.jdt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.code.CodeEngineException;

class JdtSyntaxHelperClientV4ContractTest {

  @Test
  void rejectsEveryLegacyStartOverloadWithoutExplicitTargetPlatform() {
    Duration timeout = Duration.ofSeconds(1);
    List<String> command = List.of("not-started-without-a-target");

    assertThatThrownBy(() -> JdtSyntaxHelperClient.start(command, timeout, timeout))
        .isInstanceOfSatisfying(
            CodeEngineException.class,
            failure ->
                assertThat(failure.code())
                    .isEqualTo(CodeEngineException.ENGINE_CONFIGURATION_INVALID));
    assertThatThrownBy(
            () ->
                JdtSyntaxHelperClient.start(
                    Path.of("unused-java-home"), Path.of("unused-helper.jar"), timeout, timeout))
        .isInstanceOfSatisfying(
            CodeEngineException.class,
            failure ->
                assertThat(failure.code())
                    .isEqualTo(CodeEngineException.ENGINE_CONFIGURATION_INVALID));
    assertThatThrownBy(
            () ->
                JdtSyntaxHelperClient.start(
                    Path.of("unused-java-home"),
                    Path.of("unused-helper.jar"),
                    timeout,
                    timeout,
                    List.of(),
                    List.of()))
        .isInstanceOfSatisfying(
            CodeEngineException.class,
            failure ->
                assertThat(failure.code())
                    .isEqualTo(CodeEngineException.ENGINE_CONFIGURATION_INVALID));
  }

  @Test
  void pairsTheV4WireWithItsHelperAndKeepsModuleEnvironmentsIsolated(@TempDir Path temp)
      throws Exception {
    Path helperJar = protocolPeerJar(temp.resolve("protocol-peer.jar"));
    Path javaHome = Path.of(System.getProperty("java.home"));

    String moduleAMessage =
        describeWithEnvironment(
            javaHome,
            helperJar,
            "17",
            temp.resolve("platform-A"),
            temp.resolve("source-A"),
            temp.resolve("classpath-A"),
            "module-a/A.java");
    String moduleBMessage =
        describeWithEnvironment(
            javaHome,
            helperJar,
            "8",
            temp.resolve("platform-B"),
            temp.resolve("source-B"),
            temp.resolve("classpath-B"),
            "module-b/B.java");

    assertThat(moduleAMessage)
        .contains(
            "protocol=jdt-syntax-v4",
            "targetJdk=17",
            temp.resolve("platform-A").toAbsolutePath().normalize().toString(),
            temp.resolve("source-A").toAbsolutePath().normalize().toString(),
            temp.resolve("classpath-A").toAbsolutePath().normalize().toString())
        .doesNotContain("platform-B", "source-B", "classpath-B");
    assertThat(moduleBMessage)
        .contains(
            "protocol=jdt-syntax-v4",
            "targetJdk=8",
            temp.resolve("platform-B").toAbsolutePath().normalize().toString(),
            temp.resolve("source-B").toAbsolutePath().normalize().toString(),
            temp.resolve("classpath-B").toAbsolutePath().normalize().toString())
        .doesNotContain("platform-A", "source-A", "classpath-A");
  }

  private static String describeWithEnvironment(
      Path javaHome,
      Path helperJar,
      String targetJdk,
      Path platform,
      Path sourcepath,
      Path classpath,
      String sourceKey)
      throws Exception {
    Files.createDirectories(platform);
    Files.createDirectories(sourcepath);
    Files.createDirectories(classpath);
    try (JdtSyntaxHelperClient client =
        JdtSyntaxHelperClient.start(
            javaHome,
            helperJar,
            Duration.ofSeconds(5),
            Duration.ofSeconds(2),
            List.of(sourcepath),
            List.of(classpath),
            targetJdk,
            List.of(platform))) {
      JdtSyntaxProtocol.Response response =
          client.describe(sourceKey, targetJdk.equals("8") ? "1.8" : "17", "class Probe {}\n");
      assertThat(response.diagnostics()).hasSize(1);
      return response.diagnostics().get(0).message();
    }
  }

  private static Path protocolPeerJar(Path jar) throws IOException {
    Manifest manifest = new Manifest();
    manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
    manifest
        .getMainAttributes()
        .put(Attributes.Name.MAIN_CLASS, JdtSyntaxHelperProtocolPeer.class.getName());
    String classResource =
        "/" + JdtSyntaxHelperProtocolPeer.class.getName().replace('.', '/') + ".class";
    try (InputStream classBytes =
            JdtSyntaxHelperProtocolPeer.class.getResourceAsStream(classResource);
        JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
      if (classBytes == null) {
        throw new IOException("test helper class bytes are unavailable");
      }
      output.putNextEntry(
          new JarEntry(JdtSyntaxHelperProtocolPeer.class.getName().replace('.', '/') + ".class"));
      classBytes.transferTo(output);
      output.closeEntry();
    }
    return jar;
  }
}
