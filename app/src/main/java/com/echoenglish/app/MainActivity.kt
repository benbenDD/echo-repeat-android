package com.echoenglish.app

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.echoenglish.app.data.TrackEntity
import com.echoenglish.app.model.*
import com.echoenglish.app.playback.PlaybackContract
import com.echoenglish.app.playback.PlaybackSnapshot
import com.echoenglish.app.playback.SubtitleSnapshot
import com.echoenglish.app.ui.MainViewModel
import com.echoenglish.app.ui.components.RoundedGlyph
import com.echoenglish.app.ui.components.RoundedGlyphKind
import com.echoenglish.app.util.SelectionLogic
import com.echoenglish.app.util.SubtitleSearch
import com.echoenglish.app.util.formatTime
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest

private val Ink = Color(0xFF263B41)
private val MutedInk = Color(0xFF718086)
private val Cream = Color(0xFFFFF8F2)
private val Purple = Color(0xFF7657D5)
private val PurpleLight = Color(0xFFEEE8FF)
private val Coral = Color(0xFFFF8A65)
private val CoralLight = Color(0xFFFFE4DA)
private val Yellow = Color(0xFFFFC94A)
private val YellowLight = Color(0xFFFFF2C6)
private val Mint = Color(0xFF58C6A3)
private val MintLight = Color(0xFFDDF7ED)
private val Sky = Color(0xFF63B8FF)
private val SkyLight = Color(0xFFE0F2FF)
private val Pink = Color(0xFFF783B5)
private val PinkLight = Color(0xFFFDE3EF)
private val SoftBorder = Color(0xFFE8E1DC)

private val CardShape = RoundedCornerShape(22.dp)
private val ControlShape = RoundedCornerShape(15.dp)

private data class SegmentPickerRow(
    val key: String,
    val number: Int,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val cueIds: List<Int>,
    val bookmarked: Boolean,
    val selected: Boolean
)

enum class Screen { LIBRARY, PLAYER, SETTINGS }

class MainActivity : ComponentActivity() {
    private val openPlayerRequests = MutableStateFlow(0)
    private val resumeRequests = MutableStateFlow(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent?.action == PlaybackContract.ACTION_OPEN_PLAYER) openPlayerRequests.value = 1
        setContent { EchoTheme { EchoEnglishUi(openPlayerRequests, resumeRequests) } }
    }

    override fun onResume() {
        super.onResume()
        resumeRequests.value = resumeRequests.value + 1
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.action == PlaybackContract.ACTION_OPEN_PLAYER) {
            openPlayerRequests.value = openPlayerRequests.value + 1
        }
    }
}

@Composable
private fun EchoTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Purple,
            secondary = Coral,
            tertiary = Mint,
            background = Cream,
            surface = Color.White,
            onPrimary = Color.White,
            onBackground = Ink,
            onSurface = Ink
        ),
        typography = Typography(
            bodyLarge = androidx.compose.ui.text.TextStyle(fontSize = 17.sp),
            titleLarge = androidx.compose.ui.text.TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Black)
        ),
        content = content
    )
}

@Composable
private fun EchoEnglishUi(
    openPlayerRequests: StateFlow<Int>,
    resumeRequests: StateFlow<Int>,
    vm: MainViewModel = viewModel()
) {
    val tracks by vm.tracks.collectAsState()
    val current by vm.current.collectAsState()
    val playback by vm.playback.collectAsState()
    val settings by vm.settings.collectAsState()
    val message by vm.message.collectAsState()
    val startupRestoredTrackId by vm.startupRestoredTrackId.collectAsState()
    val locatorSubtitles by vm.locatorSubtitles.collectAsState()
    val openPlayerRequest by openPlayerRequests.collectAsState()
    val resumeRequest by resumeRequests.collectAsState()
    var screen by remember { mutableStateOf(Screen.LIBRARY) }
    val snackbar = remember { SnackbarHostState() }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { vm.importUris(it) }
    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::importTree) }
    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.clearMessage() }
    }
    LaunchedEffect(openPlayerRequest) {
        if (openPlayerRequest > 0) screen = Screen.PLAYER
    }
    LaunchedEffect(startupRestoredTrackId) {
        if (startupRestoredTrackId > 0L) screen = Screen.PLAYER
    }
    LaunchedEffect(resumeRequest) {
        if (resumeRequest > 0) vm.onAppForegrounded()
    }

    Scaffold(
        containerColor = Cream,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp) {
                EchoNavItem(screen == Screen.LIBRARY, { screen = Screen.LIBRARY }, Icons.AutoMirrored.Rounded.List, "播放列表")
                EchoNavItem(screen == Screen.PLAYER, { screen = Screen.PLAYER }, Icons.Rounded.PlayArrow, "复读")
                EchoNavItem(screen == Screen.SETTINGS, { screen = Screen.SETTINGS }, Icons.Rounded.Settings, "设置")
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
                    locatorSubtitles = locatorSubtitles,
                    onCommand = vm::command,
                    onSeekAbsolute = vm::seekAbsolute,
                    onToggleBookmark = vm::toggleBookmark,
                    onToggleBookmarks = vm::toggleBookmarks,
                    onTimer = vm::setSleepTimer,
                    onLibrary = { screen = Screen.LIBRARY }
                )
                Screen.SETTINGS -> SettingsScreen(settings, current, vm::updateSettings, vm::updateSubtitleOffset)
            }
        }
    }
}

