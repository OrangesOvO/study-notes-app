package com.example.studyink

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.studyink.data.NoteRepository
import com.example.studyink.ui.editor.EditorScreen
import com.example.studyink.ui.notelist.NoteListScreen
import com.example.studyink.ui.theme.StudyInkTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StudyInkTheme {
                AppRoot((application as StudyInkApp).repository)
            }
        }
    }
}

@Composable
private fun AppRoot(repository: NoteRepository) {
    var openNoteId by remember { mutableStateOf<Long?>(null) }
    val id = openNoteId
    if (id == null) {
        NoteListScreen(repository = repository, onOpenNote = { openNoteId = it })
    } else {
        EditorScreen(repository = repository, noteId = id, onBack = { openNoteId = null })
    }
}
