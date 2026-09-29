package org.sourceanalysis.app.capture.preparation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sourceanalysis.app.analysis.inventory.DirectorySourceOrigin;
import org.sourceanalysis.app.analysis.inventory.GitCommitSourceOrigin;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Direct contracts for code-source hashing and trusted Git version measurement. */
class SourcePreparationToolIdentityFactoryTest {

  private static final String CLASSES_DOMAIN = "source-preparation-build-classes-v1";

  @TempDir Path temporaryDirectory;

  @Test
  void jarBuildDigestCoversTheCompleteRawJarFile() throws Exception {
    Path jar = temporaryDirectory.resolve("producer.jar");
    byte[] jarBytes = new byte[] {0x50, 0x4b, 0x03, 0x04, 0x00, (byte) 0xff, 0x2a};
    Files.write(jar, jarBytes);
    AtomicInteger gitProbes = new AtomicInteger();

    SourcePreparationToolIdentity identity =
        factory(jar, gitProbes).detect(directoryOrigin(temporaryDirectory.resolve("source")));

    assertThat(identity.buildSha256()).isEqualTo(digest(jarBytes));
    assertThat(identity.trustedGitVersion()).isNull();
    assertThat(gitProbes).hasValue(0);
  }

  @Test
  void classesDigestSortsUtf8PathsFramesPathAndBytesAndIgnoresOtherPackages() throws Exception {
    Map<String, byte[]> included =
        Map.of(
            "org/sourceanalysis/app/z/Last.class", new byte[] {0x7f, 0x00},
            "org/sourceanalysis/app/a/First.class", new byte[] {0x01, 0x02, 0x03});
    Path first = temporaryDirectory.resolve("classes-first");
    writeClassOutput(first, included, true);
    Files.createDirectories(first.resolve("org/sourceanalysis/other"));
    Files.write(first.resolve("org/sourceanalysis/other/Ignored.class"), new byte[] {9});
    Files.write(first.resolve("outside.class"), new byte[] {8});

    Path second = temporaryDirectory.resolve("classes-second");
    writeClassOutput(second, included, false);
    Files.createDirectories(second.resolve("org/sourceanalysis/other"));
    Files.write(second.resolve("org/sourceanalysis/other/Ignored.class"), new byte[] {4, 5});
    Files.write(second.resolve("outside.class"), new byte[] {6, 7});

    Sha256Digest expected = expectedClassesDigest(included);
    Sha256Digest firstDigest = detect(first).buildSha256();
    Sha256Digest secondDigest = detect(second).buildSha256();

    assertThat(firstDigest).isEqualTo(expected);
    assertThat(secondDigest).isEqualTo(expected);
  }

  @Test
  void classesDigestDoesNotCollapsePathAndByteBoundaries() throws Exception {
    Path first = temporaryDirectory.resolve("classes-path-a");
    Files.createDirectories(first.resolve("org/sourceanalysis/app"));
    Files.write(first.resolve("org/sourceanalysis/app/a"), new byte[] {'b', 'c'});

    Path second = temporaryDirectory.resolve("classes-path-ab");
    Files.createDirectories(second.resolve("org/sourceanalysis/app"));
    Files.write(second.resolve("org/sourceanalysis/app/ab"), new byte[] {'c'});

    assertThat(detect(first).buildSha256()).isNotEqualTo(detect(second).buildSha256());
  }

  @Test
  void rejectsSymlinkedAndUnreadableClassesBuildContents() throws Exception {
    Path linked = temporaryDirectory.resolve("classes-with-link");
    Path linkedPackage = linked.resolve("org/sourceanalysis/app");
    Files.createDirectories(linkedPackage);
    Path outside = temporaryDirectory.resolve("outside.class");
    Files.write(outside, new byte[] {1, 2, 3});
    Files.createSymbolicLink(linkedPackage.resolve("Linked.class"), outside);

    assertThatThrownBy(() -> detect(linked)).isInstanceOf(IOException.class);

    Path unreadable = temporaryDirectory.resolve("classes-unreadable");
    Path unreadablePackage = unreadable.resolve("org/sourceanalysis/app");
    Files.createDirectories(unreadablePackage);
    Path unreadableClass = unreadablePackage.resolve("NoRead.class");
    Files.write(unreadableClass, new byte[] {4, 5, 6});
    try {
      Files.setPosixFilePermissions(unreadableClass, EnumSet.noneOf(PosixFilePermission.class));
    } catch (UnsupportedOperationException unsupportedPermissions) {
      Assumptions.assumeTrue(false, "POSIX read permissions are unavailable on this filesystem");
    }
    Assumptions.assumeFalse(
        Files.isReadable(unreadableClass),
        "The test process can bypass file read permissions, so unreadability cannot be exercised");

    assertThatThrownBy(() -> detect(unreadable)).isInstanceOf(IOException.class);
  }

  @Test
  void reportsDirectoryTraversalFailuresAsCheckedIoExceptions() throws Exception {
    Path classes = temporaryDirectory.resolve("classes-untraversable");
    Path blockedDirectory = classes.resolve("org/sourceanalysis/app/blocked");
    Path blockedClass = blockedDirectory.resolve("Hidden.class");
    Files.createDirectories(blockedDirectory);
    Files.write(blockedClass, new byte[] {7, 8, 9});
    try {
      Files.setPosixFilePermissions(blockedDirectory, EnumSet.noneOf(PosixFilePermission.class));
    } catch (UnsupportedOperationException unsupportedPermissions) {
      Assumptions.assumeTrue(
          false, "POSIX directory permissions are unavailable on this filesystem");
    }
    Assumptions.assumeFalse(
        Files.isExecutable(blockedDirectory),
        "The test process can bypass directory permissions, so traversal denial cannot be exercised");

    assertThatThrownBy(() -> detect(classes)).isInstanceOf(IOException.class);
  }

