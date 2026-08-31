package com.linguan.codemd.stage03;

/** Frozen registry set used to constrain model selection and reader rendering. */
public record RegistryBundle(
        String registryBundleId,
        BusinessTermRegistry businessTerms,
        TechnicalDisplayRegistry technicalDisplays,
        ClaimRegistry claims,
        QuestionRegistry questions,
        ReaderSentenceTemplateRegistry sentenceTemplates,
        SectionOwnershipRegistry sectionOwnership) {
}
