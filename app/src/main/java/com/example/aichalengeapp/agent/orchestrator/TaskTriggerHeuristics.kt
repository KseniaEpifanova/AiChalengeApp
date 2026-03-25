package com.example.aichalengeapp.agent.orchestrator

object TaskTriggerHeuristics {

    data class TriggerSignals(
        val hasExecutionSignals: Boolean,
        val hasExplorationSignals: Boolean,
        val hasSocialSignals: Boolean,
        val executionHits: List<String>,
        val explorationHits: List<String>,
        val socialHits: List<String>
    )

    data class AutoStartDecision(
        val shouldStartTask: Boolean,
        val reason: String,
        val evidence: List<String>
    )

    private val englishExecutionTokens = setOf(
        "implement", "build", "create", "fix", "refactor", "add", "design", "develop", "write"
    )
    private val russianExecutionRoots = setOf(
        "реализ", "сдел", "созд", "исправ", "добав", "спроект", "разработ", "рефактор", "перепис"
    )

    private val englishExplorationTokens = setOf(
        "explain", "describe", "compare", "clarify", "overview", "introduce", "understand"
    )
    private val russianExplorationRoots = setOf(
        "объяс", "расскаж", "сравн", "уточн", "понят", "разбер", "обзор", "опиш"
    )
    private val questionWords = setOf(
        "how", "what", "why", "who", "which",
        "как", "что", "почему", "кто", "какой"
    )

    private val englishSocialTokens = setOf(
        "hello", "hi", "hey", "thanks", "thank", "ok", "okay", "yes", "no"
    )
    private val russianSocialRoots = setOf(
        "привет", "здрав", "спасиб", "благодар", "ок", "окей", "ага", "хорош", "понятн", "да", "нет"
    )

    private val helperTokens = setOf("please", "can", "could", "пожалуйста", "помоги", "можешь", "нужно", "надо")

    fun analyze(message: String): TriggerSignals {
        val normalized = message.trim().lowercase()
        val tokens = tokenize(normalized)

        val executionHits = buildList {
            addAll(tokens.filter { it in englishExecutionTokens })
            addAll(tokens.filter { token -> russianExecutionRoots.any(token::startsWith) })
        }.distinct()

        val explorationHits = buildList {
            addAll(tokens.filter { it in englishExplorationTokens })
            addAll(tokens.filter { token -> russianExplorationRoots.any(token::startsWith) })
            if (normalized.endsWith("?")) add("question_mark")
            tokens.firstOrNull()?.takeIf { it in questionWords }?.let { add("question_word:$it") }
        }.distinct()

        val socialHits = buildList {
            addAll(tokens.filter { it in englishSocialTokens })
            addAll(tokens.filter { token -> russianSocialRoots.any(token::startsWith) })
            if (tokens.size <= 4 && isShortSocialShape(tokens)) add("short_social_shape")
        }.distinct()

        return TriggerSignals(
            hasExecutionSignals = executionHits.isNotEmpty(),
            hasExplorationSignals = explorationHits.isNotEmpty(),
            hasSocialSignals = socialHits.isNotEmpty(),
            executionHits = executionHits,
            explorationHits = explorationHits,
            socialHits = socialHits
        )
    }

    fun evaluateAutoStart(
        message: String,
        requestKind: RequestKind,
        normalizedIntent: TaskChatIntent
    ): AutoStartDecision {
        val signals = analyze(message)
        val startIntentRequested = normalizedIntent == TaskChatIntent.START_COMPLEX_TASK
        val hasExecutionEvidence = signals.hasExecutionSignals || requestKind == RequestKind.COMPLEX

        if (signals.hasSocialSignals && !signals.hasExecutionSignals && !signals.hasExplorationSignals) {
            return AutoStartDecision(
                shouldStartTask = false,
                reason = "social_or_trivial_message",
                evidence = signals.socialHits
            )
        }

        if (signals.hasExplorationSignals && !signals.hasExecutionSignals) {
            return AutoStartDecision(
                shouldStartTask = false,
                reason = "exploration_request",
                evidence = signals.explorationHits
            )
        }

        if (requestKind == RequestKind.SIMPLE && !signals.hasExecutionSignals) {
            return AutoStartDecision(
                shouldStartTask = false,
                reason = "simple_without_execution_evidence",
                evidence = signals.socialHits + signals.explorationHits
            )
        }

        val shouldStart = hasExecutionEvidence &&
            (requestKind == RequestKind.COMPLEX || (startIntentRequested && signals.hasExecutionSignals))

        return AutoStartDecision(
            shouldStartTask = shouldStart,
            reason = if (shouldStart) "execution_evidence_present" else "missing_execution_evidence",
            evidence = buildList {
                if (startIntentRequested) add("intent:$normalizedIntent")
                if (requestKind == RequestKind.COMPLEX) add("requestKind:$requestKind")
                addAll(signals.executionHits.map { "execution:$it" })
            }
        )
    }

    fun hasLeadingExecutionVerb(message: String): Boolean {
        val tokens = tokenize(message.lowercase())
        val actionToken = when {
            tokens.isEmpty() -> null
            tokens.first() in helperTokens && tokens.size >= 2 -> tokens[1]
            tokens.size >= 3 && tokens[0] == "can" && tokens[1] == "you" -> tokens[2]
            else -> tokens.first()
        }
        return actionToken != null && (
            actionToken in englishExecutionTokens ||
                russianExecutionRoots.any(actionToken::startsWith)
            )
    }

    fun tokenize(text: String): List<String> {
        return text.split(Regex("""[^\p{L}\p{N}_]+"""))
            .filter { it.isNotBlank() }
    }

    private fun isShortSocialShape(tokens: List<String>): Boolean {
        return tokens.all { token ->
            token in englishSocialTokens || russianSocialRoots.any(token::startsWith)
        }
    }
}
