package com.nextcloud.contacts.ui.components

import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.nextcloud.contacts.data.prefs.ContactEditFieldMode
import com.nextcloud.contacts.ui.viewmodel.ContactEditHiddenField
import com.nextcloud.contacts.ui.viewmodel.ContactEditState
import com.nextcloud.contacts.ui.viewmodel.focusAddressIndexForHiddenField
import com.nextcloud.contacts.ui.viewmodel.focusRowIndexForHiddenField
import com.nextcloud.contacts.ui.viewmodel.isFocusTargetReady
import kotlinx.coroutines.delay

data class ContactEditFieldFocusTarget(
    val field: ContactEditHiddenField,
    val rowIndex: Int = 0,
    val addressIndex: Int = 0,
)

data class FormValueFocus(
    val requester: FocusRequester,
    val bringIntoViewRequester: BringIntoViewRequester,
)

fun Modifier.formBringIntoView(valueFocus: FormValueFocus?): Modifier =
    if (valueFocus == null) {
        this
    } else {
        bringIntoViewRequester(valueFocus.bringIntoViewRequester)
    }

fun Modifier.formValueFocusRequester(valueFocus: FormValueFocus?): Modifier =
    if (valueFocus == null) {
        this
    } else {
        focusRequester(valueFocus.requester)
    }

@Stable
class ContactEditFieldFocusController internal constructor(
    private val pendingState: MutableState<ContactEditFieldFocusTarget?>,
    private val focusRequesters: Map<ContactEditHiddenField, FocusRequester>,
    private val bringIntoViewRequesters: Map<ContactEditHiddenField, BringIntoViewRequester>,
) {
    var pending: ContactEditFieldFocusTarget?
        get() = pendingState.value
        set(value) {
            pendingState.value = value
        }

    fun scheduleRevealFocus(field: ContactEditHiddenField, stateAfterReveal: ContactEditState) {
        pending = ContactEditFieldFocusTarget(
            field = field,
            rowIndex = stateAfterReveal.focusRowIndexForHiddenField(field),
            addressIndex = stateAfterReveal.focusAddressIndexForHiddenField(field),
        )
    }

    fun valueFocus(
        field: ContactEditHiddenField,
        rowIndex: Int = 0,
        addressIndex: Int = 0,
    ): FormValueFocus? {
        val target = pending ?: return null
        if (target.field != field ||
            target.rowIndex != rowIndex ||
            target.addressIndex != addressIndex
        ) {
            return null
        }
        return FormValueFocus(
            requester = focusRequesters.getValue(field),
            bringIntoViewRequester = bringIntoViewRequesters.getValue(field),
        )
    }

    @Composable
    fun PendingRevealFocusEffect(
        state: ContactEditState,
        fieldMode: ContactEditFieldMode,
    ) {
        val target = pending ?: return
        LaunchedEffect(target, state, fieldMode) {
            if (!state.isFocusTargetReady(
                    target.field,
                    target.rowIndex,
                    target.addressIndex,
                    fieldMode,
                )
            ) {
                return@LaunchedEffect
            }
            delay(150)
            bringIntoViewRequesters.getValue(target.field).bringIntoView()
            focusRequesters.getValue(target.field).requestFocus()
            pending = null
        }
    }
}

@Composable
fun rememberContactEditFieldFocus(): ContactEditFieldFocusController {
    val pendingState = remember { mutableStateOf<ContactEditFieldFocusTarget?>(null) }
    val focusRequesters = remember {
        ContactEditHiddenField.entries.associateWith { FocusRequester() }
    }
    val bringIntoViewRequesters = remember {
        ContactEditHiddenField.entries.associateWith { BringIntoViewRequester() }
    }
    return remember {
        ContactEditFieldFocusController(pendingState, focusRequesters, bringIntoViewRequesters)
    }
}
