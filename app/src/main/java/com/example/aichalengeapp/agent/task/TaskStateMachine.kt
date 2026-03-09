package com.example.aichalengeapp.agent.task

import javax.inject.Inject

class TaskStateMachine @Inject constructor() {

    fun canTransition(from: TaskStage, to: TaskStage, state: TaskState): Boolean {
        if (from == TaskStage.DONE || from == TaskStage.CANCELLED) return false
        if (state.paused && to != TaskStage.CANCELLED) return false

        return when (from) {
            TaskStage.PLANNING -> (to == TaskStage.EXECUTION && state.planApproved) || to == TaskStage.CANCELLED
            TaskStage.PLAN_REVIEW -> {
                when (to) {
                    TaskStage.PLANNING -> true
                    TaskStage.EXECUTION -> state.planApproved
                    TaskStage.CANCELLED -> true
                    else -> false
                }
            }
            TaskStage.EXECUTION -> to == TaskStage.VALIDATION || to == TaskStage.CANCELLED
            TaskStage.VALIDATION -> {
                when (to) {
                    TaskStage.EXECUTION -> true
                    TaskStage.DONE -> true
                    TaskStage.CANCELLED -> true
                    else -> false
                }
            }
            TaskStage.DONE, TaskStage.CANCELLED -> false
        }
    }

    fun transition(state: TaskState, to: TaskStage): TaskTransitionResult {
        val from = state.stage
        if (!canTransition(from, to, state)) {
            val reason = when {
                state.paused && to != TaskStage.CANCELLED -> "Task is paused"
                from == TaskStage.PLANNING && to == TaskStage.EXECUTION && !state.planApproved -> "Plan is not approved"
                from == TaskStage.PLAN_REVIEW && to == TaskStage.EXECUTION && !state.planApproved -> "Plan is not approved"
                else -> "Transition $from -> $to is not allowed"
            }
            val suggestion = when (from) {
                TaskStage.PLANNING -> if (!state.planApproved) "Approve plan before EXECUTION" else "Move to EXECUTION or cancel"
                TaskStage.PLAN_REVIEW -> if (!state.planApproved) "Approve plan before EXECUTION" else "Move to EXECUTION or back to PLANNING"
                TaskStage.EXECUTION -> "Move to VALIDATION"
                TaskStage.VALIDATION -> "Move to DONE or back to EXECUTION"
                TaskStage.DONE -> "Task is already done"
                TaskStage.CANCELLED -> "Task is cancelled"
            }
            return TaskTransitionResult.Invalid(reason, suggestion)
        }

        return TaskTransitionResult.Success(
            state.copy(
                stage = to,
                paused = false
            )
        )
    }

    fun pause(state: TaskState): TaskState = state.copy(paused = true)

    fun resume(state: TaskState): TaskState = state.copy(paused = false)

    fun approvePlan(state: TaskState): TaskState = state.copy(planApproved = true)
}
