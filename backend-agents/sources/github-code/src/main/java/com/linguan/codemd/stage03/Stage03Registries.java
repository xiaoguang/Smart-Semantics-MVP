package com.linguan.codemd.stage03;

/**
 * Deterministically freezes inline Stage 03 registries.  Callers provide their
 * reviewed entries and stable registry IDs; this seam computes, rather than
 * trusts, every recursive content digest and the enclosing bundle identity.
 */
public final class Stage03Registries {
    private Stage03Registries() {
    }

    public static RegistryBundle freeze(BusinessTermRegistry businessTerms,
                                        TechnicalDisplayRegistry technicalDisplays,
                                        ClaimRegistry claims, QuestionRegistry questions,
                                        ReaderSentenceTemplateRegistry sentenceTemplates,
                                        SectionOwnershipRegistry sectionOwnership) {
        BusinessTermRegistry frozenTerms = new BusinessTermRegistry(businessTerms.schemaVersion(),
                businessTerms.registryId(), Stage03RegistryCanonicalizer.businessTermsDigest(businessTerms),
                businessTerms.terms());
        TechnicalDisplayRegistry frozenDisplays = new TechnicalDisplayRegistry(technicalDisplays.schemaVersion(),
                technicalDisplays.registryId(), Stage03RegistryCanonicalizer.technicalDisplaysDigest(technicalDisplays),
                technicalDisplays.policies());
        ClaimRegistry frozenClaims = new ClaimRegistry(claims.schemaVersion(), claims.registryId(),
                Stage03RegistryCanonicalizer.claimsDigest(claims), claims.claims());
        QuestionRegistry frozenQuestions = new QuestionRegistry(questions.schemaVersion(), questions.registryId(),
                Stage03RegistryCanonicalizer.questionsDigest(questions), questions.questions());
        ReaderSentenceTemplateRegistry frozenTemplates = new ReaderSentenceTemplateRegistry(
                sentenceTemplates.schemaVersion(), sentenceTemplates.registryId(),
                Stage03RegistryCanonicalizer.templatesDigest(sentenceTemplates), sentenceTemplates.templates());
        SectionOwnershipRegistry frozenOwnership = new SectionOwnershipRegistry(sectionOwnership.schemaVersion(),
                sectionOwnership.registryId(), Stage03RegistryCanonicalizer.ownershipDigest(sectionOwnership),
                sectionOwnership.rules());
        String bundleId = Stage03RegistryCanonicalizer.bundleId(frozenTerms, frozenDisplays, frozenClaims,
                frozenQuestions, frozenTemplates, frozenOwnership);
        return new RegistryBundle(bundleId, frozenTerms, frozenDisplays, frozenClaims, frozenQuestions,
                frozenTemplates, frozenOwnership);
    }
}
