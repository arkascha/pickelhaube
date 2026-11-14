package org.rustygnome.pickelhaube

import android.Manifest
import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.Context
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.rustygnome.pickelhaube.contacts.ContactActions

/** "Call via FRITZ!Box" rows written into the phone's contacts, kept with their contact, following it, and removed again. */
@RunWith(AndroidJUnit4::class)
class ContactActionsTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val resolver get() = context.contentResolver
    private var contactRaw = -1L

    @Before
    fun setUp() {
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.READ_CONTACTS)
        automation.grantRuntimePermission(context.packageName, Manifest.permission.WRITE_CONTACTS)
        ContactActions.disable(context)
        contactRaw = addContact("Anna Test", "0171 1234567" to Phone.TYPE_MOBILE, "089 999" to Phone.TYPE_FAX_WORK)
    }

    @After
    fun tearDown() {
        ContactActions.disable(context)
        resolver.delete(
            RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true").build(),
            "${RawContacts._ID} = ?", arrayOf(contactRaw.toString()),
        )
    }

    /** A local contact (no account) with [numbers]; its raw contact id. */
    private fun addContact(name: String, vararg numbers: Pair<String, Int>): Long {
        val ops = arrayListOf(
            ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_NAME, null).withValue(RawContacts.ACCOUNT_TYPE, null).build(),
            ContentProviderOperation.newInsert(Data.CONTENT_URI).withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE).withValue(StructuredName.DISPLAY_NAME, name).build(),
        )
        numbers.forEach { (n, type) ->
            ops += ContentProviderOperation.newInsert(Data.CONTENT_URI).withValueBackReference(Data.RAW_CONTACT_ID, 0)
                .withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE).withValue(Phone.NUMBER, n).withValue(Phone.TYPE, type).build()
        }
        val results = resolver.applyBatch(ContactsContract.AUTHORITY, ops)
        return ContentUris.parseId(results[0].uri!!)
    }

    private fun contactOf(raw: Long): Long = resolver.query(
        RawContacts.CONTENT_URI, arrayOf(RawContacts.CONTACT_ID), "${RawContacts._ID} = ?", arrayOf(raw.toString()), null,
    )!!.use { it.moveToFirst(); it.getLong(0) }

    /** The action rows of the contact [raw] belongs to: number → their data URI's number as read back. */
    private fun actionNumbers(): List<String> = resolver.query(
        Data.CONTENT_URI, arrayOf(Data._ID, Data.DATA1),
        "${Data.CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?", arrayOf(contactOf(contactRaw).toString(), ContactActions.MIME_TYPE), null,
    )!!.use { c ->
        buildList {
            while (c.moveToNext()) {
                // As the contacts app hands a tapped row over to the app.
                add(ContactActions.numberOf(context, ContentUris.withAppendedId(Data.CONTENT_URI, c.getLong(0)))!!)
            }
        }
    }

    @Test
    fun rowsBesideTheCallableNumbers_followTheContact_andGoWhenSwitchedOff() {
        ContactActions.enable(context)
        assertTrue(ContactActions.isEnabled(context))
        assertTrue(ContactActions.sync(context))
        // Within the contact (kept together), the fax left out.
        assertEquals(listOf("0171 1234567"), actionNumbers())

        // A number added to the contact gets its row too; nothing new on a second run.
        resolver.insert(
            Data.CONTENT_URI,
            android.content.ContentValues().apply {
                put(Data.RAW_CONTACT_ID, contactRaw)
                put(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
                put(Phone.NUMBER, "089 123456")
                put(Phone.TYPE, Phone.TYPE_HOME)
            },
        )
        assertTrue(ContactActions.sync(context))
        assertTrue(ContactActions.sync(context))
        assertEquals(setOf("0171 1234567", "089 123456"), actionNumbers().toSet())
        assertEquals(2, actionNumbers().size)

        ContactActions.disable(context)
        assertFalse(ContactActions.isEnabled(context))
        assertEquals(emptyList<String>(), actionNumbers())
    }
}
