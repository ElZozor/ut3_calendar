package com.edt.ut3.ui.calendar

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import com.edt.ut3.backend.celcat.Event
import com.edt.ut3.backend.database.viewmodels.EventViewModel
import com.edt.ut3.misc.timeCleaned
import java.util.*

class CalendarViewModel : ViewModel() {
    private lateinit var events: LiveData<List<Event>>
    var selectedDate = Date().timeCleaned()

    @Synchronized
    fun getEvents(context: Context) : LiveData<List<Event>> {
        if (!this::events.isInitialized) {
            events = EventViewModel(context).getEventLD()
        }

        return events
    }
}