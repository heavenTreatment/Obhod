package com.heaventreat.obhod.strategy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StrategyProfilesTest {
    @Test
    fun onlyByeByeDpiAndRootStrategiesAreSelectable() {
        assertEquals(
            listOf(StrategyProfiles.BYEBYEDPI_ID, StrategyProfiles.ROOT_ID),
            StrategyProfiles.options.map { it.id }
        )
        assertTrue(StrategyProfiles.isValid(StrategyProfiles.BYEBYEDPI_ID))
        assertTrue(StrategyProfiles.isValid(StrategyProfiles.ROOT_ID))
        assertFalse(StrategyProfiles.isValid("retired-profile"))
        assertFalse(StrategyProfiles.isValid("flowseal-general-mobile"))
    }

    @Test
    fun labelsMatchTheVisibleStrategyNames() {
        assertEquals("ByeByeDPI", StrategyProfiles.label(StrategyProfiles.BYEBYEDPI_ID))
        assertEquals("Zapret Root", StrategyProfiles.label(StrategyProfiles.ROOT_ID))
        assertEquals("ByeByeDPI", StrategyProfiles.label("unknown"))
    }

    @Test
    fun rootIsTheSelectableKernelStrategy() {
        val option = StrategyProfiles.options.firstOrNull {
            it.id == StrategyProfiles.ROOT_ID
        }
        assertNotNull(option)
        assertEquals("Zapret Root", option?.title)
        assertTrue(option?.description.orEmpty().lowercase().contains("root"))
        assertTrue(option?.description.orEmpty().contains("KernelSU"))
    }
}
