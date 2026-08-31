package com.linguan.codemd.stage01;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** M3 proof compiler over the already frozen M1 bytes and the M2 repository graph. */
final class ProvenFactCompiler {
    private static final String SCHEMA_VERSION = "proven-source-facts-v1";
    private static final String GAP_PROFILE_ID = "gap-expectation-profile-v1";
    private static final String GAP_PROFILE_SHA256 =
            "e63f976bba3fcc0acbc62c3b72f0a924d539d240d69ca86b7a598905fafc0f8a";

    ProvenSourceFacts compile(VerifiedSnapshot snapshot, RepositoryUnderstanding understanding,
                              Map<String, byte[]> reopenedBytes) {
        GraphView graph = new GraphView(snapshot, understanding.repositoryModel(), reopenedBytes);
        List<Workflow> workflows = graph.workflows();
        List<FactCandidate> candidates = workflows.stream().flatMap(workflow ->
                workflowCandidates(graph, workflow, workflows.size() > 1).stream()).toList();

        List<PendingFact> admitted = new ArrayList<>();
        List<AtomDisposition> dispositions = new ArrayList<>();
        List<FactRejection> rejections = new ArrayList<>();
        for (FactCandidate candidate : candidates) {
            CandidateEvaluation evaluation = candidate.evaluate(graph);
            List<AtomSeed> atoms = candidate.atoms(evaluation);
            String factId = factId(snapshot.snapshotId(), candidate.kind(), evaluation.subjectNodeIds(), atoms);
            if (evaluation.accepted()) {
                admitted.add(new PendingFact(candidate.key(), factId, candidate.kind(),
                        evaluation.subjectNodeIds(), atoms, evaluation.dependencyNodeIds()));
            } else {
                for (int index = 0; index < atoms.size(); index++) {
                    AtomSeed atom = atoms.get(index);
                    String reason = index == 0 ? evaluation.reasonCode() : "COMPOSITE_FACT_REJECTED";
                    dispositions.add(new AtomDisposition(candidate.key(), atom.key(),
                            "REJECTED_WITH_REASON", null, null, reason));
                    rejections.add(rejection(snapshot.snapshotId(), candidate.key(), atom.key(), reason,
                            evaluation.requiredNodeIds(), evaluation.dependencyNodeIds(),
                            evaluation.failureLocator()));
                }
            }
        }

        ProofAssembly assembly = assembleProofs(snapshot, graph, admitted);
        List<CodeFact> codeFacts = new ArrayList<>();
        for (PendingFact pending : admitted) {
            List<FactAtom> atoms = new ArrayList<>();
            for (AtomSeed atom : pending.atoms()) {
                String proofId = assembly.proofId(pending.factId(), atom.atomId());
                atoms.add(new FactAtom(atom.atomId(), atom.role(), atom.name(), atom.value(),
                        assembly.proofPackId(), proofId));
                dispositions.add(new AtomDisposition(pending.candidateFactKey(), atom.key(), "ADMITTED_WITH_PROOF",
                        pending.factId(), proofId, null));
            }
            codeFacts.add(new CodeFact(pending.factId(), pending.kind(), pending.subjectNodeIds(), atoms));
        }
        codeFacts.sort(Comparator.comparing(CodeFact::factId));
        dispositions.sort(Comparator.comparing(AtomDisposition::candidateFactKey)
                .thenComparing(AtomDisposition::atomKey));
        rejections.sort(Comparator.comparing(FactRejection::rejectionId));

        CandidateAccounting accounting = accounting(candidates.size(), codeFacts, dispositions);
        validateAccounting(accounting, codeFacts);
        String factSetId = "proven-fact-set:" + sha256(canonicalFactSet(snapshot.snapshotId(), codeFacts,
                accounting));
        ProvenFactSet factSet = new ProvenFactSet(factSetId, codeFacts, accounting);

        List<ExpectationGap> expectationGaps = expectationGaps(snapshot.snapshotId(), graph);
        List<CapabilityGap> capabilityGaps = capabilityGaps(snapshot.snapshotId(),
                understanding.capabilityReport());
        GapLedger ledger = new GapLedger("gap-ledger:" + sha256(canonicalLedger(snapshot.snapshotId(),
                capabilityGaps, rejections, expectationGaps)), new GapProfile(GAP_PROFILE_ID,
                GAP_PROFILE_SHA256), capabilityGaps, rejections, expectationGaps);
        return new ProvenSourceFacts(SCHEMA_VERSION, snapshot.snapshotId(),
                understanding.repositoryModel().repositoryModelId(),
                understanding.capabilityReport().capabilityReportId(), factSet, assembly.proofPack(), ledger);
    }

    /**
     * The M3 profile is a structural controller/service/mapper workflow, not a reservation-named
     * workflow.  Each candidate below is bound to one entry's reachable closure before any fact
     * or atom identity is calculated.  A service reached by several controllers is deliberately
     * represented once, by its deterministic owning entry, so a second entry cannot borrow the
     * same proven facts.
     */
    private static List<FactCandidate> workflowCandidates(GraphView graph, Workflow workflow,
                                                           boolean multipleWorkflows) {
        String prefix = multipleWorkflows ? workflow.entry().entryId() + ":" : "";
        return List.of(
                new FactCandidate(prefix + "F01", "HTTP_ENTRY", ignored -> workflowHttpEntry(graph, workflow),
                        evaluation -> List.of(
                                atom(graph, prefix + "F01", "A01", "LITERAL", "HTTP_METHOD",
                                        evaluation.value("method")),
                                atom(graph, prefix + "F01", "A02", "ATTRIBUTE", "ROUTE",
                                        evaluation.value("route")),
                                atom(graph, prefix + "F01", "A03", "ATTRIBUTE", "REQUEST_BODY",
                                        evaluation.value("request")),
                                atom(graph, prefix + "F01", "A04", "RELATIONSHIP", "CONTROLLER_SERVICE_CALL",
                                        evaluation.value("controllerCall")))),
                new FactCandidate(prefix + "F02", "QUANTITY_GUARD",
                        ignored -> workflowGuard(graph, workflow, "<=", "quantityGuard"),
                        evaluation -> List.of(
                                atom(graph, prefix + "F02", "A05", "CONDITION", "QUANTITY_GUARD",
                                        evaluation.value("quantityGuard")),
                                atom(graph, prefix + "F02", "A06", "LITERAL", "THROWN_EXCEPTION",
                                        evaluation.value("thrown")))),
                new FactCandidate(prefix + "F03", "INVENTORY_LOAD",
                        ignored -> workflowInventoryLoad(graph, workflow),
                        evaluation -> List.of(
                                atom(graph, prefix + "F03", "A07", "RELATIONSHIP", "MAPPER_LOOKUP",
                                        evaluation.value("lookup")),
                                atom(graph, prefix + "F03", "A08", "CONDITION", "SKU_PREDICATE",
                                        evaluation.value("predicate")))),
                new FactCandidate(prefix + "F04", "AVAILABLE_FORMULA",
                        ignored -> workflowAvailableFormula(graph, workflow),
                        evaluation -> List.of(atom(graph, prefix + "F04", "A09", "RELATIONSHIP",
                                "AVAILABLE_FORMULA", evaluation.value("formula")))),
                new FactCandidate(prefix + "F05", "INSUFFICIENT_GUARD",
                        ignored -> workflowGuard(graph, workflow, "<", "availabilityGuard"),
                        evaluation -> List.of(
                                atom(graph, prefix + "F05", "A10", "CONDITION", "AVAILABILITY_GUARD",
                                        evaluation.value("availabilityGuard")),
                                atom(graph, prefix + "F05", "A11", "LITERAL", "THROWN_EXCEPTION",
                                        evaluation.value("thrown")))),
                new FactCandidate(prefix + "F06", "OPTIMISTIC_UPDATE",
                        ignored -> workflowOptimisticUpdate(graph, workflow),
                        evaluation -> List.of(
                                atom(graph, prefix + "F06", "A12", "ATTRIBUTE", "TABLE",
                                        evaluation.value("table")),
                                atom(graph, prefix + "F06", "A13", "RELATIONSHIP", "RESERVED_INCREMENT",
                                        evaluation.value("reservedIncrement")),
                                atom(graph, prefix + "F06", "A14", "RELATIONSHIP", "VERSION_INCREMENT",
                                        evaluation.value("versionIncrement")),
                                atom(graph, prefix + "F06", "A15", "CONDITION", "SKU_PREDICATE",
                                        evaluation.value("firstPredicate")),
                                atom(graph, prefix + "F06", "A16", "CONDITION", "VERSION_PREDICATE",
                                        evaluation.value("secondPredicate")))),
                new FactCandidate(prefix + "F07", "UPDATE_COUNT_GUARD",
                        ignored -> workflowGuard(graph, workflow, "!=", "updateCountGuard"),
                        evaluation -> List.of(
                                atom(graph, prefix + "F07", "A17", "CONDITION", "UPDATE_COUNT_GUARD",
                                        evaluation.value("updateCountGuard")),
                                atom(graph, prefix + "F07", "A18", "LITERAL", "THROWN_EXCEPTION",
                                        evaluation.value("thrown")))),
                new FactCandidate(prefix + "F08", "SUCCESS_RESULT",
                        ignored -> workflowSuccessResult(graph, workflow),
                        evaluation -> List.of(
                                atom(graph, prefix + "F08", "A19", "ATTRIBUTE", "RECEIPT_SKU",
                                        evaluation.value("resultFirst")),
                                atom(graph, prefix + "F08", "A20", "ATTRIBUTE", "RECEIPT_QUANTITY",
                                        evaluation.value("resultSecond")))));
    }

