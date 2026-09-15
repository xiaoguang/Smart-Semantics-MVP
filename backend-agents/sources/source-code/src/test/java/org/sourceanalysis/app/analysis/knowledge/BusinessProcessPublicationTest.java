package org.sourceanalysis.app.analysis.knowledge;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.sourceanalysis.app.analysis.interpretation.material.SourceReference;
import org.sourceanalysis.app.artifact.ArtifactControls;
import org.sourceanalysis.app.artifact.ArtifactDescriptor;
import org.sourceanalysis.app.artifact.ArtifactId;
import org.sourceanalysis.app.artifact.ArtifactPolicyKey;
import org.sourceanalysis.app.artifact.ArtifactPolicyRegistryReference;
import org.sourceanalysis.app.artifact.CanonicalArtifactPolicy;
import org.sourceanalysis.app.artifact.CanonicalModuleArtifactStore;
import org.sourceanalysis.app.artifact.CanonicalModulePayload;
import org.sourceanalysis.app.artifact.ImmutableBytes;
import org.sourceanalysis.app.artifact.InstalledModulePublication;
import org.sourceanalysis.app.artifact.ModuleArtifactRoot;
import org.sourceanalysis.app.artifact.ModuleInstallDisposition;
import org.sourceanalysis.app.artifact.ModuleInstallRequest;
import org.sourceanalysis.app.artifact.ModulePublicationReference;
import org.sourceanalysis.app.artifact.ModuleReceipt;
import org.sourceanalysis.app.artifact.ModuleReceiptId;
import org.sourceanalysis.app.artifact.ReopenedModulePublication;
import org.sourceanalysis.app.artifact.Sha256Digest;
import org.sourceanalysis.app.artifact.VerifiedCanonicalPayload;

/** Guards deterministic publication and fresh reopen of the four Step07 process files. */
class BusinessProcessPublicationTest {

  @Test
  void publishesAndFreshReopensTheDeterministicProcessDocument() {
    ProcessDiscoveryResult discovered = discoveryResult();
    CapturingModuleStore store =
        new CapturingModuleStore(discovered.activityCheckpoint(), discovered.materialCheckpoint());

    BusinessProcessPublication published =
        new CanonicalBusinessProcessPublisher(store).publish(discovered);
    BusinessProcessPublication reopened =
        new BusinessProcessCheckpointReader(store).reopen(published.checkpoint());

    assertThat(store.installedFileNames())
        .containsExactly(
            "business-processes.md",
            "process-coverage.json",
            "repository-business-process-catalog.json",
            "source-refs.jsonl");
    assertThat(reopened).isEqualTo(published);
    assertThat(reopened.businessProcessesMarkdown())
        .contains("当当前状态为0时，允许修改订单；否则，拒绝修改")
        .doesNotContain("// create concrete source");
    assertThat(reopened.sourceReferences())
        .extracting(SourceReference::ref)
        .containsExactly("S1", "S2", "S3");
    assertThat(new BusinessProcessCheckpointReader(store).rerender(reopened))
        .isEqualTo(reopened.businessProcessesMarkdown());
  }

  @Test
  void reopensHistoricalInputsSeparatelyFromTheCurrentOutputPolicy() {
    ProcessDiscoveryResult discovered = discoveryResult();
    ArtifactControls historicalControls = CapturingModuleStore.controls('3');
    ArtifactControls currentControls = CapturingModuleStore.controls('4');
    CapturingModuleStore inputs =
        new CapturingModuleStore(
            discovered.activityCheckpoint(), discovered.materialCheckpoint(), historicalControls);
    CapturingModuleStore outputs = new CapturingModuleStore();

    BusinessProcessPublication published =
        new CanonicalBusinessProcessPublisher(inputs, outputs, currentControls).publish(discovered);

    assertThat(inputs.reopenCount()).isEqualTo(2);
    assertThat(outputs.reopenCount()).isZero();
    assertThat(outputs.installedControls()).isEqualTo(currentControls);
    assertThat(new BusinessProcessCheckpointReader(outputs).reopen(published.checkpoint()))
        .isEqualTo(published);
  }

  private static ProcessDiscoveryResult discoveryResult() {
    try {
      java.lang.reflect.Method activities =
          BusinessProcessDiscoveryTest.class.getDeclaredMethod("activities");
      java.lang.reflect.Method materials =
          BusinessProcessDiscoveryTest.class.getDeclaredMethod("materials");
      java.lang.reflect.Method profile =
          BusinessProcessDiscoveryTest.class.getDeclaredMethod("profile");
      activities.setAccessible(true);
      materials.setAccessible(true);
      profile.setAccessible(true);
      Class<?> providerType =
          Class.forName(
              "org.sourceanalysis.app.analysis.knowledge.BusinessProcessDiscoveryTest$ScriptedProvider");
      var constructor = providerType.getDeclaredConstructor();
      constructor.setAccessible(true);
      return new DefaultBusinessProcessDiscovery(
              (org.sourceanalysis.app.adapter.provider.StructuredModelProvider)
                  constructor.newInstance())
          .discover(
              new ProcessDiscoveryRequest(
                  (org.sourceanalysis.app.analysis.interpretation.activity
                          .ActivityExplanationResult)
                      activities.invoke(null),
                  (org.sourceanalysis.app.analysis.interpretation.material
                          .BusinessMaterialBuildResult)
                      materials.invoke(null),
                  (ProcessDiscoveryProfile) profile.invoke(null)));
    } catch (ReflectiveOperationException failure) {
      throw new AssertionError(failure);
    }
  }

