package com.example.aichalengeapp.agent.task

fun stageToStep(stage: TaskStage): TaskStep {
    return when (stage) {
        TaskStage.PLANNING -> TaskStep(id = "planning", title = "planning")
        TaskStage.PLAN_REVIEW -> TaskStep(id = "planning", title = "planning")
        TaskStage.EXECUTION -> TaskStep(id = "implementation", title = "implementation")
        TaskStage.VALIDATION -> TaskStep(id = "validation", title = "validation")
        TaskStage.DONE -> TaskStep(id = "done", title = "done")
        TaskStage.CANCELLED -> TaskStep(id = "cancelled", title = "cancelled")
    }
}
