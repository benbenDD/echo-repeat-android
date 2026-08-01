package com.echoenglish.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoenglish.app.data.TrackEntity
import com.echoenglish.app.model.*
import com.echoenglish.app.playback.PlaybackContract
import com.echoenglish.app.ui.MainViewModel
import com.echoenglish.app.util.formatTime
import com.echoenglish.app.util.SelectionLogic
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest

private val Ink = Color(0xFF173F49)
private val Cream = Color(0xFFFFF8F1)
private val Orange = Color(0xFFF3A85A)
private val Sage = Color(0xFFBFD8CA)
private val Purple = Color(0xFF7254C7)
private val PurpleLight = Color(0xFFEDE7FF)

enum class Screen { LIBRARY, PLAYER, SETTINGS }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { EchoTheme { EchoEnglishUi() } }
    }
}

@Composable
private fun EchoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(primary = Ink, secondary = Orange, background = Cream, surface = Color.White, onPrimary = Color.White),
        typography = Typography(bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 17.sp)),
        content = content
    )
}

@Composable
private fun EchoEnglishUi(vm: MainViewModel = viewModel()) {
    val tracks by vm.tracks.collectAsState()
    val current by vm.current.collectAsState()
    val playback by vm.playback.collectAsState()
    val settings by vm.settings.collectAsState()
    val message by vm.message.collectAsState()
    var screen by remember { mutableStateOf(Screen.LIBRARY) }
    val snackbar = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.importUris(uris) }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::importTree) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = screen == Screen.LIBRARY, onClick = { screen = Screen.LIBRARY }, icon = { Text("▤") }, label = { Text("播放列表") })
                NavigationBarItem(selected = screen == Screen.PLAYER, onClick = { screen = Screen.PLAYER }, icon = { Text("▶") }, label = { Text("复读") })
                NavigationBarItem(selected = screen == Screen.SETTINGS, onClick = { screen = Screen.SETTINGS }, icon = { Text("⚙") }, label = { Text("设置") })
            }
        }
    ) { padding ->
        Box(Modifier.padding(padding).fillMaxSize().background(Cream)) {
            when (screen) {
                Screen.LIBRARY -> LibraryScreen(
                    tracks = tracks,
                    onFiles = { importLauncher.launch(arrayOf("audio/*", "application/x-subrip", "text/plain", "application/octet-stream")) },
                    onFolder = { treeLauncher.launch(null) },
                    onOpen = { vm.openTrack(it); screen = Screen.PLAYER },
                    onDelete = vm::delete
                )
                Screen.PLAYER -> PlayerScreen(
                    title = current?.title ?: playback.title,
                    state = playback,
                    onCommand = vm::command,
                    onSeekAbsolute = vm::seekAbsolute,
                    onSeekSegment = vm::seekToSegment,
                    onTimer = vm::setSleepTimer,
                    onLibrary = { screen = Screen.LIBRARY }
                )
                Screen.SETTINGS -> SettingsScreen(settings, vm::updateSettings)
            }
        }
    }
}

@Composable
private fun LibraryScreen(
    tracks: List<TrackEntity>,
    onFiles: () -> Unit,
    onFolder: () -> Unit,
    onOpen: (TrackEntity) -> Unit,
    onDelete: (TrackEntity) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(18.dp))
        Text("回声英语", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Ink)
        Text("把长音频变成可以掌握的小段落", color = Ink.copy(alpha = .7f))
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onFiles, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors(containerColor = Ink)) { Text("批量导入文件") }
            OutlinedButton(onClick = onFolder, modifier = Modifier.weight(1f)) { Text("导入文件夹") }
        }
        Spacer(Modifier.height(18.dp))
        Text("学习列表 · ${tracks.size}", fontWeight = FontWeight.Bold, color = Ink)
        Spacer(Modifier.height(8.dp))
        if (tracks.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🎧", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("还没有音频", fontWeight = FontWeight.Bold)
                    Text("可以同时选择 MP3 与 SRT，文件名相近时会自动匹配", color = Color.Gray)
                }
            }
        } else LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
            items(tracks, key = { it.id }) { track -> TrackCard(track, { onOpen(track) }, { onDelete(track) }) }
        }
    }
}