    private static CandidateEvaluation workflowHttpEntry(GraphView graph, Workflow workflow) {
        RepositoryEntry entry = workflow.entry();
        RepositoryNode entryMethod = graph.node(entry.methodNodeId());
        RepositoryNode post = graph.workflowNode(workflow, node -> "JAVA_ANNOTATION".equals(node.kind())
                && contains(entryMethod.locator(), node.locator())
                && stripVersion(node.canonicalValue()).contains("PostMapping"));
        RepositoryEdge route = graph.workflowEdge(workflow, edge -> "ROUTE_PART".equals(edge.kind())
                && entry.routeNodeIds().contains(edge.fromNodeId()) && entry.routeNodeIds().contains(edge.toNodeId()));
        RepositoryEdge parameterType = graph.workflowEdge(workflow, edge -> "PARAMETER_TYPE".equals(edge.kind())
                && contains(entryMethod.locator(), graph.node(edge.fromNodeId()).locator()));
        RepositoryNode request = parameterType == null ? null : graph.node(parameterType.toNodeId());
        RepositoryEdge call = graph.workflowEdge(workflow, edge -> "CALL_TARGET".equals(edge.kind())
                && edge.toNodeId().equals(workflow.serviceMethodNodeId())
                && contains(entryMethod.locator(), graph.node(edge.fromNodeId()).locator()));
        RepositoryNode callFrom = call == null ? null : graph.node(call.fromNodeId());
        RepositoryNode callTo = call == null ? null : graph.node(call.toNodeId());
        return graph.workflowEvaluation(workflow,
                nullableList(entryMethod, post, request, route == null ? null : graph.node(route.fromNodeId()),
                        callFrom, callTo), nullableList(route, parameterType, call), Map.of(
                        "method", entry.httpMethod(), "route", entry.route(),
                        "request", simpleType(request == null ? null : stripVersion(request.canonicalValue())),
                        "controllerCall", relationship(entryMethod, callTo)));
    }

    private static CandidateEvaluation workflowGuard(GraphView graph, Workflow workflow,
                                                     String operator, String valueKey) {
        RepositoryNode guard = graph.workflowNode(workflow, node -> "JAVA_GUARD".equals(node.kind())
                && guardUses(stripVersion(node.canonicalValue()), operator));
        RepositoryEdge branch = guard == null ? null : graph.workflowEdge(workflow,
                edge -> "CFG_TRUE".equals(edge.kind()) && edge.fromNodeId().equals(guard.nodeId()));
        RepositoryNode thrown = branch == null ? null : graph.node(branch.toNodeId());
        return graph.workflowEvaluation(workflow, nullableList(guard, thrown), nullableList(branch), Map.of(
                valueKey, valueOr(guard == null ? null : stripVersion(guard.canonicalValue())),
                "thrown", thrownType(thrown)));
    }

    private static CandidateEvaluation workflowInventoryLoad(GraphView graph, Workflow workflow) {
        MapperBinding select = graph.mapperBinding(workflow, "select");
        RepositoryEdge call = select == null ? null : select.call();
        RepositoryEdge statement = select == null ? null : select.statement();
        RepositoryNode predicate = select == null ? null : graph.statementFragment(select.statement().toNodeId(),
                "SQL_PREDICATE");
        return graph.workflowEvaluation(workflow, nullableList(call == null ? null : graph.node(call.fromNodeId()),
                        call == null ? null : graph.node(call.toNodeId()),
                        statement == null ? null : graph.node(statement.toNodeId()), predicate),
                nullableList(call, statement), Map.of(
                        "lookup", methodName(call == null ? null : graph.node(call.toNodeId())),
                        "predicate", valueOr(predicate == null ? null : stripVersion(predicate.canonicalValue()))));
    }

    private static CandidateEvaluation workflowAvailableFormula(GraphView graph, Workflow workflow) {
        MapperBinding select = graph.mapperBinding(workflow, "select");
        RepositoryEdge resultBinding = select == null ? null : graph.resultBinding(select.statement().toNodeId());
        RepositoryNode resultType = resultBinding == null ? null : graph.node(resultBinding.toNodeId());
        RepositoryNode projection = select == null ? null : graph.statementFragment(select.statement().toNodeId(),
                "SQL_PROJECTION");
        RepositoryNode record = graph.recordFor(resultType);
        String formula = graph.availableFormula(workflow);
        if (formula == null || !projectionCarriesFormula(projection, formula)) {
            return graph.workflowRejected(workflow, "RESULT_MAPPING_OR_FORMULA_UNRESOLVED",
                    nullableList(graph.node(workflow.serviceMethodNodeId()), resultType, projection, record));
        }
        return graph.workflowEvaluation(workflow, nullableList(graph.node(workflow.serviceMethodNodeId()), resultType,
                        resultBinding == null ? null : graph.node(resultBinding.fromNodeId()), projection, record),
                nullableList(select == null ? null : select.call(), select == null ? null : select.statement(),
                        resultBinding), Map.of("formula", formula));
    }

    private static CandidateEvaluation workflowOptimisticUpdate(GraphView graph, Workflow workflow) {
        MapperBinding select = graph.mapperBinding(workflow, "select");
        MapperBinding update = graph.mapperBinding(workflow, "update");
        RepositoryEdge updateCall = update == null ? null : update.call();
        RepositoryEdge updateStatement = update == null ? null : update.statement();
        RepositoryNode table = update == null ? null : graph.statementFragment(update.statement().toNodeId(), "SQL_TABLE");
        RepositoryNode assignment = update == null ? null : graph.statementFragment(update.statement().toNodeId(),
                "SQL_ASSIGNMENT");
        RepositoryNode predicate = update == null ? null : graph.statementFragment(update.statement().toNodeId(),
                "SQL_PREDICATE");
        List<String> assignments = sqlParts(assignment == null ? null : stripVersion(assignment.canonicalValue()), ",");
        List<String> predicates = sqlParts(predicate == null ? null : stripVersion(predicate.canonicalValue()),
                "(?i)\\s+and\\s+");
        RepositoryEdge resultBinding = select == null ? null : graph.resultBinding(select.statement().toNodeId());
        RepositoryNode resultType = resultBinding == null ? null : graph.node(resultBinding.toNodeId());
        RepositoryNode projection = select == null ? null : graph.statementFragment(select.statement().toNodeId(),
                "SQL_PROJECTION");
        RepositoryNode servicePackage = graph.packageAt(graph.node(workflow.serviceMethodNodeId()).locator().path());
        RepositoryNode mapperPackage = updateCall == null ? null : graph.packageAt(
                graph.node(updateCall.toNodeId()).locator().path());
        RepositoryNode paramImport = updateCall == null ? null : graph.workflowNode(workflow,
                node -> "JAVA_IMPORT".equals(node.kind())
                        && node.locator().path().equals(graph.node(updateCall.toNodeId()).locator().path())
                        && stripVersion(node.canonicalValue()).contains("org.apache.ibatis.annotations.Param"));
        String versionParameter = predicates.size() < 2 ? null : namedSqlParameter(predicates.get(1));
        RepositoryNode mapperParameter = versionParameter == null ? null : graph.workflowNode(workflow,
                node -> "JAVA_PARAMETER".equals(node.kind())
                        && node.locator().path().equals(graph.node(updateCall.toNodeId()).locator().path())
                        && node.canonicalValue().endsWith(":" + versionParameter));
        RepositoryNode rowRecord = graph.recordFor(resultType);
        RepositoryEdge namespace = updateCall == null ? null : graph.workflowEdge(workflow,
                edge -> "NAMESPACE_INTERFACE".equals(edge.kind()) && graph.node(edge.toNodeId()).locator().path()
                        .equals(graph.node(updateCall.toNodeId()).locator().path()));
        RepositoryEdge config = namespace == null ? null : graph.workflowEdge(workflow,
                edge -> "CONFIG_RESOLVES_MAPPER".equals(edge.kind()) && edge.toNodeId().equals(namespace.fromNodeId()));
        boolean semanticUpdate = assignments.size() >= 2 && predicates.size() >= 2 && rowRecord != null;
        if (!semanticUpdate) {
            return graph.workflowRejected(workflow, "LOADED_VERSION_LINEAGE_UNRESOLVED",
                    nullableList(updateCall == null ? null : graph.node(updateCall.fromNodeId()),
                            updateCall == null ? null : graph.node(updateCall.toNodeId()),
                            updateStatement == null ? null : graph.node(updateStatement.toNodeId()), table, assignment,
                            predicate, resultType, projection, rowRecord,
                            select == null ? null : graph.node(select.call().fromNodeId())));
        }
        return graph.workflowEvaluation(workflow, nullableList(
                        updateCall == null ? null : graph.node(updateCall.fromNodeId()),
                        updateCall == null ? null : graph.node(updateCall.toNodeId()),
                        updateStatement == null ? null : graph.node(updateStatement.toNodeId()), table, assignment,
                        predicate, config == null ? null : graph.node(config.fromNodeId()),
                        namespace == null ? null : graph.node(namespace.fromNodeId()),
                        namespace == null ? null : graph.node(namespace.toNodeId()), resultType,
                        resultBinding == null ? null : graph.node(resultBinding.fromNodeId()), projection,
                        servicePackage, mapperPackage, paramImport, mapperParameter, rowRecord,
                        select == null ? null : graph.node(select.call().fromNodeId())),
                nullableList(updateCall, updateStatement, config, namespace, resultBinding,
                        select == null ? null : select.call()), Map.of(
                        "table", valueOr(table == null ? null : stripVersion(table.canonicalValue())),
                        "reservedIncrement", assignments.get(0), "versionIncrement", assignments.get(1),
                        "firstPredicate", predicates.get(0), "secondPredicate", predicates.get(1)));
    }

