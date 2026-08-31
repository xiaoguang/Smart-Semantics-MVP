package com.linguan.codemd.stage02;

import com.linguan.codemd.stage01.FactValue;

/** Read-only admitted atom projection; only the original proof ID is retained. */
public record AllowedAtomView(String atomId, String role, String name, FactValue value,
                              String proofId) {
}
