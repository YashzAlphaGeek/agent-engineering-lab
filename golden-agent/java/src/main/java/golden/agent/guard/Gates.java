package golden.agent.guard;

import java.util.List;
import java.util.Map;
import java.util.Set;

/** Ready-made gates for the most common rules. */
public final class Gates {

    private Gates() { }

    /**
     * Block a tool until other tools have succeeded first.
     * Example: {@code Map.of("process_refund", Set.of("get_customer"))}.
     */
    public static Gate prerequisites(Map<String, Set<String>> requires) {
        return (call, state) -> {
            Set<String> needed = requires.getOrDefault(call.name(), Set.of());
            List<String> missing = needed.stream().filter(t -> !state.hasSucceeded(t)).sorted().toList();
            return missing.isEmpty()
                    ? Decision.allow()
                    : Decision.deny("'" + call.name() + "' requires these tools to succeed first: " + missing);
        };
    }

    /** Ask a human before any of the named tools run. */
    public static Gate requireApproval(Set<String> toolNames, String reason) {
        return (call, state) -> toolNames.contains(call.name())
                ? Decision.askHuman(reason)
                : Decision.allow();
    }

    /** Only the named tools may run, whatever the registry holds. */
    public static Gate allowList(Set<String> toolNames) {
        return (call, state) -> toolNames.contains(call.name())
                ? Decision.allow()
                : Decision.deny("Tool '" + call.name() + "' is not permitted for this agent");
    }
}
