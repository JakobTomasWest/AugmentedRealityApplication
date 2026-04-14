package com.codersee.repository

import com.codersee.model.Note
import java.util.*
import kotlin.collections.HashMap

class NotesRepository {
    private val repo = HashMap<String, MutableList<Note>>()

    fun publicNotesFor(username: String) =
        repo.getOrDefault(username, listOf()).filter{ it.public}

    fun allNotesFor(user: String) = repo.getOrDefault(user, listOf())

    fun postNote(username: String, note: Note){
        repo.getOrPut(username) { mutableListOf() }.add(note)
    }
}