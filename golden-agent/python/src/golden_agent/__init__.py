"""golden_agent: a small, framework-free library for building agents from the core elements.

The five core elements are the model, the instructions, the tools, the loop and the state. Around them sit a guard
layer (gates, human approval, result filters) and sub agents. Standard library only, no dependencies.

    from golden_agent import Agent, Instructions, FunctionTool, ToolDefinition, InputSchema

    agent = (
        Agent.builder("weather")
        .model(my_model_client)
        .instructions(Instructions.for_role("You answer weather questions."))
        .tool(FunctionTool.of(
            ToolDefinition("get_temp", "Current temperature for a city",
                           InputSchema.object().string("city", "City name").build()),
            lambda args: f"18C in {args['city']}"))
        .build()
    )
    print(agent.run("Weather in Munich?").text)
"""

from .composition import Agent, AgentBuilder, DelegationTool
from .guard import (
    Allow,
    AskHuman,
    Decision,
    Deny,
    Gate,
    Gates,
    HumanApprover,
    ResultFilter,
    all_of,
    allow,
    allow_all,
    ask_human,
    deny,
    deny_all,
    identity_filter,
)
from .instructions import Instructions
from .loop import AgentLoop, AgentResult, LoopConfig
from .model import (
    ContentBlock,
    Message,
    ModelClient,
    ModelError,
    ModelRequest,
    ModelResponse,
    Role,
    ScriptedModelClient,
    StopReason,
    Text,
    ToolResult,
    ToolUse,
    as_model_client,
    end_turn,
    stopped_with,
    tool_use,
)
from .provider import AnthropicModelClient
from .state import AgentState, RunStatus
from .tool import (
    FunctionTool,
    InputSchema,
    Tool,
    ToolDefinition,
    ToolError,
    ToolOutput,
    ToolRegistry,
    TypedTool,
)

__version__ = "1.0.0"

__all__ = [
    "Agent",
    "AgentBuilder",
    "AgentLoop",
    "AgentResult",
    "AgentState",
    "Allow",
    "AnthropicModelClient",
    "AskHuman",
    "ContentBlock",
    "Decision",
    "DelegationTool",
    "Deny",
    "FunctionTool",
    "Gate",
    "Gates",
    "HumanApprover",
    "InputSchema",
    "Instructions",
    "LoopConfig",
    "Message",
    "ModelClient",
    "ModelError",
    "ModelRequest",
    "ModelResponse",
    "ResultFilter",
    "Role",
    "RunStatus",
    "ScriptedModelClient",
    "StopReason",
    "Text",
    "Tool",
    "ToolDefinition",
    "ToolError",
    "ToolOutput",
    "ToolRegistry",
    "ToolResult",
    "ToolUse",
    "TypedTool",
    "all_of",
    "allow",
    "allow_all",
    "as_model_client",
    "ask_human",
    "deny",
    "deny_all",
    "end_turn",
    "identity_filter",
    "stopped_with",
    "tool_use",
]
