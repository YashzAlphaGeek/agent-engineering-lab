package golden.agent.instructions;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Element 2, the instructions (system prompt).
 *
 * <p>Immutable and composable: each {@code with...} method returns a new value, so a shared
 * base can be reused and specialised per agent. Instructions steer the model. They are advisory,
 * so rules that must never be broken belong in a {@code Gate}, not here.
 */
public record Instructions(String role, List<String> rules, String outputFormat, List<String> examples) {

    public Instructions {
        Objects.requireNonNull(role, "role");
        rules = List.copyOf(rules);
        examples = List.copyOf(examples);
        outputFormat = outputFormat == null ? "" : outputFormat;
    }

    public static Instructions forRole(String role) {
        return new Instructions(role, List.of(), "", List.of());
    }

    public Instructions withRule(String rule) {
        List<String> next = new ArrayList<>(rules);
        next.add(rule);
        return new Instructions(role, next, outputFormat, examples);
    }

    public Instructions withOutputFormat(String format) {
        return new Instructions(role, rules, format, examples);
    }

    public Instructions withExample(String example) {
        List<String> next = new ArrayList<>(examples);
        next.add(example);
        return new Instructions(role, rules, outputFormat, next);
    }

    /** Merge another set of instructions in, for example a shared safety preamble. */
    public Instructions and(Instructions other) {
        List<String> r = new ArrayList<>(rules);
        r.addAll(other.rules);
        List<String> e = new ArrayList<>(examples);
        e.addAll(other.examples);
        String format = other.outputFormat.isBlank() ? outputFormat : other.outputFormat;
        return new Instructions(role + " " + other.role, r, format, e);
    }

    /** Render to the system prompt text sent to the model. */
    public String render() {
        StringBuilder sb = new StringBuilder(role);
        if (!rules.isEmpty()) {
            sb.append("\n\nRules:");
            rules.forEach(r -> sb.append("\n- ").append(r));
        }
        if (!outputFormat.isBlank()) {
            sb.append("\n\nOutput format:\n").append(outputFormat);
        }
        if (!examples.isEmpty()) {
            sb.append("\n\nExamples:");
            examples.forEach(x -> sb.append("\n- ").append(x));
        }
        return sb.toString();
    }
}
