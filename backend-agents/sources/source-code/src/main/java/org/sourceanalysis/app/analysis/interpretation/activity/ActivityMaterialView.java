package org.sourceanalysis.app.analysis.interpretation.activity;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.sourceanalysis.app.analysis.material.CodeReadingMaterialSet;
import org.sourceanalysis.app.analysis.persistence.PersistenceMaterialIndex;

/** Program-owned, packet-local view of one verified Step05 reading packet. */
public record ActivityMaterialView(
    CodeReadingMaterialSet.Packet packet,
    ActivityExplanationProfile profile,
    Map<String, String> entryKeysById,
    Map<String, String> methodRefsByKey,
    Map<String, String> callRefsByIdentity,
    Map<String, String> statementRefsById,
    Map<String, String> sourceRefsById,
    Map<String, List<XmlDependency>> dependenciesByStatementId) {

  public ActivityMaterialView {
    packet = Objects.requireNonNull(packet, "code reading packet");
    profile = Objects.requireNonNull(profile, "activity explanation profile");
    entryKeysById = immutableMap(entryKeysById, "entry key mapping");
    methodRefsByKey = immutableMap(methodRefsByKey, "method ref mapping");
    callRefsByIdentity = immutableMap(callRefsByIdentity, "call ref mapping");
    statementRefsById = immutableMap(statementRefsById, "statement ref mapping");
    sourceRefsById = immutableMap(sourceRefsById, "source ref mapping");
    Objects.requireNonNull(dependenciesByStatementId, "statement dependency mapping");
    Map<String, List<XmlDependency>> dependencies = new LinkedHashMap<>();
    dependenciesByStatementId.forEach(
        (statementId, values) ->
            dependencies.put(
                required(statementId, "statement dependency key"),
                List.copyOf(Objects.requireNonNull(values, "statement dependencies"))));
    dependenciesByStatementId = Collections.unmodifiableMap(dependencies);
  }

  public Map<String, String> entryKeysById() {
    return immutableMap(entryKeysById, "entry key mapping");
  }

  public Map<String, String> methodRefsByKey() {
    return immutableMap(methodRefsByKey, "method ref mapping");
  }

  public Map<String, String> callRefsByIdentity() {
    return immutableMap(callRefsByIdentity, "call ref mapping");
  }

  public Map<String, String> statementRefsById() {
    return immutableMap(statementRefsById, "statement ref mapping");
  }

  public Map<String, String> sourceRefsById() {
    return immutableMap(sourceRefsById, "source ref mapping");
  }

  /** One complete saved XML dependency node recovered from a selected raw resource. */
  public record XmlDependency(
      String kind,
      String reference,
      String resolution,
      String namespace,
      String elementName,
      String elementId,
      PersistenceMaterialIndex.XmlNode xml) {

    public XmlDependency {
      kind = required(kind, "XML dependency kind");
      reference = required(reference, "XML dependency reference");
      resolution = required(resolution, "XML dependency resolution");
      namespace = required(namespace, "XML dependency namespace");
      elementName = required(elementName, "XML dependency element name");
      elementId = required(elementId, "XML dependency element ID");
      xml = Objects.requireNonNull(xml, "XML dependency projection");
    }
  }

  private static Map<String, String> immutableMap(Map<String, String> source, String label) {
    Objects.requireNonNull(source, label);
    Map<String, String> copy = new LinkedHashMap<>();
    source.forEach(
        (key, value) -> copy.put(required(key, label + " key"), required(value, label + " value")));
    return Collections.unmodifiableMap(copy);
  }

  private static String required(String value, String label) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(label + " is required");
    }
    return value;
  }
}