    private static CandidateEvaluation workflowSuccessResult(GraphView graph, Workflow workflow) {
        RepositoryLocator service = graph.node(workflow.serviceMethodNodeId()).locator();
        RepositoryNode returned = graph.workflowNode(workflow, node -> "JAVA_RETURN".equals(node.kind())
                && contains(service, node.locator()));
        List<String> arguments = returnArguments(returned == null ? null : stripVersion(returned.canonicalValue()));
        if (arguments.size() != 2) {
            return graph.workflowRejected(workflow, "SUCCESS_RESULT_UNRESOLVED",
                    nullableList(graph.node(workflow.serviceMethodNodeId()), returned));
        }
        return graph.workflowEvaluation(workflow, nullableList(graph.node(workflow.serviceMethodNodeId()), returned),
                List.of(), Map.of("resultFirst", arguments.get(0), "resultSecond", arguments.get(1)));
    }

    private static boolean guardUses(String condition, String operator) {
        if ("<".equals(operator)) {
            return condition.matches(".*(?<![<>=!])<(?![=>]).*");
        }
        return condition.contains(operator);
    }

    private static List<String> sqlParts(String source, String separator) {
        if (source == null) {
            return List.of();
        }
        return Arrays.stream(source.split(separator)).map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    private static String namedSqlParameter(String predicate) {
        Matcher matcher = Pattern.compile("#\\{([A-Za-z_][A-Za-z0-9_]*)}").matcher(predicate);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static List<String> returnArguments(String returned) {
        if (returned == null) {
            return List.of();
        }
        Matcher matcher = Pattern.compile("return\\s+new\\s+[A-Za-z_][A-Za-z0-9_]*\\(([^)]*)\\)").matcher(returned);
        if (!matcher.find()) {
            return List.of();
        }
        return Arrays.stream(matcher.group(1).split(",")).map(String::trim)
                .filter(value -> !value.isEmpty()).toList();
    }

    private static String simpleType(String value) {
        if (value == null) {
            return "UNRESOLVED";
        }
        int separator = value.lastIndexOf('.');
        return separator < 0 ? value : value.substring(separator + 1);
    }

    private static String methodName(RepositoryNode method) {
        if (method == null) {
            return "UNRESOLVED";
        }
        String canonical = stripVersion(method.canonicalValue());
        int hash = canonical.lastIndexOf('#');
        int slash = canonical.lastIndexOf('/');
        return hash >= 0 && slash > hash ? canonical.substring(hash + 1, slash) : canonical;
    }

    private static String relationship(RepositoryNode from, RepositoryNode to) {
        return from == null || to == null ? "UNRESOLVED" : methodName(from) + " -> " + methodName(to);
    }

    private static String thrownType(RepositoryNode thrown) {
        if (thrown == null) {
            return "UNRESOLVED";
        }
        Matcher matcher = Pattern.compile("throw\\s+new\\s+([A-Za-z_][A-Za-z0-9_]*)").matcher(
                stripVersion(thrown.canonicalValue()));
        return matcher.find() ? matcher.group(1) : "UNRESOLVED";
    }

    private static String valueOr(String value) {
        return value == null ? "UNRESOLVED" : value;
    }

    private static boolean projectionCarriesFormula(RepositoryNode projection, String formula) {
        if (projection == null) {
            return false;
        }
        String[] parts = formula.split(" = | - ", 3);
        if (parts.length != 3) {
            return false;
        }
        String normalized = stripVersion(projection.canonicalValue()).toLowerCase(Locale.ROOT);
        return normalized.contains(" as " + parts[1].toLowerCase(Locale.ROOT))
                && normalized.contains(" as " + parts[2].toLowerCase(Locale.ROOT));
    }

    private static AtomSeed atom(GraphView graph, String factKey, String atomKey, String role,
                                 String name, String canonical) {
        FactValue value = new FactValue("STRING", canonical);
        String id = "atom:" + sha256(graph.snapshotId() + "\n" + factKey + "\n" + role + "\n"
                + name + "\n" + value.type() + "\n" + value.canonical());
        return new AtomSeed(atomKey, id, role, name, value);
    }

    private static CandidateEvaluation httpEntry(GraphView graph) {
        RepositoryEntry entry = graph.entry("POST", "/reservations");
        RepositoryNode post = graph.node(node -> "JAVA_ANNOTATION".equals(node.kind())
                && node.canonicalValue().contains("PostMapping"));
        RepositoryNode requestBody = graph.node(node -> "JAVA_METHOD".equals(node.kind())
                && node.canonicalValue().contains("ReservationController#reserve/1"));
        RepositoryNode requestRecord = graph.node(node -> "JAVA_RECORD_DECLARATION".equals(node.kind())
                && "v1:example.inventory.ReservationRequest".equals(node.canonicalValue()));
        RepositoryEdge route = entry == null ? null : graph.edge(edge -> "ROUTE_PART".equals(edge.kind())
                && entry.routeNodeIds().contains(edge.fromNodeId()) && entry.routeNodeIds().contains(edge.toNodeId()));
        RepositoryEdge call = graph.edge(edge -> "CALL_TARGET".equals(edge.kind())
                && graph.node(edge.fromNodeId()).canonicalValue().contains("service.reserve")
                && graph.node(edge.toNodeId()).canonicalValue().contains("ReservationService#reserve/2"));
        return graph.evaluation(nullableList(entry == null ? null : graph.node(entry.methodNodeId()), post,
                        requestBody, requestRecord, route == null ? null : graph.node(route.fromNodeId()),
                        call == null ? null : graph.node(call.fromNodeId()),
                        call == null ? null : graph.node(call.toNodeId())),
                nullableList(route, call), Map.of());
    }

    private static CandidateEvaluation quantityGuard(GraphView graph) {
        RepositoryNode guard = graph.node(node -> "JAVA_GUARD".equals(node.kind())
                && node.canonicalValue().matches("v1:quantity\\s*<=\\s*\\d+"));
        RepositoryNode thrown = graph.node(node -> "JAVA_THROW".equals(node.kind())
                && node.canonicalValue().contains("InvalidQuantity"));
        String value = guard == null ? "quantity <= 0" : stripVersion(guard.canonicalValue());
        return graph.evaluation(nullableList(guard, thrown), List.of(), Map.of("guard", value));
    }

    private static CandidateEvaluation inventoryLoad(GraphView graph) {
        RepositoryEdge call = graph.edge(edge -> "CALL_TARGET".equals(edge.kind())
                && graph.node(edge.fromNodeId()).canonicalValue().contains("mapper.findBySku")
                && graph.node(edge.toNodeId()).canonicalValue().contains("InventoryMapper#findBySku/1"));
        RepositoryEdge statement = call == null ? null : graph.edge(edge -> "METHOD_STATEMENT".equals(edge.kind())
                && edge.fromNodeId().equals(call.toNodeId())
                && graph.node(edge.toNodeId()).canonicalValue().contains("select:findBySku"));
        RepositoryNode predicate = graph.node(node -> "SQL_PREDICATE".equals(node.kind())
                && stripVersion(node.canonicalValue()).contains("sku = #{sku}"));
        return graph.evaluation(nullableList(call == null ? null : graph.node(call.fromNodeId()),
                        call == null ? null : graph.node(call.toNodeId()),
                        statement == null ? null : graph.node(statement.toNodeId()), predicate),
                nullableList(call, statement), Map.of());
    }

    private static CandidateEvaluation availableFormula(GraphView graph) {
        RepositoryNode service = graph.node(node -> "JAVA_METHOD".equals(node.kind())
                && node.canonicalValue().contains("ReservationService#reserve/2"));
        RepositoryNode resultType = graph.node(node -> "XML_RESULT_TYPE".equals(node.kind())
                && node.canonicalValue().contains("example.inventory.InventoryRow"));
        RepositoryEdge resultBinding = resultType == null ? null : graph.edge(edge -> "RESULT_TYPE".equals(edge.kind())
                && edge.toNodeId().equals(resultType.nodeId()));
        RepositoryNode projection = graph.node(node -> "SQL_PROJECTION".equals(node.kind())
                && stripVersion(node.canonicalValue()).contains("on_hand AS onHand")
                && stripVersion(node.canonicalValue()).contains("reserved_qty AS reserved"));
        RepositoryNode inventoryRow = graph.node(node -> "JAVA_RECORD_DECLARATION".equals(node.kind())
                && node.canonicalValue().contains("InventoryRow"));
        boolean formula = graph.anySourceContains("int available = inventory.onHand() - inventory.reserved()")
                && graph.anySourceContains("record InventoryRow(String sku, int onHand, int reserved, int version)");
        return formula ? graph.evaluation(nullableList(service, resultType,
                resultBinding == null ? null : graph.node(resultBinding.fromNodeId()), projection, inventoryRow),
                nullableList(resultBinding), Map.of())
                : graph.rejected("RESULT_MAPPING_OR_FORMULA_UNRESOLVED", nullableList(service, resultType,
                projection, inventoryRow));
    }

    private static CandidateEvaluation insufficientGuard(GraphView graph) {
        RepositoryNode guard = graph.node(node -> "JAVA_GUARD".equals(node.kind())
                && stripVersion(node.canonicalValue()).equals("available < quantity"));
        RepositoryNode thrown = graph.node(node -> "JAVA_THROW".equals(node.kind())
                && node.canonicalValue().contains("InsufficientInventory"));
        return graph.evaluation(nullableList(guard, thrown), List.of(), Map.of());
    }

    private static CandidateEvaluation optimisticUpdate(GraphView graph) {
        RepositoryEdge findCall = graph.edge(edge -> "CALL_TARGET".equals(edge.kind())
                && graph.node(edge.fromNodeId()).canonicalValue().contains("mapper.findBySku")
                && graph.node(edge.toNodeId()).canonicalValue().contains("InventoryMapper#findBySku/1"));
        RepositoryEdge call = graph.edge(edge -> "CALL_TARGET".equals(edge.kind())
                && graph.node(edge.fromNodeId()).canonicalValue().contains("mapper.addReservation")
                && graph.node(edge.toNodeId()).canonicalValue().contains("InventoryMapper#addReservation/3"));
        RepositoryEdge statement = call == null ? null : graph.edge(edge -> "METHOD_STATEMENT".equals(edge.kind())
                && edge.fromNodeId().equals(call.toNodeId())
                && graph.node(edge.toNodeId()).canonicalValue().contains("update:addReservation"));
        RepositoryNode table = graph.node(node -> "SQL_TABLE".equals(node.kind())
                && stripVersion(node.canonicalValue()).equals("inventory"));
        RepositoryNode assignment = graph.node(node -> "SQL_ASSIGNMENT".equals(node.kind())
                && stripVersion(node.canonicalValue()).contains("reserved_qty = reserved_qty + #{quantity}")
                && stripVersion(node.canonicalValue()).contains("version = version + 1"));
        RepositoryNode predicate = graph.node(node -> "SQL_PREDICATE".equals(node.kind())
                && stripVersion(node.canonicalValue()).contains("sku = #{sku}")
                && stripVersion(node.canonicalValue()).contains("version = #{version}"));
        RepositoryNode resultType = graph.node(node -> "XML_RESULT_TYPE".equals(node.kind())
                && node.canonicalValue().contains("example.inventory.InventoryRow"));
        RepositoryEdge resultBinding = resultType == null ? null : graph.edge(edge -> "RESULT_TYPE".equals(edge.kind())
                && edge.toNodeId().equals(resultType.nodeId()));
        RepositoryNode projection = graph.node(node -> "SQL_PROJECTION".equals(node.kind())
                && stripVersion(node.canonicalValue()).contains("on_hand AS onHand")
                && stripVersion(node.canonicalValue()).contains("reserved_qty AS reserved")
                && stripVersion(node.canonicalValue()).contains("version"));
        RepositoryNode servicePackage = graph.node(node -> "JAVA_PACKAGE".equals(node.kind())
                && "v1:example.inventory".equals(node.canonicalValue()));
        RepositoryNode mapperPackage = call == null ? null : graph.node(node -> "JAVA_PACKAGE".equals(node.kind())
                && node.locator().path().equals(graph.node(call.toNodeId()).locator().path()));
        RepositoryNode paramImport = graph.node(node -> "JAVA_IMPORT".equals(node.kind())
                && node.canonicalValue().contains("org.apache.ibatis.annotations.Param"));
        RepositoryNode versionParameter = graph.node(node -> "JAVA_PARAMETER".equals(node.kind())
                && node.canonicalValue().endsWith(":version"));
        RepositoryNode inventoryRow = graph.node(node -> "JAVA_RECORD_DECLARATION".equals(node.kind())
                && node.canonicalValue().contains("InventoryRow"));
        RepositoryEdge config = graph.edge(edge -> "CONFIG_RESOLVES_MAPPER".equals(edge.kind()));
        // This graph edge is the mapper-document interface binding. The exact method/statement
        // edge above then narrows it to the update operation.
        RepositoryEdge namespace = graph.edge(edge -> "NAMESPACE_INTERFACE".equals(edge.kind())
                && graph.node(edge.toNodeId()).canonicalValue().contains("InventoryMapper"));
        boolean loadedVersion = graph.anySourceContains("record InventoryRow(String sku, int onHand, int reserved, int version)")
                && graph.anySourceContains("InventoryRow inventory = mapper.findBySku(sku)")
                && graph.anySourceContains("inventory.version()")
                && graph.anySourceContains("org.apache.ibatis.annotations.Param")
                && graph.anySourceContains("@Param(\"version\") int version");
        if (!loadedVersion) {
            return graph.rejected("LOADED_VERSION_LINEAGE_UNRESOLVED", nullableList(call == null ? null
                    : graph.node(call.fromNodeId()), call == null ? null : graph.node(call.toNodeId()),
                    statement == null ? null : graph.node(statement.toNodeId()), table, assignment, predicate,
                    resultType, resultBinding == null ? null : graph.node(resultBinding.fromNodeId()), projection,
                    servicePackage, mapperPackage, paramImport, versionParameter, inventoryRow,
                    findCall == null ? null : graph.node(findCall.fromNodeId())));
        }
        return graph.evaluation(nullableList(call == null ? null : graph.node(call.fromNodeId()),
                        call == null ? null : graph.node(call.toNodeId()),
                        statement == null ? null : graph.node(statement.toNodeId()), table, assignment, predicate,
                        config == null ? null : graph.node(config.fromNodeId()),
                        namespace == null ? null : graph.node(namespace.fromNodeId()),
                        namespace == null ? null : graph.node(namespace.toNodeId()), resultType,
                        resultBinding == null ? null : graph.node(resultBinding.fromNodeId()), projection,
                        servicePackage, mapperPackage, paramImport, versionParameter, inventoryRow,
                        findCall == null ? null : graph.node(findCall.fromNodeId())),
                nullableList(call, statement, config, namespace, resultBinding, findCall), Map.of());
    }

    private static CandidateEvaluation updateCountGuard(GraphView graph) {
        RepositoryNode guard = graph.node(node -> "JAVA_GUARD".equals(node.kind())
                && stripVersion(node.canonicalValue()).equals("updateCount != 1"));
        RepositoryNode thrown = graph.node(node -> "JAVA_THROW".equals(node.kind())
                && node.canonicalValue().contains("ConcurrentInventoryChange"));
        return graph.evaluation(nullableList(guard, thrown), List.of(), Map.of());
    }

    private static CandidateEvaluation successResult(GraphView graph) {
        RepositoryNode returned = graph.node(node -> "JAVA_RETURN".equals(node.kind())
                && node.canonicalValue().contains("new ReservationReceipt(sku, quantity)"));
        RepositoryNode method = graph.node(node -> "JAVA_METHOD".equals(node.kind())
                && node.canonicalValue().contains("ReservationService#reserve/2"));
        return graph.evaluation(nullableList(method, returned), List.of(), Map.of());
    }

    private static ProofAssembly assembleProofs(VerifiedSnapshot snapshot, GraphView graph,
                                                List<PendingFact> admitted) {
        Map<String, ProofNode> proofNodes = new LinkedHashMap<>();
        Map<String, ProofEdge> proofEdges = new LinkedHashMap<>();
        List<Proof> proofs = new ArrayList<>();
        for (PendingFact fact : admitted) {
            List<RepositoryNode> dependencies = new ArrayList<>();
            for (String repositoryNodeId : fact.dependencyNodeIds()) {
                dependencies.add(graph.node(repositoryNodeId));
            }
            for (AtomSeed atom : fact.atoms()) {
                RepositoryNode rootRepositoryNode = rootFor(atom, dependencies);
                ProofNode root = proofNodes.computeIfAbsent(nodeKey(rootRepositoryNode, atom),
                        ignored -> proofNode(snapshot, graph, rootRepositoryNode, sqlLocator(graph,
                                rootRepositoryNode, atom)));
                List<ProofNode> nodes = new ArrayList<>();
                nodes.add(root);
                for (RepositoryNode dependency : dependencies) {
                    ProofNode node = proofNodes.computeIfAbsent(nodeKey(dependency, null),
                            ignored -> proofNode(snapshot, graph, dependency, sqlLocator(graph, dependency, atom)));
                    if (!node.proofNodeId().equals(root.proofNodeId())) {
                        nodes.add(node);
                    }
                }
                nodes = nodes.stream().collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toMap(ProofNode::proofNodeId, node -> node,
                                (left, right) -> left, LinkedHashMap::new), map -> new ArrayList<>(map.values())));
                List<String> requiredNodeIds = nodes.stream().map(ProofNode::proofNodeId).toList();
                List<String> rules = rulesFor(atom.name());
                List<String> requiredEdgeIds = new ArrayList<>();
                if (nodes.size() == 1) {
                    requiredEdgeIds.add(derivedEdge(proofEdges, fact, atom, root, root, rules.get(0)));
                } else {
                    for (int index = 1; index < nodes.size(); index++) {
                        requiredEdgeIds.add(derivedEdge(proofEdges, fact, atom, root, nodes.get(index),
                                rules.get((index - 1) % rules.size())));
                    }
                }
                requiredEdgeIds = requiredEdgeIds.stream().distinct().sorted().toList();
                String id = "proof:" + sha256(fact.factId() + "\n" + atom.atomId() + "\n"
                        + requiredNodeIds + "\n" + requiredEdgeIds);
                proofs.add(new Proof(id, fact.factId(), atom.atomId(), root.proofNodeId(),
                        requiredNodeIds, requiredEdgeIds, "CLOSED"));
            }
        }
        proofs.sort(Comparator.comparing(Proof::proofId));
        List<ProofNode> nodes = proofNodes.values().stream().collect(java.util.stream.Collectors.toMap(
                ProofNode::proofNodeId, node -> node, (left, right) -> left, LinkedHashMap::new)).values().stream()
                .sorted(Comparator.comparing(ProofNode::proofNodeId)).toList();
        List<ProofEdge> edges = proofEdges.values().stream()
                .sorted(Comparator.comparing(ProofEdge::proofEdgeId)).toList();
        String packId = "proof-pack:" + sha256(canonical(nodes) + "\n" + canonical(edges) + "\n"
                + canonical(proofs));
        return new ProofAssembly(new ProofPack(packId, nodes, edges, proofs));
    }

