package com.example.aichalengeapp.agent.task

import javax.inject.Inject

class TaskExecutor @Inject constructor(
    private val stateMachine: TaskStateMachine
) {

    fun advance(state: TaskState): TaskState {
        if (state.paused || state.stage == TaskStage.DONE || state.stage == TaskStage.CANCELLED) return state

        return when (state.stage) {
            TaskStage.PLANNING -> if (state.planApproved) {
                transitionOrSame(state, TaskStage.EXECUTION)
            } else {
                state
            }
            TaskStage.PLAN_REVIEW -> if (state.planApproved) {
                transitionOrSame(state, TaskStage.EXECUTION)
            } else {
                state
            }
            TaskStage.EXECUTION -> transitionOrSame(state, TaskStage.VALIDATION)
            TaskStage.VALIDATION -> transitionOrSame(state, TaskStage.DONE)
            TaskStage.DONE, TaskStage.CANCELLED -> state
        }
    }

    private fun transitionOrSame(state: TaskState, to: TaskStage): TaskState {
        return when (val result = stateMachine.transition(state, to)) {
            is TaskTransitionResult.Success -> result.newState
            is TaskTransitionResult.Invalid -> state
        }
    }
}
