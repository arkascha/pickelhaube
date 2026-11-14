package org.rustygnome.pickelhaube.contacts

import android.Manifest
import android.accounts.Account
import android.accounts.AccountManager
import android.content.ContentProviderOperation
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.AggregationExceptions
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import android.util.Log
import androidx.core.content.ContextCompat
import org.rustygnome.pickelhaube.R

/**
 * "Call via FRITZ!Box" in the phone's own contacts app: a row beside each contact's numbers that opens
 * Pickelhaube with the number, the way messengers add theirs.
 *
 * Android shows such rows only for an account, so there is one – "Pickelhaube" under Settings › Accounts,
 * with no login and nothing synced anywhere. Each contact with a callable number gets a raw contact
 * in it holding one row per number, kept together with the contact. Whether the feature is on is
 * whether that account exists; switching it off removes the account, and Android its rows with it.
 *
 * The rows follow the contacts by a job that runs when they change ([ContactActionsJob]).
 */
object ContactActions {

    const val ACCOUNT_TYPE = "org.rustygnome.pickelhaube.contacts"
    const val MIME_TYPE = "vnd.android.cursor.item/vnd.org.rustygnome.pickelhaube.call"
    private const val TAG = "ContactActions"

    private fun account(context: Context) = Account(context.getString(R.string.app_name), ACCOUNT_TYPE)