@Composable
private fun RowScope.EchoNavItem(selected: Boolean, onClick: () -> Unit, icon: ImageVector, label: String) {
    NavigationBarItem(
        selected = selected,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = label, modifier = Modifier.size(25.dp)) },
        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Purple,
            selectedTextColor = Purple,
            indicatorColor = PurpleLight,
            unselectedIconColor = MutedInk,
            unselectedTextColor = MutedInk
        )
    )
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("回声复读", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Ink)
                Text("把长音频变成容易掌握的小段落", color = MutedInk, fontSize = 14.sp)
            }
            Surface(shape = CircleShape, color = YellowLight, modifier = Modifier.size(52.dp)) {
                Box(contentAlignment = Alignment.Center) { HeadphonesSymbol(28.dp, Coral) }
            }
        }
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(
                onClick = onFiles,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = ControlShape,
                colors = ButtonDefaults.buttonColors(containerColor = Purple)
            ) {
                UploadSymbol(20.dp, Color.White)
                Spacer(Modifier.width(7.dp))
                Text("批量导入文件", fontWeight = FontWeight.Bold)
            }
            OutlinedButton(
                onClick = onFolder,
                modifier = Modifier.weight(1f).height(50.dp),
                shape = ControlShape,
                border = androidx.compose.foundation.BorderStroke(1.3.dp, Coral),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Coral)
            ) {
                FolderSymbol(20.dp, Coral)
                Spacer(Modifier.width(7.dp))
                Text("导入文件夹", fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("学习列表", fontWeight = FontWeight.Black, color = Ink, fontSize = 18.sp)
            Spacer(Modifier.width(8.dp))
            Surface(shape = CircleShape, color = MintLight) {
                Text("${tracks.size}", Modifier.padding(horizontal = 9.dp, vertical = 3.dp), color = Mint, fontWeight = FontWeight.Black, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        if (tracks.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                shape = CardShape,
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.dp, SoftBorder)
            ) {
                Column(Modifier.padding(34.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Surface(shape = CircleShape, color = SkyLight, modifier = Modifier.size(76.dp)) {
                        Box(contentAlignment = Alignment.Center) { MusicSymbol(38.dp, Sky) }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("还没有音频", fontWeight = FontWeight.Black, fontSize = 19.sp, color = Ink)
                    Spacer(Modifier.height(6.dp))
                    Text("同时选择 MP3 和 SRT，文件名相近时会自动匹配", color = MutedInk, textAlign = TextAlign.Center, lineHeight = 20.sp, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 18.dp)) {
                items(tracks, key = { it.id }) { track -> TrackCard(track, { onOpen(track) }, { onDelete(track) }) }
            }
        }
    }
}

@Composable
private fun TrackCard(track: TrackEntity, onOpen: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val hasSubtitle = track.subtitleUri != null
    val badgeColor = if (hasSubtitle) MintLight else CoralLight
    val badgeInk = if (hasSubtitle) Mint else Coral
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onOpen),
        color = Color.White,
        shape = CardShape,
        border = androidx.compose.foundation.BorderStroke(1.dp, SoftBorder),
        shadowElevation = 1.dp
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(50.dp), shape = RoundedCornerShape(16.dp), color = badgeColor) {
                Box(contentAlignment = Alignment.Center) { MusicSymbol(27.dp, badgeInk) }
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.Black, color = Ink, maxLines = 2, overflow = TextOverflow.Ellipsis, lineHeight = 20.sp)
                Spacer(Modifier.height(3.dp))
                Text("${formatTime(track.durationMs)} · ${if (hasSubtitle) "字幕已匹配" else "固定时长分段"}", color = MutedInk, fontSize = 13.sp)
                if (track.currentSegment > 0) Text("上次学到第 ${track.currentSegment + 1} 段", color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Box {
                IconButton(onClick = { showMenu = true }, colors = IconButtonDefaults.iconButtonColors(contentColor = MutedInk)) {
                    Icon(Icons.Rounded.MoreVert, contentDescription = "${track.title} 的更多操作")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("从播放列表移除", color = Coral, fontWeight = FontWeight.Bold) },
                        onClick = { showMenu = false; confirmDelete = true }
                    )
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            shape = CardShape,
            title = { Text("确认移除音频？", fontWeight = FontWeight.Black) },
            text = { Text("“${track.title}”将从播放列表中移除。手机中的原音频和字幕文件不会被删除。") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("确认移除", color = Coral, fontWeight = FontWeight.Bold) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("取消") } }
        )
    }
}

