package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.Sha256Digest;

class FrozenProcessSourceCorpusTest {

  @Test
  void exposesCanonicalDirectoryReadsExactTextSearchesLiteralsAndRejectsEscapes() {
    String java =
        "package demo;\n" + "class OrderService {\n" + "  String state = \"待审核\";\n" + "}\n";
    String xml =
        "<mapper>\n"
            + "  <select id=\"find\">\n"
            + "    SELECT * FROM orders WHERE status = 0\n"
            + "  </select>\n"
            + "</mapper>\n";
    String vue = "<template>\n" + "  <span>{{ orderLabel }}</span>\n" + "</template>\n";

    FrozenProcessSourceCorpus corpus =
        new FrozenProcessSourceCorpus(
            sourceTextSet(
                List.of(
                    text("web/Order.vue", vue, "text/html"),
                    text("src/main/resources/OrderMapper.xml", xml, "application/xml"),
                    text("src/main/java/demo/OrderService.java", java, "text/x-java-source"))));

    assertThat(corpus.files())
        .extracting("path")
        .containsExactly(
            "src/main/java/demo/OrderService.java",
            "src/main/resources/OrderMapper.xml",
            "web/Order.vue");
    assertThat(corpus.files())
        .extracting("mediaType")
        .containsExactly("text/x-java-source", "application/xml", "text/html");
    assertThat(corpus.files())
        .extracting("sizeBytes")
        .containsExactly(
            (long) java.getBytes(StandardCharsets.UTF_8).length,
            (long) xml.getBytes(StandardCharsets.UTF_8).length,
            (long) vue.getBytes(StandardCharsets.UTF_8).length);
    assertThat(corpus.files()).extracting("lineCount").containsExactly(4, 5, 3);

    Object ranged =
        corpus.read(
            "src/main/java/demo/OrderService.java", FrozenProcessSourceCorpus.ReadRange.of(2, 3));
    assertThat(ranged)
        .extracting("path", "startLine", "endLine", "text", "complete")
        .containsExactly(
            "src/main/java/demo/OrderService.java",
            2,
            3,
            "class OrderService {\n  String state = \"待审核\";\n",
            false);

    Object whole =
        corpus.read(
            "src/main/resources/OrderMapper.xml", FrozenProcessSourceCorpus.ReadRange.wholeFile());
    assertThat(whole)
        .extracting("path", "startLine", "endLine", "text", "complete")
        .containsExactly("src/main/resources/OrderMapper.xml", 1, 5, xml, true);

    assertThat(
            corpus.search(
                List.of("web/Order.vue", "src/main/resources/OrderMapper.xml"), "status = 0", 1))
        .singleElement()
        .satisfies(
            hit ->
                assertThat(hit)
                    .extracting("path", "startLine", "endLine", "text")
                    .containsExactly(
                        "src/main/resources/OrderMapper.xml",
                        2,
                        4,
                        "  <select id=\"find\">\n    SELECT * FROM orders WHERE status = 0\n  </select>\n"));

    assertThatThrownBy(
            () -> corpus.read("missing.java", FrozenProcessSourceCorpus.ReadRange.wholeFile()))
        .hasMessageStartingWith("PROCESS_SOURCE_TEXT_");
    assertThatThrownBy(
            () -> corpus.read("../outside.java", FrozenProcessSourceCorpus.ReadRange.wholeFile()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(
            () -> corpus.read("/absolute.java", FrozenProcessSourceCorpus.ReadRange.wholeFile()))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> corpus.search(List.of("missing.java"), "literal", 0))
        .hasMessageStartingWith("PROCESS_SOURCE_TEXT_");
  }

  @Test
  void preservesCrLfNoTrailingNewlineAndRepresentsAnEmptyWholeFile() {
    String crlf = "first\r\nsecond\r\n";
    String noTrailingNewline = "one\r\ntwo";
    FrozenProcessSourceCorpus corpus =
        new FrozenProcessSourceCorpus(
            sourceTextSet(
                List.of(
                    text("empty.txt", "", "text/plain"),
                    text("no-trailing.txt", noTrailingNewline, "text/plain"),
                    text("crlf.txt", crlf, "text/plain"))));

    Object crlfResult = corpus.read("crlf.txt", FrozenProcessSourceCorpus.ReadRange.wholeFile());
    assertThat(crlfResult)
        .extracting("path", "startLine", "endLine", "text", "complete")
        .containsExactly("crlf.txt", 1, 2, crlf, true);

    Object noTrailingResult =
        corpus.read("no-trailing.txt", FrozenProcessSourceCorpus.ReadRange.wholeFile());
    assertThat(noTrailingResult)
        .extracting("path", "startLine", "endLine", "text", "complete")
        .containsExactly("no-trailing.txt", 1, 2, noTrailingNewline, true);

    Object emptyResult = corpus.read("empty.txt", FrozenProcessSourceCorpus.ReadRange.wholeFile());
    assertThat(emptyResult)
        .extracting("path", "startLine", "endLine", "text", "complete")
        .containsExactly("empty.txt", 1, 0, "", true);
  }

  private static VerifiedSourceTextSet sourceTextSet(List<VerifiedSourceTextDocument> documents) {
    return new VerifiedSourceTextSet(
        "snapshot:" + "a".repeat(64),
        "COMPLETE_CAPTURE",
        true,
        reference("capability-profile", '1'),
        reference("source-inventory", '2'),
        reference("verified-snapshot", '3'),
        new ArtifactControls(
            Sha256Digest.parse("4".repeat(64)),
            Sha256Digest.parse("5".repeat(64)),
            Sha256Digest.parse("6".repeat(64)),
            Sha256Digest.parse("7".repeat(64)),
            new ArtifactPolicyRegistryReference(
                ArtifactId.parse("artifact-policy-registry:" + "8".repeat(64)),
                Sha256Digest.parse("8".repeat(64)))),
        documents);
  }

  private static VerifiedSourceTextDocument text(String path, String source, String mediaType) {
    byte[] bytes = source.getBytes(StandardCharsets.UTF_8);
    String sha256 = sha256(bytes);
    return new VerifiedSourceTextDocument(
        ArtifactId.parse("file:" + sha256((path + "\n" + sha256).getBytes(StandardCharsets.UTF_8))),
        path,
        "100644",
        mediaType,
        bytes.length,
        Sha256Digest.parse(sha256),
        ImmutableBytes.copyOf(bytes));
  }

  private static ArtifactReference reference(String prefix, char digit) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(digit).repeat(64)),
        Sha256Digest.parse(String.valueOf(digit).repeat(64)));
  }

  private static String sha256(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }
}
