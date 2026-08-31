package com.linguan.codemd.stage01;

/** A normalized semantic atom whose identity deliberately excludes its proof identity. */
public record FactAtom(String atomId, String role, String name, FactValue value,
                       String proofPackId, String proofId) {
}