@Composable
private fun PlayerScreen(
    title: String,
    state: PlaybackSnapshot,
    locatorSubtitles: List<SubtitleSnapshot>,
    onCommand: (String, Long?) -> Unit,
    onSeekAbsolute: (Long) -> Unit,
    onToggleBookmark: (Int) -> Unit,
    onToggleBookmarks: (List<Int>) -> Unit,
    onTimer: (Int) -> Unit,
    onLibrary: () -> Unit
) {
    var showTimer by remember { mutableStateOf(false) }
    var showSegments by remember { mutableStateOf(false) }
    var subtitlesExpanded by remember { mutableStateOf(false) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var segmentDrag by remember { mutableStateOf<Float?>(null) }
    var totalDrag by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(state.sleepDeadlineMs) {
        while (state.sleepDeadlineMs > 0) { now = System.currentTimeMillis(); delay(1000) }
    }
    val segmentDuration = state.segmentDurationMs.coerceAtLeast(1)
    val relative = state.segmentPositionMs.coerceIn(0, segmentDuration)
    val totalDuration = state.durationMs.coerceAtLeast(1)

    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onLibrary) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回播放列表", tint = Ink) }
            Text(title.ifBlank { "请选择音频" }, Modifier.weight(1f), color = Ink, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            IconButton(onClick = { showTimer = true }) {
                RoundedGlyph(
                    RoundedGlyphKind.ALARM,
                    contentDescription = "睡眠定时",
                    size = 25.dp,
                    tint = Coral
                )
            }
        }
        val subtitleModifier = if (subtitlesExpanded) Modifier.fillMaxWidth().weight(1f) else Modifier.fillMaxWidth().heightIn(min = 150.dp, max = 180.dp)
        SubtitlePanel(state, subtitleModifier, subtitlesExpanded, { subtitlesExpanded = it }, onSeekAbsolute, onToggleBookmark)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            InfoPill("第 ${state.segmentIndex + 1}/${state.segmentCount.coerceAtLeast(1)} 段", PurpleLight, Purple)
            Spacer(Modifier.width(6.dp))
            InfoPill("第 ${state.repeatIndex}/${if (state.repeatCount == 0) "∞" else state.repeatCount} 次", YellowLight, Color(0xFFC38D00))
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { showSegments = true }, enabled = state.segments.isNotEmpty()) {
                RoundedGlyph(RoundedGlyphKind.TARGET, size = 17.dp, tint = Purple)
                Spacer(Modifier.width(4.dp))
                Text("定位片段", color = Purple, fontWeight = FontWeight.Bold)
            }
        }
        if (state.isInSegmentGap) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                InfoPill(if (state.isSegmentGapPaused) "间隔已暂停" else "间隔中", SkyLight, Color(0xFF287EBC))
                Spacer(Modifier.width(7.dp))
                Text(String.format("剩余 %.1f 秒", state.segmentGapRemainingMs / 1000f), color = MutedInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        Surface(Modifier.fillMaxWidth(), shape = CardShape, color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, SoftBorder)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 9.dp)) {
                ProgressBlock("本段进度", segmentDrag ?: relative.toFloat(), segmentDuration.toFloat(), formatTime((segmentDrag ?: relative.toFloat()).toLong()), formatTime(segmentDuration), state.segmentCount > 0, Coral, { segmentDrag = it }) {
                    segmentDrag?.let { onCommand(PlaybackContract.ACTION_SEEK, it.toLong()) }; segmentDrag = null
                }
                Spacer(Modifier.height(4.dp))
                ProgressBlock("总进度", totalDrag ?: state.positionMs.coerceIn(0, totalDuration).toFloat(), totalDuration.toFloat(), formatTime((totalDrag ?: state.positionMs.toFloat()).toLong()), formatTime(state.durationMs), state.durationMs > 0, Purple, { totalDrag = it }) {
                    totalDrag?.let { onSeekAbsolute(it.toLong()) }; totalDrag = null
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { onCommand(PlaybackContract.ACTION_PREVIOUS, null) }, shape = ControlShape, colors = ButtonDefaults.filledTonalButtonColors(containerColor = SkyLight, contentColor = Ink)) {
                RoundedGlyph(RoundedGlyphKind.PREVIOUS, size = 17.dp, tint = Ink)
                Spacer(Modifier.width(4.dp))
                Text("上一段", fontWeight = FontWeight.Bold)
            }
            FilledIconButton(onClick = { onCommand(PlaybackContract.ACTION_TOGGLE, null) }, modifier = Modifier.size(62.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = Coral, contentColor = Color.White)) {
                if (state.isPlaying) PauseSymbol(27.dp, Color.White) else Icon(Icons.Rounded.PlayArrow, contentDescription = "播放", modifier = Modifier.size(34.dp))
            }
            FilledTonalButton(onClick = { onCommand(PlaybackContract.ACTION_NEXT, null) }, shape = ControlShape, colors = ButtonDefaults.filledTonalButtonColors(containerColor = MintLight, contentColor = Ink)) {
                Text("下一段", fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(4.dp))
                RoundedGlyph(RoundedGlyphKind.NEXT, size = 17.dp, tint = Ink)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { onCommand(PlaybackContract.ACTION_RESTART, null) }) {
                Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(19.dp))
                Spacer(Modifier.width(4.dp))
                Text("重播当前段", fontWeight = FontWeight.Bold)
            }
            if (state.sleepDeadlineMs > 0) {
                val left = (state.sleepDeadlineMs - now).coerceAtLeast(0)
                AssistChip(onClick = { showTimer = true }, label = { Text("${formatTime(left)} 后停止") }, colors = AssistChipDefaults.assistChipColors(containerColor = YellowLight))
            }
        }
    }
    if (showTimer) SleepTimerDialog({ showTimer = false }) { onTimer(it); showTimer = false }
    if (showSegments) SegmentPickerDialog(state, locatorSubtitles, { showSegments = false }, onSeekAbsolute, onToggleBookmarks)
}

