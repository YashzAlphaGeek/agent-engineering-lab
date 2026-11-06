//! Element 3, tools. A described capability the model may ask for.
//!
//! The [`Tool`] trait is deliberately small: a definition and an execute method. Behind it can be an API
//! call, a database query, a local function, an MCP server, or another agent (see
//! [`DelegationTool`](crate::agent::DelegationTool)).

use std::collections::BTreeMap;
use std::error::Error;
use std::fmt;
use std::sync::Arc;

use crate::json::Json;

/// Arguments the model passes to a tool: a map of names to JSON values.
pub type ToolInput = BTreeMap<String, Json>;

/// Build a [`ToolInput`] from name and value pairs.
pub fn tool_input<K, V, I>(pairs: I) -> ToolInput
where
    K: Into<String>,
    V: Into<Json>,
    I: IntoIterator<Item = (K, V)>,
{
    pairs
        .into_iter()
        .map(|(k, v)| (k.into(), v.into()))
        .collect()
}

/// Read a required string argument.
pub fn string_arg(input: &ToolInput, key: &str) -> Result<String, ToolError> {
    match input.get(key) {
        Some(Json::String(s)) => Ok(s.clone()),
        _ => Err(ToolError::new(format!("Argument '{key}' must be a string"))),
    }
}

/// Read a required integer argument. A float with no fractional part is accepted.
pub fn integer_arg(input: &ToolInput, key: &str) -> Result<i64, ToolError> {
    match input.get(key) {
        Some(Json::Int(i)) => Ok(*i),
        Some(Json::Float(f)) if f.fract() == 0.0 && f.is_finite() => Ok(*f as i64),
        _ => Err(ToolError::new(format!(
            "Argument '{key}' must be an integer"
        ))),
    }
}

/// An expected tool failure, such as bad arguments or a missing record. Its message goes back to the model.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ToolError {
    /// The message returned to the model.
    pub message: String,
}

impl ToolError {
    /// Create a tool error.
    pub fn new(message: impl Into<String>) -> Self {
        ToolError {
            message: message.into(),
        }
    }
}

impl fmt::Display for ToolError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.write_str(&self.message)
    }
}

impl Error for ToolError {}

/// The result of running a tool. A failure is data the model can read and react to, not a crash.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ToolOutput {
    /// The text returned to the model.
    pub content: String,
    /// True when the content describes a failure.
    pub is_error: bool,
}

impl ToolOutput {
    /// A successful result.
    pub fn ok(content: impl Into<String>) -> Self {
        ToolOutput {
            content: content.into(),
            is_error: false,
        }
    }

    /// A failed result.
    pub fn error(message: impl Into<String>) -> Self {
        ToolOutput {
            content: message.into(),
            is_error: true,
        }
    }
}

/// One property of a tool's input schema.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Property {
    /// The JSON Schema type name, such as `string` or `integer`.
    pub type_name: String,
    /// What the property means.
    pub description: String,
}

/// A small JSON Schema for a tool's input, built without any library.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct InputSchema {
    properties: Vec<(String, Property)>,
    required: Vec<String>,
}

impl InputSchema {
    /// Start building an object schema.
    pub fn object() -> InputSchemaBuilder {
        InputSchemaBuilder {
            properties: Vec::new(),
            required: Vec::new(),
        }
    }

    /// The properties in declaration order.
    pub fn properties(&self) -> &[(String, Property)] {
        &self.properties
    }

    /// The names of the required properties.
    pub fn required(&self) -> &[String] {
        &self.required
    }

    /// The JSON Schema form that providers expect as `input_schema`.
    pub fn to_json_schema(&self) -> Json {
        let props = self.properties.iter().map(|(name, p)| {
            (
                name.clone(),
                Json::object([
                    ("type", Json::from(p.type_name.as_str())),
                    ("description", Json::from(p.description.as_str())),
                ]),
            )
        });
        Json::object([
            ("type", Json::from("object")),
            ("properties", Json::object(props)),
            (
                "required",
                Json::array(self.required.iter().map(|r| Json::from(r.as_str()))),
            ),
        ])
    }
}

/// Builder for [`InputSchema`].
#[derive(Debug, Clone)]
pub struct InputSchemaBuilder {
    properties: Vec<(String, Property)>,
    required: Vec<String>,
}

impl InputSchemaBuilder {
    fn add(mut self, name: &str, type_name: &str, description: &str, required: bool) -> Self {
        self.properties.push((
            name.to_string(),
            Property {
                type_name: type_name.to_string(),
                description: description.to_string(),
            },
        ));
        if required {
            self.required.push(name.to_string());
        }
        self
    }

    /// Add a string property.
    pub fn string(self, name: &str, description: &str, required: bool) -> Self {
        self.add(name, "string", description, required)
    }

