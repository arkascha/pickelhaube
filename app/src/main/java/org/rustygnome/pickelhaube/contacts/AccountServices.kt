package org.rustygnome.pickelhaube.contacts

import android.accounts.AbstractAccountAuthenticator
import android.accounts.Account
import android.accounts.AccountAuthenticatorResponse
import android.accounts.NetworkErrorException
import android.app.Service
import android.content.AbstractThreadedSyncAdapter
import android.content.ContentProviderClient
import android.content.Context
import android.content.Intent
import android.content.SyncResult
import android.os.Bundle
import android.os.IBinder

/**
 * The account [ContactActions] keeps its rows in. Android needs an authenticator for an account type;
 * this one has nothing to authenticate: the account is only added and removed by the app itself.
 */
class AuthenticatorService : Service() {

    private val authenticator by lazy { Authenticator(this) }

    override fun onBind(intent: Intent?): IBinder? = authenticator.iBinder

    private class Authenticator(context: Context) : AbstractAccountAuthenticator(context) {
        // Not offered under "Add account": the switch in Pickelhaube's settings adds it.
        override fun addAccount(
            response: AccountAuthenticatorResponse?, accountType: String?, authTokenType: String?,
            requiredFeatures: Array<out String>?, options: Bundle?,
        ): Bundle? = null

        override fun editProperties(response: AccountAuthenticatorResponse?, accountType: String?): Bundle? = null
        override fun confirmCredentials(response: AccountAuthenticatorResponse?, account: Account?, options: Bundle?): Bundle? = null

        @Throws(NetworkErrorException::class)
        override fun getAuthToken(
            response: AccountAuthenticatorResponse?, account: Account?, authTokenType: String?, options: Bundle?,
        ): Bundle? = null

        override fun getAuthTokenLabel(authTokenType: String?): String? = null
        override fun updateCredentials(
            response: AccountAuthenticatorResponse?, account: Account?, authTokenType: String?, options: Bundle?,
        ): Bundle? = null

        override fun hasFeatures(response: AccountAuthenticatorResponse?, account: Account?, features: Array<out String>?): Bundle =
            Bundle().apply { putBoolean(android.accounts.AccountManager.KEY_BOOLEAN_RESULT, false) }
    }
}

/**
 * The contacts app learns from this service's description (`xml/contacts.xml`) how to show the
 * account's rows. When Android asks it to sync, it brings the rows in line, as [ContactActionsJob] does.
 */
class ContactsSyncService : Service() {

    private val adapter by lazy {
        object : AbstractThreadedSyncAdapter(applicationContext, true) {
            override fun onPerformSync(
                account: Account?, extras: Bundle?, authority: String?,
                provider: ContentProviderClient?, syncResult: SyncResult?,
            ) {
                ContactActions.sync(context)
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = adapter.syncAdapterBinder
}