@Composable
private fun SubtitlePanel(state: PlaybackSnapshot, modifier: Modifier, expanded: Boolean, onExpandedChange: (Boolean) -> Unit, onSubtitleClick: (Long) -> Unit, onToggleBookmark: (Int) -> Unit) {
    val listState = rememberLazyListState()
    var userBrowsing by remember { mutableStateOf(false) }
    var autoScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(listState, expanded) {
        if (!expanded) return@LaunchedEffect
        snapshotFlow { listState.isScrollInProgress }.collectLatest { scrolling ->
            if (scrolling && !autoScrolling) userBrowsing = true
            else if (!scrolling && userBrowsing) { delay(2500); userBrowsing = false }
        }
    }
    LaunchedEffect(state.subtitleIndex, expanded, userBrowsing) {
        if (expanded && !userBrowsing && state.subtitleIndex in state.subtitles.indices) {
            delay(40); autoScrolling = true
            try {
                val viewport = listState.layoutInfo.viewportSize.height
                val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == state.subtitleIndex }?.size ?: 84
                listState.animateScrollToItem(state.subtitleIndex, -((viewport - itemHeight) / 2).coerceAtLeast(0))
            } finally { autoScrolling = false }
        }
    }
    Surface(modifier = modifier, shape = CardShape, color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, SoftBorder)) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (expanded) "全部字幕" else "当前字幕", Modifier.weight(1f), color = MutedInk, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                state.subtitles.getOrNull(state.subtitleIndex)?.let { cue ->
                    IconButton(onClick = { onToggleBookmark(cue.cueId) }) {
                        RoundedGlyph(RoundedGlyphKind.BOOKMARK, contentDescription = if (cue.bookmarked) "取消当前字幕书签" else "为当前字幕添加书签", tint = if (cue.bookmarked) Coral else MutedInk)
                    }
                }
                if (state.subtitles.isNotEmpty()) {
                    TextButton(onClick = { onExpandedChange(!expanded) }) {
                        Text(if (expanded) "收起" else "展开字幕", color = Purple, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Icon(if (expanded) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = Purple)
                    }
                }
            }
            if (state.subtitles.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp, vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Text(state.subtitle.ifBlank { "当前音频暂无字幕" }, color = Ink, fontSize = 19.sp, lineHeight = 27.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }
            } else if (!expanded) {
                Box(Modifier.fillMaxWidth().weight(1f).padding(horizontal = 20.dp, vertical = 5.dp), contentAlignment = Alignment.Center) {
                    Text(state.subtitle.ifBlank { "字幕即将开始…" }, color = Ink, fontSize = 20.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            } else {
                if (userBrowsing) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { userBrowsing = false }) { Text("回到当前字幕", color = Purple, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                LazyColumn(Modifier.fillMaxWidth().weight(1f), state = listState, contentPadding = PaddingValues(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    itemsIndexed(state.subtitles) { index, cue ->
                        val selected = index == state.subtitleIndex
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                                .background(if (selected) PurpleLight else Color.Transparent, RoundedCornerShape(14.dp))
                                .border(if (selected) 1.3.dp else 0.dp, if (selected) Purple else Color.Transparent, RoundedCornerShape(14.dp))
                                .clickable { onSubtitleClick(cue.startMs) }.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(formatTime(cue.startMs), color = if (selected) Purple else MutedInk, fontSize = 11.sp)
                                Text(cue.text, color = Ink, fontSize = if (selected) 19.sp else 17.sp, lineHeight = 26.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                            }
                            IconButton(onClick = { onToggleBookmark(cue.cueId) }) {
                                RoundedGlyph(RoundedGlyphKind.BOOKMARK, contentDescription = if (cue.bookmarked) "取消字幕书签" else "为字幕添加书签", tint = if (cue.bookmarked) Coral else MutedInk)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProgressBlock(label: String, value: Float, maximum: Float, leftText: String, rightText: String, enabled: Boolean, accent: Color, onValueChange: (Float) -> Unit, onFinished: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, color = Ink, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text("$leftText / $rightText", color = MutedInk, fontSize = 12.sp)
        }
        Slider(value = value.coerceIn(0f, maximum.coerceAtLeast(1f)), onValueChange = onValueChange, onValueChangeFinished = onFinished, valueRange = 0f..maximum.coerceAtLeast(1f), enabled = enabled, modifier = Modifier.height(27.dp), colors = SliderDefaults.colors(thumbColor = accent, activeTrackColor = accent, inactiveTrackColor = accent.copy(alpha = .18f)))
    }
}

@Composable
private fun SegmentPickerDialog(
    state: PlaybackSnapshot,
    locatorSubtitles: List<SubtitleSnapshot>,
    onDismiss: () -> Unit,
    onSelect: (Long) -> Unit,
    onToggleBookmarks: (List<Int>) -> Unit
) {
    var bookmarkedOnly by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    val allRows = if (locatorSubtitles.isNotEmpty()) {
        locatorSubtitles.mapIndexed { index, cue ->
            SegmentPickerRow(
                key = "cue-${cue.cueId}",
                number = index + 1,
                startMs = cue.startMs,
                endMs = cue.endMs,
                text = cue.text,
                cueIds = listOf(cue.cueId),
                bookmarked = cue.bookmarked,
                selected = index == state.subtitleIndex
            )
        }
    } else {
        state.segments.mapIndexed { index, segment ->
            SegmentPickerRow(
                key = "segment-$index",
                number = index + 1,
                startMs = segment.startMs,
                endMs = segment.endMs,
                text = segment.text,
                cueIds = emptyList(),
                bookmarked = false,
                selected = index == state.segmentIndex
            )
        }
    }
    val rows = allRows.filter { row ->
        (!bookmarkedOnly || row.bookmarked) && SubtitleSearch.matches(row.text, searchQuery)
    }
    val initial = rows.indexOfFirst { it.selected }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val scope = rememberCoroutineScope()
    LaunchedEffect(bookmarkedOnly, searchQuery) {
        val target = rows.indexOfFirst { it.selected }.coerceAtLeast(0)
        if (rows.isNotEmpty()) listState.scrollToItem(target.coerceAtMost(rows.lastIndex))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CardShape,
        title = { Text("跳转到指定片段", fontWeight = FontWeight.Black) },
        text = {
            Column {
                if (locatorSubtitles.isNotEmpty()) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        singleLine = true,
                        label = { Text("搜索字幕") },
                        placeholder = { Text("输入台词或关键词") },
                        leadingIcon = {
                            Icon(Icons.Rounded.Search, contentDescription = null, tint = Purple)
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Rounded.Close, contentDescription = "清空字幕搜索")
                                }
                            }
                        },
                        shape = ControlShape
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !bookmarkedOnly,
                        onClick = { bookmarkedOnly = false },
                        label = { Text(if (locatorSubtitles.isNotEmpty()) "全部字幕" else "全部片段") },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = bookmarkedOnly,
                        onClick = { bookmarkedOnly = true },
                        label = { Text("仅看书签") },
                        leadingIcon = { RoundedGlyph(RoundedGlyphKind.BOOKMARK, size = 16.dp, tint = if (bookmarkedOnly) Purple else MutedInk) },
                        modifier = Modifier.weight(1f)
                    )
                }
                if (rows.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(180.dp), contentAlignment = Alignment.Center) {
                        Text(
                            when {
                                searchQuery.isNotBlank() && bookmarkedOnly -> "书签字幕中没有找到相关台词"
                                searchQuery.isNotBlank() -> "没有找到相关字幕"
                                bookmarkedOnly -> "当前音频还没有字幕书签"
                                else -> "当前音频没有可定位的片段"
                            },
                            color = MutedInk
                        )
                    }
                } else Row(Modifier.fillMaxWidth().heightIn(max = 460.dp)) {
                    LazyColumn(Modifier.weight(1f), state = listState, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                items(rows, key = { it.key }) { row ->
                    Row(
                        Modifier.fillMaxWidth().background(if (row.selected) PurpleLight else Color(0xFFF8F6F4), RoundedCornerShape(13.dp))
                            .border(if (row.selected) 1.3.dp else 0.dp, if (row.selected) Purple else Color.Transparent, RoundedCornerShape(13.dp))
                            .clickable { onSelect(row.startMs); onDismiss() }.padding(start = 12.dp, end = 4.dp, top = 7.dp, bottom = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("第 ${row.number} 条 · ${formatTime(row.startMs)}–${formatTime(row.endMs)}", color = if (row.selected) Purple else Ink, fontWeight = FontWeight.Bold)
                            if (row.text.isNotBlank()) Text(row.text, maxLines = 2, overflow = TextOverflow.Ellipsis, color = MutedInk, fontSize = 13.sp)
                            if (row.cueIds.isEmpty()) Text("这一段没有字幕，不能添加字幕书签", color = MutedInk, fontSize = 11.sp)
                        }
                        if (row.cueIds.isNotEmpty()) {
                            IconButton(onClick = { onToggleBookmarks(row.cueIds) }) {
                                RoundedGlyph(
                                    RoundedGlyphKind.BOOKMARK,
                                    contentDescription = if (row.bookmarked) "取消第 ${row.number} 条字幕书签" else "为第 ${row.number} 条字幕添加书签",
                                    tint = if (row.bookmarked) Coral else MutedInk
                                )
                            }
                        }
                    }
                }
                    }
                    FastScrollBar(
                        itemCount = rows.size,
                        firstVisibleItem = listState.firstVisibleItemIndex,
                        onScrollToItem = { scope.launch { listState.scrollToItem(it) } }
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("关闭") } }
    )
}

@Composable
private fun FastScrollBar(itemCount: Int, firstVisibleItem: Int, onScrollToItem: (Int) -> Unit) {
    var heightPx by remember { mutableIntStateOf(1) }
    val thumbHeightPx = with(androidx.compose.ui.platform.LocalDensity.current) { 48.dp.roundToPx() }
    val maxIndex = (itemCount - 1).coerceAtLeast(0)
    val travelPx = (heightPx - thumbHeightPx).coerceAtLeast(0)
    val thumbY = if (maxIndex == 0) 0 else (travelPx * firstVisibleItem.toFloat() / maxIndex).toInt()
    fun indexFor(y: Float): Int = if (travelPx == 0) 0 else
        ((y - thumbHeightPx / 2f).coerceIn(0f, travelPx.toFloat()) / travelPx * maxIndex).toInt()

    Box(
        Modifier.width(24.dp).fillMaxHeight().semantics { contentDescription = "片段快速滚动条" }
            .onSizeChanged { heightPx = it.height }
            .pointerInput(itemCount, heightPx) {
                detectDragGestures(
                    onDragStart = { onScrollToItem(indexFor(it.y)) },
                    onDrag = { change, _ -> onScrollToItem(indexFor(change.position.y)) }
                )
            }
    ) {
        Box(Modifier.align(Alignment.Center).width(4.dp).fillMaxHeight().background(PurpleLight, CircleShape))
        Box(
            Modifier.align(Alignment.TopCenter).offset { IntOffset(0, thumbY) }
                .size(width = 10.dp, height = 48.dp).background(Purple, CircleShape)
        )
    }
}

@Composable
private fun SleepTimerDialog(onDismiss: () -> Unit, onSelect: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        shape = CardShape,
        title = { Text("睡眠定时器", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(5, 10, 15, 30, 45, 60, 90).chunked(3).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { value -> OutlinedButton(onClick = { onSelect(value) }, modifier = Modifier.weight(1f), shape = ControlShape) { Text("${value}分") } }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onSelect(0) }) { Text("关闭定时") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } }
    )
}


