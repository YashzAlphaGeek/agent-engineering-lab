package golden.agent.tool;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A small JSON Schema for a tool's input, built without any library. */
public record InputSchema(Map<String, Property> properties, Set<String> required) {

    public record Property(String type, String description) { }

    public InputSchema {
        properties = Collections.unmodifiableMap(new LinkedHashMap<>(properties));
        required = Collections.unmodifiableSet(new LinkedHashSet<>(required));
    }

    public static Builder object() {
        return new Builder();
    }

    /** The JSON Schema form that providers expect as {@code input_schema}. */
    public Map<String, Object> toJsonSchema() {
        Map<String, Object> props = new LinkedHashMap<>();
        properties.forEach((name, p) -> {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("type", p.type());
            one.put("description", p.description());
            props.put(name, one);
        });
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.copyOf(required));
        return schema;
    }

    public static final class Builder {
        private final Map<String, Property> properties = new LinkedHashMap<>();
        private final Set<String> required = new LinkedHashSet<>();

        public Builder string(String name, String description, boolean isRequired) {
            return add(name, "string", description, isRequired);
        }

        public Builder integer(String name, String description, boolean isRequired) {
            return add(name, "integer", description, isRequired);
        }

        public Builder number(String name, String description, boolean isRequired) {
            return add(name, "number", description, isRequired);
        }

        public Builder bool(String name, String description, boolean isRequired) {
            return add(name, "boolean", description, isRequired);
        }

        private Builder add(String name, String type, String description, boolean isRequired) {
            properties.put(name, new Property(type, description));
            if (isRequired) {
                required.add(name);
            }
            return this;
        }

        public InputSchema build() {
            return new InputSchema(properties, required);
        }
    }
}
