from catalyst.core.state import AgentState


class Agent:

    def __init__(
        self,
        planner,
        executor,
        evaluator,
        registry
    ):

        self.planner = planner
        self.executor = executor
        self.evaluator = evaluator
        self.registry = registry

    def run(self, goal):

        state = AgentState()

        state.goal = goal

        state.plan = (
            self.planner
            .create_plan(goal)
        )

        tool_name = state.plan["tool"]

        tool = self.registry.get(
            tool_name
        )

        result = self.executor.execute(
            tool,
            goal
        )

        state.result = result

        success = (
            self.evaluator
            .evaluate(result)
        )

        state.completed = success

        return state