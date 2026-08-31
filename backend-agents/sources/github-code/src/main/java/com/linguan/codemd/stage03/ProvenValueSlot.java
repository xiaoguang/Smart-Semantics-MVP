package com.linguan.codemd.stage03;

/** A display value copied from a proven atom. */
public record ProvenValueSlot(String slotKey, String atomId, String value) implements ReaderSlot {
}
