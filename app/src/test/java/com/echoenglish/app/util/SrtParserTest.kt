package com.echoenglish.app.util

import org.junit.Assert.*
import org.junit.Test

class SrtParserTest {
    @Test fun parsesCommaAndMultilineText() {
        val cues = SrtParser.parseText("""1
00:00:01,250 --> 00:00:03,500
Hello
world

2
00:00:04.000 --> 00:00:05.250
Again""")
        assertEquals(2, cues.size)
        assertEquals(1_250, cues[0].startMs)
        assertEquals("Hello\nworld", cues[0].text)
        assertEquals(5_250, cues[1].endMs)
    }
}
