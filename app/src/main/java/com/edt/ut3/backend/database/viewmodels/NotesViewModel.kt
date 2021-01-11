package com.edt.ut3.backend.database.viewmodels

import android.content.Context
import android.util.Log
import com.edt.ut3.backend.celcat.Event
import com.edt.ut3.backend.database.AppDatabase
import com.edt.ut3.backend.note.Note
import com.edt.ut3.backend.notification.NotificationManager

class NotesViewModel(private val context: Context) {

    private val noteDao = AppDatabase.getInstance(context).noteDao()
    private val eventViewModel = EventViewModel(context)

    fun getNotesLD() = noteDao.selectAllLD()

    suspend fun getNotesByEventIDs(vararg eventIDs: String) = noteDao.selectByEventIDs(*eventIDs)

    fun getNoteByEventIDLD(eventID: String) = noteDao.selectByEventIDLD(eventID)

    suspend fun getNoteByID(id: Long) = noteDao.selectByID(id)

    suspend fun getNoteWithEvent(id: Long): Pair<Event?, Note?> {
        val note = getNoteByID(id)
        val event = note?.eventID?.let { eventID ->
            eventViewModel.getEventByID(eventID)
        }

        return Pair(event, note)
    }

    suspend fun save(note: Note) {
        if (note.isEmpty()) {
            Log.d(this::class.simpleName, "Note is empty")
            delete(note)
        } else {
            Log.d(this::class.simpleName, "Note isn't empty")

            val ids = noteDao.insert(note)

            if (note.id == 0L) {
                note.id = ids[0]
            }

            NotificationManager.getInstance(context).run {
                if (note.reminder.isActive()) {
                    this.createNoteSchedule(note)
                } else {
                    this.removeNoteSchedule(note)
                }
            }
        }
    }

    suspend fun delete(note: Note) {
        noteDao.delete(note)
        note.clearPictures()
        note.clearNotifications(context)
    }

}