@Composable
private fun TrackCard(track: TrackEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onOpen), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).background(if (track.subtitleUri != null) Sage else Orange.copy(alpha = .35f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) { Text(if (track.subtitleUri != null) "SRT" else "MP3", fontWeight = FontWeight.Bold, fontSize = 12.sp) }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("${formatTime(track.durationMs)} · ${if (track.subtitleUri != null) "字幕已匹配" else "固定时长分段"}", color = Color.Gray, fontSize = 13.sp)
                if (track.currentSegment > 0) Text("上次学到第 ${track.currentSegment + 1} 段", color = Ink, fontSize = 13.sp)
            }
            TextButton(onClick = onDelete) { Text("移除", color = Color.Gray) }
        }
    }
}

@Composable
private fun PlayerScreen(
    title: String,
    state: com.echoenglish.app.playback.PlaybackSnapshot,
    onCommand: (String, Long?) -> Unit,
    onSeekAbsolute: (Long) -> Unit,
    onSeekSegment: (Int) -> Unit,
    onTimer: (Int) -> Unit,
    onLibrary: () -> Unit
) {
    var showTimer by remember { mutableStateOf(false) }
    var showSegments by remember { mutableStateOf(false) }
    var subtitlesExpanded by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var segmentDrag by remember { mutableStateOf<Float?>(null) }
    var totalDrag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(state.sleepDeadlineMs) { while (state.sleepDeadlineMs > 0) { now = System.currentTimeMillis(); delay(1000) } }
    val segmentDuration = state.segmentDurationMs.coerceAtLeast(1)
    val relative = state.segmentPositionMs.coerceIn(0, segmentDuration)
    val totalDuration = state.durationMs.coerceAtLeast(1)

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLibrary) { Text("‹ 列表") }
            Text(title.ifBlank { "请选择音频" }, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { showTimer = true }) { Text("定时") }
        }
        val subtitleModifier = if (subtitlesExpanded) {
            Modifier.fillMaxWidth().weight(1f)
        } else {
            Modifier.fillMaxWidth().heightIn(min = 108.dp, max = 132.dp)
        }
        SubtitlePanel(
            state = state,
            modifier = subtitleModifier,
            expanded = subtitlesExpanded,
            onExpandedChange = { subtitlesExpanded = it },
            onSubtitleClick = onSeekAbsolute
        )
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("第 ${state.segmentIndex + 1}/${state.segmentCount.coerceAtLeast(1)} 段 · 第 ${state.repeatIndex}/${if (state.repeatCount == 0) "∞" else state.repeatCount} 次", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Ink, fontSize = 14.sp)
            TextButton(onClick = { showSegments = true }, enabled = state.segments.isNotEmpty()) { Text("选择片段", color = Purple) }
        }
        ProgressBlock("本段进度", segmentDrag ?: relative.toFloat(), segmentDuration.toFloat(), formatTime((segmentDrag ?: relative.toFloat()).toLong()), formatTime(segmentDuration), state.segmentCount > 0, { segmentDrag = it }) {
            segmentDrag?.let { onCommand(PlaybackContract.ACTION_SEEK, it.toLong()) }; segmentDrag = null
        }
        Spacer(Modifier.height(9.dp))
        ProgressBlock("总进度", totalDrag ?: state.positionMs.coerceIn(0, totalDuration).toFloat(), totalDuration.toFloat(), formatTime((totalDrag ?: state.positionMs.toFloat()).toLong()), formatTime(state.durationMs), state.durationMs > 0, { totalDrag = it }) {
            totalDrag?.let { onSeekAbsolute(it.toLong()) }; totalDrag = null
        }
        Spacer(Modifier.height(11.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onCommand(PlaybackContract.ACTION_PREVIOUS, null) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text("上一段") }
            FilledIconButton(onClick = { onCommand(PlaybackContract.ACTION_TOGGLE, null) }, modifier = Modifier.size(60.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Orange)) { Text(if (state.isPlaying) "Ⅱ" else "▶", fontSize = 23.sp, color = Ink) }
            OutlinedButton(onClick = { onCommand(PlaybackContract.ACTION_NEXT, null) }, contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) { Text("下一段") }
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onCommand(PlaybackContract.ACTION_RESTART, null) }) { Text("↻ 重播当前段") }
            if (state.sleepDeadlineMs > 0) { val left = (state.sleepDeadlineMs - now).coerceAtLeast(0); AssistChip(onClick = { showTimer = true }, label = { Text("${formatTime(left)} 后停止") }) }
        }
    }
    if (showTimer) SleepTimerDialog({ showTimer = false }) { onTimer(it); showTimer = false }
    if (showSegments) SegmentPickerDialog(state, { showSegments = false }) { onSeekSegment(it); showSegments = false }
}

