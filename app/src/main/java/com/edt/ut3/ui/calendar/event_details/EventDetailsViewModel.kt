package com.edt.ut3.ui.calendar.event_details

import androidx.lifecycle.ViewModel
import com.edt.ut3.backend.celcat.Event
import com.edt.ut3.backend.note.Note

abstract class EventDetailsViewModel(
        private val repository: EventDetailsRepository,
        private val id: String
) : ViewModel() {

    fun getSelectedEventAndNote() = repository.getSelectedEventAndNote(id)

    fun setSelectedEventNote(event: Event?, note: Note?) = repository.setSelectedEventNote(id, event, note)

}