package com.linguan.codemd.stage03;

import java.util.List;

/** A frozen literal with an exact finite typed-slot declaration. */
public record ReaderSentenceTemplate(String templateKey, String ownerSectionKey,
                                     String literalPattern, List<TemplateSlotDeclaration> slots) {
    public ReaderSentenceTemplate {
        slots = slots == null ? null : List.copyOf(slots);
    }
}
