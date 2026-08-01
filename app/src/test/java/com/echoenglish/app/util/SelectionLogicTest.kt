package com.echoenglish.app.util

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionLogicTest {
    @Test fun firstDurationOptionCanBeSelected() {
        assertTrue(SelectionLogic.isSelected(5, 5))
        assertFalse(SelectionLogic.isSelected(5, 15))
    }

    @Test fun firstRepeatOptionCanBeSelected() {
        assertTrue(SelectionLogic.isSelected(1, 1))
    }

    @Test fun firstSpeedOptionCanBeSelected() {
        assertTrue(SelectionLogic.isSelected(.75f, .75f))
    }
}