    private static String derivedEdge(Map<String, ProofEdge> proofEdges, PendingFact fact,
                                      AtomSeed atom, ProofNode from, ProofNode to, String ruleId) {
        String repositoryEdgeId = "derived:" + sha256(fact.factId() + "\n" + atom.atomId() + "\n"
                + from.repositoryNodeId() + "\n" + to.repositoryNodeId() + "\n" + ruleId);
        String id = "proof-edge:" + sha256(repositoryEdgeId + "\n" + from.proofNodeId() + "\n"
                + to.proofNodeId() + "\n" + ruleId);
        proofEdges.putIfAbsent(id, new ProofEdge(id, repositoryEdgeId, from.proofNodeId(),
                to.proofNodeId(), ruleId));
        return id;
    }

    private static RepositoryNode rootFor(AtomSeed atom, List<RepositoryNode> nodes) {
        String kind = switch (atom.name()) {
            case "SKU_PREDICATE", "VERSION_PREDICATE" -> "SQL_PREDICATE";
            case "TABLE" -> "SQL_TABLE";
            case "RESERVED_INCREMENT", "VERSION_INCREMENT" -> "SQL_ASSIGNMENT";
            case "HTTP_METHOD", "ROUTE", "CONTROLLER_SERVICE_CALL", "REQUEST_BODY" ->
                    "JAVA_RECORD_DECLARATION";
            case "MAPPER_LOOKUP" -> "JAVA_CALL";
            case "QUANTITY_GUARD", "AVAILABILITY_GUARD", "UPDATE_COUNT_GUARD" -> "JAVA_GUARD";
            case "THROWN_EXCEPTION" -> "JAVA_THROW";
            case "AVAILABLE_FORMULA" -> "JAVA_RECORD_DECLARATION";
            case "RECEIPT_SKU", "RECEIPT_QUANTITY" -> "JAVA_RETURN";
            default -> null;
        };
        return nodes.stream().filter(node -> kind == null || kind.equals(node.kind())).findFirst()
                .orElse(nodes.get(0));
    }

