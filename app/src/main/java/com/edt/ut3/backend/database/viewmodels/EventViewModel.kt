package com.edt.ut3.backend.database.viewmodels

import android.content.Context
import com.edt.ut3.backend.celcat.Event
import com.edt.ut3.backend.database.AppDatabase
import com.edt.ut3.backend.note.Note

class EventViewModel(context: Context) {

    private val eventDao = AppDatabase.getInstance(context).eventDao()
    private val noteViewModel = NotesViewModel(context)

    suspend fun getEventsByIDs(vararg ids: String) = eventDao.selectByIDs(*ids)

    suspend fun getEventByID(eventID: String) = getEventsByIDs(eventID).getOrNull(0)

    suspend fun getEvents() = eventDao.selectAll()

    fun getEventLD() = eventDao.selectAllLD()

    suspend fun insert(vararg events: Event) = eventDao.insert(*events)

    suspend fun delete(vararg events: Event) = eventDao.delete(*events)

    suspend fun update(vararg events: Event) = eventDao.update(*events)

    suspend fun getEventWithNote(eventID: String): Pair<Event?, Note?> {
        val event = getEventByID(eventID)
        val note = event?.noteID?.let { noteID ->
            noteViewModel.getNoteByID(noteID)
        }

        return Pair(event, note)
    }

}