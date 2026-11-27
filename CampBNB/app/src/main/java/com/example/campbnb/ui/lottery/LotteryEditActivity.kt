package com.example.campbnb.ui.lottery

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.campbnb.controller.LotteryController
import com.example.campbnb.data.UserRepository
import com.example.campbnb.models.Campsite
import com.example.campbnb.models.Lottery
import com.example.campbnb.models.LotteryModel
import com.example.campbnb.models.Role
import com.example.campbnb.models.User
import com.example.campbnb.models.ViewObserver
import com.example.campbnb.ui.shared.AppViewModificationIsValidForCurrentWindowChecker
import com.example.campbnb.viewModels.AppView
import com.example.campbnb.viewModels.AppViewSetting
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Activity for editing/adding a lottery.
 * 03-11-26: Prompted Gemini 3.6 Flash for how to create and format
 * an interactable dropdown menu to be used for selecting available campsites.
 * 03-11-26: Prompted Gemini 3.6 Flash for how to integrate date range picker file
 * into this activity for the booking and registration fields.
 * 04-11-26: Used built in gemini flash 3.6 to add a few personal comments and kdoc comments
 * 05-11-26: Prompted gemini 3.6 flash to structure my save button functionality which creates a lottery
 * object based on the input fields and then saves it to firebase.
 * 06-11-26: Prompted gemini 3.6 to modify the campsite dropdown into a checkbox(toggle) dropdown
 * to allow for multiple campsite selection.
 * 06-11-26: Prompted gemini 3.6 to fill out kdocs
 * 20-11-26: Added the invitation picker for US 02.04.08: a name search over the other
 * owners, any number of them checked to receive invitations, and a joining-owner save
 * that keeps other owners' campsites instead of rebuilding the list from my own.
 * 23-11-26: Prompted Qwen 3.8 Flash Next Coder IQ1_M for the creator-only Force Close
 * button, which asks the controller to force-close and draw the selected lottery now.
 *
 * @property returnHome Callback function to navigate back to the home screen.
 * @property navigateBackwards Callback function to navigate backwards in the navigation stack.
 * @property appViewSettings Current view settings used for window verification and access checks.
 */
