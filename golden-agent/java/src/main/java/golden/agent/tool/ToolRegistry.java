package golden.agent.tool;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The set of tools an agent may use. Only registered tools can run, which makes the registry
 * a safety boundary. {@link #subset} gives a sub agent a restricted view (least privilege).
 */
public final class ToolRegistry {

    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public static ToolRegistry of(Tool... tools) {
        ToolRegistry registry = new ToolRegistry();
        for (Tool tool : tools) {
            registry.register(tool);
        }
        return registry;
    }

    public ToolRegistry register(Tool tool) {
        if (tools.putIfAbsent(tool.name(), tool) != null) {
            throw new IllegalArgumentException("Duplicate tool: " + tool.name());
        }
        return this;
    }

    public Optional<Tool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public List<ToolDefinition> definitions() {
        return tools.values().stream().map(Tool::definition).toList();
    }

    /** All registered tools in registration order. */
    public List<Tool> all() {
        return List.copyOf(tools.values());
    }

    public Set<String> names() {
        return Set.copyOf(tools.keySet());
    }

    /** A new registry holding only the named tools. */
    public ToolRegistry subset(Collection<String> names) {
        ToolRegistry restricted = new ToolRegistry();
        for (String name : names) {
            Tool tool = tools.get(name);
            if (tool == null) {
                throw new IllegalArgumentException("Unknown tool: " + name);
            }
            restricted.register(tool);
        }
        return restricted;
    }
}
