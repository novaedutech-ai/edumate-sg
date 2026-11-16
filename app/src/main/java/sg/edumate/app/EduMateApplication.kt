package sg.edumate.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import sg.edumate.app.security.AppLockManager
import sg.edumate.app.work.WorkScheduler
import javax.inject.Inject

@HiltAndroidApp
class EduMateApplication : Application(), Configuration.Provider {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var appLock: AppLockManager

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        // Keep startup light (cold start < 2 s): no network, no DB work here.
        WorkScheduler.createChannels(this)
        WorkScheduler.schedulePeriodic(this)
        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStop(owner: LifecycleOwner) = appLock.onBackground()
            override fun onStart(owner: LifecycleOwner) = appLock.onForeground()
        })
    }
}
