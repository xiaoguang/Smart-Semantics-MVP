package org.sourceanalysis.app.analysis.flow.compiler;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactReference;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Public value-contract tests for a complete, unordered entry denominator. */
class FlowCompilationTest {

  @Test
  void acceptsACompleteCompilationWhenEntryAndContentAddressedFlowOrdersDiffer() {
    FlowCompilation compilation =
        new FlowCompilation(
            profile(),
            List.of(
                new FlowCompilation.EntryDisposition("entry:a", "COMPILED", "flow:z", List.of()),
                new FlowCompilation.EntryDisposition("entry:b", "COMPILED", "flow:a", List.of())),
            List.of(flow("flow:a", "entry:b"), flow("flow:z", "entry:a")),
            List.of());

    assertThat(compilation.entryDispositions())
        .extracting(FlowCompilation.EntryDisposition::entryId)
        .containsExactly("entry:a", "entry:b");
    assertThat(compilation.flowSlices())
        .extracting(FlowCompilation.FlowSlice::flowSliceId)
        .containsExactly("flow:a", "flow:z");
  }

  @Test
  void acceptsACompleteZeroFlowDenominatorAndAnEntryGapThatHasNoCompiledFlow() {
    FlowCompilation empty = new FlowCompilation(profile(), List.of(), List.of(), List.of());
    FlowCompilation gapped =
        new FlowCompilation(
            profile(),
            List.of(
                new FlowCompilation.EntryDisposition(
                    "entry:a", "GAP", null, List.of("gap:a"), "FLOW_GRAPH_REFERENCE_BROKEN")),
            List.of(),
            List.of(
                new FlowCompilation.FlowGap(
                    "gap:a",
                    "ENTRY",
                    "FLOW_GRAPH_REFERENCE_BROKEN",
                    List.of("entry:a"),
                    List.of())));

    assertThat(empty.entryDispositions()).isEmpty();
    assertThat(empty.flowSlices()).isEmpty();
    assertThat(gapped.flowSlices()).isEmpty();
    assertThat(gapped.flowGaps())
        .extracting(FlowCompilation.FlowGap::reasonCode)
        .containsExactly("FLOW_GRAPH_REFERENCE_BROKEN");
  }

  @Test
  void retainsRepeatedCallShapeWhenTheFrozenSourceEvidenceShowsSeparateOccurrences() {
    FlowCompilation.CallContext first =
        new FlowCompilation.CallContext(
            "example.Controller#submit()",
            "example.Service#save(java.lang.String)",
            List.of("requestId"),
            "EXACT",
            false,
            List.of(),
            List.of(),
            List.of("evidence:call-at-12"));
    FlowCompilation.CallContext second =
        new FlowCompilation.CallContext(
            "example.Controller#submit()",
            "example.Service#save(java.lang.String)",
            List.of("requestId"),
            "EXACT",
            false,
            List.of(),
            List.of(),
            List.of("evidence:call-at-23"));

    FlowCompilation.EntryContext context =
        new FlowCompilation.EntryContext(
            "entry-context:two-source-occurrences",
            "entry:submit",
            null,
            "HTTP POST /submit",
            "example.Controller#submit()",
            List.of(first, second),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of(),
            List.of());

    assertThat(context.calls()).containsExactly(first, second);
  }

  private static FlowCompilation.FlowSlice flow(String flowId, String entryId) {
    return new FlowCompilation.FlowSlice(
        flowId,
        entryId,
        "HTTP POST /" + entryId.substring("entry:".length()),
        "node:" + entryId,
        List.of("node:" + entryId),
        List.of(),
        List.of(),
        List.of(
            new FlowCompilation.OutcomePath(
                "outcome:" + entryId,
                List.of(),
                "terminal:" + entryId,
                "ENTRY_RETURN_TERMINAL",
                List.of(),
                List.of(),
                List.of())),
        List.of(),
        List.of());
  }

  private static FlowCompilationProfile profile() {
    return new FlowCompilationProfile(
        new ArtifactReference(
            ArtifactId.parse("flow-profile:" + sha256("value-contract")),
            new Sha256Digest(sha256("value-contract-bytes"))),
        16,
        8,
        64,
        96,
        32,
        64,
        256);
  }

  private static String sha256(String value) {
    try {
      return java.util.HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IllegalStateException("SHA-256 must be available", unavailable);
    }
  }
}
