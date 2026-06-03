package org.schabi.newpipe.util

import org.junit.Assert.assertEquals
import org.junit.Test

class ExtractorEngineHelperTest {
    @Test
    fun parsePreferenceValueDefaultsToNewPipeWhenMissing() {
        assertEquals(
            ExtractorEngineHelper.Engine.NEWPIPE,
            ExtractorEngineHelper.parsePreferenceValue(null, NEWPIPE_VALUE, PIPEPIPE_VALUE)
        )
    }

    @Test
    fun parsePreferenceValueFallsBackToNewPipeWhenInvalid() {
        assertEquals(
            ExtractorEngineHelper.Engine.NEWPIPE,
            ExtractorEngineHelper.parsePreferenceValue("unexpected", NEWPIPE_VALUE, PIPEPIPE_VALUE)
        )
    }

    @Test
    fun parsePreferenceValueParsesPipePipe() {
        assertEquals(
            ExtractorEngineHelper.Engine.PIPEPIPE,
            ExtractorEngineHelper.parsePreferenceValue(PIPEPIPE_VALUE, NEWPIPE_VALUE, PIPEPIPE_VALUE)
        )
    }

    companion object {
        private const val NEWPIPE_VALUE = "newpipe"
        private const val PIPEPIPE_VALUE = "pipepipe"
    }
}
