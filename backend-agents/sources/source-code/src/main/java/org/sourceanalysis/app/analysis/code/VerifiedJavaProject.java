package org.sourceanalysis.app.analysis.code;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextDocument;
import org.sourceanalysis.app.analysis.inventory.VerifiedSourceTextSet;

/**
 * The source-layer-approved Java project supplied to a selected engine.
 *
 * <p>This type deliberately has no public filesystem constructor. Source bytes and their identity
 * come only from a freshly verified {@link VerifiedSourceTextSet}; source-root hints never grant
 * the engine authority to read an unlisted file.
 */
public final class VerifiedJavaProject {

  private final VerifiedSourceTextSet sourceTexts;
  private final List<String> sourceRoots;
  private final List<ApprovedClasspathEntry> classpath;
  private final String sourceLevel;
  private final String fingerprint;

  private VerifiedJavaProject(
      VerifiedSourceTextSet sourceTexts,
      List<?> sourceRoots,
      List<Path> classpath,
      String sourceLevel) {
    this.sourceTexts = Objects.requireNonNull(sourceTexts, "verified source texts");
    this.sourceRoots = normalizedSourceRoots(sourceRoots, sourceTexts.documents());
    this.classpath = approvedClasspath(classpath);
    this.sourceLevel = requireSourceLevel(sourceLevel);
    fingerprint = fingerprint(sourceTexts, this.sourceRoots, this.classpath, this.sourceLevel);
  }

  /**
   * Binds a project to existing verified source text. The source-root values are snapshot-relative
   * layout hints and must contain admitted source documents; they are never filesystem roots.
   */
  public static VerifiedJavaProject fromVerifiedSourceTextSet(
      VerifiedSourceTextSet sourceTexts,
      List<?> sourceRoots,
      List<Path> classpath,
      String sourceLevel) {
    return new VerifiedJavaProject(sourceTexts, sourceRoots, classpath, sourceLevel);
  }

  public String snapshotId() {
    return sourceTexts.snapshotId();
  }

  public List<String> sourceEntries() {
    return sourceTexts.documents().stream().map(VerifiedSourceTextDocument::path).toList();
  }

  public List<String> sourceRoots() {
    return sourceRoots;
  }

  public List<Path> classpath() {
    return classpath.stream().map(ApprovedClasspathEntry::path).toList();
  }

  public String sourceLevel() {
    return sourceLevel;
  }

  public String fingerprint() {
    return fingerprint;
  }

  /** Validates the non-source inputs before a projection or JDT process is created. */
  public void validateForJdt() {
    for (ApprovedClasspathEntry entry : classpath) {
      if (!Files.isRegularFile(entry.path())) {
        throw sourceInvalid("approved local classpath entry is unavailable");
      }
      if (!entry.sha256().equals(sha256(entry.path()))) {
        throw sourceInvalid("approved local classpath entry content changed after project binding");
      }
    }
  }

  /** Copies exactly the admitted regular-file bytes into the session-owned project projection. */
  public void projectSourcesInto(Path projectionRoot) throws IOException {
    Objects.requireNonNull(projectionRoot, "projection root");
    validateForJdt();
    Path normalizedRoot = projectionRoot.toAbsolutePath().normalize();
    Files.createDirectories(normalizedRoot);
    for (VerifiedSourceTextDocument document : sourceTexts.documents()) {
      Path relative = verifiedRelativePath(document.path());
      Path destination = normalizedRoot.resolve(relative).normalize();
      if (!destination.startsWith(normalizedRoot)) {
        throw sourceInvalid("source projection escaped its workspace");
      }
      Files.createDirectories(destination.getParent());
      Files.write(destination, document.rawUtf8().copyToByteArray());
    }
  }

  private static List<String> normalizedSourceRoots(
      List<?> values, List<VerifiedSourceTextDocument> documents) {
    List<?> copied = List.copyOf(Objects.requireNonNull(values, "source roots"));
    if (copied.isEmpty()) {
      throw new IllegalArgumentException("at least one source root is required");
    }
    List<String> roots = new ArrayList<>(copied.size());
    for (Object value : copied) {
      String normalized = normalizedSourceRoot(value, documents);
      boolean containsDocument =
          documents.stream()
              .map(VerifiedSourceTextDocument::path)
              .anyMatch(path -> path.startsWith(normalized + "/"));
      if (!containsDocument) {
        throw new IllegalArgumentException("source root has no admitted verified source document");
      }
      roots.add(normalized);
    }
    if (new LinkedHashSet<>(roots).size() != roots.size()) {
      throw new IllegalArgumentException("source roots cannot contain duplicates");
    }
    return List.copyOf(roots);
  }

