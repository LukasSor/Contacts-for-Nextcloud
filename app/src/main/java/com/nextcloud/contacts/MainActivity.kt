package com.nextcloud.contacts

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.lifecycleScope
import com.nextcloud.contacts.data.prefs.ThemeMode
import com.nextcloud.contacts.platform.ContactPickKind
import com.nextcloud.contacts.platform.ContactsIntentRouter
import com.nextcloud.contacts.platform.ContactsLaunch
import com.nextcloud.contacts.platform.PickedContactField
import com.nextcloud.contacts.platform.pickedFieldText
import com.nextcloud.contacts.ui.util.CalendarPermissionHost
import com.nextcloud.contacts.ui.util.ContactsPermissionHost
import com.nextcloud.contacts.ui.navigation.ContactEditPrefill
import com.nextcloud.contacts.ui.navigation.ContactsNavHost
import com.nextcloud.contacts.ui.navigation.IncomingContactsRequest
import com.nextcloud.contacts.ui.navigation.Routes
import com.nextcloud.contacts.ui.setup.PostLoginSetup
import com.nextcloud.contacts.ui.theme.NextcloudContactsTheme
import android.view.Choreographer
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    private var incomingRequest by mutableStateOf<IncomingContactsRequest?>(null)
    private var isPickerMode by mutableStateOf(false)
    private var pickKind by mutableStateOf(ContactPickKind.CONTACT)

    /** Held until the list's first frame. Login and setup clear it at once. */
    private val keepSplashOnScreen = AtomicBoolean(true)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ContactsApp).container

        // Splash waits only when the list is the start destination.
        val opensContactList = PostLoginSetup.startDestination(
            loggedIn = container.credentialStore.isLoggedIn(),
            loginSkipped = container.userPreferences.isSetupSkipped(),
            setupCompleted = container.userPreferences.isPostLoginSetupComplete(),
        ) == Routes.CONTACTS
        if (!opensContactList) {
            keepSplashOnScreen.set(false)
            (application as ContactsApp).markUiInteractive()
        }
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen.get() }
        splashScreen.setOnExitAnimationListener { splashScreenView ->
            splashScreenView.remove()
        }

        if (savedInstanceState == null) {
            // A config change restores the back stack. Parsing the intent again would reset it.
            incomingRequest = parseRequest(intent)
            Choreographer.getInstance().postFrameCallback {
                container.loginFlowCoordinator.cancelLogin()
            }
        } else {
            isPickerMode = savedInstanceState.getBoolean(KEY_PICKER_MODE, false)
            pickKind = savedInstanceState.getString(KEY_PICK_KIND)
                ?.let { runCatching { ContactPickKind.valueOf(it) }.getOrNull() }
                ?: ContactPickKind.CONTACT
            // Splash is not installed again after a config change.
            keepSplashOnScreen.set(false)
            (application as ContactsApp).markUiInteractive()
        }

        setContent {
            val themeMode by container.userPreferences.themeMode.collectAsState(
                initial = ThemeMode.SYSTEM,
            )
            val useDynamicColor by container.userPreferences.useDynamicColor.collectAsState(
                initial = true,
            )

            NextcloudContactsTheme(
                themeMode = themeMode,
                useDynamicColor = useDynamicColor,
            ) {
                ContactsPermissionHost(container.contactsPermissionRequester)
                CalendarPermissionHost(container.calendarPermissionRequester)
                Surface(modifier = Modifier.fillMaxSize()) {
                    ContactsNavHost(
                        incomingRequest = incomingRequest,
                        isPickerMode = isPickerMode,
                        onRequestHandled = { incomingRequest = null },
                        onContactPicked = ::finishWithPickedContact,
                        onPickerCancel = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        },
                        onHomeContentReady = {
                            keepSplashOnScreen.set(false)
                            (application as ContactsApp).markUiInteractive()
                        },
                    )
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_PICKER_MODE, isPickerMode)
        outState.putString(KEY_PICK_KIND, pickKind.name)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        incomingRequest = parseRequest(intent)
    }

    private fun parseRequest(intent: Intent): IncomingContactsRequest? {
        val launch = ContactsIntentRouter.parse(intent)
        val isLauncher = intent.action == Intent.ACTION_MAIN &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER)
        if (isLauncher) return null
        isPickerMode = launch is ContactsLaunch.PickContact
        pickKind = (launch as? ContactsLaunch.PickContact)?.kind ?: ContactPickKind.CONTACT
        return IncomingContactsRequest(
            launch = launch,
            systemUri = ContactsIntentRouter.systemUri(intent),
        )
    }

    private fun finishWithPickedContact(appContactId: Long) {
        val container = (application as ContactsApp).container
        val kind = pickKind
        lifecycleScope.launch {
            val contact = withContext(Dispatchers.IO) {
                container.contactRepository.getContact(appContactId)
            }
            if (contact == null) {
                setResult(Activity.RESULT_CANCELED)
                finish()
                return@launch
            }
            // CoMaps queries the result for StructuredPostal.FORMATTED_ADDRESS (data1)
            // and does not hold READ_CONTACTS. A system data URI is returned only when
            // this app can grant read on our own account's row. Otherwise the result
            // is our content URI, which serves that same column.
            val systemUri = withContext(Dispatchers.IO) {
                runCatching {
                    container.systemContactsExporter.externalPickUri(appContactId, kind)
                }.getOrNull()
            }
            val uri = when {
                systemUri != null && kind == ContactPickKind.CONTACT -> systemUri
                systemUri != null && grantReadToCaller(systemUri) -> systemUri
                kind == ContactPickKind.CONTACT -> null
                else -> {
                    val value = pickedFieldText(contact, kind)
                    if (value.isNullOrBlank()) {
                        container.uiNotices.post(getString(R.string.msg_pick_no_detail))
                        return@launch
                    }
                    PickedContactField.store(value, contact.contact.displayName)
                }
            }
            if (uri == null) {
                setResult(Activity.RESULT_CANCELED)
            } else {
                grantReadToCaller(uri)
                setResult(Activity.RESULT_OK, grantedReadIntent(uri))
            }
            finish()
        }
    }

    private fun grantReadToCaller(uri: Uri): Boolean {
        val caller = callingPackage ?: return false
        return try {
            grantUriPermission(caller, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        } catch (_: SecurityException) {
            false
        }
    }

    private fun grantedReadIntent(uri: Uri): Intent =
        Intent().apply {
            data = uri
            clipData = ClipData.newRawUri("contact", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

    companion object {
        private const val KEY_PICKER_MODE = "picker_mode"
        private const val KEY_PICK_KIND = "pick_kind"

        fun clearPrefill() {
            ContactEditPrefill.input = null
        }
    }
}
