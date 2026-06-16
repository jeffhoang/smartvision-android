package app.streammog.android

import android.app.Activity
import android.app.Application
import app.streammog.android.app.AppEnvironment
import app.streammog.android.service.StreamingForegroundService
import java.lang.ref.WeakReference

class StreamMogApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        StreamingForegroundService.createChannel(this)
    }
    private var activityRef: WeakReference<Activity> = WeakReference(null)

    val currentActivity: Activity? get() = activityRef.get()

    // Lazily bootstrapped so Context is available; accessed by the coordinator and activities.
    val environment: AppEnvironment by lazy {
        AppEnvironment.bootstrap(
            context = applicationContext,
            activityRef = { activityRef.get() },
        )
    }

    fun setCurrentActivity(activity: Activity?) {
        activityRef = WeakReference(activity)
    }
}
