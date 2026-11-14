package org.rustygnome.pickelhaube.contacts

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.provider.ContactsContract
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Keeps the rows of [ContactActions] in step with the contacts, without the app running:
 * - [WATCH] runs once the contacts change (a little after, so a sync of many contacts is done first)
 *   and is set up again after each run – Android forgets such a job on a restart, so
 * - [DAILY] (kept over restarts) runs once a day: it brings the rows in line and sets up [WATCH] again.
 *
 * The rows' own writes count as a change too; the run after them finds nothing to do.
 */
class ContactActionsJob : JobService() {

    override fun onStartJob(params: JobParameters): Boolean {
        thread(name = "ContactActionsJob") {
            val ok = ContactActions.sync(applicationContext)
            // Feature still on: watch for the next change.
            if (ok || ContactActions.isEnabled(applicationContext)) scheduleWatch(applicationContext)
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean = true

    companion object {
        private const val WATCH = 7301
        private const val DAILY = 7302
        private const val NOW = 7303

        private fun component(context: Context) = ComponentName(context, ContactActionsJob::class.java)
        private fun scheduler(context: Context) = context.getSystemService(JobScheduler::class.java)

        /** Runs right away (on switching the feature on), then keeps watching. */
        fun scheduleNow(context: Context) {
            scheduler(context).schedule(
                JobInfo.Builder(NOW, component(context)).setOverrideDeadline(0).build(),
            )
            scheduleDaily(context)
        }

        /** Runs once the contacts change. */
        fun scheduleWatch(context: Context) {
            scheduler(context).schedule(
                JobInfo.Builder(WATCH, component(context))
                    .addTriggerContentUri(
                        JobInfo.TriggerContentUri(
                            ContactsContract.Contacts.CONTENT_URI,
                            JobInfo.TriggerContentUri.FLAG_NOTIFY_FOR_DESCENDANTS,
                        ),
                    )
                    .setTriggerContentUpdateDelay(TimeUnit.SECONDS.toMillis(10))
                    .setTriggerContentMaxDelay(TimeUnit.MINUTES.toMillis(2))
                    .build(),
            )
            if (scheduler(context).getPendingJob(DAILY) == null) scheduleDaily(context)
        }

        private fun scheduleDaily(context: Context) {
            scheduler(context).schedule(
                JobInfo.Builder(DAILY, component(context))
                    .setPeriodic(TimeUnit.DAYS.toMillis(1))
                    .setPersisted(true)
                    .build(),
            )
        }

        fun cancel(context: Context) {
            listOf(WATCH, DAILY, NOW).forEach(scheduler(context)::cancel)
        }
    }
}
