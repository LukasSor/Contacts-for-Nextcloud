package com.nextcloud.contacts.auth

data class AccountCredentials(
    val server: String,
    val loginName: String,
    val appPassword: String,
)
