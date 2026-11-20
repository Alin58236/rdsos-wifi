package com.rdsos.wifianalyzer.service.iface;

import com.rdsos.wifianalyzer.service.iface.model.CommandResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class ProcessCommandRunner implements CommandRunner {

    private static final long TIMEOUT_SECONDS = 15;

    @Override
    public CommandResult run(List<String> command) {
        log.debug("Running {}", command);
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            // Read output concurrently so a longer command doesnt block on pipes.
            CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> readAll(process));
            if (!process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new CommandResult(-1, "timed out after " + TIMEOUT_SECONDS + " s");
            }
            return new CommandResult(process.exitValue(), output.get());
        } catch (IOException e) {
            return new CommandResult(-1, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(-1, "interrupted");
        } catch (ExecutionException e) {
            return new CommandResult(-1, e.getCause().getMessage());
        }
    }

    private static String readAll(Process process) {
        try {
            return new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