@Composable
private fun SubtitlePanel(
    state: com.echoenglish.app.playback.PlaybackSnapshot,
    modifier: Modifier,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    onSubtitleClick: (Long) -> Unit
) {
    val listState = rememberLazyListState()
    var userBrowsing by remember { mutableStateOf(false) }
    var autoScrolling by remember { mutableStateOf(false) }

    LaunchedEffect(listState, expanded) {
        if (!expanded) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling && !autoScrolling) {
                userBrowsing = true
            } else if (!scrolling && userBrowsing) {
                delay(2500)
                userBrowsing = false
            }
        }
    }
    LaunchedEffect(state.subtitleIndex, expanded, userBrowsing) {
        if (expanded && !userBrowsing && state.subtitleIndex in state.subtitles.indices) {
            delay(40)
            autoScrolling = true
            try {
                val viewportHeight = listState.layoutInfo.viewportSize.height
                val itemHeight = listState.layoutInfo.visibleItemsInfo
                    .firstOrNull { it.index == state.subtitleIndex }?.size ?: 84
                val centerOffset = -((viewportHeight - itemHeight) / 2).coerceAtLeast(0)
                listState.animateScrollToItem(state.subtitleIndex, centerOffset)
            } finally {
                autoScrolling = false
            }
        }
    }

    Surface(modifier = modifier, shape = RoundedCornerShape(18.dp), color = Color.White) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(if (expanded) "全部字幕" else "当前字幕", Modifier.weight(1f), color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (state.subtitles.isNotEmpty()) {
                    TextButton(onClick = { onExpandedChange(!expanded) }) {
                        Text(if (expanded) "收起字幕 ︿" else "展开字幕 ﹀", color = Purple, fontSize = 13.sp)
                    }
                }
            }
            if (state.subtitles.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(state.subtitle.ifBlank { "当前音频暂无字幕" }, color = Ink, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold)
                }
            } else if (!expanded) {
                val cue = state.subtitles.getOrNull(state.subtitleIndex)
                Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp, vertical = 6.dp), contentAlignment = Alignment.Center) {
                    Text(
                        cue?.text ?: "字幕即将开始…",
                        color = Ink,
                        fontSize = 20.sp,
                        lineHeight = 29.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                if (userBrowsing) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { userBrowsing = false }) { Text("回到当前字幕", color = Purple, fontSize = 12.sp) }
                    }
                }
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    state = listState,
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    itemsIndexed(state.subtitles) { index, cue ->
                        val selected = index == state.subtitleIndex
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                                .background(if (selected) PurpleLight else Color.Transparent, RoundedCornerShape(12.dp))
                                .border(if (selected) 1.5.dp else 0.dp, if (selected) Purple else Color.Transparent, RoundedCornerShape(12.dp))
                                .clickable { onSubtitleClick(cue.startMs) }
                                .padding(horizontal = 14.dp, vertical = 10.dp)
                        ) {
                            Text(formatTime(cue.startMs), color = if (selected) Purple else Color.Gray, fontSize = 11.sp)
                            Text(cue.text, color = Ink, fontSize = if (selected) 19.sp else 17.sp, lineHeight = 26.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
        }
    }
}
@Composable
private fun ProgressBlock(label: String, value: Float, maximum: Float, leftText: String, rightText: String, enabled: Boolean, onValueChange: (Float) -> Unit, onFinished: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp); Text("$leftText / $rightText", color = Color.Gray, fontSize = 12.sp) }
        Slider(value = value.coerceIn(0f, maximum.coerceAtLeast(1f)), onValueChange = onValueChange, onValueChangeFinished = onFinished, valueRange = 0f..maximum.coerceAtLeast(1f), enabled = enabled, modifier = Modifier.height(30.dp), colors = SliderDefaults.colors(thumbColor = Purple, activeTrackColor = Purple))
    }
}

