package com.kbul.spicycrab

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.kbul.spicycrab.data.backup.BackupManager
import com.kbul.spicycrab.domain.nutrition.SubstanceRepository
import com.kbul.spicycrab.notifications.NotificationChannels
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class SpicyCrabApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var backupManager: BackupManager
    @Inject lateinit var substanceRepository: SubstanceRepository

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        NotificationChannels.ensureCreated(this)
        backupManager.startAutoBackup()
        // Push any substance samples logged while offline / before the token was set.
        substanceRepository.flushUnsynced()
    }
}
