package org.sourceanalysis.tools.jdtsyntax;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class JdtSyntaxHelperExecutableIT {

  @Test
  void shadedJarStartsAndReturnsJdtSyntax() throws Exception {
    String source = "class Example { int value() { return 7; } }\n";
    ObjectMapper mapper = new ObjectMapper();
    String request =
        mapper.writeValueAsString(
                new JdtSyntaxProtocol.Request(
                    JdtSyntaxProtocol.VERSION,
                    JdtSyntaxProtocol.DESCRIBE_COMPILATION_UNIT,
                    "executable-it",
                    "source:example",
                    "17",
                    sha256(source),
                    source))
            + "\n";
    Path jar = Path.of("target", "source-code-analysis-jdt-syntax-helper.jar").toAbsolutePath();
    Process process =
        new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-jar",
                jar.toString())
            .start();
    process.getOutputStream().write(request.getBytes(StandardCharsets.UTF_8));
    process.getOutputStream().close();

    assertThat(process.waitFor(20, TimeUnit.SECONDS)).isTrue();
    String stdout = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    String stderr = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
    assertThat(process.exitValue()).describedAs(stderr).isZero();
    JsonNode response = mapper.readTree(stdout);
    assertThat(response.path("requestId").asText()).isEqualTo("executable-it");
    assertThat(response.path("declarations").toString()).contains("value");
  }

  private static String sha256(String source) throws Exception {
    return HexFormat.of()
        .formatHex(
            MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
  }
}
