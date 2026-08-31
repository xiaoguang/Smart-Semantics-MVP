package com.linguan.codemd.stage01;

import java.util.List;

/** Conserved M3 candidate and atom numerator/denominator accounting. */
public record CandidateAccounting(int candidateFactCount, int admittedFactCount,
                                  int rejectedFactCount, int candidateAtomCount,
                                  int admittedAtomDispositionCount, int rejectedAtomCount,
                                  int provenFactAtomCount,
                                  List<AtomDisposition> atomDispositions) {
    public CandidateAccounting {
        atomDispositions = List.copyOf(atomDispositions);
    }
}
