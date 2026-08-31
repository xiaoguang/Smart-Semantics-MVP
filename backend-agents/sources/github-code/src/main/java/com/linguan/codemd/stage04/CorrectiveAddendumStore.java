package com.linguan.codemd.stage04;

/**
 * Read-only boundary for a separately archived diagnosis.  A Round-2 request
 * only carries its content-addressed ID; it never supplies corrective prose.
 */
public interface CorrectiveAddendumStore {
    CorrectiveAddendum resolveExact(String parentCandidateId, ReviewFindingSet findings, String addendumId);
}
