package com.edt.ut3.ui.calendar.event_details

import android.Manifest
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.View.*
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.DatePicker
import android.widget.TimePicker
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.*
import androidx.lifecycle.Observer
import com.axellaffite.fastgallery.FastGallery
import com.axellaffite.fastgallery.slider_animations.SlideAnimations
import com.edt.ut3.R
import com.edt.ut3.backend.celcat.Event
import com.edt.ut3.backend.database.viewmodels.NotesViewModel
import com.edt.ut3.backend.maps.MapsUtils
import com.edt.ut3.backend.maps.Place
import com.edt.ut3.backend.note.Note
import com.edt.ut3.backend.note.Note.Reminder.ReminderType
import com.edt.ut3.backend.note.Picture
import com.edt.ut3.backend.preferences.PreferencesManager
import com.edt.ut3.misc.extensions.onBackPressed
import com.edt.ut3.misc.extensions.set
import com.edt.ut3.misc.extensions.setTime
import com.edt.ut3.ui.custom_views.image_preview.ImagePreviewAdapter
import com.edt.ut3.ui.map.MapsViewModel
import com.edt.ut3.ui.preferences.Theme
import com.elzozor.yoda.utils.DateExtensions.get
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.fragment_event_details.*
import kotlinx.android.synthetic.main.fragment_event_details.view.*
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class FragmentEventDetailsOld : Fragment() {

    private var updateLocationJob: Job? = null
    private lateinit var event: Event

    private val mapsViewModel: MapsViewModel by activityViewModels()

    private var eventNoteLD : LiveData<Note>? = null
    private var canTakePicture = true
    private var firstNoteUpdate = true

    private lateinit var currentNote: Note

    private var pictureFile: File? = null
    private var pictureName: String? = null

    var onReady : (() -> Unit)? = null

    var listenTo = MutableLiveData<Event>(null)
        set(value) {
            firstNoteUpdate = true
            value.observe(viewLifecycleOwner, Observer {
                it?.let { event ->
                    setupNewEvent(event)
                }
            })

            field = value
        }

    private var spinnerObserver = SpinnerObserver().apply {
        onItemSelectedListener = { parent: AdapterView<*>?, view: View?, index: Int, id: Long ->
            fun process() {
                val type = ReminderType.values()[index]
                when (type) {
                    ReminderType.NONE -> currentNote.reminder.disable()
                    ReminderType.FIFTEEN_MINUTES -> currentNote.reminder.setFifteenMinutesBefore()
                    ReminderType.THIRTY_MINUTES -> currentNote.reminder.setThirtyMinutesBefore()
                    ReminderType.ONE_HOUR -> currentNote.reminder.setOneHourBefore()
                    ReminderType.CUSTOM -> askUserForDateTime(currentNote.date) { date: Date ->
                        currentNote.reminder.setCustomReminder(date)
                        lifecycleScope.launch { saveNote() }
                    }
                }

                // We do not want to save the note
                // 2 times, especially if the dialog is
                // actually shown which is almost always the
                // case at this point.
                if (type != ReminderType.CUSTOM) {
                    lifecycleScope.launch { saveNote() }
                }
            }

            if (!firstNoteUpdate) {
                process()
            }
        }
    }

    /**
     * Used to launch an Intent that will take
     * a picture a write the result into the
     * provided file (pictureFile).
     */
    private val takePicture = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            addPictureToNote(pictureName!!, pictureFile!!)
        } else {
            pictureFile?.delete()
        }
    }

    /**
     * Used to request a permission (in this case a CAMERA permission).
     * If the permission is granted, takePicture is called
     * otherwise, the previously created picture file is deleted.
     */
    private val grantCameraPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            pictureFile?.let {
                takePicture.launch(uriFromFile(it))
            }
        } else {
            pictureFile?.delete()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("pictureName", pictureName)
        outState.putSerializable("pictureFile", pictureFile)
        super.onSaveInstanceState(outState)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        savedInstanceState?.takeIf { it.containsKey("pictureName") && it.containsKey("pictureFile") }?.run {
            pictureName = getString("pictureName")
            pictureFile = getSerializable("pictureFile") as? File
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_event_details, container, false).also { root ->
            // Load asynchronously the attached note.
            // Once it's done, the contents
            lifecycleScope.launch {
                if (mapsViewModel.getPlaces(root.context).value.isNullOrEmpty()) {
                    mapsViewModel.launchDataUpdate(root.context)
                }
            }
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (listenTo.value == null && isVisible) {
            onBackPressed()
        }

        listenTo.observe(viewLifecycleOwner, Observer { event: Event? ->
            event?.let {
                setupNewEvent(event)
            }
        })
    }

    private fun setupNewEvent(event: Event) {
        this.event = event
        currentNote = Note.generateEmptyNote(event)
        lifecycleScope.launchWhenCreated {

            NotesViewModel(requireContext()).run {
                whenResumed {
                    setupContent()
                    setupListeners()

                    onReady?.invoke()

                    eventNoteLD?.removeObservers(viewLifecycleOwner)
                    firstNoteUpdate = true

                    eventNoteLD = getNoteByEventIDLD(event.id)
                    eventNoteLD?.observe(viewLifecycleOwner, Observer { note: Note? ->
                        updateNoteContents(note)
                    })
                }
            }
        }
    }


    /**
     * Setup the view contents.
     */
    private fun setupContent() {
        note_container.visibility = INVISIBLE
        note_loading.visibility = VISIBLE

        when (PreferencesManager.getInstance(requireContext()).currentTheme()) {
            Theme.LIGHT -> {
                event.lightBackgroundColor(requireContext()).let {
                    title_container.setCardBackgroundColor(it)
                }
            }

            Theme.DARK -> {
                event.darkBackgroundColor(requireContext()).let {
                    title_container.setCardBackgroundColor(it)
                }
            }
        }

        title.text = event.courseOrCategory(requireContext())
        from_to.text = generateDateText()

        val descriptionBuilder = StringBuilder()
        event.categoryWithEmotions()?.let { descriptionBuilder.append(it).append("\n") }

        val locations = event.locations.joinToString(", ")
        if (locations.isNotBlank()) {
            descriptionBuilder.append(locations)
        } else {
            descriptionBuilder.append(event.sites.joinToString(", "))
        }
        descriptionBuilder.append("\n")

        event.description?.let { descriptionBuilder.append(it) }
        description.text = descriptionBuilder.toString()
    }


    private fun updateNoteContents(newNote: Note?) {
        if (firstNoteUpdate) {
            initialNoteSetup(newNote)
        } else {
            newNote?.let {
                if (currentNote.pictures != it.pictures) {
                    println("setting pictures to: ${it.pictures}")
                    currentNote.pictures.clear()
                    currentNote.pictures.addAll(it.pictures)

                    pictures.adapter?.notifyDataSetChanged()
                }

                if (currentNote.reminder != newNote.reminder) {
                    currentNote.reminder.setupFrom(newNote.reminder)
                    reminder_spinner.setSelection(
                        ReminderType.values().indexOf(currentNote.reminder.getReminderType())
                    )
                }
            } ?: run {
                clearSpinner()
            }
        }
    }

    private fun clearSpinner() {
        spinnerObserver.shouldSkipNextUpdate = true
        reminder_spinner?.setSelection(0)
    }

    private fun initialNoteSetup(newNote: Note?) {
        currentNote = newNote ?: Note.generateEmptyNote(event)

        if (event_note.text.toString() != newNote?.contents) {
            event_note.setText(newNote?.contents)
        }


        // Setup the adapter contents and the item "onclick" callbacks.
        // Use the StfalconImageViewer library to display a fullscreen
        // image.
        pictures.adapter = ImagePreviewAdapter(currentNote.pictures).apply {
            onItemClickListener = { _, picture, pictures ->
                val overlayLayout = ImageOverlayLayout(requireContext())

                val fragment = FastGallery.Builder<Picture>()
                    .withBackgroundResource(R.color.backgroundColor)
                    .withImages(currentNote.pictures)
                    .withInitialPosition(pictures.indexOf(picture))
                    .withOffscreenLimit(2)
                    .withSlideAnimation(SlideAnimations.zoomOutAnimation())
                    .withOverlay(overlayLayout)
                    .withConverter { displayedPicture, imageLoader ->
                        lifecycleScope.launchWhenResumed {
                            imageLoader.fromFile(File(displayedPicture.picture).toUri())
                        }
                    }.build()

                overlayLayout.onDeleteRequest = {
                    AlertDialog.Builder(requireContext())
                        .setTitle(R.string.delete_image)
                        .setPositiveButton(R.string.action_ok) { _,_ ->
                            val viewPager = fragment.getViewPager()
                            viewPager?.run {
                                val itemPosition = currentItem
                                currentNote.removePictureAt(itemPosition)

                                lifecycleScope.launchWhenResumed {
                                    saveNote {
                                        this@FragmentEventDetails.pictures.notifyDataSetChanged()
                                        if (currentNote.pictures.isEmpty()) {
                                            fragment.dismiss()
                                        } else {
                                            adapter?.notifyItemRemoved(itemPosition)
                                        }
                                    }
                                }
                            }
                        }
                        .setNegativeButton(R.string.action_cancel) { _,_ -> }
                        .also {
                            it.show()
                        }
                }

                fragment.show(parentFragmentManager, "eventDetailsGallery")
            }


            // The "take picture" button callback
            onAddPictureClickListener = {
                takePicture()
            }
        }

        // Add all the note event picture to the view model variable
        // It is used into several cases to add and delete pictures
        // to the current note.
        pictures.notifyDataSetChanged()

        // The adapter is an extension of the ArrayAdapter class.
        // It's made like this to override the getView() function
        // in order to remove the left and right padding of the
        // spinner.
        val adapterValues = ReminderType.values().map { getString(reminderText(it)) }
        reminder_spinner.adapter = object: ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1, adapterValues) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                return super.getView(position, convertView, parent).apply {
                    setPadding(0, paddingTop, 0, paddingBottom)
                }
            }
        }

        // This variable is set to avoid observer from
        // being called every time a new event is
        // selected.
        spinnerObserver.shouldSkipNextUpdate = true
        updateReminderSpinner(currentNote)



        firstNoteUpdate = false
        note_loading.visibility = INVISIBLE
        note_container.visibility = VISIBLE
    }


    /**
     * Returns the textual representation
     * (from strings.xml, not .toString())
     * of the given ReminderType.
     *
     * @param type The reminder type to convert
     * @return Its textual representation
     */
    private fun reminderText(type: ReminderType): Int = when (type) {
        ReminderType.NONE -> R.string.reminder_none
        ReminderType.FIFTEEN_MINUTES -> R.string.reminder_fifteen
        ReminderType.THIRTY_MINUTES -> R.string.reminder_thirty
        ReminderType.ONE_HOUR -> R.string.reminder_hour
        ReminderType.CUSTOM -> R.string.reminder_custom
    }

    private fun updateReminderSpinner(newNote: Note?) {
        newNote?.let {
            val typeIndex = ReminderType.values().indexOf(newNote.reminder.getReminderType())
            reminder_spinner?.setSelection(typeIndex)
        } ?: run {
            reminder_spinner?.setSelection(0)
        }
    }

    private fun refreshLocations(places: List<Place>) {
        updateLocationJob?.cancel()
        updateLocationJob = lifecycleScope.launch {
            whenCreated {
                val matchingPlaces = withContext(Default) {
                    val parsedPlaces = places.map { it.apply { title = title.toLowerCase(Locale.FRENCH) } }

                    computeMatchingLocations(parsedPlaces)
                }

                whenResumed {
                    withContext(Main) {
                        refreshLocations(requireContext(), matchingPlaces)
                    }
                }
            }
        }
    }

    private fun refreshLocations(context: Context, places: List<Place>) {
        if (places.isNotEmpty()) {
            locations_container.removeAllViews()

            places.forEach {
                locations_container.addView(PlaceChip(context, it))
            }

            locations_container.visibility = VISIBLE
            locations_not_found_label.visibility = GONE
        } else {
            locations_container.visibility = GONE
            locations_not_found_label.visibility = VISIBLE
        }
    }

    private suspend fun computeMatchingLocations(places: List<Place>) = withContext(Default) {
        val matchingPlaces =
            event.locations.map { location ->
                val lowerCaseLocation = location.toLowerCase(Locale.FRENCH)
                val correspondingPlace = places.find { place ->
                    lowerCaseLocation.contains(place.title)
                }

                correspondingPlace
            }

        matchingPlaces.filterNotNull()
    }

    private fun setupListeners() {
        close_button.setOnClickListener {
            requireActivity().onBackPressed()
        }

        // Save the current note at each modification.
        event_note.doOnTextChanged { text, _, _, _ ->
            if (currentNote.contents != text) {
                currentNote.contents = text.toString()
                lifecycleScope.launch { saveNote() }
            }
        }

        reminder_spinner.onItemSelectedListener = spinnerObserver

        mapsViewModel.getPlaces(requireContext()).observe(viewLifecycleOwner, Observer {
            refreshLocations(it)
        })
    }

    private fun askUserForDateTime(date: Date, callback: (Date) -> Unit) {
        context?.let {
            askUserForDate(it, date, callback)
        }
    }

    private fun askUserForDate(context: Context, date: Date, callback: (Date) -> Unit) {
        val year = date.get(Calendar.YEAR)
        val month = date.get(Calendar.MONTH)
        val day = date.get(Calendar.DAY_OF_MONTH)

        val dateListener = { _: DatePicker, newYear: Int, newMonth: Int, newDayOfMonth: Int ->
            val newDate = Date().set(newYear, newMonth, newDayOfMonth)
            askUserForTime(context, date, newDate, callback)
        }

        DatePickerDialog(context, dateListener, year, month, day).apply {
            setOnCancelListener { updateReminderSpinner(currentNote) }
        }.show()
    }

    private fun askUserForTime(context: Context, date: Date, newDate: Date, callback: (Date) -> Unit) {
        val hour = date.get(Calendar.HOUR_OF_DAY)
        val minute = date.get(Calendar.MINUTE)
        println("Date: $newDate")

        val timeListener = { _: TimePicker, newHour: Int, newMinute: Int ->
            newDate.setTime(newHour, newMinute)
            println("Date: $newDate")
            callback(newDate)
        }

        TimePickerDialog(context, timeListener, hour, minute, true).apply {
            setOnCancelListener { updateReminderSpinner(currentNote) }
        }.show()
    }

    /**
     * Generate the date text depending on
     * the event status (all day or classic)
     * and its start and end dates.
     */
    private fun generateDateText(): String {
        val date =
            SimpleDateFormat("EEEE dd/MM/yyyy", Locale.getDefault()).format(event.start)
                .capitalize(Locale.getDefault())

        val time =
            if (event.allday) {
                getString(R.string.all_day)
            } else {
                val start = "%02dh%02d".format(event.start.get(Calendar.HOUR_OF_DAY), event.start.get(Calendar.MINUTE))
                val end = "%02dh%02d".format(event.end?.get(Calendar.HOUR_OF_DAY), event.end?.get(Calendar.MINUTE))

                val fromToFormat = getString(R.string.from_to_format)

                fromToFormat.format(start, end).capitalize(Locale.getDefault())
            }

        return "$date\n$time"
    }

    /**
     * Generates the output file, assign
     * the picture name and file variables
     * to the result, check the camera permission
     * and depending on the camera permission
     * call the takePicture variable or the
     * grandCameraPermission one.
     */
    private fun takePicture() {
        if (!canTakePicture) {
            event_details_snackbar?.let { snackbarContainer ->
                Snackbar.make(snackbarContainer, R.string.unable_take_while_saving, Snackbar.LENGTH_SHORT).show()
            }

            return
        }

        generateOutputFile { name, file ->
            pictureName = name
            pictureFile = file

            val cameraPermission = Manifest.permission.CAMERA
            val grantStatus = ContextCompat.checkSelfPermission(requireContext(), cameraPermission)
            if (grantStatus != PackageManager.PERMISSION_GRANTED) {
                grantCameraPermission.launch(cameraPermission)
            } else {
                takePicture.launch(uriFromFile(file))
            }
        }
    }

    /**
     * Simply generate an URI from a given file.
     *
     * @param file The concerned file
     */
    private fun uriFromFile(file: File) =
        FileProvider.getUriForFile(requireContext(), "com.edt.ut3.fileprovider", file)

    /**
     * Save the current note and create the
     * file that will store the picture.
     * This function is executed in a separate thread.
     *
     * Once it's done, call the provided callback
     * with these information.
     *
     * @param callback The action to execute once done.
     */
    private fun generateOutputFile(callback: ((name: String, file: File) -> Unit)) {
        lifecycleScope.launch {
            saveNote {
                println("Save from outputFile")

                val name = Picture.generateFilename(it.id.toString())
                val file = Picture.prepareImageFile(
                    requireContext(),
                    name
                )

                callback(name, file)
            }
        }
    }


    /**
     * Generate the thumbnail and add it to the
     * current note.
     * The results are stored into a Picture class.
     *
     * The thumbnail name is generated with the
     * provided name. It just add "_thumbnail" to it.
     *
     * @param name The picture name
     * @param file The picture file
     */
    private fun addPictureToNote(name: String, file: File) {
        lifecycleScope.launch {
            canTakePicture = false
            Log.d(this@FragmentEventDetails::class.simpleName, "Note saved")

            val generated = Picture.generateFromPictureUri(requireContext(), name, file.absolutePath)
            currentNote.pictures.add(generated)

            saveNote {
                pictures.notifyDataSetChanged()
                canTakePicture = true
            }

            Log.d(this@FragmentEventDetails::class.simpleName, "picture added")
        }
    }



    private val save = Mutex()
    private val add = Mutex()
    private val callbackStack = Stack<(Note) -> Unit>()
    /**
     * Save the provided note into the database
     * and assign the old one to the result.
     * Call the callback once done if it's defined.
     *
     * @param callback The action to execute once done
     */
    private suspend fun saveNote(callback: ((Note) -> Unit)? = null) {
        withContext(Default) {
            add.lock()
            callback?.let { callbackStack.add(it) }
            add.unlock()

            if (save.tryLock()) {

                withContext(IO) {
                    NotesViewModel(requireContext()).run {
                        save(currentNote)
                    }
                }

                add.lock()

                while (callbackStack.isNotEmpty()) {
                    withContext(Main) {
                        callbackStack.pop().invoke(currentNote)
                    }
                }

                save.unlock()
                add.unlock()
            }
        }
    }

    private class SpinnerObserver: AdapterView.OnItemSelectedListener {
        var shouldSkipNextUpdate = false
        var onItemSelectedListener: ((parent: AdapterView<*>?, view: View?, position: Int, id: Long) -> Unit)? = null

        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
            if (shouldSkipNextUpdate) {
                shouldSkipNextUpdate = false
                return
            }

            onItemSelectedListener?.invoke(parent, view, position, id)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) {

        }
    }


    private inner class PlaceChip(context: Context, val place: Place) : Chip(context) {
        init {
            setChipDrawable(
                ChipDrawable.createFromAttributes(
                    context,
                    null,
                    0,
                    R.style.Widget_MaterialComponents_Chip_Action
                )
            )

            setOnClickListener {
                activity?.let {
                    MapsUtils.routeFromTo(it, null, place.geolocalisation, place.title) {
                        view?.event_details_main?.let { mainView ->
                            Snackbar.make(mainView, R.string.unable_to_launch_googlemaps, Snackbar.LENGTH_LONG).show()
                        }
                    }
                }
            }

            setChipBackgroundColorResource(R.color.foregroundColor)

            text = place.title.toUpperCase(Locale.FRENCH)
        }
    }
}