    private static String nodeKey(RepositoryNode node, AtomSeed rootAtom) {
        return node.nodeId() + "|" + (rootAtom == null ? "dependency" : rootAtom.atomId());
    }

    private static List<String> rulesFor(String atomName) {
        if ("AVAILABLE_FORMULA".equals(atomName)) {
            return List.of("RESULTTYPE_FQN_TO_JAVA_PACKAGE_V1", "JAVA_PACKAGE_TO_RECORD_DECLARATION_V1",
                    "SELECT_PROJECTION_TO_RECORD_COMPONENT_V1");
        }
        if ("VERSION_PREDICATE".equals(atomName)) {
            return List.of("CONFIG_RESOLVES_MAPPER_DOCUMENT_V1", "XML_NAMESPACE_TO_JAVA_PACKAGE_V1",
                    "JAVA_PACKAGE_TO_INTERFACE_DECLARATION_V1", "RESULTTYPE_FQN_TO_JAVA_PACKAGE_V1",
                    "JAVA_PACKAGE_TO_RECORD_DECLARATION_V1", "SELECT_RESULTTYPE_TO_BOUND_FIND_CALL_RESULT_V1",
                    "RECORD_DECLARATION_TO_LOCAL_VARIABLE_TYPE_V1", "LOCAL_RECEIVER_TO_RECORD_ACCESSOR_V1",
                    "RECEIVER_DECLARATION_TO_CALL_SITE_V1", "ACCESSOR_EXPRESSION_TO_CALL_ARGUMENT_V1",
                    "CALL_ARGUMENT_POSITION_TO_MAPPER_PARAM_V1", "IMPORT_RESOLVES_MYBATIS_PARAM_ANNOTATION_V1",
                    "MAPPER_METHOD_TO_PARAM_V1", "MAPPER_METHOD_TO_XML_STATEMENT_V1",
                    "NAMESPACE_STATEMENT_TO_UPDATE_V1", "UPDATE_STATEMENT_CONTAINS_PREDICATE_V1",
                    "MYBATIS_PARAM_TO_SQL_PREDICATE_V1");
        }
        return List.of("SEMANTIC_BINDING_V1");
    }

    private static ProofLocator sqlLocator(GraphView graph, RepositoryNode node, AtomSeed atom) {
        String target = switch (atom.name()) {
            case "AVAILABLE_FORMULA" -> node.kind().equals("SQL_PROJECTION")
                    ? "SELECT sku, on_hand AS onHand, reserved_qty AS reserved, version" : null;
            case "SKU_PREDICATE" -> "sku = #{sku}";
            case "TABLE" -> "inventory";
            case "RESERVED_INCREMENT" -> "reserved_qty = reserved_qty + #{quantity}";
            case "VERSION_INCREMENT" -> "version = version + 1";
            case "VERSION_PREDICATE" -> "version = #{version}";
            default -> null;
        };
        if (target == null || !node.locator().path().endsWith(".xml")
                || !sqlSemanticNode(atom.name(), node.kind())) {
            return null;
        }
        byte[] bytes = graph.bytes(node.locator().path());
        String source = new String(bytes, StandardCharsets.UTF_8);
        int start = source.indexOf(target);
        if (start < 0) {
            return null;
        }
        int end = start + target.length();
        int startByte = source.substring(0, start).getBytes(StandardCharsets.UTF_8).length;
        int endByte = source.substring(0, end).getBytes(StandardCharsets.UTF_8).length;
        int startLine = 1 + (int) source.substring(0, start).chars().filter(value -> value == '\n').count();
        int lineStart = source.lastIndexOf('\n', Math.max(0, start - 1)) + 1;
        int endLine = 1 + (int) source.substring(0, end - 1).chars().filter(value -> value == '\n').count();
        int endLineStart = source.lastIndexOf('\n', Math.max(0, end - 1)) + 1;
        return new ProofLocator(node.locator().path(), startByte, endByte, startLine, start - lineStart + 1,
                endLine, end - endLineStart);
    }

    private static boolean sqlSemanticNode(String atomName, String nodeKind) {
        return ("AVAILABLE_FORMULA".equals(atomName) && "SQL_PROJECTION".equals(nodeKind))
                || (("SKU_PREDICATE".equals(atomName) || "VERSION_PREDICATE".equals(atomName))
                && "SQL_PREDICATE".equals(nodeKind))
                || ("TABLE".equals(atomName) && "SQL_TABLE".equals(nodeKind))
                || (("RESERVED_INCREMENT".equals(atomName) || "VERSION_INCREMENT".equals(atomName))
                && "SQL_ASSIGNMENT".equals(nodeKind));
    }

    private static ProofNode proofNode(VerifiedSnapshot snapshot, GraphView graph,
                                       RepositoryNode node, ProofLocator override) {
        RepositoryLocator locator = node.locator();
        ProofLocator proofLocator = override == null ? new ProofLocator(locator.path(), locator.startByte(),
                locator.endByteExclusive(), locator.startLine(), locator.startColumn(), locator.endLine(),
                locator.endColumn()) : override;
        byte[] bytes = graph.bytes(locator.path());
        if (bytes == null || proofLocator.startByte() < 0 || proofLocator.endByteExclusive() < proofLocator.startByte()
                || proofLocator.endByteExclusive() > bytes.length) {
            throw failure(Stage01FailureCode.PROOF_SOURCE_REOPEN_MISMATCH);
        }
        String sourceHash = snapshot.files().stream().filter(file -> file.path().equals(locator.path()))
                .map(VerifiedFile::sha256).findFirst().orElseThrow(
                        () -> failure(Stage01FailureCode.PROOF_SOURCE_REOPEN_MISMATCH));
        if (!sourceHash.equals(sha256(bytes))) {
            throw failure(Stage01FailureCode.PROOF_SOURCE_REOPEN_MISMATCH);
        }
        String spanHash = sha256(java.util.Arrays.copyOfRange(bytes, proofLocator.startByte(),
                proofLocator.endByteExclusive()));
        if (override == null && !spanHash.equals(node.spanSha256())) {
            throw failure(Stage01FailureCode.PROOF_SOURCE_REOPEN_MISMATCH);
        }
        String id = "proof-node:" + sha256(node.nodeId() + "\n" + sourceHash + "\n" + spanHash
                + "\n" + proofLocator.startByte() + "\n" + proofLocator.endByteExclusive());
        return new ProofNode(id, node.nodeId(), proofLocator, sourceHash, spanHash);
    }

