package com.rdsos.wifianalyzer.service.iface;

import com.rdsos.wifianalyzer.exception.CommandFailedException;
import com.rdsos.wifianalyzer.model.CommandResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/** Runs an external command given as an argument list (never a shell string). */
public interface CommandRunner {

    CommandResult run(List<String> command);

    default CommandResult runChecked(List<String> command) {
        CommandResult result = run(command);
        if (result.getExitCode() != 0) {
            throw new CommandFailedException(command, result);
        }
        return result;
    }
}
