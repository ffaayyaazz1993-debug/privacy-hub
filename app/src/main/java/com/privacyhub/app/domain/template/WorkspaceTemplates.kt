package com.privacyhub.app.domain.template

import com.privacyhub.app.data.db.NodeEntity
import com.privacyhub.app.data.repo.FocusMapRepository

data class TemplateNodeSpec(
    val title: String,
    val description: String = "",
    val estimatedSessions: Int = 0,
    val children: List<TemplateNodeSpec> = emptyList(),
)

data class PomodoroPreset(val label: String, val minutes: Int)

data class DetoxSuggestion(val name: String, val description: String)

/** One versioned definition drives every generated project. */
data class WorkspaceTemplate(
    val id: String,
    val version: Int = 1,
    val name: String,
    val purpose: String,
    val description: String,
    val workflow: String,
    val category: String,
    val root: TemplateNodeSpec?,
    val presets: List<PomodoroPreset>,
    val detox: DetoxSuggestion? = null,
)

object WorkspaceTemplates {
    const val STUDY = "study"
    const val INTERVIEW = "interview"
    const val WORK = "work"
    const val BLANK = "blank"

    private fun n(title: String, vararg children: TemplateNodeSpec) = TemplateNodeSpec(title, children = children.toList())

    val ALL = listOf(
        WorkspaceTemplate(
            id = STUDY, name = "Study", category = "Study",
            purpose = "Learn and retain knowledge",
            description = "Structured subjects, chapters, resources, revision and assessments.",
            workflow = "Set goals → break subjects into chapters → focus on topics → revise → test yourself.",
            root = n("Study Project",
                n("Learning Goals"),
                n("Subjects", n("Subject 1", n("Chapter 1", n("Topic 1"), n("Topic 2")), n("Chapter 2")), n("Subject 2")),
                n("Research and Resources", n("Books"), n("Articles"), n("Lecture Notes")),
                n("Revision", n("Concepts to Review"), n("Difficult Topics"), n("Completed Topics")),
                n("Assessments", n("Practice Questions"), n("Mock Tests"), n("Examination Preparation"))),
            presets = listOf(PomodoroPreset("Short Study", 25), PomodoroPreset("Deep Study", 50), PomodoroPreset("Extended Study", 90)),
            detox = DetoxSuggestion("Study Detox", "Optional: social media, short-video and entertainment apps (you choose which)."),
        ),
        WorkspaceTemplate(
            id = INTERVIEW, name = "Job Interview Preparation", category = "Interview",
            purpose = "Prepare and demonstrate competence",
            description = "One project per company/role, covering research, technical and behavioural preparation.",
            workflow = "Define target role → research company → prepare answers → practise → mock interview → follow up.",
            root = n("Job Interview Preparation",
                n("Target Role", n("Job Description"), n("Required Qualifications"), n("Required Skills"), n("Experience Requirements")),
                n("Company Research", n("Company Overview"), n("Products and Services"), n("Industry"), n("Competitors"), n("Company Culture")),
                n("Technical Preparation", n("Core Concepts"), n("Technical Questions"), n("Practical Exercises"), n("Problem Solving")),
                n("Behavioural Preparation", n("Strengths"), n("Weaknesses"), n("Leadership Examples"), n("Conflict Resolution"), n("Teamwork Examples")),
                n("HR Preparation", n("Self Introduction"), n("Career Goals"), n("Salary Expectations"), n("Availability")),
                n("Interview Questions", n("Questions to Ask Employer"), n("Frequently Asked Questions"), n("Difficult Questions")),
                n("Mock Interviews", n("Practice Sessions"), n("Feedback"), n("Areas to Improve")),
                n("Interview Schedule", n("Application Deadline"), n("Interview Date"), n("Follow-up"))),
            presets = listOf(PomodoroPreset("Quick Revision", 15), PomodoroPreset("Interview Preparation", 25), PomodoroPreset("Deep Preparation", 50)),
            detox = DetoxSuggestion("Interview Detox", "Optional: restrict only apps you choose during preparation."),
        ),
        WorkspaceTemplate(
            id = WORK, name = "Work Management", category = "Work",
            purpose = "Organise and execute responsibilities",
            description = "Objectives, workstreams, milestones, risks, deadlines and review, single-user.",
            workflow = "Set objectives → split workstreams → track tasks → hit milestones → review.",
            root = n("Work Project",
                n("Objectives"), n("Project Scope"),
                n("Workstreams", n("Workstream 1", n("Tasks"), n("Subtasks")), n("Workstream 2", n("Tasks"), n("Subtasks"))),
                n("Milestones"), n("Dependencies"), n("Resources"), n("Meetings and Notes"),
                n("Risks and Issues"), n("Deadlines"), n("Completed Work"), n("Review")),
            presets = listOf(PomodoroPreset("Quick Task", 25), PomodoroPreset("Standard Work", 45), PomodoroPreset("Deep Work", 60)),
            detox = DetoxSuggestion("Work Detox", "Optional: configure per project; never auto-block communication apps."),
        ),
        WorkspaceTemplate(
            id = BLANK, name = "Blank Project", category = "",
            purpose = "Start from an empty map",
            description = "One root node only. Add your own structure.",
            workflow = "Name the project, then add nodes on the canvas.",
            root = null,
            presets = listOf(PomodoroPreset("Focus", 25)),
            detox = null,
        ),
    )

    fun byId(id: String): WorkspaceTemplate = ALL.first { it.id == id }

    /** Text preview used on the selection screen; no second rendering engine. */
    fun preview(t: WorkspaceTemplate): String = buildString {
        val root = t.root
        if (root == null) { append(t.name); return@buildString }
        fun walk(spec: TemplateNodeSpec, depth: Int) {
            append("  ".repeat(depth)).append("• ").append(spec.title).append('\n')
            spec.children.forEach { walk(it, depth + 1) }
        }
        walk(root, 0)
    }

    /** Creates project/nodes with Room-generated unique ids; never reuses ids across projects. */
    suspend fun createProject(repo: FocusMapRepository, template: WorkspaceTemplate,
                              title: String, categoryOverride: String = ""): Long {
        val pid = repo.createProject(title.trim(), description = template.purpose,
            category = categoryOverride.ifBlank { template.category })
        val root = template.root ?: return pid
        val rootId = repo.nodes.insert(NodeEntity(projectId = pid, parentId = null,
            title = root.title, description = root.description, positionX = 0f, positionY = 0f))
        var row = 0
        suspend fun addChildren(parentId: Long, children: List<TemplateNodeSpec>, depth: Int) {
            children.forEachIndexed { index, spec ->
                val id = repo.nodes.insert(NodeEntity(projectId = pid, parentId = parentId,
                    title = spec.title, description = spec.description,
                    estimatedSessions = spec.estimatedSessions,
                    positionX = depth * 240f, positionY = (row++ * 120f)))
                addChildren(id, spec.children, depth + 1)
            }
        }
        addChildren(rootId, root.children, 1)
        return pid
    }
}
