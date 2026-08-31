package com.linguan.codemd.stage03;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Package-private canonical UTF-8 JSON identity material for frozen registry records. */
final class Stage03RegistryCanonicalizer {
    private static final ObjectMapper JSON = new ObjectMapper();

    private Stage03RegistryCanonicalizer() {
    }

    static String businessTermsDigest(BusinessTermRegistry registry) {
        return digest(registry("businessTerms", registry.schemaVersion(), registry.registryId(), terms(registry)));
    }

    static String technicalDisplaysDigest(TechnicalDisplayRegistry registry) {
        return digest(registry("technicalDisplays", registry.schemaVersion(), registry.registryId(), displays(registry)));
    }

    static String claimsDigest(ClaimRegistry registry) {
        return digest(registry("claims", registry.schemaVersion(), registry.registryId(), claims(registry)));
    }

    static String questionsDigest(QuestionRegistry registry) {
        return digest(registry("questions", registry.schemaVersion(), registry.registryId(), questions(registry)));
    }

    static String templatesDigest(ReaderSentenceTemplateRegistry registry) {
        return digest(registry("sentenceTemplates", registry.schemaVersion(), registry.registryId(), templates(registry)));
    }

    static String ownershipDigest(SectionOwnershipRegistry registry) {
        return digest(registry("sectionOwnership", registry.schemaVersion(), registry.registryId(), rules(registry)));
    }

    static String bundleId(BusinessTermRegistry businessTerms, TechnicalDisplayRegistry technicalDisplays,
                           ClaimRegistry claims, QuestionRegistry questions,
                           ReaderSentenceTemplateRegistry templates, SectionOwnershipRegistry ownership) {
        ObjectNode root = JSON.createObjectNode();
        root.put("schemaVersion", "registry-bundle-v1");
        root.set("businessTerms", ref(businessTerms.registryId(), businessTerms.sha256()));
        root.set("technicalDisplays", ref(technicalDisplays.registryId(), technicalDisplays.sha256()));
        root.set("claims", ref(claims.registryId(), claims.sha256()));
        root.set("questions", ref(questions.registryId(), questions.sha256()));
        root.set("sentenceTemplates", ref(templates.registryId(), templates.sha256()));
        root.set("sectionOwnership", ref(ownership.registryId(), ownership.sha256()));
        return "registry-bundle:" + digest(root);
    }

    private static ObjectNode ref(String id, String sha256) {
        ObjectNode ref = JSON.createObjectNode();
        ref.put("registryId", requireText(id));
        ref.put("sha256", requireText(sha256));
        return ref;
    }

    private static ObjectNode registry(String kind, String schema, String id, ArrayNode entries) {
        ObjectNode root = JSON.createObjectNode();
        root.put("registryKind", kind);
        root.put("schemaVersion", requireText(schema));
        root.put("registryId", requireText(id));
        root.set("entries", entries);
        return root;
    }

    private static ArrayNode terms(BusinessTermRegistry registry) {
        ArrayNode result = JSON.createArrayNode();
        checked(registry.terms()).stream().sorted(Comparator.comparing(entry -> requireText(entry.businessTermKey())))
                .forEach(entry -> {
                    ObjectNode node = result.addObject();
                    node.put("businessTermKey", requireText(entry.businessTermKey()));
                    node.put("anchorKind", requireText(entry.anchorKind()));
                    node.put("localizedValue", requireText(entry.localizedValue()));
                    node.set("eligibleAtomKinds", sortedStrings(entry.eligibleAtomKinds()));
                    node.set("minimumBasisAtomIds", sortedStrings(entry.minimumBasisAtomIds()));
                    node.put("priority", entry.priority());
                    node.put("technicalFallbackPolicyKey", requireText(entry.technicalFallbackPolicyKey()));
                });
        return result;
    }

