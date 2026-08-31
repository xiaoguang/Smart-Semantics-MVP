package com.linguan.codemd.stage03;

/** Replays frozen Stage 03 rounds without constructing or invoking a model provider. */
public final class Stage03DeterministicReplay {
    public Stage03Result replay(Stage03ReplayRequest request) {
        return new Stage03Generator().replay(request);
    }
}
