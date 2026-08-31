package com.linguan.codemd.stage01;

/** Immutable identity of the repository bound to a frozen capture. */
public record Origin(String kind, String repositoryUrl, String revision) {
}
