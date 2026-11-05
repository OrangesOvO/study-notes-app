package com.example.studyink

import android.app.Application
import com.example.studyink.data.AppDatabase
import com.example.studyink.data.AppSettings
import com.example.studyink.data.NoteRepository

class StudyInkApp : Application() {
    val repository: NoteRepository by lazy {
        NoteRepository(AppDatabase.get(this).noteDao())
    }
    val settings: AppSettings by lazy { AppSettings(this) }
}