  private static String normalizedSourceRoot(
      Object value, List<VerifiedSourceTextDocument> documents) {
    String sourceRoot;
    if (value instanceof String text) {
      sourceRoot = text;
    } else if (value instanceof Path path && !path.isAbsolute()) {
      sourceRoot = path.toString();
    } else {
      throw new IllegalArgumentException("source root must be a snapshot-relative path");
    }
    try {
      return verifiedRelativePath(sourceRoot).toString().replace('\\', '/');
    } catch (IllegalArgumentException invalid) {
      throw new IllegalArgumentException("source root must be a snapshot-relative path", invalid);
    }
  }

  private static List<ApprovedClasspathEntry> approvedClasspath(List<Path> values) {
    List<Path> copied = List.copyOf(Objects.requireNonNull(values, "approved local classpath"));
    if (copied.stream().anyMatch(Objects::isNull)) {
      throw new IllegalArgumentException("approved local classpath cannot contain null paths");
    }
    List<ApprovedClasspathEntry> approved = new ArrayList<>(copied.size());
    for (Path value : copied) {
      Path path = value.toAbsolutePath().normalize();
      if (!Files.isRegularFile(path)) {
        throw sourceInvalid("approved local classpath entry is unavailable");
      }
      approved.add(new ApprovedClasspathEntry(path, sha256(path)));
    }
    return List.copyOf(approved);
  }

  private static Path verifiedRelativePath(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("verified source path is required");
    }
    try {
      Path relative = Path.of(value).normalize();
      if (relative.isAbsolute() || relative.startsWith("..")) {
        throw new IllegalArgumentException("verified source path must be snapshot relative");
      }
      return relative;
    } catch (InvalidPathException failure) {
      throw new IllegalArgumentException("verified source path is invalid", failure);
    }
  }

  private static String requireSourceLevel(String value) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("source level is required");
    }
    try {
      if (Integer.parseInt(value) <= 0) {
        throw new IllegalArgumentException("source level must be positive");
      }
      return value;
    } catch (NumberFormatException failure) {
      throw new IllegalArgumentException("source level must be numeric", failure);
    }
  }

  private static String fingerprint(
      VerifiedSourceTextSet sourceTexts,
      List<String> sourceRoots,
      List<ApprovedClasspathEntry> classpath,
      String sourceLevel) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      update(digest, sourceTexts.snapshotId());
      sourceTexts.documents().stream()
          .sorted(Comparator.comparing(VerifiedSourceTextDocument::path))
          .forEach(
              document -> {
                update(digest, document.fileId().value());
                update(digest, document.path());
                update(digest, document.sha256().value());
                digest.update(document.rawUtf8().copyToByteArray());
              });
      sourceRoots.forEach(root -> update(digest, root));
      classpath.forEach(
          entry -> {
            update(digest, entry.path().toString());
            update(digest, entry.sha256());
          });
      update(digest, sourceLevel);
      return HexFormat.of().formatHex(digest.digest());
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static void update(MessageDigest digest, String value) {
    digest.update(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    digest.update((byte) 0);
  }

  private static String sha256(Path path) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      try (var input = Files.newInputStream(path)) {
        byte[] buffer = new byte[16 * 1024];
        int count;
        while ((count = input.read(buffer)) >= 0) {
          digest.update(buffer, 0, count);
        }
      }
      return HexFormat.of().formatHex(digest.digest());
    } catch (IOException failure) {
      throw sourceInvalid("approved local classpath entry cannot be read");
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
  }

  private static CodeEngineException sourceInvalid(String detail) {
    return new CodeEngineException(CodeEngineException.SOURCE_INVALID, detail);
  }

  private record ApprovedClasspathEntry(Path path, String sha256) {}
}
