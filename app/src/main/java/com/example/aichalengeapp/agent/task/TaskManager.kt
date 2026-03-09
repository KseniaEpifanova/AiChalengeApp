package com.example.aichalengeapp.agent.task

import com.example.aichalengeapp.agent.memory.WorkingMemoryStore
import com.example.aichalengeapp.debug.TaskTrace
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class TaskManager @Inject constructor(
    private val workingMemoryStore: WorkingMemoryStore,
    private val planner: TaskPlanner,
    private val stateMachine: TaskStateMachine
) {
    private val mutex = Mutex()

    suspend fun getTaskState(): TaskState? = mutex.withLock {
        loadTaskStateInternal()
    }

    suspend fun startTask(goal: String): TaskState = mutex.withLock {
        val planned = planner.plan(goal)
        val state = TaskState(
            goal = goal.trim(),
            stage = TaskStage.PLANNING,
            paused = false,
            steps = planned,
            planApproved = false
        )
        persistTaskStateInternal(state)
        TaskTrace.d(
            "event" to "task_started",
            "source" to "auto_or_manual",
            "taskId" to TaskTrace.taskId(state),
            "msg" to goal.trim(),
            "afterStage" to state.stage,
            "afterApproved" to state.planApproved,
            "paused" to state.paused,
            "persisted" to true
        )
        state
    }

    suspend fun nextTaskStep(): TaskState? = mutex.withLock {
        val current = loadTaskStateInternal() ?: return@withLock null
        TaskTrace.d(
            "event" to "task_next_attempt",
            "source" to "button_or_intent",
            "taskId" to TaskTrace.taskId(current),
            "beforeStage" to current.stage,
            "beforeApproved" to current.planApproved,
            "paused" to current.paused,
            "action" to "NEXT"
        )
        if (current.paused || current.stage == TaskStage.DONE || current.stage == TaskStage.CANCELLED) {
            persistTaskStateInternal(current)
            TaskTrace.d(
                "event" to "task_next_result",
                "taskId" to TaskTrace.taskId(current),
                "fsmResult" to "INVALID",
                "reason" to "Task is paused or terminal",
                "afterStage" to current.stage,
                "persisted" to true
            )
            return@withLock current
        }
        val next = when (current.stage) {
            TaskStage.PLANNING, TaskStage.PLAN_REVIEW -> {
                if (current.planApproved) transitionOrSame(current, TaskStage.EXECUTION) else current
            }
            TaskStage.EXECUTION -> transitionOrSame(current, TaskStage.VALIDATION)
            TaskStage.VALIDATION -> transitionOrSame(current, TaskStage.DONE)
            TaskStage.DONE, TaskStage.CANCELLED -> current
        }
        persistTaskStateInternal(next)
        TaskTrace.d(
            "event" to "task_next_result",
            "taskId" to TaskTrace.taskId(next),
            "fsmResult" to if (next.stage == current.stage) "INVALID" else "SUCCESS",
            "afterStage" to next.stage,
            "afterApproved" to next.planApproved,
            "persisted" to true
        )
        next
    }

    suspend fun attemptTransition(to: TaskStage): TaskTransitionResult = mutex.withLock {
        val current = loadTaskStateInternal()
            ?: return@withLock TaskTransitionResult.Invalid("No active task", "Start a task first")
        TaskTrace.d(
            "event" to "task_transition_attempt",
            "taskId" to TaskTrace.taskId(current),
            "beforeStage" to current.stage,
            "beforeApproved" to current.planApproved,
            "paused" to current.paused,
            "requestedTarget" to to,
            "fsmValidationCalled" to true
        )

        val result = stateMachine.transition(current, to)
        if (result is TaskTransitionResult.Success) {
            persistTaskStateInternal(result.newState)
            TaskTrace.d(
                "event" to "task_transition_result",
                "taskId" to TaskTrace.taskId(result.newState),
                "fsmResult" to "SUCCESS",
                "afterStage" to result.newState.stage,
                "afterApproved" to result.newState.planApproved,
                "persisted" to true
            )
        } else if (result is TaskTransitionResult.Invalid) {
            TaskTrace.d(
                "event" to "task_transition_result",
                "taskId" to TaskTrace.taskId(current),
                "fsmResult" to "INVALID",
                "reason" to result.reason,
                "suggestedNextAction" to result.suggestedNextAction,
                "afterStage" to current.stage,
                "persisted" to false
            )
        }
        result
    }

    suspend fun approvePlan(): TaskState? = mutex.withLock {
        val current = loadTaskStateInternal() ?: return@withLock null
        val approved = stateMachine.approvePlan(current)
        persistTaskStateInternal(approved)
        TaskTrace.d(
            "event" to "task_plan_approved",
            "taskId" to TaskTrace.taskId(approved),
            "beforeStage" to current.stage,
            "afterStage" to approved.stage,
            "afterApproved" to approved.planApproved,
            "persisted" to true
        )
        approved
    }

    suspend fun pauseTask(): TaskState? = mutex.withLock {
        val current = loadTaskStateInternal() ?: return@withLock null
        val updated = stateMachine.pause(current)
        persistTaskStateInternal(updated)
        TaskTrace.d(
            "event" to "task_paused",
            "taskId" to TaskTrace.taskId(updated),
            "beforeStage" to current.stage,
            "afterStage" to updated.stage,
            "paused" to updated.paused,
            "persisted" to true
        )
        updated
    }

    suspend fun resumeTask(): TaskState? = mutex.withLock {
        val current = loadTaskStateInternal() ?: return@withLock null
        val updated = stateMachine.resume(current)
        persistTaskStateInternal(updated)
        TaskTrace.d(
            "event" to "task_resumed",
            "taskId" to TaskTrace.taskId(updated),
            "beforeStage" to current.stage,
            "afterStage" to updated.stage,
            "paused" to updated.paused,
            "persisted" to true
        )
        updated
    }

    suspend fun cancelTask(): TaskState? = mutex.withLock {
        val current = loadTaskStateInternal() ?: return@withLock null
        val result = stateMachine.transition(current, TaskStage.CANCELLED)
        val updated = if (result is TaskTransitionResult.Success) result.newState else current
        persistTaskStateInternal(updated)
        TaskTrace.d(
            "event" to "task_cancelled",
            "taskId" to TaskTrace.taskId(updated),
            "beforeStage" to current.stage,
            "afterStage" to updated.stage,
            "fsmResult" to if (result is TaskTransitionResult.Success) "SUCCESS" else "INVALID",
            "persisted" to true
        )
        updated
    }

    suspend fun stopTask() = mutex.withLock {
        val current = loadTaskStateInternal()
        clearTaskStateInternal()
        TaskTrace.d(
            "event" to "task_stopped",
            "taskId" to TaskTrace.taskId(current),
            "beforeStage" to current?.stage,
            "persisted" to true
        )
    }

    private fun transitionOrSame(state: TaskState, to: TaskStage): TaskState {
        return when (val result = stateMachine.transition(state, to)) {
            is TaskTransitionResult.Success -> result.newState
            is TaskTransitionResult.Invalid -> state
        }
    }

    private suspend fun loadTaskStateInternal(): TaskState? {
        val root = loadRoot()
        val taskObj = root.optJSONObject(keyTaskState) ?: return null
        return withContext(Dispatchers.Default) { decodeTaskState(taskObj) }
    }

    private suspend fun persistTaskStateInternal(state: TaskState) {
        val root = loadRoot()
        val encoded = withContext(Dispatchers.Default) { encodeTaskState(state) }
        root.put(keyTaskState, encoded)
        saveRoot(root)
    }

    private suspend fun clearTaskStateInternal() {
        val root = loadRoot()
        root.remove(keyTaskState)
        saveRoot(root)
    }

    private suspend fun loadRoot(): JSONObject {
        val raw = workingMemoryStore.loadJson().trim()
        if (raw.isBlank()) return JSONObject()
        return withContext(Dispatchers.Default) {
            runCatching { JSONObject(raw) }.getOrElse { JSONObject() }
        }
    }

    private suspend fun saveRoot(root: JSONObject) {
        val serialized = withContext(Dispatchers.Default) { root.toString() }
        workingMemoryStore.saveJson(serialized)
    }

    private fun encodeTaskState(state: TaskState): JSONObject {
        val steps = JSONArray()
        state.steps.forEach { step ->
            steps.put(
                JSONObject()
                    .put("id", step.id)
                    .put("title", step.title)
            )
        }

        return JSONObject()
            .put("goal", state.goal)
            .put("stage", state.stage.name)
            .put("paused", state.paused)
            .put("steps", steps)
            .put("planApproved", state.planApproved)
    }

    private fun decodeTaskState(obj: JSONObject): TaskState? {
        val goal = obj.optString("goal", "").trim()
        if (goal.isEmpty()) return null

        val stageName = obj.optString("stage", TaskStage.PLANNING.name)
        val stage = runCatching { TaskStage.valueOf(stageName) }.getOrElse {
            if (stageName == "DONE") TaskStage.DONE else TaskStage.PLANNING
        }

        val paused = obj.optBoolean("paused", false)
        val planApproved = obj.optBoolean("planApproved", false)
        val stepsArray = obj.optJSONArray("steps") ?: JSONArray()
        val steps = decodeSteps(stepsArray)

        return TaskState(
            goal = goal,
            stage = stage,
            paused = paused,
            steps = steps,
            planApproved = planApproved
        )
    }

    private fun decodeSteps(array: JSONArray): List<TaskStep> {
        if (array.length() == 0) return emptyList()

        return buildList {
            for (i in 0 until array.length()) {
                when (val item = array.opt(i)) {
                    is JSONObject -> {
                        val id = item.optString("id", "step_${i + 1}")
                        val title = item.optString("title", "").trim()
                        if (title.isNotEmpty()) {
                            add(TaskStep(id = id, title = title))
                        }
                    }
                    is String -> {
                        val title = item.trim()
                        if (title.isNotEmpty()) {
                            add(TaskStep(id = "step_${i + 1}", title = title))
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val keyTaskState = "task_state"
    }
}
