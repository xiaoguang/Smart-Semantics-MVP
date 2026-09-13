package org.sourceanalysis.app;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** RED contract for the local unit/integration Maven split. */
class MavenLocalCiClassificationTest {

  private static final Path REAL_COLLECTION_TEST =
      Path.of(
          "src",
          "test",
          "java",
          "org",
          "sourceanalysis",
          "app",
          "analysis",
          "code",
          "jdt",
          "JdtRealSourceCollectionTest.java");
  private static final Path REAL_COLLECTION_IT =
      REAL_COLLECTION_TEST.resolveSibling("JdtRealSourceCollectionIT.java");
  private static final Path REAL_HELPER_IT =
      REAL_COLLECTION_TEST.resolveSibling("JdtSyntaxHelperRealIT.java");

  @Test
  void declaresIndependentSkipProperties() throws Exception {
    Document pom = readPom();
    Element properties = first(pom.getDocumentElement(), "properties");
    assertThat(childText(properties, "skipUTs")).isEqualTo("false");
    assertThat(childText(properties, "skipITs")).isEqualTo("false");
  }

  @Test
  void keepsSurefireOnTheUnitSkipProperty() throws Exception {
    Document pom = readPom();
    Element surefire = plugin(pom.getDocumentElement(), "maven-surefire-plugin");
    assertThat(childText(first(surefire, "configuration"), "skipTests")).isEqualTo("${skipUTs}");
  }

  @Test
  void wiresRealJdtProfileToFailsafeWithJdtOnlySelector() throws Exception {
    Document pom = readPom();
    Element profile = profile(pom, "real-jdt-it");
    if (profile == null) {
      assertThat(profile).as("real-jdt-it must be an explicit Maven profile").isNotNull();
      return;
    }
    Element failsafe = plugin(profile, "maven-failsafe-plugin");
    assertThat(descendantTexts(failsafe, "goal")).contains("integration-test", "verify");
    assertThat(childText(first(failsafe, "configuration"), "skipITs")).isEqualTo("${skipITs}");
    assertThat(descendantTexts(failsafe, "include"))
        .containsExactlyInAnyOrder(
            "**/JdtRealSourceCollectionIT.java", "**/JdtSyntaxHelperRealIT.java");
  }

  @Test
  void keepsRealJdtTestsOutOfSurefireNamingAndInDedicatedClasses() {
    assertThat(Files.exists(REAL_COLLECTION_TEST))
        .as("the real frozen-source test must not be a Surefire *Test")
        .isFalse();
    assertThat(Files.isRegularFile(REAL_COLLECTION_IT)).isTrue();
    assertThat(Files.isRegularFile(REAL_HELPER_IT)).isTrue();
  }

  @Test
  void realNavigationItUsesConfiguredPortablePrerequisites() throws Exception {
    String source = Files.readString(REAL_COLLECTION_IT);

    assertThat(source)
        .as("real navigation IT prerequisite property names")
        .contains(
            "sourceanalysis.jdt.testJavaHome",
            "sourceanalysis.jdt.testProject",
            "sourceanalysis.jdt.testDistribution",
            "sourceanalysis.jdt.testDependencies");
  }

  @Test
  void realNavigationItContainsNoDeveloperHostPaths() throws Exception {
    assertThat(Files.readString(REAL_COLLECTION_IT))
        .as("real navigation IT must be portable across developer and CI hosts")
        .doesNotContain("/Users/", "/Library/");
  }

  @Test
  void realNavigationItFailsClosedWhenPrerequisitesAreMissing() throws Exception {
    assertThat(Files.readString(REAL_COLLECTION_IT))
        .as("explicit real-jdt-it selection must fail a missing-prerequisite preflight")
        .doesNotContain("org.junit.jupiter.api.Assumptions", "Assumptions.assumeTrue")
        .contains("throw new IllegalStateException", "Missing required real-jdt-it prerequisite");
  }

  @Test
  void realSyntaxHelperItFailsClosedWithoutItsConfiguredToolJavaHome() throws Exception {
    assertThat(Files.readString(REAL_HELPER_IT))
        .as("real helper IT must use the same explicit tool-JDK prerequisite")
        .contains("sourceanalysis.jdt.testJavaHome", "Missing required real-jdt-it prerequisite")
        .doesNotContain("/usr/libexec/java_home", "new ProcessBuilder(\"java\"");
  }

  private static Document readPom() throws Exception {
    var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
    factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
    factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
    factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    factory.setXIncludeAware(false);
    factory.setExpandEntityReferences(false);
    return factory.newDocumentBuilder().parse(Path.of("pom.xml").toFile());
  }

  private static Element profile(Document pom, String id) {
    return elements(pom.getDocumentElement(), "profile").stream()
        .filter(profile -> id.equals(childText(profile, "id")))
        .findFirst()
        .orElse(null);
  }

  private static Element plugin(Element owner, String artifactId) {
    List<Element> matches =
        elements(owner, "plugin").stream()
            .filter(plugin -> artifactId.equals(childText(plugin, "artifactId")))
            .toList();
    return matches.stream()
        .filter(
            plugin -> first(plugin, "configuration") != null || first(plugin, "executions") != null)
        .findFirst()
        .orElseGet(() -> matches.stream().findFirst().orElse(null));
  }

  private static Element first(Element owner, String name) {
    if (owner == null) {
      return null;
    }
    return elements(owner, name).stream().findFirst().orElse(null);
  }

  private static String childText(Element owner, String name) {
    if (owner == null) {
      return null;
    }
    return elements(owner, name).stream()
        .findFirst()
        .map(Element::getTextContent)
        .map(String::strip)
        .orElse(null);
  }

  private static List<String> descendantTexts(Element owner, String name) {
    if (owner == null) {
      return List.of();
    }
    return elements(owner, name).stream().map(Element::getTextContent).map(String::strip).toList();
  }

  private static List<Element> elements(Element owner, String name) {
    if (owner == null) {
      return List.of();
    }
    List<Element> matches = new ArrayList<>();
    for (int index = 0; index < owner.getElementsByTagName(name).getLength(); index++) {
      matches.add((Element) owner.getElementsByTagName(name).item(index));
    }
    return matches;
  }
}