    private static CandidateAccounting accounting(int candidateFacts, List<CodeFact> facts,
                                                   List<AtomDisposition> dispositions) {
        int admittedFacts = facts.size();
        int rejectedFacts = candidateFacts - admittedFacts;
        int candidateAtoms = dispositions.size();
        int admitted = (int) dispositions.stream().filter(value -> "ADMITTED_WITH_PROOF".equals(
                value.disposition())).count();
        int rejected = candidateAtoms - admitted;
        return new CandidateAccounting(candidateFacts, admittedFacts, rejectedFacts, candidateAtoms,
                admitted, rejected, admitted, dispositions);
    }

    private static void validateAccounting(CandidateAccounting accounting, List<CodeFact> facts) {
        int factAtoms = facts.stream().mapToInt(fact -> fact.atoms().size()).sum();
        boolean valid = accounting.candidateFactCount() == accounting.admittedFactCount()
                + accounting.rejectedFactCount()
                && accounting.candidateAtomCount() == accounting.admittedAtomDispositionCount()
                + accounting.rejectedAtomCount()
                && accounting.provenFactAtomCount() == accounting.admittedAtomDispositionCount()
                && accounting.provenFactAtomCount() == factAtoms
                && accounting.candidateAtomCount() == accounting.atomDispositions().size()
                && accounting.atomDispositions().stream().allMatch(disposition ->
                ("ADMITTED_WITH_PROOF".equals(disposition.disposition())
                        && disposition.admittedFactId() != null && disposition.proofId() != null
                        && disposition.reasonCode() == null)
                        || ("REJECTED_WITH_REASON".equals(disposition.disposition())
                        && disposition.admittedFactId() == null && disposition.proofId() == null
                        && disposition.reasonCode() != null));
        if (!valid) {
            throw failure(Stage01FailureCode.M3_ACCOUNTING_INVARIANT_BROKEN);
        }
    }

    private static List<ExpectationGap> expectationGaps(String snapshotId, GraphView graph) {
        if (!graph.looksLikeReservationWorkflow()) {
            return List.of();
        }
        RepositoryNode root = graph.node(node -> "JAVA_METHOD".equals(node.kind())
                && node.canonicalValue().contains("ReservationService#reserve/2"));
        String rootId = root == null ? "node:unresolved" : root.nodeId();
        List<ExpectationGap> gaps = new ArrayList<>();
        gaps.add(expectation(snapshotId, "LIFECYCLE_SYMMETRY", "RESERVATION_LIFECYCLE_TRIGGER_V1",
                rootId, "SUPPORTED_METHOD_AND_MAPPER_SEARCH_V1", "NO_SUPPORTED_LIFECYCLE_COUNTERPART_IN_SEARCHED_SCOPE",
                "ASK_RESERVATION_EXPIRY_RELEASE_POLICY"));
        if (!graph.anySourceContains("warehouse_id")) {
            gaps.add(expectation(snapshotId, "WAREHOUSE_KEY_SCOPE", "INVENTORY_KEY_SCOPE_TRIGGER_V1",
                    rootId, "FLOW_KEY_SCOPE_SEARCH_V1", "NO_WAREHOUSE_KEY_IN_FLOW_KEY_SCOPE",
                    "ASK_WAREHOUSE_SCOPE_POLICY"));
        }
        gaps.add(expectation(snapshotId, "UNIT_SEMANTICS", "QUANTITY_ARITHMETIC_TRIGGER_V1",
                rootId, "QUANTITY_SCOPE_SEARCH_V1", "NO_UNIT_SEMANTICS_IN_QUANTITY_SCOPE",
                "ASK_QUANTITY_UNIT_POLICY"));
        if (!graph.anySourceContains("for (") && !graph.anySourceContains("catch (")) {
            gaps.add(expectation(snapshotId, "RETRY_HANDLING", "NON_SINGLE_UPDATE_TRIGGER_V1", rootId,
                    "ENTRY_ROOTED_SUPPORTED_CFG_V1", "NO_RETRY_HANDLING_IN_ENTRY_FLOW_SCOPE",
                    "ASK_NON_SINGLE_UPDATE_RETRY_POLICY"));
        }
        gaps.add(expectation(snapshotId, "MISSING_ROW_HANDLING", "ROW_LOAD_DEREFERENCE_TRIGGER_V1",
                rootId, "LOAD_TO_DEREFERENCE_CFG_V1", "NO_MISSING_ROW_BRANCH_IN_LOAD_SCOPE",
                "ASK_MISSING_ROW_POLICY"));
        return List.copyOf(gaps);
    }

    private static ExpectationGap expectation(String snapshotId, String expectationId,
                                              String triggerRuleId, String rootNodeId,
                                              String searchRuleId, String reason, String question) {
        String id = "gap:" + sha256(snapshotId + "\n" + expectationId + "\n" + triggerRuleId + "\n"
                + rootNodeId + "\n" + searchRuleId + "\n" + reason + "\n" + question);
        return new ExpectationGap(id, expectationId, triggerRuleId,
                List.of(new SearchedScope(rootNodeId, searchRuleId)), List.of(rootNodeId),
                new AbsenceEvidence(searchRuleId, 0), reason, question);
    }

    private static List<CapabilityGap> capabilityGaps(String snapshotId, CapabilityReport report) {
        List<CapabilityGap> gaps = new ArrayList<>();
        for (CapabilitySite site : report.sites()) {
            if ("SUPPORTED".equals(site.disposition())) {
                continue;
            }
            RepositoryLocator locator = site.locator();
            ProofLocator proofLocator = new ProofLocator(locator.path(), locator.startByte(),
                    locator.endByteExclusive(), locator.startLine(), locator.startColumn(), locator.endLine(),
                    locator.endColumn());
            String id = "gap:" + sha256(snapshotId + "\n" + site.siteId() + "\n" + site.reasonCode());
            gaps.add(new CapabilityGap(id, site.siteId(), site.reasonCode(), site.entryIds(), proofLocator));
        }
        gaps.sort(Comparator.comparing(CapabilityGap::gapId));
        return List.copyOf(gaps);
    }

    private static FactRejection rejection(String snapshotId, String factKey, String atomKey,
                                           String code, List<String> required, List<String> available,
                                           ProofLocator locator) {
        List<String> requiredIds = required.stream().filter(Objects::nonNull).sorted().toList();
        List<String> availableIds = available.stream().filter(Objects::nonNull).distinct().sorted().toList();
        String id = "rejection:" + sha256(snapshotId + "\n" + factKey + "\n" + atomKey + "\n"
                + code + "\n" + requiredIds + "\n" + availableIds);
        return new FactRejection(id, factKey, atomKey, code, requiredIds, availableIds, locator);
    }

    private static String factId(String snapshotId, String kind, List<String> subjects,
                                 List<AtomSeed> atoms) {
        return "fact:" + sha256(snapshotId + "\n" + kind + "\n"
                + subjects.stream().sorted().toList() + "\n"
                + atoms.stream().map(AtomSeed::atomId).sorted().toList());
    }

    private static String canonicalFactSet(String snapshotId, List<CodeFact> facts,
                                           CandidateAccounting accounting) {
        return snapshotId + "\n" + canonical(facts) + "\n" + accounting;
    }

    private static String canonicalLedger(String snapshotId, List<CapabilityGap> capabilityGaps,
                                          List<FactRejection> rejections,
                                          List<ExpectationGap> expectations) {
        return snapshotId + "\n" + GAP_PROFILE_ID + "\n" + GAP_PROFILE_SHA256 + "\n"
                + canonical(capabilityGaps) + "\n" + canonical(rejections) + "\n"
                + canonical(expectations);
    }

    private static String canonical(Collection<?> values) {
        return values.stream().map(Object::toString).sorted().reduce("", (left, right) -> left + "\n" + right);
    }

    private static String stripVersion(String value) {
        return value != null && value.startsWith("v1:") ? value.substring(3) : value;
    }

    private static boolean contains(RepositoryLocator outer, RepositoryLocator inner) {
        return outer != null && inner != null && outer.path().equals(inner.path())
                && outer.startByte() <= inner.startByte()
                && outer.endByteExclusive() >= inner.endByteExclusive();
    }

    @SafeVarargs
    private static <T> List<T> nullableList(T... values) {
        return Arrays.asList(values);
    }

