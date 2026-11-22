package com.rdsos.wifianalyzer.exception;

import com.rdsos.wifianalyzer.service.iface.model.CommandResult;

import java.util.List;

public class CommandFailedException extends RuntimeException {

    public CommandFailedException(List<String> command, CommandResult result) {
        super("'%s' exited with %d: %s".formatted(String.join(" ", command), result.getExitCode(), result.getOutput().strip()));
    }
}
