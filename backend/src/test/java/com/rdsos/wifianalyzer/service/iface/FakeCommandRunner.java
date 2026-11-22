package com.rdsos.wifianalyzer.service.iface;

import com.rdsos.wifianalyzer.model.CommandResult;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;

/** Records commands instead of running them; can be told to fail commands matching a predicate. */
class FakeCommandRunner implements CommandRunner {

    final List<List<String>> commands = new CopyOnWriteArrayList<>();
    private Predicate<List<String>> failWhen = command -> false;

    void failWhen(Predicate<List<String>> predicate) {
        this.failWhen = predicate;
    }

    List<String> joined() {
        List<String> result = new ArrayList<>();
        for (List<String> command : commands) {
            result.add(String.join(" ", command));
        }
        return result;
    }

    @Override
    public CommandResult run(List<String> command) {
        commands.add(List.copyOf(command));
        return failWhen.test(command)
                ? new CommandResult(1, "simulated failure")
                : new CommandResult(0, "");
    }
}
