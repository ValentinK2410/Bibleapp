package com.example.bible.data

import android.content.ContentResolver
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Phone
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Поля контакта, прочитанные из записи после [android.content.Intent.ACTION_PICK].
 * Дату рождения подставляем только если в системном контакте указан полный календарный год
 * ([Event.START_DATE] с распознаваемым годом и датой, не без года «--MM-dd»).
 */
data class ImportedPhoneContact(
    val fullName: String,
    val phone: String,
    val email: String,
    val birthEpochDay: Long?,
)

private val extraBirthDateFormats =
    arrayOf(
        DateTimeFormatter.ofPattern("yyyy-M-d"),
        DateTimeFormatter.ofPattern("yyyy.M.d"),
        DateTimeFormatter.ofPattern("yyyy/M/d"),
    )

private fun parseFullBirthdayFromAndroid(raw: String): Long? {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return null
    val today = LocalDate.now()

    fun valid(d: LocalDate) = d.year >= 1900 && !d.isAfter(today)

    runCatching { LocalDate.parse(trimmed) }.getOrNull()?.takeIf(::valid)?.toEpochDay()?.let { return it }
    for (fmt in extraBirthDateFormats) {
        runCatching { LocalDate.parse(trimmed, fmt) }.getOrNull()?.takeIf(::valid)?.toEpochDay()?.let { return it }
    }
    return null
}

private fun Cursor.colStr(column: String): String {
    val i = getColumnIndex(column)
    return if (i >= 0) getString(i).orEmpty() else ""
}

private fun pickBestPhone(cr: ContentResolver, contactId: String): String {
    fun norm(s: String) = s.replace(Regex("[^+0-9]"), "").trim()

    return cr.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.NUMBER, Phone.TYPE, Phone.IS_PRIMARY),
            "${Phone.CONTACT_ID} = ?",
            arrayOf(contactId),
            null,
        )
        ?.use { cursor ->
            data class Num(val digits: String, val display: String, val type: Int, val primary: Boolean)

            val list = ArrayList<Num>(4)
            val numberIdx = cursor.getColumnIndex(Phone.NUMBER)
            val typeIdx = cursor.getColumnIndex(Phone.TYPE)
            val primIdx = cursor.getColumnIndex(Phone.IS_PRIMARY)
            if (numberIdx < 0) return@use ""

            while (cursor.moveToNext()) {
                val raw = cursor.getString(numberIdx).orEmpty()
                val display = raw.trim()
                val n = norm(raw).ifEmpty { continue }
                val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else Phone.TYPE_OTHER
                val primary = primIdx >= 0 && cursor.getInt(primIdx) != 0
                list.add(Num(n, display, type, primary))
            }

            fun pickPrefer(vararg types: Int): Num? {
                val set = types.toSet()
                return list.firstOrNull { it.type in set }
            }

            val chosen =
                list.firstOrNull { it.primary && it.type == Phone.TYPE_MOBILE }
                    ?: list.firstOrNull { it.primary }
                    ?: pickPrefer(Phone.TYPE_MOBILE)
                    ?: pickPrefer(Phone.TYPE_MAIN, Phone.TYPE_HOME, Phone.TYPE_WORK)
                    ?: list.firstOrNull()
            return@use chosen?.display?.takeIf { it.isNotBlank() } ?: chosen?.digits.orEmpty()
        } ?: ""
}

private fun pickBestEmail(cr: ContentResolver, contactId: String): String =
    cr.query(
            Email.CONTENT_URI,
            arrayOf(Email.DATA, Email.TYPE, Email.IS_PRIMARY),
            "${Email.CONTACT_ID} = ?",
            arrayOf(contactId),
            null,
        )
        ?.use { cursor ->
            val dataIdx = cursor.getColumnIndex(Email.DATA)
            val primIdx = cursor.getColumnIndex(Email.IS_PRIMARY)
            if (dataIdx < 0) return@use ""

            data class Addr(val addr: String, val primary: Boolean)

            val list = ArrayList<Addr>(4)
            while (cursor.moveToNext()) {
                val addr = cursor.getString(dataIdx).orEmpty().trim().ifBlank { continue }
                val primary = primIdx >= 0 && cursor.getInt(primIdx) != 0
                list.add(Addr(addr, primary))
            }
            (list.firstOrNull { it.primary } ?: list.firstOrNull())?.addr.orEmpty()
        } ?: ""

private fun readBirthEpochDay(cr: ContentResolver, contactId: String): Long? =
    cr.query(
            ContactsContract.Data.CONTENT_URI,
            arrayOf(Event.START_DATE),
            "${ContactsContract.Data.CONTACT_ID} = ? AND ${ContactsContract.Data.MIMETYPE} = ? AND ${Event.TYPE} = ?",
            arrayOf(contactId, Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString()),
            null,
        )
        ?.use { cursor ->
            val ix = cursor.getColumnIndex(Event.START_DATE)
            if (ix < 0) return@use null
            while (cursor.moveToNext()) {
                val raw = cursor.getString(ix).orEmpty()
                parseFullBirthdayFromAndroid(raw)?.let { return@use it }
            }
            null
        }

fun ContentResolver.loadImportedPhoneContact(contactUri: Uri): ImportedPhoneContact? =
    query(
            contactUri,
            arrayOf(
                ContactsContract.Contacts._ID,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Contacts.DISPLAY_NAME,
            ),
            null,
            null,
            null,
        )
        ?.use cursor@{ cursor ->
            if (!cursor.moveToFirst()) return@cursor null

            val idIdx = cursor.getColumnIndexOrThrow(ContactsContract.Contacts._ID)
            val contactId = cursor.getLong(idIdx).toString()

            val nPrimary = cursor.colStr(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY).trim()
            val nFall = cursor.colStr(ContactsContract.Contacts.DISPLAY_NAME).trim()
            val displayName =
                when {
                    nPrimary.isNotEmpty() -> nPrimary
                    nFall.isNotEmpty() -> nFall
                    else -> ""
                }

            val phone = pickBestPhone(this@loadImportedPhoneContact, contactId)
            val email = pickBestEmail(this@loadImportedPhoneContact, contactId)
            val birth = readBirthEpochDay(this@loadImportedPhoneContact, contactId)

            if (displayName.isBlank() && phone.isBlank() && email.isBlank()) {
                return@cursor null
            }

            ImportedPhoneContact(
                fullName = displayName,
                phone = phone,
                email = email,
                birthEpochDay = birth,
            )
        }
