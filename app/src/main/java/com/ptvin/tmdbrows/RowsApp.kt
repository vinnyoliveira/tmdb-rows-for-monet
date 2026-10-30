package com.ptvin.tmdbrows

import android.app.Application
import com.ptvin.tmdbrows.channel.RefreshScheduler
import com.ptvin.tmdbrows.data.Prefs
import com.ptvin.tmdbrows.data.Rows

class RowsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Rows.init(this)
        // Liga ou cancela o trabalho periódico conforme a escolha da pessoa (também limpa
        // agendamentos deixados por versões anteriores).
        RefreshScheduler.ensure(this, Prefs.refreshHours(this), Prefs.autoSync(this))
    }
}
