package com.edt.ut3.ui.calendar.event_details

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.edt.ut3.R
import com.edt.ut3.backend.celcat.Event
import com.edt.ut3.backend.note.Note
import org.koin.androidx.viewmodel.ext.android.viewModel

class FragmentEventDetails: Fragment() {

    private val eventDetailsViewModel : EventDetailsViewModel by viewModel()

    override fun onCreateView (
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
    ) = inflater.inflate(R.layout.fragment_event_details, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        eventDetailsViewModel.getSelectedEventAndNote().observe(viewLifecycleOwner) { eventAndNote ->
            setupNewEventAndNote(eventAndNote)
        }
    }

    private fun setupNewEventAndNote(eventAndNote: Pair<Event?, Note?>) {

    }

    private fun setupNewEvent(event: Event?) {
        TODO("Setup the view for the incoming event")
    }

    private fun setNewNote(event: Note) {
        TODO("Setup the view for the incoming note")
    }

}