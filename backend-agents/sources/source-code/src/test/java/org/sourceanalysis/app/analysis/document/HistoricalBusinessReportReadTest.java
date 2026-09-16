package org.sourceanalysis.app.analysis.document;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.AnalysisRunId;
import org.sourceanalysis.app.artifact.AnalysisStepKey;
import org.sourceanalysis.app.artifact.AnalysisStepModuleAddress;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalJsonCodec;
import org.sourceanalysis.app.artifact.CanonicalMediaType;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModuleCompletionStatus;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceipt;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Proves historical nine-section checkpoints remain readable after their producer is retired. */
class HistoricalBusinessReportReadTest {

  private static final CanonicalJsonCodec JSON = new CanonicalJsonCodec();

  @Test
  void reopensAndRerendersAFrozenHistoricalCheckpointWithoutItsProducer() {
    AnalysisRunId runId = AnalysisRunId.parse("analysis-run:" + "1".repeat(64));
    ModulePublicationReference checkpoint =
        new ModulePublicationReference(
            new AnalysisStepModuleAddress(
                runId, AnalysisStepKey.NINE_SECTION_DOCUMENT, 1, "business-report-publisher"),
            ModuleArtifactRoot.parse("module-root:" + "2".repeat(64)),
            ModuleReceiptId.parse("module-receipt:" + "3".repeat(64)),
            new Sha256Digest("4".repeat(64)));
    SourceReference source =
        new SourceReference(
            "S1", "src/main/java/example/OrderService.java", 10, 12, "save(order);");
    BusinessReport expectedReport = report();
    String expectedMarkdown =
        BusinessReportMarkdownRenderer.render(expectedReport, List.of(source));
    List<VerifiedCanonicalPayload> payloads =
        List.of(
            payload(
                "business-report.json",
                "BUSINESS_DOCUMENT_REPORT",
                "business-document-report-v1",
                CanonicalMediaType.APPLICATION_JSON,
                reportJson(expectedReport)),
            payload(
                "document.md",
                "BUSINESS_DOCUMENT_MARKDOWN",
                "business-document-markdown-v2",
                CanonicalMediaType.TEXT_MARKDOWN,
                ImmutableBytes.copyOf(expectedMarkdown.getBytes(StandardCharsets.UTF_8))),
            payload(
                "report-validation.json",
                "BUSINESS_DOCUMENT_VALIDATION",
                "business-document-validation-v1",
                CanonicalMediaType.APPLICATION_JSON,
                validationJson()),
            payload(
                "source-refs.jsonl",
                "BUSINESS_DOCUMENT_SOURCE_REFERENCES",
                "business-document-source-references-v1",
                CanonicalMediaType.APPLICATION_X_NDJSON,
                sourceJsonl(source)));
    ReopenedModulePublication reopened =
        new ReopenedModulePublication(
            checkpoint,
            new ModuleReceipt(
                "module-receipt-v1",
                checkpoint.moduleReceiptId(),
                checkpoint.address(),
                "v1",
                List.of(),
                null,
                ModuleCompletionStatus.SUCCEEDED,
                payloads.stream().map(VerifiedCanonicalPayload::descriptor).toList(),
                checkpoint.moduleArtifactRoot(),
                List.of()),
            payloads);
    CanonicalModuleArtifactStore store = frozenStore(reopened);

    BusinessReportPublication publication =
        new BusinessReportCheckpointReader(store).reopen(checkpoint);
    assertThat(publication.businessReport()).isEqualTo(expectedReport);
    assertThat(publication.sourceReferences()).containsExactly(source);
    assertThat(publication.documentMarkdown()).isEqualTo(expectedMarkdown);
    assertThat(new BusinessReportCheckpointReader(store).rerender(publication))
        .isEqualTo(expectedMarkdown);

    assertThat(new BusinessReportCheckpointRenderer(store).render(runId, checkpoint).sizeBytes())
        .isEqualTo(expectedMarkdown.getBytes(StandardCharsets.UTF_8).length);
  }

