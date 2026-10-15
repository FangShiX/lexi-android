package com.finisdiscago.leximvp.feature.learning

import com.finisdiscago.leximvp.domain.model.StudyHistoryDay

// Welcome/tasks/calendar come from FirstTabFragment. Selection uses Lexi's local queues.
enum class StudyHomePage { Welcome, Tasks, Calendar }

data class StudyOverviewSnapshot(
    val hasVocabulary: Boolean,
    val learningCount: Int,
    val reviewCount: Int,
    val intraDayCount: Int,
    val readingWordCount: Int,
    val studiedToday: Int,
    val history: List<StudyHistoryDay>
) {
    val page: StudyHomePage
        get() = when {
            !hasVocabulary -> StudyHomePage.Welcome
            learningCount > 0 || reviewCount > 0 -> StudyHomePage.Tasks
            else -> StudyHomePage.Calendar
        }
}

data class StudyOverviewState(
    val snapshot: StudyOverviewSnapshot? = null,
    val loading: Boolean = true,
    val error: String? = null
)
