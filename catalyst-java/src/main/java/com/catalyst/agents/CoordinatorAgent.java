package com.catalyst.agents;

import com.catalyst.context.ContextBuilder;
import com.catalyst.model.CoverageReport;
import com.catalyst.model.ResearchContext;
import com.catalyst.model.ResearchFinding;
import com.catalyst.model.ResearchReport;
import com.catalyst.model.SubTopic;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CoordinatorAgent {

    private final JobSearchAgent jobSearchAgent;

    private final ResumeAnalysisAgent resumeAnalysisAgent;

    private final SkillsMatchAgent skillsMatchAgent;

    private final ContextBuilder contextBuilder;

    public CoordinatorAgent() {

        this.jobSearchAgent =
                new JobSearchAgent();

        this.resumeAnalysisAgent =
                new ResumeAnalysisAgent();

        this.skillsMatchAgent =
                new SkillsMatchAgent();

        this.contextBuilder =
                new ContextBuilder();
    }

    public ResearchReport research(
            String topic
    ) {

        System.out.println(
                "Coordinator started"
        );

        List<SubTopic> subTopics =
                decompose(topic);

        List<ResearchFinding> findings =
                delegate(
                        topic,
                        subTopics
                );

        CoverageReport coverage =
                evaluateCoverage(
                        subTopics,
                        findings
                );

        System.out.println(
                "Coverage: "
                        + coverage.completeness()
                );

        return aggregate(
                topic,
                findings
        );
    }

    /**
     * Coordinator owns decomposition.
     */
    private List<SubTopic> decompose(
            String topic
    ) {

        List<SubTopic> topics =
                new ArrayList<>();

        topics.add(
                new SubTopic(
                        "Job Search"
                )
        );

        topics.add(
                new SubTopic(
                        "Resume Analysis"
                )
        );

        topics.add(
                new SubTopic(
                        "Skills Match"
                )
        );

        topics.add(
                new SubTopic(
                        "Salary Analysis"
                )
        );

        topics.add(
                new SubTopic(
                        "Market Trends"
                )
        );

        return topics;
    }

    /**
     * Hub-and-Spoke delegation.
     *
     * Context is explicitly passed
     * to every subagent.
     */
    private List<ResearchFinding> delegate(
            String goal,
            List<SubTopic> topics
    ) {

        List<ResearchFinding> findings =
                new ArrayList<>();

        String priorFindings = "";

        for (SubTopic topic : topics) {

            ResearchContext context =
                    contextBuilder.build(

                            goal,

                            topic.name(),

                            priorFindings
                    );

            ResearchFinding finding;

            switch (topic.name()) {

                case "Job Search" ->

                        finding =
                                jobSearchAgent
                                        .research(
                                                context
                                        );

                case "Resume Analysis" ->

                        finding =
                                resumeAnalysisAgent
                                        .research(
                                                context
                                        );

                default ->

                        finding =
                                skillsMatchAgent
                                        .research(
                                                context
                                        );
            }

            findings.add(
                    finding
            );

            /*
             * Feed findings forward.
             *
             * This simulates context
             * flowing through the
             * coordinator only.
             */
            priorFindings +=

                    "\n- "

                    +

                    finding.claim();
        }

        return findings;
    }

    /**
     * Aggregate all findings
     * into a final report.
     */
    private ResearchReport aggregate(
            String topic,
            List<ResearchFinding> findings
    ) {

        return new ResearchReport(
                topic,
                findings
        );
    }

    /**
     * Evaluate coverage.
     */
    private CoverageReport evaluateCoverage(
            List<SubTopic> topics,
            List<ResearchFinding> findings
    ) {

        List<String> covered =

                findings.stream()

                        .map(
                                f -> f.metadata()
                                      .retrievedBy()
                        )

                        .distinct()

                        .collect(
                                Collectors.toList()
                        );

        List<String> gaps =
                new ArrayList<>();

        double completeness =

                (double) findings.size()

                /

                topics.size();

        return new CoverageReport(

                covered,

                gaps,

                completeness
        );
    }
}