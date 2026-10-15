package com.finisdiscago.leximvp.domain.usecase

import com.finisdiscago.leximvp.domain.model.WordEntry

class BuildLearningQueueUseCase {
    operator fun invoke(words: List<WordEntry>): List<WordEntry> = words
}
