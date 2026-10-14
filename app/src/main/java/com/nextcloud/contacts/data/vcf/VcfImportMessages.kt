package com.nextcloud.contacts.data.vcf

import android.content.Context
import com.nextcloud.contacts.R
import com.nextcloud.contacts.data.ContactRepository

fun formatVcfImportMessage(context: Context, result: ContactRepository.VcfImportResult): String {
    val resources = context.resources
    val parts = buildList {
        if (result.imported > 0) {
            add(resources.getQuantityString(R.plurals.vcf_imported, result.imported, result.imported))
        }
        if (result.updated > 0) {
            add(resources.getQuantityString(R.plurals.vcf_updated, result.updated, result.updated))
        }
        if (result.failed > 0) {
            add(resources.getQuantityString(R.plurals.vcf_failed, result.failed, result.failed))
        }
    }
    return when {
        parts.isEmpty() -> context.getString(R.string.msg_no_contacts_in_file)
        else -> parts.joinToString(context.getString(R.string.vcf_joiner))
            .replaceFirstChar { it.uppercase() }
    }
}