private fun formatSubtitleOffset(offsetMs: Long): String {
    if (offsetMs == 0L) return "不调整"
    val absolute = kotlin.math.abs(offsetMs)
    val seconds = if (absolute % 1_000L == 0L) {
        "${absolute / 1_000L}秒"
    } else {
        "${absolute / 1_000.0}秒"
    }
    return if (offsetMs < 0L) "提前 $seconds" else "延后 $seconds"
}
@Composable
private fun SettingsScreen(
    value: PlaybackSettings,
    currentTrack: TrackEntity?,
    onChange: (PlaybackSettings) -> Unit,
    onSubtitleOffsetChange: (Long) -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("学习设置", fontSize = 29.sp, fontWeight = FontWeight.Black, color = Ink)
            Text("每次调整都会立即应用到当前音频", color = MutedInk, fontSize = 14.sp)
        }
        item {
            val enabled = currentTrack?.subtitleUri != null
            val offsetMs = currentTrack?.subtitleOffsetMs ?: 0L
            SettingCard(
                "字幕同步校准",
                RoundedGlyphKind.SYNC,
                Color(0xFF287EBC),
                SkyLight,
                enabled,
                if (enabled) "字幕比声音早请选择延后，字幕比声音晚请选择提前" else "请先打开一个带字幕的音频"
            ) {
                Text(
                    "当前：${formatSubtitleOffset(offsetMs)}",
                    color = if (offsetMs == 0L) MutedInk else Purple,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(8.dp))
                ChoiceGrid(
                    listOf(
                        "提前2秒" to -2_000L,
                        "提前1秒" to -1_000L,
                        "提前0.5秒" to -500L,
                        "不调整" to 0L,
                        "延后0.5秒" to 500L,
                        "延后1秒" to 1_000L,
                        "延后2秒" to 2_000L
                    ),
                    offsetMs,
                    enabled
                ) { onSubtitleOffsetChange(it) }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { onSubtitleOffsetChange((offsetMs - 100L).coerceAtLeast(-10_000L)) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        shape = ControlShape
                    ) { Text("提前0.1秒", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { onSubtitleOffsetChange(0L) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        shape = ControlShape
                    ) { Text("恢复为0", fontSize = 12.sp) }
                    OutlinedButton(
                        onClick = { onSubtitleOffsetChange((offsetMs + 100L).coerceAtMost(10_000L)) },
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                        shape = ControlShape
                    ) { Text("延后0.1秒", fontSize = 12.sp) }
                }
            }
        }
        item { SettingCard("分段方式", RoundedGlyphKind.CUT, Purple, PurpleLight) { ChoiceGrid(listOf("固定时长" to SegmentMode.FIXED, "按字幕" to SegmentMode.SUBTITLE), value.segmentMode) { onChange(value.copy(segmentMode = it)) } } }
        item {
            val enabled = value.segmentMode == SegmentMode.SUBTITLE
            SettingCard(
                "字幕播放范围",
                RoundedGlyphKind.SUBTITLES,
                Pink,
                PinkLight,
                enabled,
                if (enabled) "可跳过字幕之间没有文字的原音频" else "仅在按字幕分段时可设置"
            ) {
                ChoiceGrid(
                    listOf(
                        "播放完整时间线" to SubtitlePlaybackScope.FULL_TIMELINE,
                        "仅播放字幕片段" to SubtitlePlaybackScope.CUES_ONLY,
                        "仅播放书签字幕" to SubtitlePlaybackScope.BOOKMARKED_CUES
                    ),
                    value.subtitlePlaybackScope,
                    enabled
                ) { onChange(value.copy(subtitlePlaybackScope = it)) }
            }
        }
        if (
            value.segmentMode == SegmentMode.SUBTITLE &&
            value.subtitlePlaybackScope != SubtitlePlaybackScope.FULL_TIMELINE
        ) {
            item {
                SettingCard(
                    "字幕前置补偿",
                    RoundedGlyphKind.LEAD_IN,
                    Coral,
                    CoralLight,
                    hint = "在字幕时间点前提前播放，避免句首辅音被切掉"
                ) {
                    ChoiceGrid(
                        listOf("0ms" to 0L, "0.2秒" to 200L, "0.3秒" to 300L, "0.5秒" to 500L, "0.8秒" to 800L),
                        value.leadInMs
                    ) { onChange(value.copy(leadInMs = it)) }
                }
            }
            item {
                SettingCard(
                    "字幕后置补偿",
                    RoundedGlyphKind.LEAD_OUT,
                    Color(0xFF287EBC),
                    SkyLight,
                    hint = "在字幕结束后继续播放，保留句尾与自然收音"
                ) {
                    ChoiceGrid(
                        listOf("0ms" to 0L, "0.2秒" to 200L, "0.3秒" to 300L, "0.5秒" to 500L, "0.8秒" to 800L),
                        value.leadOutMs
                    ) { onChange(value.copy(leadOutMs = it)) }
                }
            }
        }
        item {
            val enabled = value.segmentMode == SegmentMode.FIXED
            SettingCard("每段时长", RoundedGlyphKind.TIMER, Coral, CoralLight, enabled, if (!enabled) "按字幕分段时，片段长度由字幕时间轴决定" else null) {
                ChoiceGrid(listOf("5秒" to 5, "10秒" to 10, "15秒" to 15, "20秒" to 20, "30秒" to 30), value.segmentSeconds, enabled) { onChange(value.copy(segmentSeconds = it)) }
            }
        }
        item { SettingCard("每段重复", RoundedGlyphKind.REPEAT, Mint, MintLight) { ChoiceGrid(listOf("1次" to 1, "3次" to 3, "5次" to 5, "10次" to 10, "无限" to 0), value.repeatCount) { onChange(value.copy(repeatCount = it)) } } }
        item { SettingCard("分段间隔", RoundedGlyphKind.GAP, Color(0xFF287EBC), SkyLight, hint = "仅在同一片段的多次重复之间保留静音间隔") { ChoiceGrid(listOf("无间隔" to 0L, "0.5秒" to 500L, "1秒" to 1_000L, "2秒" to 2_000L, "3秒" to 3_000L, "5秒" to 5_000L), value.segmentGapMs) { onChange(value.copy(segmentGapMs = it)) } } }
        item { SettingCard("播放速度", RoundedGlyphKind.SPEED, Sky, SkyLight) { ChoiceGrid(listOf("0.75x" to .75f, "1.0x" to 1f, "1.25x" to 1.25f, "1.5x" to 1.5f, "2.0x" to 2f), value.speed) { onChange(value.copy(speed = it)) } } }
        item { SettingCard("列表播放", RoundedGlyphKind.QUEUE, Pink, PinkLight) { ChoiceGrid(listOf("单曲停止" to PlaylistMode.STOP_AFTER_TRACK, "单曲循环" to PlaylistMode.LOOP_TRACK, "顺序播放" to PlaylistMode.SEQUENTIAL, "列表循环" to PlaylistMode.LOOP_LIST), value.playlistMode) { onChange(value.copy(playlistMode = it)) } } }
        item { SettingCard("定时到点", RoundedGlyphKind.BEDTIME, Color(0xFFC38D00), YellowLight) { ChoiceGrid(listOf("当前段结束" to true, "立即停止" to false), value.stopAtSegmentEnd) { onChange(value.copy(stopAtSegmentEnd = it)) } } }
    }
}

