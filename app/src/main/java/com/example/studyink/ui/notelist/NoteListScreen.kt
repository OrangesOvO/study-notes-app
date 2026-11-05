package com.example.studyink.ui.notelist

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.studyink.data.NoteEntity
import com.example.studyink.data.NoteRepository
import com.example.studyink.model.NoteType
import com.example.studyink.pdf.PdfManager
import com.example.studyink.ui.theme.Blue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteListScreen(
    repository: NoteRepository,
    onOpenNote: (Long) -> Unit
) {
    val vm: NoteListViewModel = viewModel(factory = NoteListViewModel.factory(repository))
    val notes by vm.notes.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var importing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<NoteEntity?>(null) }

    val openDoc = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                importing = true
                try {
                    val (filePath, pageCount) = withContext(Dispatchers.IO) {
                        PdfManager.importPdf(context, uri)
                    }
                    val title = withContext(Dispatchers.IO) {
                        PdfManager.displayName(context, uri).removeSuffix(".pdf")
                    }.ifBlank { "导入的PDF" }
                    val id = withContext(Dispatchers.IO) {
                        repository.createPdfNote(title, filePath, pageCount)
                    }
                    onOpenNote(id)
                } catch (e: Exception) {
                    errorMsg = "导入失败：${e.message}"
                } finally {
                    importing = false
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("StudyInk 学习笔记") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Blue,
                    titleContentColor = Color.White
                )
            )
        },
        bottomBar = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF5F6FA))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        scope.launch {
                            val id = withContext(Dispatchers.IO) {
                                repository.createBlankNote("空白笔记")
                            }
                            onOpenNote(id)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = Blue
                    )
                ) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("新建空白笔记")
                }
                OutlinedButton(
                    onClick = { openDoc.launch(arrayOf("application/pdf")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("导入 PDF")
                }
            }
        }
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (notes.isEmpty()) {
                Column(
                    Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("还没有笔记", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "点下方「新建空白笔记」，或「导入 PDF」开始",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.Gray
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 160.dp),
                    contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(notes, key = { it.id }) { note ->
                        NoteCard(
                            note = note,
                            onClick = { onOpenNote(note.id) },
                            onDelete = { deleteTarget = note }
                        )
                    }
                }
            }

            if (importing) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color(0x66000000)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        Text("正在导入 PDF…", color = Color.White)
                    }
                }
            }
        }
    }

    errorMsg?.let { msg ->
        AlertDialog(
            onDismissRequest = { errorMsg = null },
            title = { Text("提示") },
            text = { Text(msg) },
            confirmButton = {
                TextButton(onClick = { errorMsg = null }) { Text("知道了") }
            }
        )
    }

    deleteTarget?.let { note ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除笔记") },
            text = { Text("确定删除「${note.title}」吗？删除后不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteNote(note.id)
                    deleteTarget = null
                }) { Text("删除", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun NoteCard(
    note: NoteEntity,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val fmt = SimpleDateFormat("MM-dd HH:mm", Locale.getDefault())
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    note.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "删除",
                        tint = Color.Gray
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                val typeText = if (note.type == NoteType.PDF.name) "PDF · ${note.pageCount} 页" else "空白笔记"
                Text(
                    typeText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (note.type == NoteType.PDF.name) Color(0xFF00897B) else Blue
                )
                Spacer(Modifier.weight(1f))
                Text(
                    fmt.format(Date(note.updatedAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
            }
        }
    }
}
