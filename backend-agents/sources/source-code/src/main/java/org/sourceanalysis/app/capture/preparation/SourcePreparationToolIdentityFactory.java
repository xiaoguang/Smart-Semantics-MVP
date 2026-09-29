package org.sourceanalysis.app.capture.preparation;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.inventory.GitCommitSourceOrigin;
import org.sourceanalysis.app.analysis.inventory.SourceOrigin;
import org.sourceanalysis.app.artifact.Sha256Digest;

/** Derives a path-free preparation identity from actual application bytes and the running JVM. */
final class SourcePreparationToolIdentityFactory {

  private static final String PRODUCER_VERSION = "verified-source-inventory/v3";
  private static final String CLASSES_DOMAIN = "source-preparation-build-classes-v1";
  private static final String APPLICATION_PACKAGE = "org/sourceanalysis/app";
  private static final int COPY_BUFFER_BYTES = 16 * 1024;
  private static final Comparator<String> UTF8_ORDER =
      SourcePreparationToolIdentityFactory::compareUtf8;

  private final Class<?> productAnchor;
  private final Path fixtureCodeSourceLocation;
  private final String javaVendor;
  private final String javaVersion;
  private final TrustedGitVersionProbe trustedGitVersion;

  /** Uses the product's actual CodeSource and the JVM that is running this process. */
  SourcePreparationToolIdentityFactory(
      Class<?> productAnchor, TrustedGitVersionProbe trustedGitVersion) {
    this.productAnchor = Objects.requireNonNull(productAnchor, "product CodeSource anchor");
    this.fixtureCodeSourceLocation = null;
    this.javaVendor = requireText(System.getProperty("java.vendor"), "running Java vendor");
    this.javaVersion = requireText(System.getProperty("java.version"), "running Java version");
    this.trustedGitVersion = Objects.requireNonNull(trustedGitVersion, "trusted Git version probe");
  }

  /** Package-visible deterministic constructor for byte-level tests; never used by composition. */
  SourcePreparationToolIdentityFactory(
      Path codeSourceLocation,
      String javaVendor,
      String javaVersion,
      TrustedGitVersionProbe trustedGitVersion) {
    this.productAnchor = null;
    this.fixtureCodeSourceLocation =
        Objects.requireNonNull(codeSourceLocation, "fixture CodeSource location")
            .toAbsolutePath()
            .normalize();
    this.javaVendor = requireText(javaVendor, "fixture Java vendor");
    this.javaVersion = requireText(javaVersion, "fixture Java version");
    this.trustedGitVersion = Objects.requireNonNull(trustedGitVersion, "trusted Git version probe");
  }

  SourcePreparationToolIdentity detect(SourceOrigin origin) throws IOException {
    if (origin == null) {
      throw new IOException("source preparation origin is required for tool identity");
    }
    BuildIdentity build = buildIdentity(codeSourceLocation());
    String gitVersion =
        origin instanceof GitCommitSourceOrigin
            ? requireMeasuredText(trustedGitVersion.measure(), "trusted Git version")
            : null;
    return new SourcePreparationToolIdentity(
        PRODUCER_VERSION, build.kind(), build.sha256(), javaVendor, javaVersion, gitVersion);
  }

  private Path codeSourceLocation() throws IOException {
    if (fixtureCodeSourceLocation != null) {
      return fixtureCodeSourceLocation;
    }
    CodeSource source =
        productAnchor.getProtectionDomain() == null
            ? null
            : productAnchor.getProtectionDomain().getCodeSource();
    if (source == null
        || source.getLocation() == null
        || !"file".equals(source.getLocation().getProtocol())) {
      throw new IOException("product CodeSource location is unavailable");
    }
    try {
      return Path.of(source.getLocation().toURI()).toAbsolutePath().normalize();
    } catch (URISyntaxException invalid) {
      throw new IOException("product CodeSource location is invalid", invalid);
    }
  }

