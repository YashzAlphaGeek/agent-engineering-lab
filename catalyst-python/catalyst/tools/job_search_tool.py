from catalyst.domain.job import Job
from catalyst.tools.base_tool import BaseTool


class JobSearchTool(BaseTool):

    @property
    def name(self) -> str:
        return "job_search"

    def execute(self, input_data):

        return [
            Job(
                title="Senior Python Developer",
                company="TechCorp",
                location="Munich",
                skills=["Python", "FastAPI"]
            ),
            Job(
                title="AI Engineer",
                company="InnovateAI",
                location="Berlin",
                skills=["Python", "LLM", "Agents"]
            )
        ]