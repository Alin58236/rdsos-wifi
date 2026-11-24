package com.rdsos.wifianalyzer.api;

import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class ReplayFilesController {

    private final ReplayFiles replayFiles;

    @GetMapping("/api/replay-files")
    public List<String> listReplayFiles() {
        return replayFiles.list();
    }
}