  private static BuildIdentity buildIdentity(Path location) throws IOException {
    BasicFileAttributes attributes =
        Files.readAttributes(location, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (attributes.isSymbolicLink()) {
      throw new IOException("product CodeSource must not be a symbolic link");
    }
    if (attributes.isRegularFile()) {
      return new BuildIdentity("JAR", sha256File(location));
    }
    if (attributes.isDirectory()) {
      return new BuildIdentity("CLASSES_DIRECTORY", sha256Classes(location));
    }
    throw new IOException("product CodeSource must be a regular JAR or classes directory");
  }

  private static Sha256Digest sha256File(Path file) throws IOException {
    if (Files.size(file) == 0L) {
      throw new IOException("product JAR must not be empty");
    }
    MessageDigest digest = sha256Digest();
    try (var input = Files.newInputStream(file)) {
      byte[] buffer = new byte[COPY_BUFFER_BYTES];
      for (int count; (count = input.read(buffer)) >= 0; ) {
        if (count > 0) {
          digest.update(buffer, 0, count);
        }
      }
    }
    return new Sha256Digest(java.util.HexFormat.of().formatHex(digest.digest()));
  }

  private static Sha256Digest sha256Classes(Path classesRoot) throws IOException {
    Path packageRoot = classesRoot.resolve(APPLICATION_PACKAGE);
    requireSafeDirectory(classesRoot);
    requireSafeDirectory(classesRoot.resolve("org"));
    requireSafeDirectory(classesRoot.resolve("org/sourceanalysis"));
    requireSafeDirectory(packageRoot);
    List<ClassFile> files = new ArrayList<>();
    try (var paths = Files.walk(packageRoot)) {
      List<Path> discovered;
      try {
        discovered = paths.toList();
      } catch (UncheckedIOException traversalFailure) {
        throw traversalFailure.getCause();
      }
      for (Path path : discovered) {
        BasicFileAttributes attributes =
            Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (attributes.isSymbolicLink()) {
          throw new IOException("product classes must not contain symbolic links");
        }
        if (attributes.isDirectory()) {
          continue;
        }
        if (!attributes.isRegularFile()) {
          throw new IOException("product classes must contain regular files only");
        }
        files.add(new ClassFile(path, relativeClassPath(classesRoot, path)));
      }
    }
    if (files.isEmpty()) {
      throw new IOException("product classes package is empty");
    }
    files.sort(Comparator.comparing(ClassFile::relativePath, UTF8_ORDER));
    MessageDigest digest = sha256Digest();
    frame(digest, CLASSES_DOMAIN.getBytes(StandardCharsets.UTF_8));
    for (ClassFile file : files) {
      frame(digest, file.relativePath().getBytes(StandardCharsets.UTF_8));
      frameFileBytes(digest, file.path());
    }
    return new Sha256Digest(java.util.HexFormat.of().formatHex(digest.digest()));
  }

  private static void requireSafeDirectory(Path directory) throws IOException {
    BasicFileAttributes attributes =
        Files.readAttributes(directory, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
    if (!attributes.isDirectory() || attributes.isSymbolicLink()) {
      throw new IOException("product classes directory is invalid");
    }
  }

  private static String relativeClassPath(Path classesRoot, Path file) throws IOException {
    Path relative = classesRoot.relativize(file);
    String result = relative.toString().replace(file.getFileSystem().getSeparator(), "/");
    if (!result.startsWith(APPLICATION_PACKAGE + "/")) {
      throw new IOException("product class path escapes application package");
    }
    return result;
  }

  private static void frameFileBytes(MessageDigest digest, Path file) throws IOException {
    frameLength(digest, Files.size(file));
    try (var input = Files.newInputStream(file)) {
      byte[] buffer = new byte[COPY_BUFFER_BYTES];
      long read = 0L;
      for (int count; (count = input.read(buffer)) >= 0; ) {
        if (count > 0) {
          digest.update(buffer, 0, count);
          read = Math.addExact(read, count);
        }
      }
      if (read != Files.size(file)) {
        throw new IOException("product class bytes changed while hashing");
      }
    }
  }

  private static void frame(MessageDigest digest, byte[] bytes) {
    frameLength(digest, bytes.length);
    digest.update(bytes);
  }

  private static void frameLength(MessageDigest digest, long length) {
    digest.update(
        ByteBuffer.allocate(Long.BYTES).order(ByteOrder.BIG_ENDIAN).putLong(length).array());
  }

  private static MessageDigest sha256Digest() throws IOException {
    try {
      return MessageDigest.getInstance("SHA-256");
    } catch (NoSuchAlgorithmException unavailable) {
      throw new IOException("SHA-256 is unavailable", unavailable);
    }
  }

  private static int compareUtf8(String first, String second) {
    byte[] left = first.getBytes(StandardCharsets.UTF_8);
    byte[] right = second.getBytes(StandardCharsets.UTF_8);
    for (int index = 0; index < Math.min(left.length, right.length); index++) {
      int comparison =
          Integer.compare(Byte.toUnsignedInt(left[index]), Byte.toUnsignedInt(right[index]));
      if (comparison != 0) {
        return comparison;
      }
    }
    return Integer.compare(left.length, right.length);
  }

  private static String requireText(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value;
  }

  private static String requireMeasuredText(String value, String label) throws IOException {
    if (value == null || value.isBlank()) {
      throw new IOException(label + " is required");
    }
    return value;
  }

  private record BuildIdentity(String kind, Sha256Digest sha256) {}

  private record ClassFile(Path path, String relativePath) {}
}
