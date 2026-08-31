package com.linguan.codemd.stage03;

/** A pending-question value tied to an admitted evidence gap. */
public record BoundedQuestionSlot(String slotKey, String gapId, String value) implements ReaderSlot {
}
