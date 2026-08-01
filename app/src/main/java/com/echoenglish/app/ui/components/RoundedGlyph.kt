package com.echoenglish.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

enum class RoundedGlyphKind {
    CUT,
    TIMER,
    REPEAT,
    SUBTITLES,
    SYNC,
    LEAD_IN,
    LEAD_OUT,
    GAP,
    SPEED,
    QUEUE,
    BEDTIME,
    ALARM,
    TARGET,
    PREVIOUS,
    NEXT
}

@Composable
fun RoundedGlyph(
    kind: RoundedGlyphKind,
    contentDescription: String? = null,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    tint: Color
) {
    val semanticsModifier = if (contentDescription == null) {
        modifier
    } else {
        modifier.semantics { this.contentDescription = contentDescription }
    }
    Canvas(semanticsModifier.then(Modifier.size(size))) {
        val s = this.size.minDimension / 24f
        fun p(x: Float, y: Float) = Offset(x * s, y * s)
        val line = 2.05f * s
        val stroke = Stroke(line, cap = StrokeCap.Round, join = StrokeJoin.Round)

        when (kind) {
            RoundedGlyphKind.CUT -> {
                drawCircle(tint, 2.5f * s, p(6f, 7f), style = stroke)
                drawCircle(tint, 2.5f * s, p(6f, 17f), style = stroke)
                drawLine(tint, p(8.2f, 8.2f), p(19f, 3.8f), line, StrokeCap.Round)
                drawLine(tint, p(8.2f, 15.8f), p(19f, 20.2f), line, StrokeCap.Round)
                drawLine(tint, p(10.5f, 11f), p(14f, 12.5f), line, StrokeCap.Round)
            }

            RoundedGlyphKind.TIMER -> {
                drawCircle(tint, 7.5f * s, p(12f, 13f), style = stroke)
                drawLine(tint, p(9f, 3f), p(15f, 3f), line, StrokeCap.Round)
                drawLine(tint, p(12f, 3f), p(12f, 5.2f), line, StrokeCap.Round)
                drawLine(tint, p(18.2f, 6.8f), p(20f, 5f), line, StrokeCap.Round)
                drawLine(tint, p(12f, 13f), p(12f, 8.5f), line, StrokeCap.Round)
                drawLine(tint, p(12f, 13f), p(15.2f, 15f), line, StrokeCap.Round)
            }

            RoundedGlyphKind.REPEAT -> {
                drawLine(tint, p(5f, 8f), p(18f, 8f), line, StrokeCap.Round)
                drawLine(tint, p(18f, 8f), p(15f, 5f), line, StrokeCap.Round)
                drawLine(tint, p(18f, 8f), p(15f, 11f), line, StrokeCap.Round)
                drawLine(tint, p(19f, 16f), p(6f, 16f), line, StrokeCap.Round)
                drawLine(tint, p(6f, 16f), p(9f, 13f), line, StrokeCap.Round)
                drawLine(tint, p(6f, 16f), p(9f, 19f), line, StrokeCap.Round)
            }

            RoundedGlyphKind.SUBTITLES -> {
                drawRoundRect(
                    tint,
                    p(3f, 5f),
                    Size(18f * s, 14f * s),
                    CornerRadius(3f * s),
                    style = stroke
                )
                drawLine(tint, p(6f, 11f), p(10f, 11f), line, StrokeCap.Round)
                drawLine(tint, p(14f, 11f), p(18f, 11f), line, StrokeCap.Round)
                drawLine(tint, p(6f, 15f), p(12f, 15f), line, StrokeCap.Round)
                drawLine(tint, p(15f, 15f), p(18f, 15f), line, StrokeCap.Round)
            }

            RoundedGlyphKind.SYNC -> {
                drawRoundRect(
                    tint,
                    p(3f, 3.5f),
                    Size(18f * s, 11f * s),
                    CornerRadius(3f * s),
                    style = stroke
                )
                drawLine(tint, p(6f, 8f), p(10f, 8f), line, StrokeCap.Round)
                drawLine(tint, p(14f, 8f), p(18f, 8f), line, StrokeCap.Round)
                drawLine(tint, p(6f, 19f), p(18f, 19f), line, StrokeCap.Round)
                drawLine(tint, p(6f, 19f), p(8.5f, 16.5f), line, StrokeCap.Round)
                drawLine(tint, p(6f, 19f), p(8.5f, 21.5f), line, StrokeCap.Round)
                drawLine(tint, p(18f, 19f), p(15.5f, 16.5f), line, StrokeCap.Round)
                drawLine(tint, p(18f, 19f), p(15.5f, 21.5f), line, StrokeCap.Round)
            }

            RoundedGlyphKind.LEAD_IN -> {
                drawLine(tint, p(7f, 5f), p(7f, 19f), line, StrokeCap.Round)
                drawLine(tint, p(11f, 8f), p(20f, 8f), line, StrokeCap.Round)
                drawLine(tint, p(11f, 12f), p(18f, 12f), line, StrokeCap.Round)
                drawLine(tint, p(11f, 16f), p(16f, 16f), line, StrokeCap.Round)
                drawLine(tint, p(2.5f, 12f), p(5f, 9.5f), line, StrokeCap.Round)
                drawLine(tint, p(2.5f, 12f), p(5f, 14.5f), line, StrokeCap.Round)
            }

            RoundedGlyphKind.LEAD_OUT -> {
                drawLine(tint, p(17f, 5f), p(17f, 19f), line, StrokeCap.Round)
                drawLine(tint, p(4f, 8f), p(13f, 8f), line, StrokeCap.Round)
                drawLine(tint, p(6f, 12f), p(13f, 12f), line, StrokeCap.Round)
                drawLine(tint, p(8f, 16f), p(13f, 16f), line, StrokeCap.Round)
                drawLine(tint, p(21.5f, 12f), p(19f, 9.5f), line, StrokeCap.Round)
                drawLine(tint, p(21.5f, 12f), p(19f, 14.5f), line, StrokeCap.Round)
            }

            RoundedGlyphKind.GAP -> {
                drawRoundRect(tint, p(4f, 6f), Size(4f * s, 12f * s), CornerRadius(2f * s))
                drawRoundRect(tint, p(16f, 6f), Size(4f * s, 12f * s), CornerRadius(2f * s))
                drawCircle(tint, 1.1f * s, p(11f, 12f))
                drawCircle(tint, 1.1f * s, p(14f, 12f))
            }

            RoundedGlyphKind.SPEED -> {
                drawArc(
                    tint,
                    startAngle = 145f,
                    sweepAngle = 250f,
                    useCenter = false,
                    topLeft = p(4f, 5f),
                    size = Size(16f * s, 16f * s),
                    style = stroke
                )
                drawLine(tint, p(12f, 14f), p(17.5f, 9f), line, StrokeCap.Round)
                drawCircle(tint, 1.6f * s, p(12f, 14f))
            }

            RoundedGlyphKind.QUEUE -> {
                drawLine(tint, p(4f, 7f), p(14f, 7f), line, StrokeCap.Round)
                drawLine(tint, p(4f, 12f), p(12f, 12f), line, StrokeCap.Round)
                drawLine(tint, p(4f, 17f), p(10f, 17f), line, StrokeCap.Round)
                drawLine(tint, p(16f, 8f), p(20f, 7f), line, StrokeCap.Round)
                drawLine(tint, p(16f, 8f), p(16f, 17f), line, StrokeCap.Round)
                drawCircle(tint, 2.3f * s, p(13.8f, 17.5f), style = stroke)
            }

            RoundedGlyphKind.BEDTIME -> {
                drawArc(
                    tint,
                    startAngle = 55f,
                    sweepAngle = 255f,
                    useCenter = false,
                    topLeft = p(4f, 3f),
                    size = Size(15f * s, 17f * s),
                    style = Stroke(2.5f * s, cap = StrokeCap.Round)
                )
                drawLine(tint, p(17.5f, 5f), p(17.5f, 8f), line, StrokeCap.Round)
                drawLine(tint, p(16f, 6.5f), p(19f, 6.5f), line, StrokeCap.Round)
                drawCircle(tint, 1f * s, p(20f, 12f))
            }

            RoundedGlyphKind.ALARM -> {
                drawCircle(tint, 7f * s, p(12f, 13f), style = stroke)
                drawLine(tint, p(12f, 13f), p(12f, 8.5f), line, StrokeCap.Round)
                drawLine(tint, p(12f, 13f), p(15.5f, 15f), line, StrokeCap.Round)
                drawLine(tint, p(5f, 5f), p(2.8f, 7.2f), line, StrokeCap.Round)
                drawLine(tint, p(19f, 5f), p(21.2f, 7.2f), line, StrokeCap.Round)
                drawLine(tint, p(7f, 19f), p(5f, 21f), line, StrokeCap.Round)
                drawLine(tint, p(17f, 19f), p(19f, 21f), line, StrokeCap.Round)
            }

            RoundedGlyphKind.TARGET -> {
                drawCircle(tint, 7.5f * s, p(12f, 12f), style = stroke)
                drawCircle(tint, 3.2f * s, p(12f, 12f), style = stroke)
                drawCircle(tint, 1f * s, p(12f, 12f))
                drawLine(tint, p(12f, 2f), p(12f, 5f), line, StrokeCap.Round)
                drawLine(tint, p(12f, 19f), p(12f, 22f), line, StrokeCap.Round)
            }

            RoundedGlyphKind.PREVIOUS -> {
                drawLine(tint, p(6f, 5f), p(6f, 19f), line, StrokeCap.Round)
                val path = Path().apply {
                    moveTo(18f * s, 5f * s)
                    lineTo(9f * s, 12f * s)
                    lineTo(18f * s, 19f * s)
                    close()
                }
                drawPath(path, tint)
            }

            RoundedGlyphKind.NEXT -> {
                drawLine(tint, p(18f, 5f), p(18f, 19f), line, StrokeCap.Round)
                val path = Path().apply {
                    moveTo(6f * s, 5f * s)
                    lineTo(15f * s, 12f * s)
                    lineTo(6f * s, 19f * s)
                    close()
                }
                drawPath(path, tint)
            }
        }
    }
}
