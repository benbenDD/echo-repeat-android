package com.echoenglish.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.delay

private val Ink = Color(0xFF173F49)
private val Cream = Color(0xFFFFF8F1)
private val Orange = Color(0xFFF3A85A)
private val Sage = Color(0xFFBFD8CA)

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
    onTimer: (Int) -> Unit,
    onLibrary: () -> Unit
) {
    var showTimer by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(state.sleepDeadlineMs) { while (state.sleepDeadlineMs > 0) { now = System.currentTimeMillis(); delay(1000) } }
    val segmentDuration = (state.segmentEndMs - state.segmentStartMs).coerceAtLeast(1)
    val relative = (state.positionMs - state.segmentStartMs).coerceIn(0, segmentDuration)
    Column(Modifier.fillMaxSize().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onLibrary) { Text("‹ 列表") }
            Text(title.ifBlank { "请选择音频" }, Modifier.weight(1f), fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            TextButton(onClick = { showTimer = true }) { Text("定时") }
        }
        Spacer(Modifier.height(24.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = Ink, modifier = Modifier.fillMaxWidth().heightIn(min = 210.dp)) {
            Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.Center) {
                Text("当前字幕", color = Orange, fontSize = 13.sp)
                Spacer(Modifier.height(12.dp))
                Text(state.subtitle.ifBlank { "正在按固定时长复读" }, color = Color.White, fontSize = 25.sp, lineHeight = 36.sp, fontWeight = FontWeight.Bold)
                if (state.nextSubtitle.isNotBlank()) {
                    Spacer(Modifier.height(22.dp)); Text("下一句  ${state.nextSubtitle}", color = Color.White.copy(alpha = .55f), maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text("第 ${state.segmentIndex + 1}/${state.segmentCount.coerceAtLeast(1)} 段  ·  第 ${state.repeatIndex}/${if (state.repeatCount == 0) "∞" else state.repeatCount} 次", fontWeight = FontWeight.Bold, color = Ink)
        Slider(value = relative.toFloat(), onValueChange = { onCommand(PlaybackContract.ACTION_SEEK, it.toLong()) }, valueRange = 0f..segmentDuration.toFloat())
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(relative), color = Color.Gray); Text(formatTime(segmentDuration), color = Color.Gray)
        }
        Spacer(Modifier.height(18.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { onCommand(PlaybackContract.ACTION_PREVIOUS, null) }) { Text("上一段") }
            FilledIconButton(onClick = { onCommand(PlaybackContract.ACTION_TOGGLE, null) }, modifier = Modifier.size(72.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Orange)) { Text(if (state.isPlaying) "Ⅱ" else "▶", fontSize = 26.sp, color = Ink) }
            OutlinedButton(onClick = { onCommand(PlaybackContract.ACTION_NEXT, null) }) { Text("下一段") }
        }
        TextButton(onClick = { onCommand(PlaybackContract.ACTION_RESTART, null) }) { Text("↻ 重新播放当前段") }
        if (state.sleepDeadlineMs > 0) {
            val left = (state.sleepDeadlineMs - now).coerceAtLeast(0)
            AssistChip(onClick = { showTimer = true }, label = { Text("将在 ${formatTime(left)} 后停止") })
        }
    }
    if (showTimer) SleepTimerDialog(onDismiss = { showTimer = false }, onSelect = { onTimer(it); showTimer = false })
}

@Composable
private fun SleepTimerDialog(onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("睡眠定时器") }, text = {
        Column { listOf(5, 10, 15, 30, 45, 60, 90).chunked(3).forEach { row -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { row.forEach { value -> OutlinedButton(onClick = { onSelect(value) }) { Text("${value}分") } } } } }
    }, confirmButton = { TextButton(onClick = { onSelect(0) }) { Text("关闭定时") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } })
}

@Composable
private fun SettingsScreen(value: PlaybackSettings, onChange: (PlaybackSettings) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp).padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("学习设置", fontSize = 28.sp, fontWeight = FontWeight.Black, color = Ink)
        SettingCard("分段方式") {
            ChoiceRow(listOf("固定时长", "按字幕"), if (value.segmentMode == SegmentMode.FIXED) 0 else 1) { onChange(value.copy(segmentMode = if (it == 0) SegmentMode.FIXED else SegmentMode.SUBTITLE)) }
        }
        SettingCard("每段时长") {
            ChoiceRow(listOf("5秒", "10秒", "15秒", "20秒", "30秒"), listOf(5,10,15,20,30).indexOf(value.segmentSeconds).coerceAtLeast(2)) { onChange(value.copy(segmentSeconds = listOf(5,10,15,20,30)[it])) }
        }
        SettingCard("每段重复") {
            ChoiceRow(listOf("1次", "3次", "5次", "10次", "无限"), listOf(1,3,5,10,0).indexOf(value.repeatCount).coerceAtLeast(1)) { onChange(value.copy(repeatCount = listOf(1,3,5,10,0)[it])) }
        }
        SettingCard("播放速度") {
            val speeds = listOf(0.75f, 1f, 1.25f, 1.5f, 2f)
            val labels = listOf("0.75x", "1.0x", "1.25x", "1.5x", "2.0x")
            ChoiceRow(labels, speeds.indexOf(value.speed).coerceAtLeast(1)) { onChange(value.copy(speed = speeds[it])) }
        }
        SettingCard("列表播放") {
            ChoiceRow(listOf("单曲停止", "顺序播放", "列表循环"), value.playlistMode.ordinal) { onChange(value.copy(playlistMode = PlaylistMode.entries[it])) }
        }
        SettingCard("定时到点") {
            ChoiceRow(listOf("当前段结束", "立即停止"), if (value.stopAtSegmentEnd) 0 else 1) { onChange(value.copy(stopAtSegmentEnd = it == 0)) }
        }
        Text("设置会自动保存。修改分段参数后，重新打开当前音频即可生效。", color = Color.Gray, fontSize = 13.sp)
    }
}

@Composable private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Column { Text(title, fontWeight = FontWeight.Bold, color = Ink); Spacer(Modifier.height(7.dp)); content() }
}

@Composable private fun ChoiceRow(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { i, label -> FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(label, fontSize = 12.sp) }) }
    }
}

