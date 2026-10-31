package io.github.yuj1ng1.mycampus

import android.app.Application
import androidx.room.Room
import io.github.yuj1ng1.mycampus.data.CampusDatabase
import io.github.yuj1ng1.mycampus.data.CampusRepository
import io.github.yuj1ng1.mycampus.data.SchoolPreferences
import io.github.yuj1ng1.mycampus.data.SchoolSecrets
import io.github.yuj1ng1.mycampus.data.UiPreferences
import io.github.yuj1ng1.mycampus.sync.SchoolAutoSync
import io.github.yuj1ng1.mycampus.notifications.PaymentCaptureStatus
import io.github.yuj1ng1.mycampus.notifications.PaymentNotificationCapture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class CampusApplication : Application() {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val database by lazy {
        Room.databaseBuilder(this, CampusDatabase::class.java, "campus.db").build()
    }
    val repository by lazy { CampusRepository(database) }
    val schoolOptions by lazy { SchoolPreferences(this) }
    val schoolSecrets by lazy { SchoolSecrets(this) }
    val uiOptions by lazy { UiPreferences(this) }
    val schoolSync by lazy { SchoolAutoSync(this, repository, schoolOptions, schoolSecrets) }
    val paymentStatus by lazy { PaymentCaptureStatus(this) }
    val paymentCapture by lazy { PaymentNotificationCapture(repository, paymentStatus, applicationScope) }
    override fun onCreate() { super.onCreate(); schoolSync.configureSchedule() }
}
