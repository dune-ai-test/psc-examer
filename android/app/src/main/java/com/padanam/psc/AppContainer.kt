package com.padanam.psc

import android.content.Context
import com.padanam.psc.data.local.AppDao
import com.padanam.psc.data.local.AppDatabase
import com.padanam.psc.data.prefs.ExamStore
import com.padanam.psc.data.prefs.SettingsRepository
import com.padanam.psc.data.remote.GeminiRepository
import com.padanam.psc.data.remote.NeonRepository
import com.padanam.psc.data.repo.AttemptRepository
import com.padanam.psc.data.repo.LibraryRepository
import com.padanam.psc.data.repo.SyncRepository
import com.padanam.psc.util.ScanSessionStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Manual dependency container. A DI framework would add ceremony without
 * benefit for an app of this size.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val database: AppDatabase = AppDatabase.get(appContext)
    val dao: AppDao = database.dao()

    val settingsRepository = SettingsRepository(appContext)
    val examStore = ExamStore(appContext)

    val geminiRepository = GeminiRepository()
    val neonRepository = NeonRepository()

    val libraryRepository = LibraryRepository(dao)
    val attemptRepository = AttemptRepository(dao)
    val syncRepository = SyncRepository(dao, neonRepository, settingsRepository)

    val scanSession = ScanSessionStore()
}
