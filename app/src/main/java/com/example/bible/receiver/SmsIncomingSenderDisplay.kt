package com.example.bible.receiver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/**
 * Имя отправителя для озвучки: при [READ_CONTACTS] и совпадении в телефонной книге — отображаемое имя контакта.
 */
internal object SmsIncomingSenderDisplay {
    fun resolve(context: Context, rawAddress: String?): String {
        val trimmed = rawAddress?.trim().orEmpty()
        if (trimmed.isEmpty()) return ""

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return trimmed
        }

        if (!trimmed.any { it.isDigit() }) {
            return trimmed
        }

        return runCatching {
            val lookupUri =
                Uri.withAppendedPath(
                    ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                    Uri.encode(trimmed),
                )
            context.contentResolver.query(
                lookupUri,
                arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val ix = c.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (ix >= 0) {
                        val name = c.getString(ix)?.trim().orEmpty()
                        if (name.isNotEmpty()) return name
                    }
                }
            }
            trimmed
        }.getOrElse { trimmed }
    }
}
