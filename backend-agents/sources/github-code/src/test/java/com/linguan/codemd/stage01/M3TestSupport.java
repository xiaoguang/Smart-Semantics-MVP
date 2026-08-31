package com.linguan.codemd.stage01;

import org.junit.jupiter.api.Assertions;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Test-only support for the public M3 result records and mutation fixtures. */
final class M3TestSupport {
    private static final String CONTROLLER =
            "src/main/java/example/inventory/ReservationController.java";
    private static final String SERVICE =
            "src/main/java/example/inventory/ReservationService.java";
    private static final String MAPPER_XML =
            "src/main/resources/mappers/InventoryMapper.xml";

    private M3TestSupport() {
    }

    static Stage01Result analyze(Path snapshotRoot) {
        return new Stage01Analyzer().analyze(Stage01Fixtures.request(snapshotRoot));
    }

    static FrozenRepositoryRequest requestWithMutation(Path snapshotRoot, String path,
                                                       UnaryOperator<String> mutation)
            throws IOException {
        String original = Files.readString(snapshotRoot.resolve(path), StandardCharsets.UTF_8);
        String changed = mutation.apply(original);
        if (original.equals(changed)) {
            throw new AssertionError("mutation did not change " + path);
        }
        Files.writeString(snapshotRoot.resolve(path), changed, StandardCharsets.UTF_8);

        List<DeclaredFile> declarations = new ArrayList<>(Stage01Fixtures.declaredFiles());
        declarations.removeIf(file -> file.path().equals(path));
        Stage01Fixtures.FileSpec expected = Stage01Fixtures.expectedFile(path);
        declarations.add(new DeclaredFile(path, expected.mediaType(), changed.getBytes(StandardCharsets.UTF_8).length,
                Stage01Fixtures.sha256(changed.getBytes(StandardCharsets.UTF_8)), expected.textEncoding()));
        return Stage01Fixtures.request(snapshotRoot, declarations, Stage01Fixtures.defaultBudget());
    }

    static FrozenRepositoryRequest removeControllerRoutePrefix(Path root) throws IOException {
        return requestWithMutation(root, CONTROLLER,
                source -> source.replace("@RequestMapping(\"/reservations\")\n", ""));
    }

    static FrozenRepositoryRequest removeVersionPredicate(Path root) throws IOException {
        return requestWithMutation(root, MAPPER_XML,
                source -> source.replace(" AND version = #{version}", ""));
    }

    static FrozenRepositoryRequest changeQuantityGuardLiteral(Path root) throws IOException {
        return requestWithMutation(root, SERVICE,
                source -> source.replace("quantity <= 0", "quantity <= 1"));
    }

    static Object provenSourceFacts(Stage01Result result) {
        return member(result, "provenSourceFacts");
    }

    static Object provenFactSet(Stage01Result result) {
        return member(provenSourceFacts(result), "provenFactSet");
    }

    static Object proofPack(Stage01Result result) {
        return member(provenSourceFacts(result), "proofPack");
    }

    static Object gapLedger(Stage01Result result) {
        return member(provenSourceFacts(result), "gapLedger");
    }

    static List<?> codeFacts(Stage01Result result) {
        return list(provenFactSet(result), "codeFacts");
    }

    static List<?> atoms(Object codeFact) {
        return list(codeFact, "atoms");
    }

    static List<?> atomDispositions(Stage01Result result) {
        return list(member(provenFactSet(result), "candidateAccounting"), "atomDispositions");
    }

    static Object accounting(Stage01Result result) {
        return member(provenFactSet(result), "candidateAccounting");
    }

    static List<?> proofNodes(Stage01Result result) {
        return list(proofPack(result), "nodes");
    }

    static List<?> proofEdges(Stage01Result result) {
        return list(proofPack(result), "edges");
    }

    static List<?> proofs(Stage01Result result) {
        return list(proofPack(result), "proofs");
    }

    static List<?> expectationGaps(Stage01Result result) {
        return list(gapLedger(result), "expectationGaps");
    }

    static Object member(Object target, String accessor) {
        assertNotNull(target, "cannot read " + accessor + " from null");
        try {
            Method method;
            try {
                method = target.getClass().getMethod(accessor);
            } catch (NoSuchMethodException unavailable) {
                method = target.getClass().getDeclaredMethod(accessor);
            }
            method.setAccessible(true);
            return method.invoke(target);
        } catch (NoSuchMethodException | IllegalAccessException failure) {
            throw new AssertionError("missing M3 accessor " + accessor + " on "
                    + target.getClass().getName(), failure);
        } catch (InvocationTargetException failure) {
            throw new AssertionError("M3 accessor " + accessor + " failed", failure.getCause());
        }
    }

