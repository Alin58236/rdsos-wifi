package com.rdsos.wifianalyzer.service.iface;

import com.rdsos.wifianalyzer.exception.CommandFailedException;
import com.rdsos.wifianalyzer.service.iface.model.CommandResult;

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