    fun hasPermission(context: Context): Boolean =
        listOf(Manifest.permission.READ_CONTACTS, Manifest.permission.WRITE_CONTACTS).all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }

    /** The account is there – the feature is on (whether it may still write is [hasPermission]). */
    fun isEnabled(context: Context): Boolean =
        AccountManager.get(context).getAccountsByType(ACCOUNT_TYPE).isNotEmpty()

    /** Adds the account and fills in the rows. Call with the contacts permissions granted. */
    fun enable(context: Context) {
        val manager = AccountManager.get(context)
        if (manager.getAccountsByType(ACCOUNT_TYPE).isEmpty()) {
            manager.addAccountExplicitly(account(context), null, null)
        }
        ContactActionsJob.scheduleNow(context)
    }

    /**
     * Stops the job, removes the rows and then the account. Android would remove the rows with the
     * account by itself too, but only a while later. Call off the main thread.
     */
    fun disable(context: Context) {
        ContactActionsJob.cancel(context)
        val manager = AccountManager.get(context)
        manager.getAccountsByType(ACCOUNT_TYPE).forEach { account ->
            runCatching {
                context.contentResolver.delete(
                    RawContacts.CONTENT_URI.asSyncAdapter(account),
                    "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ?",
                    arrayOf(account.type, account.name),
                )
            }
            manager.removeAccountExplicitly(account)
        }
    }

    /**
     * At app start, off the main thread: brings the rows in line (the language may have changed) and
     * keeps the job around while the feature is on (it does not outlive a restart by itself).
     */
    fun ensureScheduled(context: Context) {
        if (!isEnabled(context)) return
        sync(context)
        ContactActionsJob.scheduleWatch(context)
    }

    /**
     * Brings the rows in line with the contacts. Call off the main thread. Returns false if it could
     * not (no permission, the account gone), true otherwise – also when there was nothing to change.
     * One at a time: the job and Android's own sync may start at once (as on switching it on), and
     * two runs side by side would both add the same contacts.
     */
    @Synchronized
    fun sync(context: Context): Boolean {
        if (!hasPermission(context)) return false
        val account = AccountManager.get(context).getAccountsByType(ACCOUNT_TYPE).firstOrNull() ?: return false
        return try {
            val detail = { number: String -> context.getString(R.string.contact_action_detail, number) }
            val wanted = ContactActionsPlan.wanted(readNumbers(context), ACCOUNT_TYPE, detail)
            val plan = ContactActionsPlan.plan(wanted, readExisting(context, account))
            if (!plan.isEmpty) apply(context, account, plan)
            true
        } catch (e: Exception) {
            // A permission taken back meanwhile, the contacts provider busy: the next run tries again.
            Log.w(TAG, "sync failed", e)
            false
        }
    }

    /** The number of an action row tapped in the contacts app, or null if [uri] is no such row. */
    fun numberOf(context: Context, uri: Uri): String? {
        if (uri.authority != ContactsContract.AUTHORITY) return null
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null
        return runCatching {
            context.contentResolver.query(uri, arrayOf(Data.MIMETYPE, Data.DATA1), null, null, null)?.use { c ->
                if (c.moveToFirst() && c.getString(0) == MIME_TYPE) c.getString(1) else null
            }
        }.getOrNull()
    }

    private fun Uri.asSyncAdapter(account: Account): Uri = buildUpon()
        .appendQueryParameter(ContactsContract.CALLER_IS_SYNCADAPTER, "true")
        .appendQueryParameter(RawContacts.ACCOUNT_NAME, account.name)
        .appendQueryParameter(RawContacts.ACCOUNT_TYPE, account.type)
        .build()

    private fun readNumbers(context: Context): List<ContactActionsPlan.PhoneNumber> {
        val numbers = mutableListOf<ContactActionsPlan.PhoneNumber>()
        val projection = arrayOf(Phone.CONTACT_ID, Phone.RAW_CONTACT_ID, Phone.NUMBER, Phone.TYPE, RawContacts.ACCOUNT_TYPE)
        context.contentResolver.query(Phone.CONTENT_URI, projection, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                numbers += ContactActionsPlan.PhoneNumber(
                    contactId = c.getLong(0),
                    rawContactId = c.getLong(1),
                    number = c.getString(2).orEmpty(),
                    type = c.getInt(3),
                    accountType = c.getString(4),
                )
            }
        }
        return numbers
    }

    private fun readExisting(context: Context, account: Account): List<ContactActionsPlan.Existing> {
        val rows = HashMap<Long, MutableList<ContactActionsPlan.Row>>()
        context.contentResolver.query(
            Data.CONTENT_URI.asSyncAdapter(account),
            arrayOf(Data.RAW_CONTACT_ID, Data.DATA1, Data.DATA3),
            "${Data.MIMETYPE} = ? AND ${RawContacts.ACCOUNT_TYPE} = ?",
            arrayOf(MIME_TYPE, account.type),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                rows.getOrPut(c.getLong(0)) { mutableListOf() } += ContactActionsPlan.Row(c.getString(1).orEmpty(), c.getString(2).orEmpty())
            }
        }
        val existing = mutableListOf<ContactActionsPlan.Existing>()
        context.contentResolver.query(
            RawContacts.CONTENT_URI.asSyncAdapter(account),
            arrayOf(RawContacts._ID, RawContacts.SYNC1, RawContacts.DELETED),
            "${RawContacts.ACCOUNT_TYPE} = ? AND ${RawContacts.ACCOUNT_NAME} = ?",
            arrayOf(account.type, account.name),
            null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                existing += ContactActionsPlan.Existing(id, c.getString(1)?.toLongOrNull(), c.getInt(2) != 0, rows[id].orEmpty())
            }
        }
        return existing
    }

    private fun apply(context: Context, account: Account, plan: ContactActionsPlan.Plan) {
        val rawUri = RawContacts.CONTENT_URI.asSyncAdapter(account)
        val dataUri = Data.CONTENT_URI.asSyncAdapter(account)
        // Each change as a group of operations built for the index its first one gets in the batch:
        // back references count from the start of the batch, and a batch never splits a group.
        val groups = mutableListOf<Pair<Int, (Int) -> List<ContentProviderOperation>>>()

        plan.remove.forEach { id ->
            groups += 1 to { _ ->
                listOf(ContentProviderOperation.newDelete(rawUri).withSelection("${RawContacts._ID} = ?", arrayOf(id.toString())).build())
            }
        }
        plan.replace.forEach { (id, rows) ->
            groups += (1 + rows.size) to { _ ->
                listOf(
                    ContentProviderOperation.newDelete(dataUri)
                        .withSelection("${Data.RAW_CONTACT_ID} = ? AND ${Data.MIMETYPE} = ?", arrayOf(id.toString(), MIME_TYPE))
                        .build(),
                ) + rows.map { row -> rowInsert(dataUri, row).withValue(Data.RAW_CONTACT_ID, id).build() }
            }
        }
        plan.add.forEach { w ->
            groups += (2 + w.rows.size) to { base ->
                listOf(
                    ContentProviderOperation.newInsert(rawUri)
                        .withValue(RawContacts.ACCOUNT_NAME, account.name)
                        .withValue(RawContacts.ACCOUNT_TYPE, account.type)
                        .withValue(RawContacts.SYNC1, w.anchor.toString())
                        .build(),
                ) + w.rows.map { row -> rowInsert(dataUri, row).withValueBackReference(Data.RAW_CONTACT_ID, base).build() } +
                    // Shown as part of the contact, not as a contact of its own.
                    ContentProviderOperation.newUpdate(AggregationExceptions.CONTENT_URI)
                        .withValue(AggregationExceptions.TYPE, AggregationExceptions.TYPE_KEEP_TOGETHER)
                        .withValue(AggregationExceptions.RAW_CONTACT_ID1, w.anchor)
                        .withValueBackReference(AggregationExceptions.RAW_CONTACT_ID2, base)
                        .build()
            }
        }

        val batch = ArrayList<ContentProviderOperation>()
        fun flush() {
            if (batch.isNotEmpty()) context.contentResolver.applyBatch(ContactsContract.AUTHORITY, batch)
            batch.clear()
        }
        for ((size, build) in groups) {
            if (batch.isNotEmpty() && batch.size + size > MAX_BATCH) flush()
            batch += build(batch.size)
        }
        flush()
    }

    private fun rowInsert(dataUri: Uri, row: ContactActionsPlan.Row) = ContentProviderOperation.newInsert(dataUri)
        .withValue(Data.MIMETYPE, MIME_TYPE)
        .withValue(Data.DATA1, row.number)
        .withValue(Data.DATA2, "Pickelhaube")
        .withValue(Data.DATA3, row.detail)

    /** Operations per batch; the contacts provider handles a few hundred at once well. */
    private const val MAX_BATCH = 300
}
