package com.heaventreat.obhod.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReleaseVersionTest {
    @Test
    fun comparesNumericComponentsRatherThanStrings() {
        assertEquals(1, ReleaseVersion.parse("v2.10")!!.compareTo(ReleaseVersion.parse("2.9")!!))
        assertEquals(0, ReleaseVersion.parse("v2.0")!!.compareTo(ReleaseVersion.parse("2.0.0")!!))
        assertEquals(-1, ReleaseVersion.parse("v1.9")!!.compareTo(ReleaseVersion.parse("2.0")!!))
    }

    @Test
    fun rejectsNonVersionTags() {
        assertNull(ReleaseVersion.parse("release-v2.0"))
        assertNull(ReleaseVersion.parse("v2.0-beta"))
    }
}
