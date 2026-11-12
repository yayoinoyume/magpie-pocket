package com.magpie.pocket.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import com.magpie.pocket.core.data.DemoRepository
import com.magpie.pocket.core.network.MagpieTiming
import com.magpie.pocket.core.security.CredentialStore
import com.magpie.pocket.core.security.PreferencesManager
import com.magpie.pocket.core.util.AppLog
import com.magpie.pocket.widget.WidgetRefresh
import com.magpie.pocket.widget.WidgetStateStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MagpieApplication : Application() {

    lateinit var credentialStore: CredentialStore
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    /** Its JSON is read lazily, so a LIVE process never loads the bundled demo asset. */
    val demoRepository: DemoRepository by lazy { DemoRepository(this) }

    /** Process-lifetime home for startup disk work that must not touch the main thread. */
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One process-owned runtime shared by every Activity and the foreground service. */
    val appViewModel: AppViewModel by lazy {
        AppViewModel(
            credentialStore = CredentialStoreConnectionSource(credentialStore),
            preferencesManager = PreferencesManagerDisplaySource(preferencesManager),
            demoRepo = demoRepository,
            keepaliveController = AndroidKeepaliveController(this),
            onOverviewRefreshed = { WidgetRefresh.updateAll(this) }
        ).also {
            existingViewModel = it
            startWidgetStateSync()
        }
    }

    /**
     * Persists every connection/overview change so a later widget-only process can render the
     * latest state, including a failure. Runs only once the app itself built the view model;
     * a widget broadcast never reaches this method.
     */
    private fun startWidgetStateSync() {
        startupScope.launch {
            WidgetStateStore.observe(this@MagpieApplication).collect { state ->
                if (state == null) return@collect
                runCatching { WidgetStateStore.save(this@MagpieApplication, state) }
            }
        }
    }

    /** Set when [appViewModel] is first built; widget-only processes leave it null. */
    @Volatile
    private var existingViewModel: AppViewModel? = null

    /**
     * The process-owned runtime if it already exists. A widget update must never construct
     * [appViewModel]; doing so would load credentials and start the network stack.
     */
    val existingAppViewModel: AppViewModel?
        get() = existingViewModel

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Timing lines are the only diagnostic for connection latency; keep them in
        // debuggable builds so a release APK stays quiet.
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            MagpieTiming.logger = { line -> Log.d(TIMING_TAG, line) }
            AppLog.logger = { message, error -> Log.w(LOG_TAG, message, error) }
        }

        credentialStore = CredentialStore(this)
        // The keystore unlock, the legacy-plaintext wipe and the first decryption are disk
        // work; start them away from the main thread before an Activity asks for them.
        startupScope.launch { credentialStore.prepare() }

        preferencesManager = PreferencesManager(this)

        // Apply the stored refresh policy: event-driven unless the user chose the 15-minute
        // fallback. The fallback reads the last snapshot instead of networking.
        WidgetRefresh.applyRefreshPolicy(this)
    }

    companion object {
        private const val TIMING_TAG = "MagpieTiming"
        private const val LOG_TAG = "MagpiePocket"

        lateinit var instance: MagpieApplication
            private set
    }
}
