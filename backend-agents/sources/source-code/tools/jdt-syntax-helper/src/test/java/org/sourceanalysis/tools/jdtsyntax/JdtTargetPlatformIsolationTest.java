package org.sourceanalysis.tools.jdtsyntax;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JdtTargetPlatformIsolationTest {

  private static final String SOURCE =
      "package example;\n"
          + "class OptionalProbe {\n"
          + "  boolean isEmpty(java.util.Optional value) { return value.isEmpty(); }\n"
          + "}\n";

  @Test
  void explicitJava8PlatformControlsApiVisibilityWithoutTheHostBootclasspath(@TempDir Path temp)
      throws Exception {
    assertThat(JdtSyntaxProtocol.VERSION).isEqualTo("jdt-syntax-v4");
    assertThat(Arrays.stream(JdtSyntaxProtocol.Request.class.getRecordComponents())
            .map(java.lang.reflect.RecordComponent::getName))
        .contains("targetJdkVersion", "targetPlatformEntries");

    Path java8WithoutIsEmpty = createPlatform(temp.resolve("java8-without-isEmpty"), false);
    Path java8WithIsEmpty = createPlatform(temp.resolve("java8-with-isEmpty"), true);

    JdtSyntaxProtocol.Response withoutIsEmpty =
        new JdtSyntaxReader().describe(request(List.of(java8WithoutIsEmpty.toString())));
    JdtSyntaxProtocol.Response withIsEmpty =
        new JdtSyntaxReader().describe(request(List.of(java8WithIsEmpty.toString())));

    assertThat(withoutIsEmpty.diagnostics())
        .as("the Java 8-shaped platform omits Optional.isEmpty")
        .anySatisfy(
            diagnostic -> {
              assertThat(diagnostic.severity()).isEqualTo("ERROR");
              assertThat(diagnostic.message()).contains("isEmpty");
            });
    assertThat(withIsEmpty.diagnostics())
        .as("the explicit Java 8-shaped platform exposes Optional.isEmpty")
        .noneSatisfy(
            diagnostic ->
                assertThat(diagnostic.message()).contains("isEmpty"));
  }

  private static JdtSyntaxProtocol.Request request(List<String> platformEntries) {
    return new JdtSyntaxProtocol.Request(
        "jdt-syntax-v4",
        JdtSyntaxProtocol.DESCRIBE_COMPILATION_UNIT,
        "target-jdk-8",
        "example/OptionalProbe.java",
        "1.8",
        sha256(SOURCE),
        List.of(),
        List.of(),
        "8",
        platformEntries,
        SOURCE);
  }

  private static Path createPlatform(Path root, boolean optionalHasIsEmpty) throws IOException {
    Path objectClass = root.resolve("java/lang/Object.class");
    Path optionalClass = root.resolve("java/util/Optional.class");
    Files.createDirectories(objectClass.getParent());
    Files.createDirectories(optionalClass.getParent());
    Files.write(objectClass, minimalClass("java/lang/Object", null));
    Files.write(
        optionalClass,
        minimalClass("java/util/Optional", optionalHasIsEmpty ? "isEmpty" : null));
    return root;
  }

  /** Writes a tiny classfile fixture so the test has no dependency on installed JDK paths. */
  private static byte[] minimalClass(String internalName, String booleanMethod)
      throws IOException {
    ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    try (DataOutputStream output = new DataOutputStream(bytes)) {
      output.writeInt(0xCAFEBABE);
      output.writeShort(0);
      output.writeShort(52); // Java 8 classfile
      output.writeShort(booleanMethod == null ? 5 : 8);
      writeUtf8(output, internalName); // #1
      output.writeByte(7);
      output.writeShort(1); // #2 Class
      writeUtf8(output, "java/lang/Object"); // #3
      output.writeByte(7);
      output.writeShort(3); // #4 Class
      if (booleanMethod != null) {
        writeUtf8(output, booleanMethod); // #5
        writeUtf8(output, "()Z"); // #6
        writeUtf8(output, "Code"); // #7
      }

      output.writeShort(0x0021); // public, super
      output.writeShort(2);
      output.writeShort(internalName.equals("java/lang/Object") ? 0 : 4);
      output.writeShort(0); // interfaces
      output.writeShort(0); // fields
      output.writeShort(booleanMethod == null ? 0 : 1);
      if (booleanMethod != null) {
        output.writeShort(0x0001); // public
        output.writeShort(5);
        output.writeShort(6);
        output.writeShort(1); // method attributes
        output.writeShort(7); // Code
        output.writeInt(14);
        output.writeShort(1); // max stack
        output.writeShort(1); // max locals
        output.writeInt(2);
        output.writeByte(0x03); // iconst_0
        output.writeByte(0xac); // ireturn
        output.writeShort(0); // exception table
        output.writeShort(0); // nested attributes
      }
      output.writeShort(0); // class attributes
    }
    return bytes.toByteArray();
  }

  private static void writeUtf8(DataOutputStream output, String value) throws IOException {
    output.writeByte(1);
    output.writeUTF(value);
  }

  private static String sha256(String source) {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.NoSuchAlgorithmException impossible) {
      throw new AssertionError(impossible);
    }
  }
}
