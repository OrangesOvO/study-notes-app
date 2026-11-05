package com.example.studyink.ui.editor

import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.studyink.data.NoteRepository
import com.example.studyink.model.CanvasSpec
import com.example.studyink.model.NoteType
import com.example.studyink.model.PenType
import com.example.studyink.pdf.PdfExporter
import com.example.studyink.pdf.PdfManager
import com.example.studyink.ui.theme.Blue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    var showColorPicker by remember { mutableStateOf(false) }
    var exporting by remember { mutableStateOf(false) }

    // 当前页底图
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
                title = { Text(note?.title ?: "") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    TextButton(
                        onClick = { vm.undo() },
                        enabled = vm.undoStack.isNotEmpty(),
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color.White,
                            disabledContentColor = Color.White.copy(alpha = 0.45f)
                        )
                    ) { Text("撤销") }
                    TextButton(
                        onClick = { vm.redo() },
                        enabled = vm.redoStack.isNotEmpty(),
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
                    ) { Text("导出") }
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
            ToolBar(
                penType = vm.penType,
                penColor = vm.penColor,
                onPenTypeChange = { vm.setPenType(it) },
                onColorClick = { showColorPicker = true }
            )
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (note == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            } else {
                DrawingCanvas(
                    canvasWidth = canvasW,
                    canvasHeight = canvasH,
                    baseBitmap = baseBitmap,
                    strokes = strokes,
                    penType = vm.penType,
                    penColor = vm.penColor,
                    penWidth = vm.penWidth(),
                    onStrokeEnd = { vm.addStroke(it) },
                    modifier = Modifier.fillMaxSize()
                )

                val n = note!!
                if (n.pageCount > 1) {
                    PageNavBar(
                        current = currentPage,
                        total = n.pageCount,
                        onPrev = { if (currentPage > 0) vm.setPage(currentPage - 1) },
                        onNext = {
                            if (currentPage < n.pageCount - 1) vm.setPage(currentPage + 1)
                        },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 16.dp)
                    )
                }
            }
        }
    }

    if (showColorPicker) {
        ColorPickerDialog(
            initial = vm.penColor,
            onDismiss = { showColorPicker = false },
            onConfirm = {
                vm.setPenColor(it)
                showColorPicker = false
            }
        )
    }
}

@Composable
private fun ToolBar(
    penType: PenType,
    penColor: Color,
    onPenTypeChange: (PenType) -> Unit,
    onColorClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color(0xFFF5F6FA))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        PenButton("钢笔", penType == PenType.PEN) { onPenTypeChange(PenType.PEN) }
        PenButton("马克笔", penType == PenType.MARKER) { onPenTypeChange(PenType.MARKER) }
        PenButton("荧光笔", penType == PenType.HIGHLIGHTER) { onPenTypeChange(PenType.HIGHLIGHTER) }

        Spacer(Modifier.weight(1f))

        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(penColor)
                .border(2.dp, Color.White, CircleShape)
                .clickable(onClick = onColorClick)
        )
    }
}

@Composable
private fun PenButton(label: String, selected: Boolean, onClick: () -> Unit) {
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
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Text(
            label,
            color = if (selected) Color.White else Color(0xFF333333),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun PageNavBar(
    current: Int,
    total: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Color(0xCCFFFFFF))
            .border(1.dp, Color(0xFFE0E0E0), RoundedCornerShape(20.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onPrev, enabled = current > 0) { Text("上一页") }
        Text(
            "${current + 1} / $total",
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFF333333)
        )
        TextButton(onClick = onNext, enabled = current < total - 1) { Text("下一页") }
    }
}
