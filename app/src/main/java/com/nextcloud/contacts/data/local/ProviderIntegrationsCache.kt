package com.nextcloud.contacts.data.local

import com.nextcloud.contacts.platform.ContactsProviderIntegrations
import com.nextcloud.contacts.platform.MessagingAction
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cached Connected-apps discovery for a contact's phone numbers (stale-while-revalidate).
 */
object ProviderIntegrationsCache {

    fun seedAppGroups(raw: String?, phones: List<String>): List<ContactsProviderIntegrations.AppGroup> =
        getGroups(raw, phones).ifEmpty { ContactsProviderIntegrations.cachedAppGroups(phones) }

    fun getGroups(raw: String?, phones: List<String>): List<ContactsProviderIntegrations.AppGroup> {
        val currentKey = ContactsProviderIntegrations.phonesCacheKey(phones) ?: return emptyList()
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return emptyList()
        return runCatching {
            val root = JSONObject(trimmed)
            val storedKey = root.optString("phonesKey").trim()
            if (storedKey.isBlank() || storedKey != currentKey) return emptyList()
            decodeGroups(root.optJSONArray("groups"))
        }.getOrDefault(emptyList())
    }

    fun withGroups(raw: String?, phones: List<String>, groups: List<ContactsProviderIntegrations.AppGroup>): String {
        val phonesKey = ContactsProviderIntegrations.phonesCacheKey(phones) ?: return raw.orEmpty()
        val encoded = JSONObject()
            .put("phonesKey", phonesKey)
            .put("groups", encodeGroups(groups))
        return encoded.toString()
    }

    fun merge(winnerRaw: String?, loserRaw: String?): String {
        if (!winnerRaw.isNullOrBlank()) return winnerRaw
        return loserRaw.orEmpty()
    }

    private fun encodeGroups(groups: List<ContactsProviderIntegrations.AppGroup>): JSONArray {
        val array = JSONArray()
        groups.forEach { group ->
            val actions = JSONArray()
            group.actions.forEach { action ->
                actions.put(
                    JSONObject()
                        .put("dataId", action.dataId)
                        .put("mimeType", action.mimeType)
                        .put("label", action.label)
                        .put("detail", action.detail.orEmpty())
                        .put("accountType", action.accountType.orEmpty())
                        .put("accountName", action.accountName.orEmpty())
                        .put("packageName", action.packageName.orEmpty())
                        .put("messagingAction", action.messagingAction?.name.orEmpty()),
                )
            }
            array.put(
                JSONObject()
                    .put("key", group.key)
                    .put("label", group.label)
                    .put("packageName", group.packageName.orEmpty())
                    .put("accountType", group.accountType.orEmpty())
                    .put("actions", actions),
            )
        }
        return array
    }

    private fun decodeGroups(array: JSONArray?): List<ContactsProviderIntegrations.AppGroup> {
        if (array == null || array.length() == 0) return emptyList()
        return buildList {
            for (i in 0 until array.length()) {
                val groupJson = array.optJSONObject(i) ?: continue
                val key = groupJson.optString("key").trim()
                if (key.isBlank()) continue
                val label = groupJson.optString("label").trim()
                val actionsArray = groupJson.optJSONArray("actions") ?: JSONArray()
                val actions = buildList {
                    for (j in 0 until actionsArray.length()) {
                        val actionJson = actionsArray.optJSONObject(j) ?: continue
                        val dataId = actionJson.optLong("dataId")
                        val mimeType = actionJson.optString("mimeType").trim()
                        if (mimeType.isBlank()) continue
                        val messagingRaw = actionJson.optString("messagingAction").trim()
                        val messagingAction = messagingRaw.takeIf { it.isNotBlank() }?.let { raw ->
                            runCatching { MessagingAction.valueOf(raw) }.getOrNull()
                        }
                        add(
                            ContactsProviderIntegrations.Action(
                                dataId = dataId,
                                mimeType = mimeType,
                                label = actionJson.optString("label").trim(),
                                detail = actionJson.optString("detail").trim().takeIf { it.isNotBlank() },
                                accountType = actionJson.optString("accountType").trim().takeIf { it.isNotBlank() },
                                accountName = actionJson.optString("accountName").trim().takeIf { it.isNotBlank() },
                                packageName = actionJson.optString("packageName").trim().takeIf { it.isNotBlank() },
                                messagingAction = messagingAction,
                            ),
                        )
                    }
                }
                if (actions.isEmpty()) continue
                add(
                    ContactsProviderIntegrations.AppGroup(
                        key = key,
                        label = label.ifBlank { key },
                        packageName = groupJson.optString("packageName").trim().takeIf { it.isNotBlank() },
                        accountType = groupJson.optString("accountType").trim().takeIf { it.isNotBlank() },
                        actions = actions,
                    ),
                )
            }
        }
    }
}
