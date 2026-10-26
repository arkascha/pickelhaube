package org.rustygnome.pickelhaube.data

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.Settings
import androidx.core.content.ContextCompat
import org.rustygnome.pickelhaube.tr064.Phonebook
import org.rustygnome.pickelhaube.tr064.PhonebookContact
import org.rustygnome.pickelhaube.tr064.PhonebookNumber

/**
 * The contacts stored on this Android device (all accounts: local, Google, CardDAV via DAVx⁵, …),
 * presented as one more phone book next to the FRITZ!Box ones.
 */
object DeviceContacts {

    /** Id used for the device "phone book", distinct from the box' ids (0, 1, …). */
    const val BOOK_ID = -1
    const val BOOK_NAME = "This phone"

    /** Android's settings page of this app, where the contacts permission can be granted or revoked. */
    fun appSettingsIntent(context: Context): Intent =
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        )

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) ==
            PackageManager.PERMISSION_GRANTED

    /** Reads all contacts that have at least one phone number. Call off the main thread. */
    fun load(context: Context): Phonebook {
        if (!hasPermission(context)) return Phonebook(BOOK_ID, BOOK_NAME, emptyList())

        data class Entry(val name: String, val numbers: MutableList<PhonebookNumber>)
        val byContact = LinkedHashMap<Long, Entry>()

        val projection = arrayOf(
            Phone.CONTACT_ID,
            Phone.DISPLAY_NAME_PRIMARY,
            Phone.NUMBER,
            Phone.TYPE,
            Phone.LABEL,
        )
        context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, null)?.use { c ->
            val idCol = c.getColumnIndexOrThrow(Phone.CONTACT_ID)
            val nameCol = c.getColumnIndexOrThrow(Phone.DISPLAY_NAME_PRIMARY)
            val numberCol = c.getColumnIndexOrThrow(Phone.NUMBER)
            val typeCol = c.getColumnIndexOrThrow(Phone.TYPE)
            val labelCol = c.getColumnIndexOrThrow(Phone.LABEL)
            while (c.moveToNext()) {
                val number = c.getString(numberCol)?.trim().orEmpty()
                if (number.isEmpty()) continue
                val id = c.getLong(idCol)
                val name = c.getString(nameCol)?.trim().orEmpty()
                // The kinds the box knows by its tags, so both are labelled alike; others by Android's label.
                val androidType = c.getInt(typeCol)
                val label = BOX_TYPES[androidType]
                    ?: Phone.getTypeLabel(context.resources, androidType, c.getString(labelCol)).toString()
                val entry = byContact.getOrPut(id) { Entry(name, mutableListOf()) }
                // The same number is often stored twice (e.g. synced from two accounts).
                val digits = number.filter { it.isDigit() || it == '+' }
                if (entry.numbers.none { it.number.filter { ch -> ch.isDigit() || ch == '+' } == digits }) {
                    entry.numbers += PhonebookNumber(number, label)
                }
            }
        }

        val contacts = byContact.values.map { e ->
            PhonebookContact(e.name.ifBlank { e.numbers.first().number }, e.numbers, BOOK_NAME)
        }.sortedBy { it.name.lowercase() }
        return Phonebook(BOOK_ID, BOOK_NAME, contacts)
    }

    /**
     * Opens the contacts app with a new contact filled in from [contact] – name and numbers with
     * their kind –, to be saved there (in the account chosen there). Needs no permission.
     */
    fun insertIntent(contact: PhonebookContact): Intent {
        val rows = ArrayList(
            contact.numbers.map { n ->
                ContentValues().apply {
                    put(ContactsContract.Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                    put(Phone.NUMBER, n.number)
                    put(Phone.TYPE, ANDROID_TYPES[n.type.lowercase()] ?: Phone.TYPE_OTHER)
                }
            },
        )
        return Intent(Intent.ACTION_INSERT, ContactsContract.Contacts.CONTENT_URI).apply {
            // A contact without a name is shown by its number: then leave the name to fill in.
            if (contact.numbers.none { it.number == contact.name }) putExtra(ContactsContract.Intents.Insert.NAME, contact.name)
            putParcelableArrayListExtra(ContactsContract.Intents.Insert.DATA, rows)
        }
    }

    /** Android's kinds of numbers as the box tags them. */
    private val BOX_TYPES = mapOf(
        Phone.TYPE_HOME to "home",
        Phone.TYPE_MOBILE to "mobile",
        Phone.TYPE_WORK to "work",
        Phone.TYPE_FAX_WORK to "fax_work",
        Phone.TYPE_OTHER to "other",
    )

    private val ANDROID_TYPES = BOX_TYPES.entries.associate { (android, box) -> box to android } + ("fax" to Phone.TYPE_FAX_WORK)
}
