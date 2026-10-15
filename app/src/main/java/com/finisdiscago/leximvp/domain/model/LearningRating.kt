package com.finisdiscago.leximvp.domain.model

enum class LearningRating(val score: Int) {
    Mastered(3),
    Know(2),
    Fuzzy(1),
    Forgot(0)
}
