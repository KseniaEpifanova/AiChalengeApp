package com.example.aichalengeapp.retrieval

import kotlin.math.sqrt

object CosineSimilarity {
    fun compute(left: FloatArray, right: FloatArray): Double {
        if (left.isEmpty() || right.isEmpty() || left.size != right.size) return 0.0

        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0
        for (i in left.indices) {
            val l = left[i].toDouble()
            val r = right[i].toDouble()
            dot += l * r
            leftNorm += l * l
            rightNorm += r * r
        }

        if (leftNorm == 0.0 || rightNorm == 0.0) return 0.0
        return dot / (sqrt(leftNorm) * sqrt(rightNorm))
    }
}