// Note: injecting temporary comments cause I need to know what I'm doing.
// remove once actual docs are available.
// also: why is this a class???? - W
class LotteryEditActivity(
    val returnHome: () -> Unit,
    val navigateBackwards: () -> Unit,
    val appViewSettings: AppViewSetting

) : ViewObserver<LotteryModel> {

    /**
     * Updates the UI observer when [model] changes. Handled via Compose state recomposition.
     *
     * @param model The updated [LotteryModel] instance.
     */
    override fun update(model: LotteryModel) {
    }

    /**
     * Renders the form for creating or updating a campsite lottery.
     *
     * Allows owners to select multiple campsites, specify winner numbers and waitlist capacity,
     * and pick date ranges for registration and booking windows.
     *
     * @param model The lottery state model shown by views.
     * @param controller The controller managing lottery database actions.
     * @param userRepository The user repository providing the owner candidates that
     * invitations may be checked for (US 02.04.08).
     * @param initialLottery Optional existing [Lottery] instance to edit; null if creating a new lottery.
     * @param ownerId Unique identifier of the campsite owner creating or editing the lottery.
     */
    @OptIn(ExperimentalMaterial3Api::class)
    // Editing Lotteries
    @Composable
    fun LotteryEdit(
        model: LotteryModel,
        controller: LotteryController,
        userRepository: UserRepository,
        initialLottery: Lottery? = null,
        ownerId: String = "",
        deviceID: String
    ) {
        AppViewModificationIsValidForCurrentWindowChecker(
            returnHome,
            appViewSettings,
            listOf(
                AppView.EntrantView,
                // could remove, but afaik admins shouldn't see the lottery unless posing
                // as an Owner.
                AppView.AdminView
            )
        )

        var selectedCampsites by remember { mutableStateOf<Set<Campsite>>(emptySet()) }
        var dropdownExpanded by remember { mutableStateOf(false) }

        var numberOfWinners by remember { mutableStateOf("") }
        var waitlistCapacity by remember { mutableStateOf("") }

        // Date selection state
        var regStartMillis by remember { mutableStateOf<Long?>(null) }
        var regEndMillis by remember { mutableStateOf<Long?>(null) }
        var showRegDatePicker by remember { mutableStateOf(false) }

        var bookingStartMillis by remember { mutableStateOf<Long?>(null) }
        var bookingEndMillis by remember { mutableStateOf<Long?>(null) }
        var showBookingDatePicker by remember { mutableStateOf(false) }

        var ownerCampsitesList by remember { mutableStateOf<List<Campsite>>(emptyList()) }

        // Invitation selection state (US 02.04.08): the other owners the name search
        // offers, and any number of them checked to be invited.
        var invitedOwnerCandidates by remember { mutableStateOf<List<User>>(emptyList()) }
        var checkedInvitees by remember { mutableStateOf<Set<User>>(emptySet()) }
        var ownerNameSearch by remember { mutableStateOf("") }
        var inviteDropdownExpanded by remember { mutableStateOf(false) }

        LaunchedEffect(initialLottery) {
            if (initialLottery != null) {
                numberOfWinners = initialLottery.numberOfWinners.toString()
                waitlistCapacity = initialLottery.waitlistCapacity?.toString() ?: ""
                regStartMillis = initialLottery.registrationStart
                regEndMillis = initialLottery.registrationEnd
                bookingStartMillis = initialLottery.bookingStart
                bookingEndMillis = initialLottery.bookingEnd
            }
        }

        val resolvedOwnerId = ownerId.ifBlank {
            FirebaseAuth
                .getInstance()
                .currentUser?.uid ?: deviceID }

        val isEditMode = initialLottery != null
        // A joining owner edits a lottery owned by someone else: they may only ADD one of
        // their own campsites to it, so the parameter fields stay read-only below, the
        // picker starts empty, and the save takes the joining-owner path that keeps the
        // lottery exactly as its primary owner wrote it apart from the added campsite.
        val isJoiningOwnerEdit = initialLottery != null && initialLottery.ownerId != resolvedOwnerId

        val lotteryToSave = Lottery(
            ownerId = resolvedOwnerId,
            campsiteIds = selectedCampsites.map { it.id },
            // The owners checked in the invitation picker are invited with this lottery;
            // the model refuses self-invites and repeats, so the save stays honest even
            // if the picker was left checked oddly.
            invitedOwnerIds = checkedInvitees.map { it.id },
            numberOfWinners = numberOfWinners.toIntOrNull() ?: 0,
            waitlistCapacity = waitlistCapacity.toIntOrNull(), // null if blank
            registrationStart = regStartMillis ?: 0L,
            registrationEnd = regEndMillis ?: 0L,
            bookingStart = bookingStartMillis ?: 0L,
            bookingEnd = bookingEndMillis ?: 0L
        ).apply {
            if (initialLottery != null && initialLottery.id.isNotBlank()) {
                id = initialLottery.id
            }
        }

        // A joining owner adds to the lottery AS ITS PRIMARY OWNER WROTE IT: the form's own
        // fields (owner, invitations, parameters) belong to the creator's edit path only, so
        // the joining save starts from the record on file and only the campsite is added.
        val lotteryToSaveAgainst = if (isJoiningOwnerEdit) initialLottery ?: lotteryToSave else lotteryToSave

        LaunchedEffect(resolvedOwnerId) {
            if (resolvedOwnerId.isNotBlank()) {
                controller.loadOwnerCampsites(resolvedOwnerId) { fetchedCampsites ->
                    ownerCampsitesList = fetchedCampsites
                    if (initialLottery != null && !isJoiningOwnerEdit && selectedCampsites.isEmpty()) {
                        selectedCampsites = fetchedCampsites.filter { it.id in initialLottery.campsiteIds }.toSet()
                    }
                }
            }
        }

        // Invitations are only for other accounts, so the current viewer is never offered
        // as a candidate; editing keeps the lottery's pending invitations checked.
        // Co-owner candidates (US 02.04.08, extended for the admin-as-owner story): an
        // account holding the owner setting is offered as an owner. Admin accounts are
        // offered too when the creator is themselves an admin acting as an owner, so an
        // admin creator can put other admins on the multi-owner list; an admin that also
        // holds the owner setting is offered on the owner rule either way.
        LaunchedEffect(resolvedOwnerId, initialLottery) {
            if (resolvedOwnerId.isNotBlank()) {
                userRepository.getAll(
                    onSuccess = { users ->
                        val creatorActsAsAdminOwner =
                            users.any { it.id == deviceID && it.hasRole(Role.ADMIN) }
                        invitedOwnerCandidates = users.filter { candidate ->
                            candidate.id != resolvedOwnerId &&
                                candidate.id != deviceID &&
                                (
                                    candidate.hasRole(Role.OWNER) ||
                                    (candidate.hasRole(Role.ADMIN) && creatorActsAsAdminOwner)
                                )
                        }
                        if (initialLottery != null) {
                            checkedInvitees = users.filter { it.id in initialLottery.invitedOwnerIds }.toSet()
                        }
                    },
                    onFailure = { }
                )
            }
        }

        fun formatDateRange(start: Long?, end: Long?): String {
            if (start == null || end == null) return ""
            val formatter = SimpleDateFormat("MMM dd, yyyy", Locale.getDefault())
            // The picker's boundaries are UTC-midnight millis (DateRangePickerModal);
            // formatting them in the device timezone would show the day before on
            // devices west of UTC, so the calendar day is kept on the UTC basis.
            formatter.timeZone = TimeZone.getTimeZone("UTC")
            return "${formatter.format(Date(start))} - ${formatter.format(Date(end))}"
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { navigateBackwards() },
                    modifier = Modifier.testTag("lottery_edit_back").padding(end = 8.dp)
                ) {
                    Text(text = "Back")
                }
                Text(
                    text = if (isEditMode) "Edit Lottery" else "Create Lottery",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Multiple Campsites Dropdown Selection (Checkbox Dropdown)
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded,
                onExpandedChange = { dropdownExpanded = !dropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (selectedCampsites.isEmpty()) "" else selectedCampsites.joinToString(", ") { it.campName },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Pick Campsites") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier
                        .testTag("campsite_picker_field")
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )

                ExposedDropdownMenu(
                    expanded = dropdownExpanded,
                    onDismissRequest = { dropdownExpanded = false }
                ) {
                    if (ownerCampsitesList.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No campsites available") },
                            modifier = Modifier.testTag("campsite_picker_choice"),
                            onClick = { dropdownExpanded = false }
                        )
                    } else {
                        ownerCampsitesList.forEach { campsite ->
                            val isSelected = campsite in selectedCampsites
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = null
                                        )
                                        Column {
                                            Text(
                                                text = campsite.campName,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (campsite.campLoc.isNotBlank()) {
                                                Text(
                                                    text = campsite.campLoc,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("campsite_picker_choice"),
                                onClick = {
                                    selectedCampsites = if (isSelected) {
                                        selectedCampsites - campsite
                                    } else {
                                        selectedCampsites + campsite
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Invite Owners: an optional name search narrows the list of the app's
            // other owners, and any number of them may be checked to receive an
            // invitation to join this lottery (US 02.04.08).
            OutlinedTextField(
                value = ownerNameSearch,
                onValueChange = { ownerNameSearch = it },
                label = { Text("Search Owners by Name (optional)") },
                modifier = Modifier.testTag("owner_search_field").fillMaxWidth()
            )

            val inviteeChoices = invitedOwnerCandidates.filter {
                ownerNameSearch.isBlank() ||
                    it.name.contains(ownerNameSearch, ignoreCase = true) ||
                    it.id.contains(ownerNameSearch, ignoreCase = true)
            }

            ExposedDropdownMenuBox(
                expanded = inviteDropdownExpanded,
                onExpandedChange = { inviteDropdownExpanded = !inviteDropdownExpanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = if (checkedInvitees.isEmpty()) "" else checkedInvitees.joinToString(", ") { it.name.ifBlank { it.id } },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Invite Owners") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = inviteDropdownExpanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    // the menu anchors on this field; without the anchor the panel
                    // below has no owner and the dropdown never opens (regression 4)
                    modifier = Modifier
                        .testTag("invite_field")
                        .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                )

                // The invited-owner list is a dropdown menu, the same shape the campsite
                // picker above uses: tapping a row checks that owner, and every candidate
                // the app holds is offered.
                ExposedDropdownMenu(
                    expanded = inviteDropdownExpanded,
                    onDismissRequest = { inviteDropdownExpanded = false }
                ) {
                    if (inviteeChoices.isEmpty()) {
                        DropdownMenuItem(
                            text = { Text("No other owners to invite") },
                            modifier = Modifier.testTag("invite_choice"),
                            onClick = { inviteDropdownExpanded = false }
                        )
                    } else {
                        inviteeChoices.forEach { candidate ->
                            val isSelected = candidate in checkedInvitees
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Checkbox(
                                            checked = isSelected,
                                            onCheckedChange = null
                                        )
                                        Column {
                                            Text(
                                                text = candidate.name.ifBlank { candidate.id },
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium
                                            )
                                            if (candidate.name.isNotBlank()) {
                                                Text(
                                                    text = candidate.id,
                                                    style = MaterialTheme.typography.bodySmall
                                                )
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("invite_choice"),
                                onClick = {
                                    checkedInvitees = if (isSelected) {
                                        checkedInvitees - candidate
                                    } else {
                                        checkedInvitees + candidate
                                    }
                                }
                            )
                        }
                    }
                }
            }

            if (!isJoiningOwnerEdit) {
                OutlinedTextField(
                    value = numberOfWinners,
                    onValueChange = { numberOfWinners = it },
                    label = { Text("Number of Winners") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("winners_field").fillMaxWidth()
                )

                OutlinedTextField(
                    value = waitlistCapacity,
                    onValueChange = { waitlistCapacity = it },
                    label = { Text("Waitlist Capacity (leave blank for unlimited)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("waitlist_field").fillMaxWidth()
                )

                // Registration Date Range Picker
                OutlinedTextField(
                    value = formatDateRange(regStartMillis, regEndMillis),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Registration Period") },
                    trailingIcon = {
                        TextButton(modifier = Modifier.testTag("reg_period_open"), onClick = { showRegDatePicker = true }) {
                            Text("Select")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("reg_period_open")
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showRegDatePicker = true }
                )

                // Booking Date Range Picker
                OutlinedTextField(
                    value = formatDateRange(bookingStartMillis, bookingEndMillis),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Booking Period") },
                    trailingIcon = {
                        TextButton(modifier = Modifier.testTag("booking_period_open"), onClick = { showBookingDatePicker = true }) {
                            Text("Select")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("booking_period_open")
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showBookingDatePicker = true }
                )
            } else {
                Text(
                    text = "You joined this lottery, so only your own campsites can be changed.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = { navigateBackwards() },
                    modifier = Modifier.testTag("lottery_cancel").weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    enabled = lotteryToSaveAgainst.isValid(System.currentTimeMillis()),
                    onClick = {
                        if (isJoiningOwnerEdit) {
                            // Joining owners add their own campsite to the lottery as it is
                            // on file: its primary owner and every parameter stay exactly as
                            // they were written, and no second lottery is ever made.
                            controller.saveCampsitesEditedBy(
                                editorId = resolvedOwnerId,
                                lottery = lotteryToSaveAgainst,
                                selectedCampsiteIds = selectedCampsites.map { it.id },
                                onSuccess = {
                                    navigateBackwards()
                                },
                                onFailure = { exception ->
                                }
                            )
                        } else {
                            //Call controller to save to Firestore
                            controller.saveLottery(
                                lottery = lotteryToSave,
                                onSuccess = {
                                    navigateBackwards()
                                },
                                onFailure = { exception ->
                                }
                            )
                        }
                    },
                    modifier = Modifier.testTag("lottery_save").weight(1f)
                ) {
                    Text(if (isEditMode) "Update" else "Save")
                }
            }

            if (isEditMode && !isJoiningOwnerEdit && initialLottery != null) {
                // feature 1: only the lottery's own admin — the creator — may force it to
                // close and draw now; the booking window moves onto today so the whole
                // lifecycle can be tested
                val closingLottery = initialLottery
                // A lottery is force-closed once: once the draw is stored the button is
                // no longer offered, and the edit form is left so the lottery view shows
                // the closed record rather than the parameters this form still holds.
                var forceClosed by remember { mutableStateOf(false) }
                Button(
                    enabled = !forceClosed,
                    onClick = {
                        controller.forceCloseLottery(
                            lottery = closingLottery,
                            nameOfCampsite = { id ->
                                ownerCampsitesList.firstOrNull { it.id == id }?.campName
                                    ?: id
                            },
                            onDone = { stored ->
                                if (stored) {
                                    forceClosed = true
                                    navigateBackwards()
                                }
                            }
                        )
                    },
                    modifier = Modifier.testTag("force_close").fillMaxWidth().padding(top = 12.dp)
                ) {
                    Text("Force Close")
                }
            }
        }

        // Modals for Date Range Pickers
        if (showRegDatePicker) {
            DateRangePickerModal(
                onDateRangeSelected = { (start, end) ->
                    regStartMillis = start
                    regEndMillis = end
                },
                onDismiss = { showRegDatePicker = false }
            )
        }

        if (showBookingDatePicker) {
            DateRangePickerModal(
                onDateRangeSelected = { (start, end) ->
                    bookingStartMillis = start
                    bookingEndMillis = end
                },
                onDismiss = { showBookingDatePicker = false }
            )
        }
    }
}
