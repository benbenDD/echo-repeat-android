package com.echoenglish.app

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.echoenglish.app.model.SrtCue
import com.echoenglish.app.model.SubtitleCalibration
import com.echoenglish.app.model.SubtitleCalibrationMode
import com.echoenglish.app.util.SubtitleTiming
import com.echoenglish.app.util.formatSubtitleTime
import kotlinx.coroutines.launch

@Composable
internal fun SubtitleCalibrationDialog(
    cues: List<SrtCue>,
    calibration: SubtitleCalibration,
    initialCueId: Int?,
    offsetMs: Long,
    positionMs: Long,
    playbackRequested: Boolean,
    error: String?,
    onSave: (Int, Long) -> Unit,
    onDelete: (Int) -> Unit,
    onModeChange: (SubtitleCalibrationMode) -> Unit,
    onClear: () -> Unit,
    onTogglePlayback: () -> Unit,
    onDismiss: () -> Unit
) {
    var numberInput by remember(initialCueId) {
        mutableStateOf((cues.indexOfFirst { it.index == initialCueId }.coerceAtLeast(0) + 1).toString())
    }
    val cue = numberInput.toIntOrNull()?.let { cues.getOrNull(it - 1) }
    val point = calibration.points.firstOrNull { it.cueId == cue?.index }
    var timeInput by remember(cue?.index, point, offsetMs) {
        mutableStateOf(formatSubtitleTime(
            point?.let { it.audioTimeMs + offsetMs }
                ?: (calibration.mapTime(cue?.startMs ?: 0L) + offsetMs)
        ))
    }
    var inputError by remember { mutableStateOf<String?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(.94f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        shape = RoundedCornerShape(22.dp),
        title = { Text("多点字幕校准", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp),
                state = listState,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text("校准点记录：一句字幕的原始开始时间，对应音频中的实际开始时间。原 SRT 不会改写。", fontSize = 13.sp)
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = calibration.mode == SubtitleCalibrationMode.GRADUAL,
                            onClick = { onModeChange(SubtitleCalibrationMode.GRADUAL) },
                            label = { Text("渐进校准") }, modifier = Modifier.weight(1f)
                        )
                        FilterChip(
                            selected = calibration.mode == SubtitleCalibrationMode.SECTION,
                            onClick = { onModeChange(SubtitleCalibrationMode.SECTION) },
                            label = { Text("分区校准") }, modifier = Modifier.weight(1f)
                        )
                    }
                    Text(
                        if (calibration.mode == SubtitleCalibrationMode.GRADUAL)
                            "适合越播越偏：两点之间平滑调整；点范围外保持最近点的偏移。只有一个点时相当于整体调整。"
                        else "适合某处突然错位：每个点开始一个新区间，此前区间保持原样，每句时长不拉伸。",
                        fontSize = 12.sp
                    )
                }
                item {
                    OutlinedTextField(
                        value = numberInput,
                        onValueChange = { numberInput = it; inputError = null },
                        label = { Text("原始字幕序号（1～${cues.size}）") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        isError = cue == null
                    )
                }
                item {
                    if (cue != null) {
                        Text("原始 SRT：${formatSubtitleTime(cue.startMs)}–${formatSubtitleTime(cue.endMs)}", fontSize = 12.sp)
                        Text(cue.text, maxLines = 3, fontSize = 14.sp)
                    } else Text("请输入有效的字幕序号")
                }
                item {
                    OutlinedTextField(
                        value = timeInput,
                        onValueChange = { timeInput = it; inputError = null },
                        label = { Text("这句在音频中的实际开始时间") },
                        supportingText = { Text("例如 12:34.500 或 1:02:03.250，也可输入秒数") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        isError = inputError != null
                    )
                }
                item {
                    Row {
                        TextButton(onClick = {
                            timeInput = formatSubtitleTime(positionMs); inputError = null
                        }, modifier = Modifier.weight(1f)) {
                            Text("取当前位置 ${formatSubtitleTime(positionMs)}", fontSize = 12.sp)
                        }
                        TextButton(onClick = onTogglePlayback) {
                            Text(if (playbackRequested) "暂停" else "播放")
                        }
                    }
                    Button(
                        onClick = {
                            val audioTime = SubtitleTiming.parseTime(timeInput)
                            if (audioTime == null) inputError = "时间格式不正确，请使用分:秒.毫秒"
                            else cue?.let { onSave(it.index, audioTime) }
                        }, enabled = cue != null,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(if (point == null) "保存校准点" else "更新这个校准点") }
                    (inputError ?: error)?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                    }
                }
                item {
                    Text("已保存 ${calibration.points.size} 个校准点", fontWeight = FontWeight.Bold)
                    Text("整体提前／推后仍可微调全部校准时间。切换音频后分别保存。", fontSize = 12.sp)
                }
                items(calibration.points, key = { it.cueId }) { saved ->
                    val ordinal = cues.indexOfFirst { it.index == saved.cueId } + 1
                    Surface(tonalElevation = 2.dp, shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.fillMaxWidth().padding(10.dp)) {
                            Text("第 $ordinal 条字幕", fontWeight = FontWeight.Bold)
                            Text(
                                "原始 ${formatSubtitleTime(saved.subtitleTimeMs)} → 音频 ${formatSubtitleTime(saved.audioTimeMs + offsetMs)}",
                                fontSize = 12.sp
                            )
                            Row {
                                TextButton(onClick = {
                                    numberInput = ordinal.toString()
                                    timeInput = formatSubtitleTime(saved.audioTimeMs + offsetMs)
                                    scope.launch { listState.animateScrollToItem(0) }
                                }) { Text("编辑") }
                                TextButton(onClick = { onDelete(saved.cueId) }) { Text("删除") }
                            }
                        }
                    }
                }
                if (calibration.points.isNotEmpty()) item {
                    TextButton(onClick = { confirmReset = true }) { Text("清除所有校准点") }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("清除这条音频的校准点？") },
        text = { Text("将恢复原字幕加整体偏移，不删除字幕文件或书签。") },
        confirmButton = { TextButton(onClick = { confirmReset = false; onClear() }) { Text("清除") } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } }
    )
}