    private static ArrayNode displays(TechnicalDisplayRegistry registry) {
        ArrayNode result = JSON.createArrayNode();
        checked(registry.policies()).stream().sorted(Comparator.comparing(entry -> requireText(entry.policyKey())))
                .forEach(entry -> {
                    ObjectNode node = result.addObject();
                    node.put("policyKey", requireText(entry.policyKey()));
                    node.put("anchorKind", requireText(entry.anchorKind()));
                    node.set("resolutionOrder", orderedStrings(entry.resolutionOrder()));
                    node.put("displayTemplateKey", requireText(entry.displayTemplateKey()));
                });
        return result;
    }

    private static ArrayNode claims(ClaimRegistry registry) {
        ArrayNode result = JSON.createArrayNode();
        checked(registry.claims()).stream().sorted(Comparator.comparing(entry -> requireText(entry.claimKey())))
                .forEach(entry -> {
                    ObjectNode node = result.addObject();
                    node.put("claimKey", requireText(entry.claimKey()));
                    node.put("targetAnchorKind", requireText(entry.targetAnchorKind()));
                    node.set("requiredAtomPatterns", sortedStrings(entry.requiredAtomPatterns()));
                    node.put("readerTemplateKey", requireText(entry.readerTemplateKey()));
                });
        return result;
    }

    private static ArrayNode questions(QuestionRegistry registry) {
        ArrayNode result = JSON.createArrayNode();
        checked(registry.questions()).stream().sorted(Comparator.comparing(entry -> requireText(entry.questionKey())))
                .forEach(entry -> {
                    ObjectNode node = result.addObject();
                    node.put("questionKey", requireText(entry.questionKey()));
                    node.set("allowedGapReasonCodes", sortedStrings(entry.allowedGapReasonCodes()));
                    node.put("readerTemplateKey", requireText(entry.readerTemplateKey()));
                });
        return result;
    }

    private static ArrayNode templates(ReaderSentenceTemplateRegistry registry) {
        ArrayNode result = JSON.createArrayNode();
        checked(registry.templates()).stream().sorted(Comparator.comparing(entry -> requireText(entry.templateKey())))
                .forEach(entry -> {
                    ObjectNode node = result.addObject();
                    node.put("templateKey", requireText(entry.templateKey()));
                    node.put("ownerSectionKey", requireText(entry.ownerSectionKey()));
                    node.put("literalPattern", requireText(entry.literalPattern()));
                    ArrayNode slots = node.putArray("slots");
                    checked(entry.slots()).stream().sorted(Comparator.comparing(slot -> requireText(slot.slotKey())))
                            .forEach(slot -> slots.addObject().put("slotKey", requireText(slot.slotKey()))
                                    .put("slotKind", requireText(slot.slotKind())));
                });
        return result;
    }

    private static ArrayNode rules(SectionOwnershipRegistry registry) {
        ArrayNode result = JSON.createArrayNode();
        checked(registry.rules()).stream().sorted(Comparator.comparing(rule -> requireText(rule.knowledgeKind())))
                .forEach(rule -> result.addObject().put("knowledgeKind", requireText(rule.knowledgeKind()))
                        .put("ownerSectionKey", requireText(rule.ownerSectionKey())));
        return result;
    }

    private static ArrayNode sortedStrings(List<String> values) {
        return strings(values, true);
    }

    private static ArrayNode orderedStrings(List<String> values) {
        return strings(values, false);
    }

    private static ArrayNode strings(List<String> values, boolean sort) {
        List<String> checked = checked(values).stream().map(Stage03RegistryCanonicalizer::requireText).toList();
        if (checked.size() != checked.stream().distinct().count()) {
            throw new IllegalArgumentException("duplicate registry list value");
        }
        ArrayNode result = JSON.createArrayNode();
        (sort ? checked.stream().sorted() : checked.stream()).forEach(result::add);
        return result;
    }

    private static <T> List<T> checked(List<T> values) {
        if (values == null || values.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("malformed registry list");
        }
        return values;
    }

    private static String requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("malformed registry text");
        }
        return value;
    }

    private static String digest(ObjectNode value) {
        try {
            return sha256(JSON.writeValueAsString(value));
        } catch (JsonProcessingException impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private static String sha256(String value) {
        try {
            return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