@Composable
private fun SegmentPickerDialog(state: com.echoenglish.app.playback.PlaybackSnapshot, onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    val initial = state.segmentIndex.coerceIn(0, (state.segments.size - 1).coerceAtLeast(0))
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("跳转到指定片段") }, text = {
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 460.dp), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            itemsIndexed(state.segments) { index, segment ->
                val selected = index == state.segmentIndex
                Column(Modifier.fillMaxWidth().background(if (selected) PurpleLight else Color(0xFFF5F5F5), RoundedCornerShape(10.dp)).border(if (selected) 1.5.dp else 0.dp, if (selected) Purple else Color.Transparent, RoundedCornerShape(10.dp)).clickable { onSelect(index) }.padding(12.dp)) {
                    Text("第 ${index + 1} 段  ${formatTime(segment.startMs)}–${formatTime(segment.endMs)}", color = if (selected) Purple else Ink, fontWeight = FontWeight.Bold)
                    if (segment.text.isNotBlank()) Text(segment.text, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Color.DarkGray, fontSize = 13.sp)
                }
            }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } })
}

@Composable
private fun SleepTimerDialog(onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("睡眠定时器") }, text = { Column { listOf(5, 10, 15, 30, 45, 60, 90).chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { value -> OutlinedButton(onClick = { onSelect(value) }) { Text("${value}分") } } } } } }, confirmButton = { TextButton(onClick = { onSelect(0) }) { Text("关闭定时") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun SettingsScreen(value: PlaybackSettings, onChange: (PlaybackSettings) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 20.dp), contentPadding = PaddingValues(top = 20.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        item { Text("学习设置", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Ink) }
        item { SettingCard("分段方式") { ChoiceGrid(listOf("固定时长" to SegmentMode.FIXED, "按字幕" to SegmentMode.SUBTITLE), value.segmentMode) { onChange(value.copy(segmentMode = it)) } } }
        item { val enabled = value.segmentMode == SegmentMode.FIXED; SettingCard("每段时长", enabled, if (!enabled) "按字幕分段时，片段长度由字幕时间轴决定" else null) { ChoiceGrid(listOf("5秒" to 5, "10秒" to 10, "15秒" to 15, "20秒" to 20, "30秒" to 30), value.segmentSeconds, enabled) { onChange(value.copy(segmentSeconds = it)) } } }
        item { SettingCard("每段重复") { ChoiceGrid(listOf("1次" to 1, "3次" to 3, "5次" to 5, "10次" to 10, "无限" to 0), value.repeatCount) { onChange(value.copy(repeatCount = it)) } } }
        item { SettingCard("播放速度") { ChoiceGrid(listOf("0.75x" to .75f, "1.0x" to 1f, "1.25x" to 1.25f, "1.5x" to 1.5f, "2.0x" to 2f), value.speed) { onChange(value.copy(speed = it)) } } }
        item { SettingCard("列表播放") { ChoiceGrid(listOf("单曲停止" to PlaylistMode.STOP_AFTER_TRACK, "顺序播放" to PlaylistMode.SEQUENTIAL, "列表循环" to PlaylistMode.LOOP_LIST), value.playlistMode) { onChange(value.copy(playlistMode = it)) } } }
        item { SettingCard("定时到点") { ChoiceGrid(listOf("当前段结束" to true, "立即停止" to false), value.stopAtSegmentEnd) { onChange(value.copy(stopAtSegmentEnd = it)) } } }
        item { Text("设置会自动保存，并立即应用到当前正在播放的音频。", color = Color.Gray, fontSize = 13.sp) }
    }
}

@Composable
private fun SettingCard(title: String, enabled: Boolean = true, hint: String? = null, content: @Composable () -> Unit) {
    Column(Modifier.alpha(if (enabled) 1f else .48f)) { Text(title, fontWeight = FontWeight.Bold, color = Ink); hint?.let { Text(it, color = Color.Gray, fontSize = 12.sp) }; Spacer(Modifier.height(7.dp)); content() }
}

@Composable
private fun <T> ChoiceGrid(options: List<Pair<String, T>>, selectedValue: T, enabled: Boolean = true, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
        options.chunked(3).forEach { rowOptions ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { (label, option) ->
                    val selected = SelectionLogic.isSelected(selectedValue, option)
                    val shape = RoundedCornerShape(10.dp)
                    Box(
                        modifier = Modifier.weight(1f).heightIn(min = 42.dp)
                            .background(if (selected) PurpleLight else Color.White, shape)
                            .border(if (selected) 1.5.dp else 0.8.dp, if (selected) Purple else Color.LightGray, shape)
                            .clickable(enabled = enabled) { onSelect(option) }
                            .padding(horizontal = 8.dp, vertical = 9.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (selected) Purple else Ink,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
                repeat(3 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
