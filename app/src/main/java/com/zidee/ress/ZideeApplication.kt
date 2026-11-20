package com.zidee.ress

import android.app.Application
import com.zidee.ress.core.coroutines.DispatcherProvider
import com.zidee.ress.di.zideeAppModules
import com.zidee.ress.engine.StagingDirectoryCleaner
import com.zidee.ress.engine.service.MediaOptimizationForegroundService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.startKoin

class ZideeApplication : Application() {
    private var applicationScope: CoroutineScope? = null

    override fun onCreate() {
        super.onCreate()
        val koinApp =
            startKoin {
                androidContext(this@ZideeApplication)
                modules(zideeAppModules)
            }
        MediaOptimizationForegroundService.ensureNotificationChannel(this)

        val dispatchers = koinApp.koin.get<DispatcherProvider>()
        val cleaner = koinApp.koin.get<StagingDirectoryCleaner>()
        val scope = CoroutineScope(SupervisorJob() + dispatchers.io)
        applicationScope = scope
        scope.launch {
            cleaner.deleteOrphanedOlderThan()
        }
    }
}
