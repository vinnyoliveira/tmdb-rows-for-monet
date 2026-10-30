package com.ptvin.tmdbrows.channel

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ptvin.tmdbrows.data.Prefs
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // Se a pessoa desligou a sincronização, o trabalho já não deve correr.
        if (!Prefs.autoSync(applicationContext)) return Result.success()
        ChannelRefresher.refreshAll(applicationContext)
        return Result.success()
    }
}

/** Só existe trabalho em segundo plano quando a sincronização automática está ligada. */
object RefreshScheduler {
    private const val WORK_NAME = "tmdb-rows-refresh"

    fun ensure(context: Context, hours: Int, enabled: Boolean = Prefs.autoSync(context)) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) {
            manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(hours.toLong(), TimeUnit.HOURS)
            .setInitialDelay(1, TimeUnit.HOURS)
            .build()
        manager.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }
}
