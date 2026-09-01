package com.linguan.codemd.target;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Prevents the replacement implementation from silently rebuilding a compatibility dependency. */
@AnalyzeClasses(packages = "com.linguan.codemd.target")
class TargetArchitectureTest {
    @ArchTest
    static final ArchRule targetProductionDoesNotDependOnLegacyImplementation =
            noClasses()
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "..mvp..",
                            "..analysis..",
                            "..discovery..",
                            "..stage01..",
                            "..stage02..",
                            "..stage03..",
                            "..stage04..",
                            "..cli..");
}