@Composable
private fun SettingCard(title: String, icon: RoundedGlyphKind, accent: Color, background: Color, enabled: Boolean = true, hint: String? = null, content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().alpha(if (enabled) 1f else .5f), shape = CardShape, color = Color.White, border = androidx.compose.foundation.BorderStroke(1.dp, SoftBorder)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = background, modifier = Modifier.size(38.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        RoundedGlyph(icon, size = 22.dp, tint = accent)
                    }
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(title, fontWeight = FontWeight.Black, color = Ink)
                    hint?.let { Text(it, color = MutedInk, fontSize = 12.sp, lineHeight = 16.sp) }
                }
            }
            Spacer(Modifier.height(11.dp))
            content()
        }
    }
}

@Composable
private fun <T> ChoiceGrid(options: List<Pair<String, T>>, selectedValue: T, enabled: Boolean = true, onSelect: (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        options.chunked(3).forEach { rowOptions ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowOptions.forEach { (label, option) ->
                    val selected = SelectionLogic.isSelected(selectedValue, option)
                    Surface(
                        modifier = Modifier.weight(1f).heightIn(min = 43.dp).clickable(enabled = enabled) { onSelect(option) },
                        shape = RoundedCornerShape(13.dp),
                        color = if (selected) PurpleLight else Color(0xFFFBFAF9),
                        border = androidx.compose.foundation.BorderStroke(if (selected) 1.5.dp else 1.dp, if (selected) Purple else SoftBorder)
                    ) {
                        Box(Modifier.padding(horizontal = 7.dp, vertical = 9.dp), contentAlignment = Alignment.Center) {
                            Text(label, color = if (selected) Purple else Ink, fontSize = 13.sp, textAlign = TextAlign.Center, fontWeight = if (selected) FontWeight.Black else FontWeight.Medium)
                        }
                    }
                }
                repeat(3 - rowOptions.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun InfoPill(text: String, background: Color, foreground: Color) {
    Surface(shape = CircleShape, color = background) { Text(text, Modifier.padding(horizontal = 9.dp, vertical = 5.dp), color = foreground, fontSize = 12.sp, fontWeight = FontWeight.Black) }
}

@Composable
private fun PauseSymbol(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val bar = size.toPx() * .22f
        val gap = size.toPx() * .14f
        val left = (this.size.width - bar * 2 - gap) / 2f
        drawRoundRect(color, Offset(left, size.toPx() * .2f), androidx.compose.ui.geometry.Size(bar, size.toPx() * .6f), androidx.compose.ui.geometry.CornerRadius(bar / 3f))
        drawRoundRect(color, Offset(left + bar + gap, size.toPx() * .2f), androidx.compose.ui.geometry.Size(bar, size.toPx() * .6f), androidx.compose.ui.geometry.CornerRadius(bar / 3f))
    }
}

@Composable
private fun MusicSymbol(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val stroke = Stroke(2.1f * s, cap = StrokeCap.Round)
        drawLine(color, Offset(10f*s, 5f*s), Offset(19f*s, 3f*s), strokeWidth = 2.1f*s, cap = StrokeCap.Round)
        drawLine(color, Offset(10f*s, 5f*s), Offset(10f*s, 16f*s), strokeWidth = 2.1f*s, cap = StrokeCap.Round)
        drawLine(color, Offset(19f*s, 3f*s), Offset(19f*s, 14f*s), strokeWidth = 2.1f*s, cap = StrokeCap.Round)
        drawLine(color, Offset(10f*s, 9f*s), Offset(19f*s, 7f*s), strokeWidth = 2.1f*s, cap = StrokeCap.Round)
        drawCircle(color, 3.2f*s, Offset(7f*s, 17f*s), style = stroke)
        drawCircle(color, 3.2f*s, Offset(16f*s, 15f*s), style = stroke)
    }
}

@Composable
private fun UploadSymbol(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val w = 2.1f*s
        drawLine(color, Offset(12f*s, 16f*s), Offset(12f*s, 5f*s), w, StrokeCap.Round)
        drawLine(color, Offset(12f*s, 5f*s), Offset(8f*s, 9f*s), w, StrokeCap.Round)
        drawLine(color, Offset(12f*s, 5f*s), Offset(16f*s, 9f*s), w, StrokeCap.Round)
        drawLine(color, Offset(5f*s, 19f*s), Offset(19f*s, 19f*s), w, StrokeCap.Round)
    }
}

@Composable
private fun FolderSymbol(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val path = androidx.compose.ui.graphics.Path().apply { moveTo(3f*s, 7f*s); lineTo(9f*s, 7f*s); lineTo(11f*s, 9f*s); lineTo(21f*s, 9f*s); lineTo(20f*s, 19f*s); lineTo(4f*s, 19f*s); close() }
        drawPath(path, color, style = Stroke(2.1f*s, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    }
}

@Composable
private fun HeadphonesSymbol(size: Dp, color: Color) {
    Canvas(Modifier.size(size)) {
        val s = this.size.minDimension / 24f
        val stroke = Stroke(2.2f*s, cap = StrokeCap.Round)
        drawArc(color, 200f, 140f, false, Offset(4f*s, 3f*s), androidx.compose.ui.geometry.Size(16f*s, 16f*s), style = stroke)
        drawRoundRect(color, Offset(3f*s, 12f*s), androidx.compose.ui.geometry.Size(5f*s, 8f*s), androidx.compose.ui.geometry.CornerRadius(2f*s), style = stroke)
        drawRoundRect(color, Offset(16f*s, 12f*s), androidx.compose.ui.geometry.Size(5f*s, 8f*s), androidx.compose.ui.geometry.CornerRadius(2f*s), style = stroke)
    }
}
