package com.nextcloud.contacts.ui.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.dropUnlessResumed
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nextcloud.contacts.ContactsApp
import com.nextcloud.contacts.platform.ContactsLaunch
import com.nextcloud.contacts.ui.screens.AccountSetupScreen
import com.nextcloud.contacts.ui.screens.ContactDetailScreen
import com.nextcloud.contacts.ui.screens.ContactEditScreen
import com.nextcloud.contacts.ui.screens.ContactListScreen
import com.nextcloud.contacts.ui.screens.OrganizeBinScreen
import com.nextcloud.contacts.ui.screens.OrganizeCalendarScreen
import com.nextcloud.contacts.ui.screens.OrganizeImportScreen
import com.nextcloud.contacts.ui.screens.OrganizeMergeScreen
import com.nextcloud.contacts.ui.screens.OrganizeScreen
import com.nextcloud.contacts.ui.screens.SettingsScreen
import com.nextcloud.contacts.ui.screens.DateCountdownScreen
import com.nextcloud.contacts.ui.screens.TimeZoneClockScreen
import com.nextcloud.contacts.ui.navigation.ContactListPendingGroupFilter
import com.nextcloud.contacts.util.ContactDateCountdownField
import com.nextcloud.contacts.util.parseContactDateForCountdown
import android.net.Uri
import com.nextcloud.contacts.ui.screens.PostLoginSetupScreen
import com.nextcloud.contacts.ui.setup.PostLoginSetup
import com.nextcloud.contacts.ui.viewmodel.AccountSetupViewModel
import com.nextcloud.contacts.ui.viewmodel.ContactDetailViewModel
import com.nextcloud.contacts.ui.viewmodel.ContactListViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun ContactsNavHost(
    incomingRequest: IncomingContactsRequest? = null,
    isPickerMode: Boolean = false,
    onRequestHandled: () -> Unit = {},
    onContactPicked: (Long) -> Unit = {},
    onHomeContentReady: () -> Unit = {},
) {
    val context = LocalContext.current
    val container = (context.applicationContext as ContactsApp).container
    val isLoggedIn = container.credentialStore.isLoggedIn()
    val setupSkipped = container.userPreferences.isSetupSkipped()
    val setupCompleted = container.userPreferences.isPostLoginSetupComplete()
    val navController = rememberNavController()
    val startDestination = PostLoginSetup.startDestination(
        loggedIn = isLoggedIn,
        loginSkipped = setupSkipped,
        setupCompleted = setupCompleted,
    )
    val currentBackStackEntry by navController.currentBackStackEntryAsState()
    // A deep link can open detail before the list is ready. Sync still waits for a screen.
    LaunchedEffect(currentBackStackEntry?.destination?.route) {
        val route = currentBackStackEntry?.destination?.route ?: return@LaunchedEffect
        if (route != Routes.CONTACTS) {
            onHomeContentReady()
        }
    }

    LaunchedEffect(incomingRequest) {
        val request = incomingRequest ?: return@LaunchedEffect
        if (!container.credentialStore.isLoggedIn()) {
            onRequestHandled()
            return@LaunchedEffect
        }
        val showSetup = PostLoginSetup.startDestination(
            loggedIn = true,
            loginSkipped = container.userPreferences.isSetupSkipped(),
            setupCompleted = container.userPreferences.isPostLoginSetupComplete(),
        ) == Routes.POST_LOGIN_SETUP
        when (val launch = request.launch) {
            ContactsLaunch.Home,
            ContactsLaunch.List,
            -> {
                if (showSetup) {
                    if (navController.currentDestination?.route != Routes.POST_LOGIN_SETUP) {
                        navController.navigate(Routes.POST_LOGIN_SETUP) {
                            popUpTo(0) { inclusive = true }
                            launchSingleTop = true
                            restoreState = false
                        }
                    }
                } else if (navController.currentDestination?.route != Routes.CONTACTS) {
                    // Open the list without clearing a restored screen above it.
                    navController.navigate(Routes.CONTACTS) {
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            }
            ContactsLaunch.PickContact -> navController.navigate(Routes.CONTACTS) {
                launchSingleTop = true
                restoreState = true
            }
            is ContactsLaunch.InsertContact -> {
                ContactEditPrefill.input = launch.prefilled
                navController.navigate(Routes.contactEdit(0))
            }
            is ContactsLaunch.ViewContact -> {
                val appId = resolveAppContactId(container, request.systemUri)
                if (appId != null) {
                    navController.navigate(Routes.contactDetail(appId))
                } else {
                    navController.navigate(Routes.CONTACTS)
                }
            }
            is ContactsLaunch.EditContact -> {
                val appId = resolveAppContactId(container, request.systemUri)
                if (appId != null) {
                    navController.navigate(Routes.contactEdit(appId))
                } else {
                    ContactEditPrefill.input = null
                    navController.navigate(Routes.contactEdit(0))
                }
            }
        }
        onRequestHandled()
    }

    LaunchedEffect(currentBackStackEntry?.destination?.route) {
        val route = currentBackStackEntry?.destination?.route ?: return@LaunchedEffect
        if (route == Routes.POST_LOGIN_SETUP) return@LaunchedEffect
        val forced = PostLoginSetup.startDestination(
            loggedIn = container.credentialStore.isLoggedIn(),
            loginSkipped = container.userPreferences.isSetupSkipped(),
            setupCompleted = container.userPreferences.isPostLoginSetupComplete(),
        )
        if (forced != Routes.POST_LOGIN_SETUP) return@LaunchedEffect
        navController.navigate(Routes.POST_LOGIN_SETUP) {
            popUpTo(0) { inclusive = true }
            launchSingleTop = true
            restoreState = false
        }
    }

    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.SETUP) {
            val viewModel: AccountSetupViewModel = viewModel()
            AccountSetupScreen(
                onConnected = {
                    navController.navigate(PostLoginSetup.afterLoginDestination()) {
                        popUpTo(0) { inclusive = true }
                        launchSingleTop = true
                        restoreState = false
                    }
                },
                onSkip = {
                    navController.navigate(Routes.CONTACTS) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                viewModel = viewModel,
            )
        }

        composable(Routes.POST_LOGIN_SETUP) {
            PostLoginSetupScreen(
                onDone = {
                    navController.navigate(Routes.CONTACTS) {
                        popUpTo(0) { inclusive = true }
                    }
                },
            )
        }

        composable(
            route = Routes.CONTACTS,
            enterTransition = { EnterTransition.None },
            popEnterTransition = { EnterTransition.None },
        ) {
            ContactListScreen(
                onContactClick = { id ->
                    if (isPickerMode) {
                        onContactPicked(id)
                    } else {
                        navController.navigate(Routes.contactDetail(id))
                    }
                },
                onAddContact = {
                    navController.navigate(Routes.contactEdit(0))
                },
                onOpenOrganize = dropUnlessResumed {
                    navController.navigate(Routes.ORGANIZE) {
                        launchSingleTop = true
                    }
                },
                onOpenSettings = dropUnlessResumed {
                    navController.navigate(Routes.SETTINGS) {
                        launchSingleTop = true
                    }
                },
                isPickerMode = isPickerMode,
                onContactsReady = onHomeContentReady,
            )
        }

        composable(
            route = Routes.CONTACT_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
            popExitTransition = { ExitTransition.None },
        ) { backStackEntry ->
            val contactId = backStackEntry.arguments?.getString("id")?.toLongOrNull() ?: 0L
            val detailViewModel: ContactDetailViewModel = viewModel(backStackEntry)
            val listBackStackEntry = runCatching {
                navController.getBackStackEntry(Routes.CONTACTS)
            }.getOrNull()
            val listViewModel: ContactListViewModel? = listBackStackEntry?.let { entry ->
                viewModel(entry)
            }
            ContactDetailScreen(
                viewModel = detailViewModel,
                onBack = dropUnlessResumed { navController.popBackStack() },
                onEdit = {
                    navController.navigate(Routes.contactEdit(contactId))
                },
                onOpenContact = { id -> navController.navigate(Routes.contactDetail(id)) },
                onOpenTimeZoneClock = { zoneId ->
                    navController.navigate(Routes.timeZoneClock(zoneId))
                },
                onOpenDateCountdown = { field, dateRaw, contactName ->
                    navController.navigate(Routes.dateCountdown(field.name, dateRaw, contactName))
                },
                onGroupFilterClick = { group ->
                    if (listViewModel != null) {
                        listViewModel.setGroupFilterOnly(group)
                        navController.popBackStack(Routes.CONTACTS, inclusive = false)
                    } else {
                        ContactListPendingGroupFilter.set(group)
                        navController.navigate(Routes.CONTACTS) {
                            launchSingleTop = true
                        }
                    }
                },
            )
        }

        composable(
            route = Routes.DATE_COUNTDOWN,
            arguments = listOf(
                navArgument("kind") { type = NavType.StringType },
                navArgument("dateRaw") { type = NavType.StringType },
                navArgument("contactName") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val kindName = Uri.decode(backStackEntry.arguments?.getString("kind").orEmpty())
            val dateRaw = Uri.decode(backStackEntry.arguments?.getString("dateRaw").orEmpty())
            val contactName = Uri.decode(backStackEntry.arguments?.getString("contactName").orEmpty())
            val field = runCatching { ContactDateCountdownField.valueOf(kindName) }.getOrNull()
            val parsed = parseContactDateForCountdown(dateRaw)
            if (field != null && parsed != null) {
                DateCountdownScreen(
                    field = field,
                    parsedDate = parsed,
                    contactName = contactName,
                    onBack = { navController.popBackStack() },
                )
            } else {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }

        composable(
            route = Routes.TIME_ZONE_CLOCK,
            arguments = listOf(navArgument("zoneId") { type = NavType.StringType }),
        ) { backStackEntry ->
            val encoded = backStackEntry.arguments?.getString("zoneId").orEmpty()
            val zoneId = Uri.decode(encoded)
            TimeZoneClockScreen(
                primaryZoneId = zoneId,
                onBack = { navController.popBackStack() },
            )
        }

        composable(
            route = Routes.CONTACT_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.StringType }),
        ) {
            ContactEditScreen(
                onBack = { navController.popBackStack() },
                onSaved = { createdContactId ->
                    if (createdContactId != null) {
                        // Create opens the editor from the list, so a pop would skip detail.
                        // Show the new contact and remove the editor so Back returns to the list.
                        navController.navigate(Routes.contactDetail(createdContactId)) {
                            popUpTo(Routes.CONTACT_EDIT) { inclusive = true }
                        }
                    } else {
                        navController.popBackStack()
                    }
                },
                onDeleted = {
                    // Edit sits on the detail screen. Pop both so a deleted contact
                    // is not left as an empty detail page.
                    if (navController.currentDestination?.route != Routes.CONTACTS) {
                        val poppedToList = navController.popBackStack(Routes.CONTACTS, inclusive = false)
                        if (!poppedToList) navController.popBackStack()
                    }
                },
            )
        }

        composable(Routes.ORGANIZE) {
            OrganizeScreen(
                onBack = { navController.popBackStack() },
                onOpenBin = dropUnlessResumed {
                    navController.navigate(Routes.ORGANIZE_BIN) { launchSingleTop = true }
                },
                onOpenImportDevice = dropUnlessResumed {
                    navController.navigate(Routes.ORGANIZE_IMPORT) { launchSingleTop = true }
                },
                onOpenCalendar = dropUnlessResumed {
                    navController.navigate(Routes.ORGANIZE_CALENDAR) { launchSingleTop = true }
                },
                onOpenMerge = dropUnlessResumed {
                    navController.navigate(Routes.ORGANIZE_MERGE) { launchSingleTop = true }
                },
            )
        }

        composable(Routes.ORGANIZE_BIN) {
            OrganizeBinScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ORGANIZE_IMPORT) {
            OrganizeImportScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ORGANIZE_CALENDAR) {
            OrganizeCalendarScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.ORGANIZE_MERGE) {
            OrganizeMergeScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onDisconnected = {
                    navController.navigate(Routes.SETUP) {
                        popUpTo(Routes.CONTACTS) { inclusive = true }
                    }
                },
                onConnectAccount = {
                    navController.navigate(Routes.SETUP)
                },
            )
        }
    }
}

private suspend fun resolveAppContactId(
    container: com.nextcloud.contacts.AppContainer,
    systemUri: android.net.Uri?,
): Long? {
    if (systemUri == null) return null
    return withContext(Dispatchers.IO) {
        container.systemContactsExporter.lookupAppContactId(systemUri)
    }
}
