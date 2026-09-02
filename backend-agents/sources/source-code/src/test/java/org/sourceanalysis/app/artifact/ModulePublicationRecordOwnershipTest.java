package org.sourceanalysis.app.artifact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ModulePublicationRecordOwnershipTest {

  @Test
  void publicationRecordsOwnTheirListValuesAndExposeOnlyUnmodifiableViews() {
    AnalysisRunId runId =
        AnalysisRunId.parse(
            "analysis-run:0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");
    AnalysisStepModuleAddress address =
        new AnalysisStepModuleAddress(
            runId, AnalysisStepKey.VERIFIED_SOURCE_INVENTORY, 1, "request-admission");
    ArtifactReference upstream = reference("upstream", '1');
    CanonicalModulePayload payload =
        new CanonicalModulePayload(
            "admitted-source-request.json",
            "VERIFIED_SOURCE_INVENTORY_ADMITTED_SOURCE_REQUEST",
            "verified-source-inventory-admitted-source-request-v2",
            ArtifactId.parse("source-request:" + String.valueOf('2').repeat(64)),
            CanonicalMediaType.APPLICATION_JSON,
            ImmutableBytes.copyOf("{}".getBytes(StandardCharsets.UTF_8)));
    ArtifactDescriptor descriptor =
        new ArtifactDescriptor(
            payload.fileName(),
            payload.artifactType(),
            payload.schemaVersion(),
            payload.artifactId(),
            payload.mediaType(),
            payload.canonicalUtf8().size(),
            digest('3'));
    ArtifactControls controls =
        new ArtifactControls(
            digest('4'),
            digest('5'),
            digest('6'),
            null,
            new ArtifactPolicyRegistryReference(
                ArtifactId.parse("artifact-policy-registry:" + String.valueOf('7').repeat(64)),
                digest('7')));
    ModulePublicationReference publicationReference =
        new ModulePublicationReference(
            address,
            ModuleArtifactRoot.parse("module-root:" + String.valueOf('8').repeat(64)),
            ModuleReceiptId.parse("module-receipt:" + String.valueOf('9').repeat(64)),
            digest('a'));
    List<ArtifactReference> upstreamInput = new ArrayList<>(List.of(upstream));
    List<String> gapInput = new ArrayList<>(List.of("gap:a"));
    List<CanonicalModulePayload> payloadInput = new ArrayList<>(List.of(payload));
    List<ArtifactDescriptor> descriptorInput = new ArrayList<>(List.of(descriptor));
    ModuleInstallRequest request =
        new ModuleInstallRequest(
            address,
            "v2",
            upstreamInput,
            controls,
            ModuleCompletionStatus.SUCCEEDED,
            gapInput,
            payloadInput);
    ModuleReceipt receipt =
        new ModuleReceipt(
            "module-receipt-v1",
            publicationReference.moduleReceiptId(),
            address,
            "v2",
            upstreamInput,
            controls,
            ModuleCompletionStatus.SUCCEEDED,
            descriptorInput,
            publicationReference.moduleArtifactRoot(),
            gapInput);
    InstalledModulePublication installed =
        new InstalledModulePublication(
            publicationReference, ModuleInstallDisposition.INSTALLED, descriptorInput);
    ReopenedModulePublication reopened =
        new ReopenedModulePublication(
            publicationReference,
            receipt,
            new ArrayList<>(
                List.of(new VerifiedCanonicalPayload(descriptor, payload.canonicalUtf8()))));

    upstreamInput.clear();
    gapInput.clear();
    payloadInput.clear();
    descriptorInput.clear();

    assertThat(request.upstreamArtifacts()).containsExactly(upstream);
    assertThat(request.gapRefs()).containsExactly("gap:a");
    assertThat(request.payloads()).containsExactly(payload);
    assertThat(receipt.upstreamArtifacts()).containsExactly(upstream);
    assertThat(receipt.gapRefs()).containsExactly("gap:a");
    assertThat(receipt.payloadArtifacts()).containsExactly(descriptor);
    assertThat(installed.artifactDescriptors()).containsExactly(descriptor);
    assertThat(reopened.payloads()).hasSize(1);
    assertThatThrownBy(() -> request.upstreamArtifacts().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> receipt.payloadArtifacts().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> installed.artifactDescriptors().clear())
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> reopened.payloads().clear())
        .isInstanceOf(UnsupportedOperationException.class);
  }

  private static ArtifactReference reference(String prefix, char digit) {
    return new ArtifactReference(
        ArtifactId.parse(prefix + ":" + String.valueOf(digit).repeat(64)), digest(digit));
  }

  private static Sha256Digest digest(char digit) {
    return Sha256Digest.parse(String.valueOf(digit).repeat(64));
  }
}
