package com.example.aichalengeapp.agent.task

import com.example.aichalengeapp.agent.memory.WorkingMemoryStore
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
    private val executor: TaskExecutor,
    private val validator: TaskValidator
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
            currentStep = 0,
            steps = planned,
            paused = false
        )
        persistTaskStateInternal(state)
        state
    }

    suspend fun nextTaskStep(): TaskState? = mutex.withLock {
        val current = loadTaskStateInternal() ?: return@withLock null
        var next = executor.advance(current)
        if (next.stage == TaskStage.VALIDATION && validator.isComplete(next)) {
            next = next.copy(stage = TaskStage.DONE)
        } else if (next.stage == TaskStage.VALIDATION && !validator.isComplete(next)) {
            next = next.copy(stage = TaskStage.EXECUTION)
        }
        persistTaskStateInternal(next)
        next
    }

    suspend fun pauseTask(): TaskState? = mutex.withLock {
        val current = loadTaskStateInternal() ?: return@withLock null
        val updated = current.copy(paused = true)
        persistTaskStateInternal(updated)
        updated
    }

    suspend fun resumeTask(): TaskState? = mutex.withLock {
        val current = loadTaskStateInternal() ?: return@withLock null
        val updated = current.copy(paused = false)
        persistTaskStateInternal(updated)
        updated
    }

    suspend fun stopTask() = mutex.withLock {
        clearTaskStateInternal()
    }

    private suspend fun loadTaskStateInternal(): TaskState? {
        val root = loadRoot()
        val taskObj = root.optJSONObject(KEY_TASK_STATE) ?: return null
        return withContext(Dispatchers.Default) { decodeTaskState(taskObj) }
    }

    private suspend fun persistTaskStateInternal(state: TaskState) {
        val root = loadRoot()
        val encoded = withContext(Dispatchers.Default) { encodeTaskState(state) }
        root.put(KEY_TASK_STATE, encoded)
        saveRoot(root)
    }

    private suspend fun clearTaskStateInternal() {
        val root = loadRoot()
        root.remove(KEY_TASK_STATE)
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
        state.steps.forEach { steps.put(it) }
        return JSONObject()
            .put("goal", state.goal)
            .put("stage", state.stage.name)
            .put("currentStep", state.currentStep)
            .put("steps", steps)
            .put("paused", state.paused)
    }

    private fun decodeTaskState(obj: JSONObject): TaskState? {
        val goal = obj.optString("goal", "").trim()
        val stageName = obj.optString("stage", TaskStage.PLANNING.name)
        val stage = runCatching { TaskStage.valueOf(stageName) }.getOrElse { TaskStage.PLANNING }
        val currentStep = obj.optInt("currentStep", 0).coerceAtLeast(0)
        val paused = obj.optBoolean("paused", false)
        val stepsArray = obj.optJSONArray("steps") ?: JSONArray()
        val steps = buildList {
            for (i in 0 until stepsArray.length()) {
                val step = stepsArray.optString(i).trim()
                if (step.isNotEmpty()) add(step)
            }
        }
        if (goal.isEmpty()) return null
        return TaskState(
            goal = goal,
            stage = stage,
            currentStep = currentStep.coerceAtMost(steps.size),
            steps = steps,
            paused = paused
        )
    }

    private companion object {
        const val KEY_TASK_STATE = "task_state"
    }
}
