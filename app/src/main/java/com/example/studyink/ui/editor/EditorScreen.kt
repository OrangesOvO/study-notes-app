package com.example.studyink.ui.editor

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.studyink.StudyInkApp
import com.example.studyink.data.NoteRepository
import com.example.studyink.model.CanvasSpec
import com.example.studyink.model.NoteType
import com.example.studyink.model.PageBackground
import com.example.studyink.model.Tool
import com.example.studyink.pdf.PdfExporter
import com.example.studyink.pdf.PdfManager
import com.example.studyink.ui.theme.Blue
import com.example.studyink.ui.theme.BorderGray
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

private fun backgroundOf(name: String?): PageBackground =
    PageBackground.values().firstOrNull { it.name == name } ?: PageBackground.BLANK

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    repository: NoteRepository,
    noteId: Long,
    onBack: () -> Unit
) {
    val vm: EditorViewModel = viewModel(
        key = "editor_$noteId",
        factory = EditorViewModel.factory(repository, noteId)
    )
    val note by vm.note.collectAsState()
    val currentPage by vm.currentPage.collectAsState()
    val strokes by vm.strokes.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings = remember { (context.applicationContext as StudyInkApp).settings }

    var showColorPicker by remember { mutableStateOf(false) }
    var showBackgroundPicker by remember { mutableStateOf(false) }
    var showPagePicker by remember { mutableStateOf(false) }
    var showRename by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }
    var stylusOnly by remember { mutableStateOf(settings.stylusOnly) }
    var fingerHintShown by remember { mutableStateOf(false) }

    var baseBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var canvasW by remember { mutableStateOf(CanvasSpec.A4_WIDTH) }
    var canvasH by remember { mutableStateOf(CanvasSpec.A4_HEIGHT) }

    LaunchedEffect(note?.pdfPath, currentPage) {
        val n = note ?: return@LaunchedEffect
        val (cw, ch) = PdfManager.canvasSizeFor(n.type, n.pdfPath, currentPage)
        canvasW = cw
        canvasH = ch
        baseBitmap = withContext(Dispatchers.IO) {
            if (n.type == NoteType.PDF.name && n.pdfPath != null) {
                PdfManager.renderPageExact(n.pdfPath, currentPage, cw, ch)
            } else null
        }
    }

    val background = backgroundOf(note?.background)

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf")
    ) { uri ->
        if (uri != null) {
            val n = note
            if (n != null) {
                scope.launch {
                    exporting = true
                    val all = withContext(Dispatchers.IO) { repository.getAllStrokes(n.id) }
                    val ok = PdfExporter.export(context, n, all.groupBy { it.pageIndex }, uri)
                    exporting = false
                    Toast.makeText(
                        context,
                        if (ok) "已导出 PDF" else "导出失败",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = note?.title ?: "",
                        modifier = Modifier.clickable { showRename = true },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { vm.undo() },
                        enabled = vm.canUndo,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.45f)
                        )
                    ) { Text("撤销") }
                    TextButton(
                        onClick = { vm.redo() },
                        enabled = vm.canRedo,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.45f)
                        )
                    ) { Text("重做") }
                    TextButton(
                        onClick = {
                            val n = note
                            if (n != null) exportLauncher.launch("${n.title}.pdf")
                        },
                        enabled = !exporting,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.45f)
                        )
                    ) { Text(if (exporting) "导出中…" else "导出") }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Blue,
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F6FA))
                    .navigationBarsPadding()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Tool.values().forEach { t ->
                        ToolChip(t.label, vm.tool == t) { vm.selectTool(t) }
                    }
                }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("粗细", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = vm.penWidth,
                        onValueChange = { vm.updatePenWidth(it) },
                        valueRange = 2f..48f,
                        modifier = Modifier.width(150.dp)
                    )

                    Box(
                        Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(vm.penColor)
                            .border(2.dp, Color.White, CircleShape)
                            .clickable { showColorPicker = true }
                    )

                    Spacer(Modifier.weight(1f))

                    if (note?.type == NoteType.BLANK.name) {
                        ToolChip("底纹", false) { showBackgroundPicker = true }
                    }

                    ToolChip(
                        label = if (stylusOnly) "仅笔书写 ✓" else "仅笔书写",
                        selected = stylusOnly,
                        onClick = {
                            stylusOnly = !stylusOnly
                            settings.stylusOnly = stylusOnly
                        }
                    )
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            val n = note
            if (n == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                DrawingCanvas(
                    canvasWidth = canvasW,
                    canvasHeight = canvasH,
                    background = background,
                    baseBitmap = baseBitmap,
                    strokes = strokes,
                    tool = vm.tool,
                    penColor = vm.penColor,
                    penWidth = vm.penWidth,
                    stylusOnly = stylusOnly,
                    onStrokeEnd = { vm.addStroke(it) },
                    onEraseStroke = { vm.eraseStroke(it) },
                    onFingerRejected = {
                        if (!fingerHintShown) {
                            fingerHintShown = true
                            Toast.makeText(
                                context,
                                "仅触控笔模式：手指输入已忽略。若你的笔被识别成手指，请关掉该开关",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (n.pageCount > 1 || n.type == NoteType.BLANK.name) {
                    PageNavBar(
                        current = currentPage,
                        total = n.pageCount,
                        canEditPages = n.type == NoteType.BLANK.name,
                        onPrev = { if (currentPage > 0) vm.setPage(currentPage - 1) },
                        onNext = { if (currentPage < n.pageCount - 1) vm.setPage(currentPage + 1) },
                        onPickPage = { showPagePicker = true },
                        onAddPage = { vm.addPage() },
                        onDeletePage = { vm.deleteCurrentPage() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 12.dp)
                    )
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initial = vm.penColor,
            recentColors = vm.recentColors,
            onDismiss = { showColorPicker = false },
            onConfirm = {
                vm.updatePenColor(it)
                showColorPicker = false
            }
        )
    }

    if (showBackgroundPicker) {
        BackgroundPickerDialog(
            current = background,
            onPick = { vm.applyBackground(it) },
            onDismiss = { showBackgroundPicker = false }
        )
    }

    if (showPagePicker) {
        PagePickerDialog(
            total = note?.pageCount ?: 1,
            current = currentPage,
            onPick = { vm.setPage(it) },
            onDismiss = { showPagePicker = false }
        )
    }

    if (showRename) {
        RenameDialog(
            initial = note?.title ?: "",
            onDismiss = { showRename = false },
            onConfirm = {
                vm.rename(it)
                showRename = false
            }
        )
    }
}

@Composable
private fun ToolChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (selected) Blue else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) Blue else Color(0xFFCCCCCC),
                shape = RoundedCornerShape(8.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color(0xFF333333),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun PageNavBar(
    current: Int,
    total: Int,
    canEditPages: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onPickPage: () -> Unit,
    onAddPage: () -> Unit,
    onDeletePage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xE6FFFFFF))
            .border(1.dp, BorderGray, RoundedCornerShape(20.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onPrev, enabled = current > 0) { Text("上一页") }
        TextButton(onClick = onPickPage) {
            Text("${current + 1} / $total", style = MaterialTheme.typography.bodyMedium)
        }
        TextButton(onClick = onNext, enabled = current < total - 1) { Text("下一页") }
        if (canEditPages) {
            TextButton(onClick = onAddPage) { Text("＋加页") }
            TextButton(onClick = onDeletePage, enabled = total > 1) { Text("删本页") }
        }
    }
}

@Composable
private fun PagePickerDialog(
    total: Int,
    current: Int,
    onPick: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("跳转到页码") },
        text = {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 56.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(count = total) { idx ->
                    val selected = idx == current
                    Box(
                        Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) Blue else Color(0xFFF0F1F5))
                            .clickable {
                                onPick(idx)
                                onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "${idx + 1}",
                            color = if (selected) Color.White else Color(0xFF333333)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun BackgroundPickerDialog(
    current: PageBackground,
    onPick: (PageBackground) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("页面底纹") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PageBackground.values().forEach { bg ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (bg == current) Color(0xFFE8EAF6) else Color.Transparent)
                            .clickable {
                                onPick(bg)
                                onDismiss()
                            }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Canvas(
                            Modifier
                                .size(44.dp, 58.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                        ) {
                            val logicalW = 400f
                            val logicalH = 520f
                            val s = min(size.width / logicalW, size.height / logicalH)
                            withTransform({
                                scale(s, s, pivot = Offset.Zero)
                            }) {
                                drawRect(
                                    color = Color.White,
                                    topLeft = Offset.Zero,
                                    size = Size(logicalW, logicalH)
                                )
                                drawPageBackground(bg, logicalW, logicalH)
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Text(bg.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("重命名笔记") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                label = { Text("标题") },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
