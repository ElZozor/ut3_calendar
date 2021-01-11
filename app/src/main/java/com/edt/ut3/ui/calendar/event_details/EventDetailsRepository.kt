package com.edt.ut3.ui.calendar.event_details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.edt.ut3.backend.celcat.Event
import com.edt.ut3.backend.note.Note

object EventDetailsRepository {

    private val eventNoteMap = mutableMapOf<String, MutableLiveData<Pair<Event?, Note?>>>()

    fun getSelectedEventAndNote(id: String): LiveData<Pair<Event?, Note?>> {
        return eventNoteMap.getOrPut(id) {
            MutableLiveData(Pair(null, null))
        }
    }

    fun setSelectedEventNote(id: String, event: Event?, note: Note?) {
        val eventLD = eventNoteMap.getOrPut(id) {
            MutableLiveData()
        }

        eventLD.value = Pair(event, note)
    }

}