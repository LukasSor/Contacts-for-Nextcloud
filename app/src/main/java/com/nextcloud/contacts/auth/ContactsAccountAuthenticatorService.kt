package com.nextcloud.contacts.auth

import android.app.Service
import android.content.Intent
import android.os.IBinder

class ContactsAccountAuthenticatorService : Service() {
    private val authenticator by lazy { ContactsAccountAuthenticator(this) }

    override fun onBind(intent: Intent?): IBinder = authenticator.iBinder
}