  private static BusinessReport report() {
    List<BusinessReportSection> sections = new ArrayList<>();
    for (int index = 0; index < NineSectionContract.titles().size(); index++) {
      sections.add(
          new BusinessReportSection(
              index + 1,
              NineSectionContract.titles().get(index),
              List.of(new BusinessReportContent("本章保留历史业务内容。", List.of("S1"))),
              List.of()));
    }
    return new BusinessReport("历史仓库业务报告", sections);
  }

  private static ImmutableBytes reportJson(BusinessReport report) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("artifactId", "business-document-report:" + "a".repeat(64));
    root.put("artifactType", "BUSINESS_DOCUMENT_REPORT");
    root.put("schemaVersion", "business-document-report-v1");
    ArrayNode sections = root.putArray("sections");
    report
        .sections()
        .forEach(
            section -> {
              ObjectNode value = sections.addObject();
              value.putArray("items");
              value.put("number", section.number());
              ArrayNode paragraphs = value.putArray("paragraphs");
              section
                  .paragraphs()
                  .forEach(
                      paragraph -> {
                        ObjectNode content = paragraphs.addObject();
                        content.putArray("refs").add("S1");
                        content.put("text", paragraph.text());
                      });
              value.put("title", section.title());
            });
    root.put("title", report.title());
    return JSON.encodeCanonical(root);
  }

  private static ImmutableBytes validationJson() {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("artifactId", "business-document-validation:" + "b".repeat(64));
    root.put("artifactType", "BUSINESS_DOCUMENT_VALIDATION");
    root.put("schemaVersion", "business-document-validation-v1");
    root.put("sectionCount", 9);
    root.put("sectionOrderValid", true);
    root.put("sourceRefsValid", true);
    root.put("status", "VALID");
    return JSON.encodeCanonical(root);
  }

  private static ImmutableBytes sourceJsonl(SourceReference source) {
    ObjectNode root = JsonNodeFactory.instance.objectNode();
    root.put("endLine", source.endLine());
    root.put("file", source.file());
    root.put("recordType", "SOURCE_REFERENCE");
    root.put("ref", source.ref());
    root.put("schemaVersion", "business-document-source-references-v1");
    root.put("snippet", source.snippet());
    root.put("startLine", source.startLine());
    byte[] canonical = JSON.encodeCanonical(root).copyToByteArray();
    byte[] withNewline = java.util.Arrays.copyOf(canonical, canonical.length + 1);
    withNewline[withNewline.length - 1] = '\n';
    return ImmutableBytes.copyOf(withNewline);
  }

  private static VerifiedCanonicalPayload payload(
      String fileName,
      String type,
      String schema,
      CanonicalMediaType mediaType,
      ImmutableBytes bytes) {
    String prefix =
        type.equals("BUSINESS_DOCUMENT_REPORT")
            ? "business-document-report"
            : type.equals("BUSINESS_DOCUMENT_VALIDATION")
                ? "business-document-validation"
                : "artifact";
    char fill =
        type.equals("BUSINESS_DOCUMENT_REPORT")
            ? 'a'
            : type.equals("BUSINESS_DOCUMENT_VALIDATION") ? 'b' : 'c';
    return new VerifiedCanonicalPayload(
        new ArtifactDescriptor(
            fileName,
            type,
            schema,
            ArtifactId.parse(prefix + ":" + String.valueOf(fill).repeat(64)),
            mediaType,
            bytes.size(),
            new Sha256Digest(String.valueOf(fill).repeat(64))),
        bytes);
  }

  private static CanonicalModuleArtifactStore frozenStore(ReopenedModulePublication reopened) {
    return new CanonicalModuleArtifactStore() {
      @Override
      public InstalledModulePublication install(ModuleInstallRequest request) {
        throw new AssertionError("historical reading must not install artifacts");
      }

      @Override
      public CanonicalArtifactPolicy resolveArtifactPolicy(
          org.sourceanalysis.app.artifact.ArtifactPolicyKey key) {
        throw new AssertionError("historical reading must not resolve producer policies");
      }

      @Override
      public ReopenedModulePublication reopen(ModulePublicationReference reference) {
        assertThat(reference).isEqualTo(reopened.reference());
        return reopened;
      }
    };
  }
}
