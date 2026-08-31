package com.linguan.codemd.stage03;

import com.linguan.codemd.stage01.Stage01Analyzer;
import com.linguan.codemd.stage01.Stage01Request;
import com.linguan.codemd.stage01.Stage01Result;
import com.linguan.codemd.stage02.Stage02Compiler;
import com.linguan.codemd.stage02.Stage02Request;
import com.linguan.codemd.stage02.Stage02ResourceBudget;
import com.linguan.codemd.stage02.Stage02Result;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Test-only public bridge that keeps Stage03Fixtures package-private while
 * allowing a Stage04 test to consume an honest Stage01 -> Stage03 scenario.
 */
public final class Stage03ScenarioBridge {
    private Stage03ScenarioBridge() {
    }

    public static Scenario reservation(Path temporaryDirectory) throws IOException {
        Path root = Stage03Fixtures.copyReservationSnapshot(temporaryDirectory);
        Stage01Request stage01Request = Stage03Fixtures.stage01Request(root);
        Stage01Result stage01Result = new Stage01Analyzer().analyze(stage01Request);
        Stage02Request stage02Request = Stage03Fixtures.stage02Request(stage01Request,
                stage01Result.stage01ResultId(), new Stage02ResourceBudget(128, 64, 20_000,
                        40_000, 128, 64, 16_384, 262_144, 256));
        Stage02Result stage02Result = new Stage02Compiler().compile(stage02Request);
        Stage03Request stage03Request = Stage03Fixtures.stage03Request(stage02Request,
                stage02Result.stage02ResultId(), Stage03Fixtures.registryBundle(stage02Result));
        Stage03Result stage03Result = new Stage03Generator().generate(stage03Request,
                Stage03Fixtures.validProvider(stage02Result));
        return new Scenario(stage01Request, stage01Result, stage02Request, stage02Result,
                stage03Request, stage03Result);
    }

    public record Scenario(Stage01Request stage01Request, Stage01Result stage01Result,
                           Stage02Request stage02Request, Stage02Result stage02Result,
                           Stage03Request stage03Request, Stage03Result stage03Result) {
    }
}
