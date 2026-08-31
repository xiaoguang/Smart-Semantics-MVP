package com.linguan.codemd.stage01;

/** Disposition for every candidate atom, including siblings of rejected composite facts. */
public record AtomDisposition(String candidateFactKey, String atomKey, String disposition,
                              String admittedFactId, String proofId, String reasonCode) {
}
