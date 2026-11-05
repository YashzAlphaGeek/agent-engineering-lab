from catalyst.core.agent import Agent
from catalyst.core.planner import Planner
from catalyst.core.executor import Executor
from catalyst.core.evaluator import Evaluator

from catalyst.tools.registry import ToolRegistry
from catalyst.tools.job_search_tool import (
    JobSearchTool
)

registry = ToolRegistry()

registry.register(
    JobSearchTool()
)

agent = Agent(
    Planner(),
    Executor(),
    Evaluator(),
    registry
)

state = agent.run(
    "Find Python jobs in Munich"
)

print(state.result)