    private static String sha256(String value) {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value));
        } catch (NoSuchAlgorithmException unavailable) {
            throw new IllegalStateException("SHA-256 unavailable", unavailable);
        }
    }

    private static Stage01Exception failure(Stage01FailureCode code) {
        return new Stage01Exception(code);
    }

    private record AtomSeed(String key, String atomId, String role, String name, FactValue value) {
    }

    private record PendingFact(String candidateFactKey, String factId, String kind,
                               List<String> subjectNodeIds,
                               List<AtomSeed> atoms, List<String> dependencyNodeIds) {
        private PendingFact {
            subjectNodeIds = subjectNodeIds.stream().distinct().sorted().toList();
            atoms = List.copyOf(atoms);
            dependencyNodeIds = dependencyNodeIds.stream().distinct().sorted().toList();
        }
    }

    private record FactCandidate(String key, String kind, Evaluator evaluator, AtomFactory atomFactory) {
        private CandidateEvaluation evaluate(GraphView graph) {
            return evaluator.evaluate(graph);
        }

        private List<AtomSeed> atoms(CandidateEvaluation evaluation) {
            return atomFactory.create(evaluation);
        }
    }

    @FunctionalInterface
    private interface Evaluator {
        CandidateEvaluation evaluate(GraphView graph);
    }

    @FunctionalInterface
    private interface AtomFactory {
        List<AtomSeed> create(CandidateEvaluation evaluation);
    }

    private record CandidateEvaluation(boolean accepted, List<String> subjectNodeIds,
                                       List<String> dependencyNodeIds, List<String> requiredNodeIds,
                                       String reasonCode, ProofLocator failureLocator,
                                       Map<String, String> values) {
        private CandidateEvaluation {
            subjectNodeIds = subjectNodeIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
            dependencyNodeIds = dependencyNodeIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
            requiredNodeIds = requiredNodeIds.stream().filter(Objects::nonNull).distinct().sorted().toList();
            values = Map.copyOf(values);
        }

        private String value(String key) {
            return values.get(key);
        }
    }

    private record UnpackedProof(String factId, String atomId, List<String> requiredNodeIds,
                                 List<String> requiredEdgeIds) {
    }

    private record ProofAssembly(ProofPack proofPack) {
        private String proofPackId() {
            return proofPack.proofPackId();
        }

        private String proofId(String factId, String atomId) {
            return proofPack.proofs().stream().filter(proof -> proof.factId().equals(factId)
                    && proof.atomId().equals(atomId)).map(Proof::proofId).findFirst().orElseThrow(
                            () -> failure(Stage01FailureCode.PROOF_PACK_REFERENCE_BROKEN));
        }
    }

    /** One unique service closure, owned by its deterministic HTTP entry. */
    private record Workflow(RepositoryEntry entry, String serviceMethodNodeId, Set<String> paths) {
        private Workflow {
            paths = Set.copyOf(paths);
        }
    }

    private record MapperBinding(RepositoryEdge call, RepositoryEdge statement) {
    }

    private static final class GraphView {
        private final VerifiedSnapshot snapshot;
        private final RepositoryModel model;
        private final Map<String, byte[]> sourceBytes;
        private final Map<String, RepositoryNode> nodes;
        private final Map<String, RepositoryEdge> edges;
        private final Set<String> workflowPaths;

        private GraphView(VerifiedSnapshot snapshot, RepositoryModel model,
                          Map<String, byte[]> sourceBytes) {
            this.snapshot = Objects.requireNonNull(snapshot, "snapshot");
            this.model = Objects.requireNonNull(model, "model");
            this.sourceBytes = Map.copyOf(sourceBytes);
            this.nodes = model.nodes().stream().collect(java.util.stream.Collectors.toMap(
                    RepositoryNode::nodeId, node -> node, (left, right) -> left, LinkedHashMap::new));
            this.edges = model.edges().stream().collect(java.util.stream.Collectors.toMap(
                    RepositoryEdge::edgeId, edge -> edge, (left, right) -> left, LinkedHashMap::new));
            this.workflowPaths = workflowPaths();
        }

        private String snapshotId() {
            return snapshot.snapshotId();
        }

        private RepositoryNode node(String nodeId) {
            RepositoryNode node = nodes.get(nodeId);
            if (node == null) {
                throw failure(Stage01FailureCode.PROOF_PACK_REFERENCE_BROKEN);
            }
            return node;
        }

        private RepositoryNode node(Predicate<RepositoryNode> predicate) {
            return nodes.values().stream().filter(node -> workflowPaths.isEmpty()
                    || workflowPaths.contains(node.locator().path())).filter(predicate).sorted(Comparator.comparing(
                    RepositoryNode::nodeId)).findFirst().orElse(null);
        }

        private RepositoryEdge edge(Predicate<RepositoryEdge> predicate) {
            return edges.values().stream().filter(predicate).sorted(Comparator.comparing(
                    RepositoryEdge::edgeId)).findFirst().orElse(null);
        }

        private RepositoryEntry entry(String method, String route) {
            return model.entries().stream().filter(entry -> method.equals(entry.httpMethod())
                    && route.equals(entry.route())).sorted(Comparator.comparing(RepositoryEntry::entryId))
                    .findFirst().orElse(null);
        }

        private List<RepositoryEdge> edges() {
            return model.edges();
        }

        private List<Workflow> workflows() {
            Map<String, List<RepositoryEntry>> entriesByService = new LinkedHashMap<>();
            for (RepositoryEntry entry : model.entries().stream().sorted(Comparator.comparing(RepositoryEntry::route)
                    .thenComparing(RepositoryEntry::entryId)).toList()) {
                String service = model.controlFlows().stream().filter(flow -> flow.entryId().equals(entry.entryId()))
                        .flatMap(flow -> flow.flowEdgeIds().stream()).map(edges::get)
                        .filter(Objects::nonNull).filter(edge -> "CFG_ENTRY".equals(edge.kind()))
                        .filter(edge -> edge.fromNodeId().equals(entry.methodNodeId()))
                        .map(RepositoryEdge::toNodeId).filter(target -> !target.equals(entry.methodNodeId()))
                        .sorted().findFirst().orElse(null);
                if (service != null && nodes.containsKey(service)) {
                    entriesByService.computeIfAbsent(service, ignored -> new ArrayList<>()).add(entry);
                }
            }
            List<Workflow> result = new ArrayList<>();
            Set<String> representedEntries = new LinkedHashSet<>();
            for (Map.Entry<String, List<RepositoryEntry>> group : entriesByService.entrySet()) {
                RepositoryEntry owner = group.getValue().stream().sorted(Comparator.comparing(RepositoryEntry::route)
                        .thenComparing(RepositoryEntry::entryId)).findFirst().orElseThrow();
                result.add(new Workflow(owner, group.getKey(), workflowPaths(owner, group.getKey())));
                group.getValue().forEach(entry -> representedEntries.add(entry.entryId()));
            }
            // Candidate accounting remains total when a route is parsed but its direct target cannot
            // be resolved (for example, after a package-only symbol rename).  This provisional
            // closure contains only that entry's bytes, so every candidate is honestly rejected
            // instead of borrowing another service's facts.
            for (RepositoryEntry entry : model.entries().stream().sorted(Comparator.comparing(RepositoryEntry::route)
                    .thenComparing(RepositoryEntry::entryId)).toList()) {
                if (!representedEntries.contains(entry.entryId())) {
                    result.add(new Workflow(entry, entry.methodNodeId(), workflowPaths(entry, entry.methodNodeId())));
                }
            }
            return result.stream().sorted(Comparator.comparing((Workflow workflow) -> workflow.entry().route())
                    .thenComparing(workflow -> workflow.entry().entryId())).toList();
        }

        private Set<String> workflowPaths(RepositoryEntry entry, String serviceMethodNodeId) {
            LinkedHashSet<String> paths = new LinkedHashSet<>();
            RepositoryNode entryMethod = node(entry.methodNodeId());
            RepositoryNode serviceMethod = node(serviceMethodNodeId);
            paths.add(entryMethod.locator().path());
            paths.add(serviceMethod.locator().path());
            List<RepositoryEdge> serviceCalls = edges.values().stream().filter(edge -> "CALL_TARGET".equals(edge.kind()))
                    .filter(edge -> contains(serviceMethod.locator(), node(edge.fromNodeId()).locator()))
                    .sorted(Comparator.comparing(RepositoryEdge::edgeId)).toList();
            for (RepositoryEdge call : serviceCalls) {
                RepositoryNode mapperMethod = node(call.toNodeId());
                if (!"JAVA_METHOD".equals(mapperMethod.kind())) {
                    continue;
                }
                paths.add(mapperMethod.locator().path());
                edges.values().stream().filter(edge -> "METHOD_STATEMENT".equals(edge.kind())
                                && edge.fromNodeId().equals(mapperMethod.nodeId()))
                        .forEach(edge -> paths.add(node(edge.toNodeId()).locator().path()));
            }
            edges.values().stream().filter(edge -> "NAMESPACE_INTERFACE".equals(edge.kind()))
                    .filter(edge -> paths.contains(node(edge.toNodeId()).locator().path()))
                    .forEach(edge -> paths.add(node(edge.fromNodeId()).locator().path()));
            edges.values().stream().filter(edge -> "CONFIG_RESOLVES_MAPPER".equals(edge.kind()))
                    .filter(edge -> paths.contains(node(edge.toNodeId()).locator().path()))
                    .forEach(edge -> paths.add(node(edge.fromNodeId()).locator().path()));
            return Set.copyOf(paths);
        }

        private RepositoryNode workflowNode(Workflow workflow, Predicate<RepositoryNode> predicate) {
            return nodes.values().stream().filter(node -> workflow.paths().contains(node.locator().path()))
                    .filter(predicate).sorted(Comparator.comparing(RepositoryNode::nodeId)).findFirst().orElse(null);
        }

        private RepositoryEdge workflowEdge(Workflow workflow, Predicate<RepositoryEdge> predicate) {
            return edges.values().stream().filter(edge -> endpointsIn(workflow, edge)).filter(predicate)
                    .sorted(Comparator.comparing(RepositoryEdge::edgeId)).findFirst().orElse(null);
        }

        private boolean endpointsIn(Workflow workflow, RepositoryEdge edge) {
            RepositoryNode from = nodes.get(edge.fromNodeId());
            RepositoryNode to = nodes.get(edge.toNodeId());
            return from != null && to != null && workflow.paths().contains(from.locator().path())
                    && workflow.paths().contains(to.locator().path());
        }

        private CandidateEvaluation workflowEvaluation(Workflow workflow, List<RepositoryNode> requiredNodes,
                                                       List<RepositoryEdge> requiredEdges,
                                                       Map<String, String> values) {
            List<RepositoryNode> present = requiredNodes.stream().filter(Objects::nonNull).toList();
            List<RepositoryEdge> presentEdges = requiredEdges.stream().filter(Objects::nonNull).toList();
            boolean outside = present.stream().anyMatch(node -> !workflow.paths().contains(node.locator().path()))
                    || presentEdges.stream().anyMatch(edge -> !endpointsIn(workflow, edge));
            if (outside || present.size() != requiredNodes.size() || presentEdges.size() != requiredEdges.size()) {
                RepositoryNode first = present.isEmpty() ? null : present.get(0);
                return new CandidateEvaluation(false, ids(present), ids(present), ids(requiredNodes),
                        "REQUIRED_GRAPH_BINDING_UNRESOLVED", locator(first), values);
            }
            List<String> dependencies = new ArrayList<>(ids(present));
            for (RepositoryEdge edge : presentEdges) {
                dependencies.add(edge.fromNodeId());
                dependencies.add(edge.toNodeId());
            }
            return new CandidateEvaluation(true, ids(present), dependencies, ids(requiredNodes), null, null, values);
        }

        private CandidateEvaluation workflowRejected(Workflow workflow, String code,
                                                      List<RepositoryNode> available) {
            List<RepositoryNode> present = available.stream().filter(Objects::nonNull)
                    .filter(node -> workflow.paths().contains(node.locator().path())).toList();
            return new CandidateEvaluation(false, ids(present), ids(present), ids(available), code,
                    locator(present.isEmpty() ? null : present.get(0)), Map.of());
        }

        private MapperBinding mapperBinding(Workflow workflow, String statementKind) {
            RepositoryNode service = node(workflow.serviceMethodNodeId());
            for (RepositoryEdge call : edges.values().stream().filter(edge -> "CALL_TARGET".equals(edge.kind()))
                    .filter(edge -> contains(service.locator(), node(edge.fromNodeId()).locator()))
                    .sorted(Comparator.comparing(RepositoryEdge::edgeId)).toList()) {
                RepositoryEdge statement = edges.values().stream().filter(edge -> "METHOD_STATEMENT".equals(edge.kind()))
                        .filter(edge -> edge.fromNodeId().equals(call.toNodeId()))
                        .filter(edge -> endpointsIn(workflow, edge))
                        .filter(edge -> stripVersion(node(edge.toNodeId()).canonicalValue()).startsWith(statementKind + ":"))
                        .sorted(Comparator.comparing(RepositoryEdge::edgeId)).findFirst().orElse(null);
                if (statement != null) {
                    return new MapperBinding(call, statement);
                }
            }
            return null;
        }

        private RepositoryNode statementFragment(String statementNodeId, String kind) {
            return edges.values().stream().filter(edge -> "STATEMENT_SQL_FRAGMENT".equals(edge.kind()))
                    .filter(edge -> edge.fromNodeId().equals(statementNodeId)).map(edge -> nodes.get(edge.toNodeId()))
                    .filter(Objects::nonNull).filter(node -> kind.equals(node.kind()))
                    .sorted(Comparator.comparing(RepositoryNode::nodeId)).findFirst().orElse(null);
        }

        private RepositoryEdge resultBinding(String statementNodeId) {
            return edges.values().stream().filter(edge -> "RESULT_TYPE".equals(edge.kind()))
                    .filter(edge -> edge.fromNodeId().equals(statementNodeId))
                    .sorted(Comparator.comparing(RepositoryEdge::edgeId)).findFirst().orElse(null);
        }

        private RepositoryNode recordFor(RepositoryNode resultType) {
            if (resultType == null) {
                return null;
            }
            String expected = resultType.canonicalValue();
            return nodes.values().stream().filter(node -> "JAVA_RECORD_DECLARATION".equals(node.kind()))
                    .filter(node -> expected.equals(node.canonicalValue())).sorted(Comparator.comparing(
                            RepositoryNode::nodeId)).findFirst().orElse(null);
        }

        private RepositoryNode packageAt(String path) {
            return nodes.values().stream().filter(node -> "JAVA_PACKAGE".equals(node.kind()))
                    .filter(node -> path.equals(node.locator().path())).sorted(Comparator.comparing(
                            RepositoryNode::nodeId)).findFirst().orElse(null);
        }

        private String availableFormula(Workflow workflow) {
            RepositoryNode service = node(workflow.serviceMethodNodeId());
            byte[] bytes = sourceBytes.get(service.locator().path());
            if (bytes == null) {
                return null;
            }
            Matcher matcher = Pattern.compile("int\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*"
                    + "[A-Za-z_][A-Za-z0-9_]*\\.([A-Za-z_][A-Za-z0-9_]*)\\(\\)\\s*-\\s*"
                    + "[A-Za-z_][A-Za-z0-9_]*\\.([A-Za-z_][A-Za-z0-9_]*)\\(\\)").matcher(
                    new String(bytes, StandardCharsets.UTF_8));
            return matcher.find() ? matcher.group(1) + " = " + matcher.group(2) + " - " + matcher.group(3) : null;
        }

        private byte[] bytes(String path) {
            return sourceBytes.get(path);
        }

        private boolean anySourceContains(String text) {
            return sourceBytes.entrySet().stream().filter(entry -> workflowPaths.isEmpty()
                    || workflowPaths.contains(entry.getKey())).map(entry -> new String(entry.getValue(), StandardCharsets.UTF_8))
                    .anyMatch(source -> source.contains(text));
        }

        private Set<String> workflowPaths() {
            LinkedHashSet<String> paths = new LinkedHashSet<>();
            List<RepositoryEdge> calls = model.edges().stream().filter(edge -> "CALL_TARGET".equals(edge.kind()))
                    .sorted(Comparator.comparing(RepositoryEdge::edgeId)).toList();
            for (RepositoryEdge controllerCall : calls) {
                RepositoryNode serviceMethod = nodes.get(controllerCall.toNodeId());
                if (serviceMethod == null || !"JAVA_METHOD".equals(serviceMethod.kind())) {
                    continue;
                }
                String servicePath = serviceMethod.locator().path();
                boolean hasDownstreamMapperCall = calls.stream().anyMatch(edge -> {
                    RepositoryNode from = nodes.get(edge.fromNodeId());
                    return from != null && servicePath.equals(from.locator().path());
                });
                if (!hasDownstreamMapperCall) {
                    continue;
                }
                paths.add(nodes.get(controllerCall.fromNodeId()).locator().path());
                paths.add(servicePath);
                for (RepositoryEdge mapperCall : calls) {
                    RepositoryNode from = nodes.get(mapperCall.fromNodeId());
                    if (from == null || !servicePath.equals(from.locator().path())) {
                        continue;
                    }
                    RepositoryNode mapperMethod = nodes.get(mapperCall.toNodeId());
                    if (mapperMethod == null) {
                        continue;
                    }
                    paths.add(mapperMethod.locator().path());
                    model.edges().stream().filter(edge -> "METHOD_STATEMENT".equals(edge.kind())
                                    && edge.fromNodeId().equals(mapperMethod.nodeId()))
                            .forEach(edge -> paths.add(nodes.get(edge.toNodeId()).locator().path()));
                }
            }
            model.edges().stream().filter(edge -> "CONFIG_RESOLVES_MAPPER".equals(edge.kind()))
                    .filter(edge -> paths.contains(nodes.get(edge.toNodeId()).locator().path()))
                    .forEach(edge -> paths.add(nodes.get(edge.fromNodeId()).locator().path()));
            return Set.copyOf(paths);
        }

        private boolean looksLikeReservationWorkflow() {
            return nodes.values().stream().anyMatch(node -> "JAVA_GUARD".equals(node.kind()));
        }

        private CandidateEvaluation evaluation(List<RepositoryNode> requiredNodes,
                                               List<RepositoryEdge> requiredEdges,
                                               Map<String, String> values) {
            List<RepositoryNode> present = requiredNodes.stream().filter(Objects::nonNull).toList();
            List<RepositoryEdge> presentEdges = requiredEdges.stream().filter(Objects::nonNull).toList();
            if (present.size() != requiredNodes.size() || presentEdges.size() != requiredEdges.size()) {
                RepositoryNode first = present.isEmpty() ? null : present.get(0);
                return new CandidateEvaluation(false, ids(present), ids(present), ids(requiredNodes),
                        "REQUIRED_GRAPH_BINDING_UNRESOLVED", locator(first), values);
            }
            List<String> dependencies = new ArrayList<>(ids(present));
            for (RepositoryEdge edge : presentEdges) {
                dependencies.add(edge.fromNodeId());
                dependencies.add(edge.toNodeId());
            }
            List<String> subjects = ids(present);
            return new CandidateEvaluation(true, subjects, dependencies, ids(requiredNodes), null, null, values);
        }

        private CandidateEvaluation rejected(String code, List<RepositoryNode> available) {
            List<RepositoryNode> present = available.stream().filter(Objects::nonNull).toList();
            return new CandidateEvaluation(false, ids(present), ids(present), ids(available), code,
                    locator(present.isEmpty() ? null : present.get(0)), Map.of());
        }

        private static List<String> ids(List<RepositoryNode> nodes) {
            return nodes.stream().filter(Objects::nonNull).map(RepositoryNode::nodeId).distinct().sorted().toList();
        }

        private static ProofLocator locator(RepositoryNode node) {
            if (node == null) {
                return null;
            }
            RepositoryLocator locator = node.locator();
            return new ProofLocator(locator.path(), locator.startByte(), locator.endByteExclusive(),
                    locator.startLine(), locator.startColumn(), locator.endLine(), locator.endColumn());
        }
    }
}
