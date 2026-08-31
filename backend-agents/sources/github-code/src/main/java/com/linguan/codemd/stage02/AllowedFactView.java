package com.linguan.codemd.stage02;

import java.util.List;

/** Read-only admitted fact projection for a single Capsule. */
public record AllowedFactView(String factId, String kind, List<AllowedAtomView> atoms) {
    public AllowedFactView {
        atoms = List.copyOf(atoms);
    }
}