    /// Add an integer property.
    pub fn integer(self, name: &str, description: &str, required: bool) -> Self {
        self.add(name, "integer", description, required)
    }

    /// Add a number property.
    pub fn number(self, name: &str, description: &str, required: bool) -> Self {
        self.add(name, "number", description, required)
    }

    /// Add a boolean property.
    pub fn boolean(self, name: &str, description: &str, required: bool) -> Self {
        self.add(name, "boolean", description, required)
    }

    /// Finish the schema.
    pub fn build(self) -> InputSchema {
        InputSchema {
            properties: self.properties,
            required: self.required,
        }
    }
}

/// Why a [`ToolDefinition`] could not be created.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ToolDefinitionError {
    /// The name is not 1 to 64 characters of letters, digits, `_` or `-`.
    InvalidName(String),
    /// The description is empty.
    EmptyDescription(String),
}

impl fmt::Display for ToolDefinitionError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            ToolDefinitionError::InvalidName(n) => {
                write!(f, "Tool name must match [a-zA-Z0-9_-]{{1,64}}: {n}")
            }
            ToolDefinitionError::EmptyDescription(n) => write!(f, "Tool '{n}' needs a description"),
        }
    }
}

impl Error for ToolDefinitionError {}

/// What the model sees about a tool: a name, a description and an input schema.
///
/// The description is the only way the model knows when to use the tool, so write it carefully:
/// what it does, when to use it, when not to, and what it returns.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct ToolDefinition {
    name: String,
    description: String,
    input_schema: InputSchema,
}

impl ToolDefinition {
    /// Create a definition, validating the name and description.
    pub fn new(
        name: impl Into<String>,
        description: impl Into<String>,
        input_schema: InputSchema,
    ) -> Result<Self, ToolDefinitionError> {
        let name = name.into();
        let description = description.into();
        let valid_name = !name.is_empty()
            && name.len() <= 64
            && name
                .chars()
                .all(|c| c.is_ascii_alphanumeric() || c == '_' || c == '-');
        if !valid_name {
            return Err(ToolDefinitionError::InvalidName(name));
        }
        if description.trim().is_empty() {
            return Err(ToolDefinitionError::EmptyDescription(name));
        }
        Ok(ToolDefinition {
            name,
            description,
            input_schema,
        })
    }

    /// The tool name.
    pub fn name(&self) -> &str {
        &self.name
    }

    /// The description shown to the model.
    pub fn description(&self) -> &str {
        &self.description
    }

    /// The input schema.
    pub fn input_schema(&self) -> &InputSchema {
        &self.input_schema
    }
}

/// A capability the model may ask for. Implementations must be `Send + Sync` so agents can be shared.
pub trait Tool: Send + Sync {
    /// The definition the model sees.
    fn definition(&self) -> &ToolDefinition;

    /// Run the tool. Return `Err` for an expected failure, whose message goes back to the model.
    fn execute(&self, input: &ToolInput) -> Result<ToolOutput, ToolError>;

    /// The tool name.
    fn name(&self) -> &str {
        self.definition().name()
    }
}

fn check_required(definition: &ToolDefinition, input: &ToolInput) -> Result<(), ToolError> {
    for key in definition.input_schema().required() {
        match input.get(key) {
            None | Some(Json::Null) => {
                return Err(ToolError::new(format!("Missing required argument '{key}'")));
            }
            Some(_) => {}
        }
    }
    Ok(())
}

type DecodeFn<I> = Box<dyn Fn(&ToolInput) -> Result<I, ToolError> + Send + Sync>;
type RunFn<I, O> = Box<dyn Fn(I) -> Result<O, ToolError> + Send + Sync>;
type EncodeFn<O> = Box<dyn Fn(&O) -> String + Send + Sync>;

/// A tool with a typed input `I` and a typed output `O`.
///
/// `execute` validates required arguments, decodes the raw map into `I`, runs the typed logic, and encodes
/// `O` as text for the model. You supply the three steps as closures.
pub struct TypedTool<I: 'static, O: 'static> {
    definition: ToolDefinition,
    decode: DecodeFn<I>,
    run: RunFn<I, O>,
    encode: EncodeFn<O>,
}

impl<I: 'static, O: 'static> TypedTool<I, O> {
    /// Create a typed tool from a definition and the decode, run and encode steps.
    pub fn new(
        definition: ToolDefinition,
        decode: impl Fn(&ToolInput) -> Result<I, ToolError> + Send + Sync + 'static,
        run: impl Fn(I) -> Result<O, ToolError> + Send + Sync + 'static,
        encode: impl Fn(&O) -> String + Send + Sync + 'static,
    ) -> Self {
        TypedTool {
            definition,
            decode: Box::new(decode),
            run: Box::new(run),
            encode: Box::new(encode),
        }
    }
}

