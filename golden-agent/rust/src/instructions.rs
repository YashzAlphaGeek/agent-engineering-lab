//! Element 2, the instructions (system prompt).
//!
//! Immutable and composable: each `with_*` method consumes the value and returns a new one, so a shared base
//! can be cloned and specialised per agent. Instructions steer the model. They are advisory, so rules that
//! must never be broken belong in a [`Gate`](crate::guard::Gate), not here.

/// Role, rules, output format and examples that render to a system prompt.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Instructions {
    role: String,
    rules: Vec<String>,
    output_format: String,
    examples: Vec<String>,
}

impl Instructions {
    /// Start with a role.
    pub fn for_role(role: impl Into<String>) -> Self {
        Instructions {
            role: role.into(),
            rules: Vec::new(),
            output_format: String::new(),
            examples: Vec::new(),
        }
    }

    /// Add a rule.
    pub fn with_rule(mut self, rule: impl Into<String>) -> Self {
        self.rules.push(rule.into());
        self
    }

    /// Set the output format.
    pub fn with_output_format(mut self, format: impl Into<String>) -> Self {
        self.output_format = format.into();
        self
    }

    /// Add an example.
    pub fn with_example(mut self, example: impl Into<String>) -> Self {
        self.examples.push(example.into());
        self
    }

    /// Merge another set of instructions in, for example a shared safety preamble.
    /// The other output format wins when it is not blank.
    pub fn and(mut self, other: &Instructions) -> Self {
        self.role = format!("{} {}", self.role, other.role);
        self.rules.extend(other.rules.iter().cloned());
        self.examples.extend(other.examples.iter().cloned());
        if !other.output_format.trim().is_empty() {
            self.output_format = other.output_format.clone();
        }
        self
    }

    /// The role text.
    pub fn role(&self) -> &str {
        &self.role
    }

    /// The rules.
    pub fn rules(&self) -> &[String] {
        &self.rules
    }

    /// Render to the system prompt text sent to the model.
    pub fn render(&self) -> String {
        let mut out = self.role.clone();
        if !self.rules.is_empty() {
            out.push_str("\n\nRules:");
            for rule in &self.rules {
                out.push_str("\n- ");
                out.push_str(rule);
            }
        }
        if !self.output_format.trim().is_empty() {
            out.push_str("\n\nOutput format:\n");
            out.push_str(&self.output_format);
        }
        if !self.examples.is_empty() {
            out.push_str("\n\nExamples:");
            for example in &self.examples {
                out.push_str("\n- ");
                out.push_str(example);
            }
        }
        out
    }
}
