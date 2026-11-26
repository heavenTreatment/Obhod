package com.heaventreat.obhod.strategy

import org.junit.Assert.assertEquals
import org.junit.Test

class ByeByeDpiProgressTest {
    @Test
    fun tenOfTwelveSuccessfulRequestsMeetsTheSelectionThreshold() {
        assertEquals(true, ByeByeDpiManager.isSuccessfulScore(10, 12))
        assertEquals(false, ByeByeDpiManager.isSuccessfulScore(9, 12))
        assertEquals(false, ByeByeDpiManager.isSuccessfulScore(0, 0))
    }

    @Test
    fun fullAnalysisDoesNotStopAtTheFirstSuccessfulCandidate() {
        assertEquals(false, ByeByeDpiManager.shouldStopEarly(true, 10, 12))
        assertEquals(true, ByeByeDpiManager.shouldStopEarly(false, 10, 12))
    }

    @Test
    fun progressSplitsTestedCandidatesIntoSuccessfulFailedAndPending() {
        val progress = ByeByeDpiManager.Progress(
            totalCandidates = 10,
            successfulCandidates = 1,
            failedCandidates = 3,
            currentCandidate = null,
            isRunning = false
        )

        assertEquals(4, progress.checked)
        assertEquals(1, progress.successful)
        assertEquals(3, progress.failed)
        assertEquals(0, progress.active)
        assertEquals(6, progress.pending)
        assertEquals(25, progress.successPercent)
        assertEquals(75, progress.failurePercent)
    }

    @Test
    fun runningCandidateUsesOnePendingSegment() {
        val progress = ByeByeDpiManager.Progress(
            totalCandidates = 10,
            successfulCandidates = 1,
            failedCandidates = 3,
            currentCandidate = "byedpi-05",
            currentProgress = 0.5f,
            isRunning = true
        )

        assertEquals(1, progress.active)
        assertEquals(5, progress.checked + progress.active)
        assertEquals(5, progress.pending)
        assertEquals(0.45f, progress.fraction, 0.001f)
    }
}
