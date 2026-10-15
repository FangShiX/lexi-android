package com.finisdiscago.leximvp.feature.vocabulary

import com.finisdiscago.leximvp.core.database.VocabularyImportProgress
import com.finisdiscago.leximvp.core.database.VocabularyImportResult
import com.finisdiscago.leximvp.domain.model.VocabularyBook
import com.finisdiscago.leximvp.domain.model.WordEntry
import com.finisdiscago.leximvp.domain.repository.VocabularyRepository
import com.finisdiscago.leximvp.domain.repository.WordInput
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BookListViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun importProgressAndSuccessRemainInViewModelState() = runTest(dispatcher) {
        val viewModel = BookListViewModel(FakeVocabularyRepository())

        viewModel.startImport { onProgress ->
            onProgress(VocabularyImportProgress(40, 100))
            VocabularyImportResult(1, "Core", 100)
        }
        advanceUntilIdle()

        val success = viewModel.importUiState.value as VocabularyImportUiState.Success
        assertEquals("Core", success.result.bookName)
        assertEquals(100, success.result.entryCount)
    }

    @Test
    fun failureCanBeAcknowledgedBeforeRetry() = runTest(dispatcher) {
        val viewModel = BookListViewModel(FakeVocabularyRepository())

        viewModel.startImport { throw IllegalStateException("broken package") }
        advanceUntilIdle()

        val failed = viewModel.importUiState.value as VocabularyImportUiState.Failed
        assertEquals("broken package", failed.message)
        viewModel.acknowledgeImportResult()
        assertEquals(VocabularyImportUiState.Idle, viewModel.importUiState.value)
    }

    @Test
    fun cancellationProducesRecoverableCancelledState() = runTest(dispatcher) {
        val viewModel = BookListViewModel(FakeVocabularyRepository())

        viewModel.startImport {
            awaitCancellation()
        }
        dispatcher.scheduler.runCurrent()
        assertTrue(viewModel.importUiState.value is VocabularyImportUiState.Running)

        viewModel.cancelImport()
        advanceUntilIdle()

        assertEquals(VocabularyImportUiState.Cancelled, viewModel.importUiState.value)
    }
}

private class FakeVocabularyRepository : VocabularyRepository {
    override fun observeBooks(): Flow<List<VocabularyBook>> = flowOf(emptyList())
    override suspend fun getBook(bookId: Long): VocabularyBook? = null
    override suspend fun createBook(name: String, description: String): Long = 1
    override suspend fun updateBook(bookId: Long, name: String, description: String) = Unit
    override suspend fun updateBookPlan(
        bookId: Long,
        selectionEnabled: Boolean,
        dailySelectionLimit: Int,
        selectionPriority: Int
    ) = Unit
    override suspend fun deleteBook(bookId: Long) = Unit
    override fun observeWords(bookId: Long, query: String): Flow<List<WordEntry>> =
        flowOf(emptyList())
    override suspend fun getWord(wordId: Long): WordEntry? = null
    override suspend fun createWord(input: WordInput): Long = 1
    override suspend fun updateWord(wordId: Long, input: WordInput) = Unit
    override suspend fun setWordFamiliarity(wordId: Long, familiarity: String?) = Unit
    override suspend fun deleteWord(wordId: Long) = Unit
}
