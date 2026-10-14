package com.nextcloud.contacts.util

object HttpStatuses {
    private val httpStatus = Regex("HTTP (\\d+)")

    fun codeString(message: String): String? =
        httpStatus.find(message)?.groupValues?.getOrNull(1)

    fun code(message: String?): Int? = message?.let { codeString(it) }?.toIntOrNull()
}
