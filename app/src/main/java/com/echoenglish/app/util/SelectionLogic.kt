package com.echoenglish.app.util

object SelectionLogic {
    fun <T> isSelected(currentValue: T, optionValue: T): Boolean = currentValue == optionValue
}
