package com.linguan.codemd.stage03;

import java.util.List;

/** A field or dimension whose role is established by an admitted atom. */
public record FieldDimension(String fieldId, String display, List<String> basisAtomIds) {
    public FieldDimension {
        basisAtomIds = List.copyOf(basisAtomIds);
    }
}
