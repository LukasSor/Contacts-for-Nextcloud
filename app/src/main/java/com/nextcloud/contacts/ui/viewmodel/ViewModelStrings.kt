package com.nextcloud.contacts.ui.viewmodel

import android.app.Application
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel

internal fun AndroidViewModel.str(@StringRes id: Int, vararg args: Any): String {
    val app = getApplication<Application>()
    return if (args.isEmpty()) app.getString(id) else app.getString(id, *args)
}
