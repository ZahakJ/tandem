package io.github.zahakj.tandem

import android.app.Application

/**
 * Tandem's process. Only what every part of it needs before anything shows: the language (Tx.kt) and the calendar
 * (Dates.kt), so a service, a receiver or the screensaver started on its own speaks it too. Everything else stays
 * in the activities.
 */
class TandemApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Lang.init(this)
        Dates.init(this)
    }
}
