package com.linguan.codemd.stage01;

import java.util.List;

/** One admitted composite source fact; every atom has a closed proof. */
public record CodeFact(String factId, String kind, List<String> subjectNodeIds,
                       List<FactAtom> atoms) {
    public CodeFact {
        subjectNodeIds = List.copyOf(subjectNodeIds);
        atoms = List.copyOf(atoms);
    }
}