impl<I: 'static, O: fmt::Display + 'static> TypedTool<I, O> {
    /// Like [`TypedTool::new`], encoding the output with its `Display` implementation.
    pub fn displaying(
        definition: ToolDefinition,
        decode: impl Fn(&ToolInput) -> Result<I, ToolError> + Send + Sync + 'static,
        run: impl Fn(I) -> Result<O, ToolError> + Send + Sync + 'static,
    ) -> Self {
        TypedTool::new(definition, decode, run, |output: &O| output.to_string())
    }
}

impl<I: 'static, O: 'static> Tool for TypedTool<I, O> {
    fn definition(&self) -> &ToolDefinition {
        &self.definition
    }

    fn execute(&self, input: &ToolInput) -> Result<ToolOutput, ToolError> {
        check_required(&self.definition, input)?;
        let typed = (self.decode)(input)?;
        let result = (self.run)(typed)?;
        Ok(ToolOutput::ok((self.encode)(&result)))
    }
}

type BodyFn = Box<dyn Fn(&ToolInput) -> Result<String, ToolError> + Send + Sync>;

/// The quickest way to make a tool: a definition plus a function.
pub struct FunctionTool {
    definition: ToolDefinition,
    body: BodyFn,
}

impl FunctionTool {
    /// A tool whose body returns text and cannot fail in an expected way.
    pub fn new(
        definition: ToolDefinition,
        body: impl Fn(&ToolInput) -> String + Send + Sync + 'static,
    ) -> Self {
        FunctionTool {
            definition,
            body: Box::new(move |input| Ok(body(input))),
        }
    }

    /// A tool whose body may return a [`ToolError`].
    pub fn fallible(
        definition: ToolDefinition,
        body: impl Fn(&ToolInput) -> Result<String, ToolError> + Send + Sync + 'static,
    ) -> Self {
        FunctionTool {
            definition,
            body: Box::new(body),
        }
    }
}

impl Tool for FunctionTool {
    fn definition(&self) -> &ToolDefinition {
        &self.definition
    }

    fn execute(&self, input: &ToolInput) -> Result<ToolOutput, ToolError> {
        check_required(&self.definition, input)?;
        (self.body)(input).map(ToolOutput::ok)
    }
}

/// Why a registry operation failed.
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum RegistryError {
    /// A tool with this name is already registered.
    Duplicate(String),
    /// No tool with this name is registered.
    Unknown(String),
}

impl fmt::Display for RegistryError {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        match self {
            RegistryError::Duplicate(n) => write!(f, "Duplicate tool: {n}"),
            RegistryError::Unknown(n) => write!(f, "Unknown tool: {n}"),
        }
    }
}

impl Error for RegistryError {}

/// The set of tools an agent may use. Only registered tools can run, which makes the registry a safety
/// boundary. [`ToolRegistry::subset`] gives a sub agent a restricted view (least privilege).
#[derive(Clone, Default)]
pub struct ToolRegistry {
    tools: Vec<Arc<dyn Tool>>,
}

impl ToolRegistry {
    /// An empty registry.
    pub fn new() -> Self {
        ToolRegistry::default()
    }

    /// Register a tool. A duplicate name is rejected.
    pub fn register(&mut self, tool: Arc<dyn Tool>) -> Result<(), RegistryError> {
        if self.find(tool.name()).is_some() {
            return Err(RegistryError::Duplicate(tool.name().to_string()));
        }
        self.tools.push(tool);
        Ok(())
    }

    /// Find a tool by name.
    pub fn find(&self, name: &str) -> Option<&Arc<dyn Tool>> {
        self.tools.iter().find(|t| t.name() == name)
    }

    /// The definitions of all tools, in registration order.
    pub fn definitions(&self) -> Vec<ToolDefinition> {
        self.tools.iter().map(|t| t.definition().clone()).collect()
    }

    /// The tool names, in registration order.
    pub fn names(&self) -> Vec<String> {
        self.tools.iter().map(|t| t.name().to_string()).collect()
    }

    /// All registered tools, in registration order.
    pub fn all(&self) -> Vec<Arc<dyn Tool>> {
        self.tools.clone()
    }

    /// True when no tool is registered.
    pub fn is_empty(&self) -> bool {
        self.tools.is_empty()
    }

    /// A new registry holding only the named tools.
    pub fn subset(&self, names: &[&str]) -> Result<ToolRegistry, RegistryError> {
        let mut restricted = ToolRegistry::new();
        for name in names {
            let tool = self
                .find(name)
                .ok_or_else(|| RegistryError::Unknown((*name).to_string()))?;
            restricted.register(Arc::clone(tool))?;
        }
        Ok(restricted)
    }
}

impl fmt::Debug for ToolRegistry {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        f.debug_list().entries(self.names()).finish()
    }
}