    static String text(Object target, String accessor) {
        Object value = member(target, accessor);
        return value == null ? null : value.toString();
    }

    static int number(Object target, String accessor) {
        Object value = member(target, accessor);
        assertTrue(value instanceof Number,
                () -> accessor + " must be numeric but was " + value);
        return ((Number) value).intValue();
    }

    static List<?> list(Object target, String accessor) {
        Object value = member(target, accessor);
        assertTrue(value instanceof List,
                () -> accessor + " must be a list but was " + value);
        return (List<?>) value;
    }

    static Map<String, Object> byId(List<?> values, String idAccessor) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (Object value : values) {
            String id = text(value, idAccessor);
            assertNotNull(id, idAccessor + " must not be null");
            assertTrue(result.put(id, value) == null, "duplicate " + idAccessor + ": " + id);
        }
        return result;
    }

    static Object onlyFact(Stage01Result result, String kind) {
        List<?> matching = codeFacts(result).stream()
                .filter(fact -> kind.equals(text(fact, "kind")))
                .toList();
        assertEquals(1, matching.size(), "expected exactly one Fact of kind " + kind);
        return matching.get(0);
    }

    static Set<String> allAtomIds(Stage01Result result) {
        Set<String> ids = new LinkedHashSet<>();
        for (Object fact : codeFacts(result)) {
            for (Object atom : atoms(fact)) {
                assertTrue(ids.add(text(atom, "atomId")), "duplicate atom id");
            }
        }
        return ids;
    }

    static Set<String> canonicalAtomValues(Object codeFact) {
        Set<String> values = new LinkedHashSet<>();
        for (Object atom : atoms(codeFact)) {
            Object value = member(atom, "value");
            values.add(text(value, "canonical"));
        }
        return values;
    }

    static Object atomWithCanonicalValue(Object codeFact, String canonical) {
        return atoms(codeFact).stream()
                .filter(atom -> canonical.equals(text(member(atom, "value"), "canonical")))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing atom value " + canonical));
    }

    static List<Object> dispositionsForAtomKeys(Stage01Result result, String... keys) {
        List<Object> matches = new ArrayList<>();
        for (Object disposition : atomDispositions(result)) {
            String factKey = text(disposition, "candidateFactKey");
            String atomKey = text(disposition, "atomKey");
            for (String key : keys) {
                if (key.equals(factKey) || key.equals(atomKey)
                        || containsKey(factKey, key) || containsKey(atomKey, key)) {
                    matches.add(disposition);
                    break;
                }
            }
        }
        return matches;
    }

    static void assertRejectedDispositions(Stage01Result result, String... keys) {
        List<Object> matches = dispositionsForAtomKeys(result, keys);
        assertEquals(keys.length, matches.size(), "candidate atom dispositions must not disappear");
        for (Object disposition : matches) {
            assertEquals("REJECTED_WITH_REASON", text(disposition, "disposition"));
            assertNotNull(text(disposition, "reasonCode"), "rejected atom must have reason");
            assertEquals(null, text(disposition, "proofId"), "rejected atom cannot have proof");
            assertEquals(null, text(disposition, "admittedFactId"),
                    "rejected atom cannot have admitted Fact");
        }
    }

    static void assertFactRejections(Stage01Result result, String... keys) {
        List<?> rejections = list(gapLedger(result), "factRejections");
        for (String key : keys) {
            List<?> matches = rejections.stream()
                    .filter(rejection -> containsKey(text(rejection, "candidateFactKey"), key)
                            || containsKey(text(rejection, "atomKey"), key))
                    .toList();
            assertEquals(1, matches.size(), "rejected atom must have one fact rejection: " + key);
            assertNotNull(text(matches.get(0), "code"), "fact rejection needs a reason code: " + key);
        }
    }

    static void assertM3RecordTypesArePublic() {
        String packageName = "com.linguan.codemd.stage01.";
        for (String simpleName : List.of("ProvenSourceFacts", "ProvenFactSet", "CodeFact",
                "FactAtom", "CandidateAccounting", "ProofPack", "Proof", "ProofNode",
                "ProofEdge", "GapLedger")) {
            try {
                Class<?> type = Class.forName(packageName + simpleName);
                assertTrue(type.isRecord(), simpleName + " must be a public record");
                assertTrue(Modifier.isPublic(type.getModifiers()), simpleName + " must be public");
            } catch (ClassNotFoundException failure) {
                throw new AssertionError("missing public M3 type " + simpleName, failure);
            }
        }
    }

    static void assertCanonicalIdsAndProofReferences(Stage01Result result) {
        assertM3RecordTypesArePublic();
        assertTrue(text(result, "stage01ResultId").matches("stage01-result:[0-9a-f]{64}"));
        assertTrue(text(provenFactSet(result), "provenFactSetId")
                .matches("proven-fact-set:[0-9a-f]{64}"));
        assertTrue(text(proofPack(result), "proofPackId")
                .matches("proof-pack:[0-9a-f]{64}"));
        assertTrue(text(gapLedger(result), "gapLedgerId")
                .matches("gap-ledger:[0-9a-f]{64}"));
        for (Object fact : codeFacts(result)) {
            assertTrue(text(fact, "factId").matches("fact:[0-9a-f]{64}"));
            for (Object atom : atoms(fact)) {
                assertTrue(text(atom, "atomId").matches("atom:[0-9a-f]{64}"));
                assertTrue(text(atom, "proofId").matches("proof:[0-9a-f]{64}"));
            }
        }
    }

    static void assertProofPackClosure(Stage01Result result, Path sourceRoot) throws IOException {
        Map<String, Object> nodes = byId(proofNodes(result), "proofNodeId");
        Map<String, Object> edges = byId(proofEdges(result), "proofEdgeId");
        Map<String, Object> proofs = byId(proofs(result), "proofId");
        Set<String> expectedPaths = Stage01Fixtures.expectedFiles().stream()
                .map(Stage01Fixtures.FileSpec::path).collect(java.util.stream.Collectors.toSet());
        for (Object node : nodes.values()) {
            Object locator = member(node, "locator");
            String path = text(locator, "path");
            assertTrue(expectedPaths.contains(path), "Proof node must point into declared M1 file: " + path);
            String sourceHash = text(node, "sourceFileSha256");
            assertEquals(Stage01Fixtures.expectedFile(path).sha256(), sourceHash,
                    "Proof node source hash must bind to M1 declaration");
            byte[] sourceBytes = Files.readAllBytes(sourceRoot.resolve(path));
            int startByte = number(locator, "startByte");
            int endByte = number(locator, "endByteExclusive");
            assertTrue(startByte >= 0 && startByte <= endByte && endByte <= sourceBytes.length,
                    "Proof node span must be within its M1 byte handle");
            assertEquals(Stage01Fixtures.sha256(Arrays.copyOfRange(sourceBytes, startByte, endByte)),
                    text(node, "spanSha256"),
                    "Proof node span digest must be independently recomputable from M1 bytes");
        }
        for (Object proof : proofs.values()) {
            assertEquals("CLOSED", text(proof, "status"));
            String root = text(proof, "rootProofNodeId");
            assertTrue(nodes.containsKey(root), "CLOSED Proof root node must exist");
            List<?> requiredNodes = list(proof, "requiredProofNodeIds");
            List<?> requiredEdges = list(proof, "requiredProofEdgeIds");
            assertFalse(requiredNodes.isEmpty(), "CLOSED Proof needs node closure");
            for (Object nodeId : requiredNodes) {
                assertTrue(nodes.containsKey(nodeId.toString()), "Proof node closure must be present");
            }
            for (Object edgeId : requiredEdges) {
                Object edge = edges.get(edgeId.toString());
                assertNotNull(edge, "Proof edge closure must be present");
                assertTrue(nodes.containsKey(text(edge, "fromProofNodeId")));
                assertTrue(nodes.containsKey(text(edge, "toProofNodeId")));
                assertNotNull(text(edge, "ruleId"));
            }
            assertEquals("CLOSED", text(proofs.get(text(proof, "proofId")), "status"));
        }
        for (Object fact : codeFacts(result)) {
            for (Object atom : atoms(fact)) {
                Object proof = proofs.get(text(atom, "proofId"));
                assertNotNull(proof, "admitted atom must reference a Proof in its ProofPack");
                assertEquals(text(fact, "factId"), text(proof, "factId"));
                assertEquals(text(atom, "atomId"), text(proof, "atomId"));
            }
        }
    }

    static void assertAccounting(Stage01Result result, int facts, int atoms, int rejectedAtoms) {
        Object accounting = accounting(result);
        assertEquals(facts, number(accounting, "candidateFactCount"));
        assertEquals(facts - rejectedFacts(result), number(accounting, "admittedFactCount"));
        assertEquals(rejectedFacts(result), number(accounting, "rejectedFactCount"));
        assertEquals(atoms, number(accounting, "candidateAtomCount"));
        assertEquals(atoms - rejectedAtoms, number(accounting, "admittedAtomDispositionCount"));
        assertEquals(rejectedAtoms, number(accounting, "rejectedAtomCount"));
        assertEquals(atoms - rejectedAtoms, number(accounting, "provenFactAtomCount"));
        assertEquals(atoms, atomDispositions(result).size());
        assertEquals(atoms - rejectedAtoms,
                codeFacts(result).stream().mapToInt(fact -> atoms(fact).size()).sum(),
                "ProvenFactSet atoms must equal admitted disposition count");
    }

    static int rejectedFacts(Stage01Result result) {
        return number(accounting(result), "rejectedFactCount");
    }

    static boolean containsKey(String value, String expected) {
        return value != null && (value.equals(expected) || value.contains(expected));
    }

    static void assertTamperedProofRejected(Stage01Analyzer analyzer, Stage01Result result,
                                            FrozenRepositoryRequest originalRequest) {
        Object facts = provenSourceFacts(result);
        Object proofPackValue = proofPack(result);
        for (Method method : analyzer.getClass().getMethods()) {
            if (!method.getName().equals("validateProof")
                    && !method.getName().equals("validateProvenFacts")) {
                continue;
            }
            Object[] arguments = validationArguments(method, result, facts, proofPackValue,
                    originalRequest);
            if (arguments == null) {
                continue;
            }
            try {
                Object validation = method.invoke(analyzer, arguments);
                if (validation instanceof Boolean) {
                    assertFalse((Boolean) validation,
                            method.getName() + " must reject tampered source bytes");
                } else {
                    throw new AssertionError(method.getName()
                            + " returned normally for tampered source bytes");
                }
                return;
            } catch (InvocationTargetException expected) {
                assertNotNull(expected.getCause(), "proof rejection must have a stable cause");
                return;
            } catch (IllegalAccessException failure) {
                throw new AssertionError("cannot invoke proof validation seam", failure);
            }
        }

        // Until the optional proof-only seam is introduced, analyze must fail when it
        // reopens the source against the original M1 declaration/hash inventory.
        Assertions.assertThrows(Stage01Exception.class,
                () -> analyzer.analyze(originalRequest),
                "tampered bytes must fail closed during Stage 01 revalidation");
    }

    private static Object[] validationArguments(Method method, Stage01Result result,
                                                Object facts, Object proofPackValue,
                                                FrozenRepositoryRequest request) {
        Object[] arguments = new Object[method.getParameterCount()];
        for (int index = 0; index < arguments.length; index++) {
            Class<?> parameterType = method.getParameterTypes()[index];
            if (parameterType.isInstance(request)) {
                arguments[index] = request;
            } else if (parameterType.isInstance(result)) {
                arguments[index] = result;
            } else if (parameterType.isInstance(facts)) {
                arguments[index] = facts;
            } else if (parameterType.isInstance(proofPackValue)) {
                arguments[index] = proofPackValue;
            } else {
                return null;
            }
        }
        return arguments;
    }

    static String sortAndJoinIds(Stage01Result result) {
        List<String> ids = new ArrayList<>();
        ids.add(text(result, "stage01ResultId"));
        ids.add(text(provenFactSet(result), "provenFactSetId"));
        ids.add(text(proofPack(result), "proofPackId"));
        ids.add(text(gapLedger(result), "gapLedgerId"));
        for (Object fact : codeFacts(result)) {
            ids.add(text(fact, "factId"));
            for (Object atom : atoms(fact)) {
                ids.add(text(atom, "atomId"));
                ids.add(text(atom, "proofId"));
            }
        }
        ids.sort(Comparator.naturalOrder());
        return String.join("\n", ids);
    }
}
