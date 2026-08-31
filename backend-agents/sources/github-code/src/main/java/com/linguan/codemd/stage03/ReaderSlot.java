package com.linguan.codemd.stage03;

/** Closed slot family; no model-controlled free-text slot exists. */
public sealed interface ReaderSlot permits ProvenValueSlot, BusinessTermSlot, TechnicalDisplaySlot,
        BoundedQuestionSlot {
    String slotKey();
}