  private static final class CapturingModuleStore implements CanonicalModuleArtifactStore {
    private final Map<ModulePublicationReference, ReopenedModulePublication> publications =
        new HashMap<>();
    private List<String> installedFileNames = List.of();
    private ArtifactControls installedControls;
    private int reopenCount;

    private CapturingModuleStore() {}

    private CapturingModuleStore(
        ModulePublicationReference activity, ModulePublicationReference material) {
      this(activity, material, controls('3'));
    }

    private CapturingModuleStore(
        ModulePublicationReference activity,
        ModulePublicationReference material,
        ArtifactControls controls) {
      publications.put(activity, upstream(activity, controls, "activity-upstream"));
      publications.put(material, upstream(material, controls, "material-upstream"));
    }

    @Override
    public InstalledModulePublication install(ModuleInstallRequest request) {
      List<VerifiedCanonicalPayload> payloads =
          request.payloads().stream().map(CapturingModuleStore::verified).toList();
      List<ArtifactDescriptor> descriptors =
          payloads.stream().map(VerifiedCanonicalPayload::descriptor).toList();
      installedFileNames = descriptors.stream().map(ArtifactDescriptor::fileName).toList();
      installedControls = request.controls();
      String digest = "9".repeat(64);
      ModulePublicationReference reference =
          new ModulePublicationReference(
              request.address(),
              ModuleArtifactRoot.parse("module-root:" + digest),
              ModuleReceiptId.parse("module-receipt:" + digest),
              Sha256Digest.parse(digest));
      ModuleReceipt receipt =
          new ModuleReceipt(
              "module-receipt-v2",
              reference.moduleReceiptId(),
              request.address(),
              request.moduleVersion(),
              request.upstreamArtifacts(),
              request.controls(),
              request.status(),
              descriptors,
              reference.moduleArtifactRoot(),
              request.gapRefs());
      publications.put(reference, new ReopenedModulePublication(reference, receipt, payloads));
      return new InstalledModulePublication(
          reference, ModuleInstallDisposition.INSTALLED, descriptors);
    }

    @Override
    public CanonicalArtifactPolicy resolveArtifactPolicy(ArtifactPolicyKey key) {
      throw new UnsupportedOperationException();
    }

    @Override
    public ReopenedModulePublication reopen(ModulePublicationReference reference) {
      reopenCount++;
      ReopenedModulePublication reopened = publications.get(reference);
      if (reopened == null) {
        throw new IllegalArgumentException("unknown test publication");
      }
      return reopened;
    }

    private List<String> installedFileNames() {
      return installedFileNames;
    }

    private ArtifactControls installedControls() {
      return installedControls;
    }

    private int reopenCount() {
      return reopenCount;
    }

    private static ReopenedModulePublication upstream(
        ModulePublicationReference reference, ArtifactControls controls, String prefix) {
      ArtifactDescriptor descriptor =
          new ArtifactDescriptor(
              prefix + ".json",
              "TEST_UPSTREAM",
              "test-v1",
              ArtifactId.parse(prefix + ":" + "1".repeat(64)),
              org.sourceanalysis.app.artifact.CanonicalMediaType.APPLICATION_JSON,
              2,
              Sha256Digest.parse("2".repeat(64)));
      ModuleReceipt receipt =
          new ModuleReceipt(
              "module-receipt-v2",
              reference.moduleReceiptId(),
              reference.address(),
              "v1",
              List.of(),
              controls,
              org.sourceanalysis.app.artifact.ModuleCompletionStatus.SUCCEEDED,
              List.of(descriptor),
              reference.moduleArtifactRoot(),
              List.of());
      return new ReopenedModulePublication(reference, receipt, List.of());
    }

    private static VerifiedCanonicalPayload verified(CanonicalModulePayload payload) {
      Sha256Digest sha = sha256(payload.canonicalUtf8());
      ArtifactDescriptor descriptor =
          new ArtifactDescriptor(
              payload.fileName(),
              payload.artifactType(),
              payload.schemaVersion(),
              payload.artifactId(),
              payload.mediaType(),
              payload.canonicalUtf8().size(),
              sha);
      return new VerifiedCanonicalPayload(descriptor, payload.canonicalUtf8());
    }

    private static ArtifactControls controls(char fill) {
      String digest = String.valueOf(fill).repeat(64);
      return new ArtifactControls(
          Sha256Digest.parse(digest),
          Sha256Digest.parse(digest),
          Sha256Digest.parse(digest),
          Sha256Digest.parse(digest),
          new ArtifactPolicyRegistryReference(
              ArtifactId.parse("artifact-policy-registry:" + digest), Sha256Digest.parse(digest)));
    }

    private static Sha256Digest sha256(ImmutableBytes bytes) {
      try {
        return Sha256Digest.parse(
            java.util.HexFormat.of()
                .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes.copyToByteArray())));
      } catch (NoSuchAlgorithmException impossible) {
        throw new IllegalStateException(impossible);
      }
    }
  }
}