  @Test
  void directoryOriginsSkipGitProbeAndGitOriginsMeasureAndSaveItExactlyOnce() throws Exception {
    Path classes = temporaryDirectory.resolve("classes");
    Files.createDirectories(classes.resolve("org/sourceanalysis/app"));
    Files.write(classes.resolve("org/sourceanalysis/app/Anchor.class"), new byte[] {1});
    AtomicInteger probes = new AtomicInteger();
    SourcePreparationToolIdentityFactory factory =
        new SourcePreparationToolIdentityFactory(
            classes,
            "Fixture Vendor",
            "Fixture Java",
            () -> {
              probes.incrementAndGet();
              return "git version 2.44.1-fixture";
            });

    SourcePreparationToolIdentity directoryIdentity =
        factory.detect(directoryOrigin(temporaryDirectory.resolve("directory-source")));
    assertThat(probes).hasValue(0);
    assertThat(directoryIdentity.trustedGitVersion()).isNull();

    SourcePreparationToolIdentity gitIdentity =
        factory.detect(
            new GitCommitSourceOrigin(
                "fixture:git",
                temporaryDirectory.resolve("git-source").toAbsolutePath().normalize(),
                "a".repeat(40)));
    assertThat(probes).hasValue(1);
    assertThat(gitIdentity.trustedGitVersion()).isEqualTo("git version 2.44.1-fixture");
  }

  @Test
  void productionConstructorUsesTheProductCodeSourceAndRunningJvm() throws Exception {
    AtomicInteger probes = new AtomicInteger();
    SourcePreparationToolIdentityFactory factory =
        new SourcePreparationToolIdentityFactory(
            SourcePreparationToolIdentity.class,
            () -> {
              probes.incrementAndGet();
              return "git version should-not-be-requested";
            });

    SourcePreparationToolIdentity identity =
        factory.detect(directoryOrigin(temporaryDirectory.resolve("source")));

    assertThat(identity.producerVersion()).isEqualTo("verified-source-inventory/v3");
    assertThat(identity.javaVendor()).isEqualTo(System.getProperty("java.vendor"));
    assertThat(identity.javaVersion()).isEqualTo(System.getProperty("java.version"));
    assertThat(identity.buildSha256()).isNotNull();
    assertThat(identity.trustedGitVersion()).isNull();
    assertThat(probes).hasValue(0);
  }

  private SourcePreparationToolIdentity detect(Path codeSourceLocation) throws IOException {
    return factory(codeSourceLocation, new AtomicInteger())
        .detect(directoryOrigin(temporaryDirectory.resolve("source")));
  }

  private SourcePreparationToolIdentityFactory factory(Path codeSource, AtomicInteger probes) {
    return new SourcePreparationToolIdentityFactory(
        codeSource,
        "Fixture Vendor",
        "Fixture Java",
        () -> {
          probes.incrementAndGet();
          return "git version fixture";
        });
  }

  private DirectorySourceOrigin directoryOrigin(Path root) {
    return new DirectorySourceOrigin("fixture:directory", root.toAbsolutePath().normalize());
  }

  private void writeClassOutput(Path root, Map<String, byte[]> files, boolean reverse)
      throws IOException {
    List<Map.Entry<String, byte[]>> entries = new ArrayList<>(files.entrySet());
    entries.sort(Map.Entry.comparingByKey());
    if (reverse) {
      java.util.Collections.reverse(entries);
    }
    for (Map.Entry<String, byte[]> entry : entries) {
      Path file = root.resolve(entry.getKey());
      Files.createDirectories(file.getParent());
      Files.write(file, entry.getValue());
    }
  }

  private Sha256Digest expectedClassesDigest(Map<String, byte[]> files) {
    MessageDigest digest = sha256Digest();
    frame(digest, CLASSES_DOMAIN.getBytes(StandardCharsets.UTF_8));
    files.entrySet().stream()
        .sorted((left, right) -> compareUnsignedUtf8(left.getKey(), right.getKey()))
        .forEach(
            entry -> {
              frame(digest, entry.getKey().getBytes(StandardCharsets.UTF_8));
              frame(digest, entry.getValue());
            });
    return new Sha256Digest(HexFormat.of().formatHex(digest.digest()));
  }

  private int compareUnsignedUtf8(String left, String right) {
    byte[] leftBytes = left.getBytes(StandardCharsets.UTF_8);
    byte[] rightBytes = right.getBytes(StandardCharsets.UTF_8);
    int common = Math.min(leftBytes.length, rightBytes.length);
    for (int index = 0; index < common; index++) {
      int leftValue = Byte.toUnsignedInt(leftBytes[index]);
      int rightValue = Byte.toUnsignedInt(rightBytes[index]);
      if (leftValue != rightValue) {
        return Integer.compare(leftValue, rightValue);
      }
    }
    return Integer.compare(leftBytes.length, rightBytes.length);
  }

  private void frame(MessageDigest digest, byte[] value) {
    digest.update(ByteBuffer.allocate(Long.BYTES).putLong(value.length).array());
    digest.update(value);
  }

  private Sha256Digest digest(byte[] value) {
    return new Sha256Digest(HexFormat.of().formatHex(sha256Digest().digest(value)));
  }

  private MessageDigest sha256Digest() {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
