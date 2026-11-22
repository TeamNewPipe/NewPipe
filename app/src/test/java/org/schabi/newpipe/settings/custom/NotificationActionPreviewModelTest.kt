package org.schabi.newpipe.settings.custom

import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationActionPreviewModelTest {
    @Test
    fun includesOnlyConfiguredNotificationSlots() {
        assertEquals(listOf(0, 1, 2, 3, 4), NotificationActionPreviewModel.positions(5, 28))
        assertEquals(listOf(0, 1), NotificationActionPreviewModel.positions(2, 28))
        assertEquals(emptyList<Int>(), NotificationActionPreviewModel.positions(0, 28))
    }

    @Test
    fun android13ShowsOnlyCustomizableButtons() {
        assertEquals(listOf(3, 4), NotificationActionPreviewModel.positions(5, 33))
    }

    @Test
    fun previewCannotExceedFiveSlots() {
        assertEquals(5, NotificationActionPreviewModel.positions(100, 28).size)
    }
}
