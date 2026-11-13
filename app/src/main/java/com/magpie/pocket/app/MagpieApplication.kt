package com.magpie.pocket.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.res.Configuration
import android.util.Log
import com.magpie.pocket.core.data.DemoRepository
import com.magpie.pocket.core.model.ThemeMode
import com.magpie.pocket.core.network.MagpieTiming
import com.magpie.pocket.core.security.CredentialStore
import com.magpie.pocket.core.security.PreferencesManager
import com.magpie.pocket.core.util.AppLog
import com.magpie.pocket.widget.WidgetRefresh
import com.magpie.pocket.widget.WidgetStateStore
import com.magpie.pocket.widget.WidgetThemeSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
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
     * The last observed app ThemeMode. The keepalive service tints its notifications from it
     * without reading DataStore itself; [ThemeMode.SYSTEM] is the safe pre-emission value.
     */
    @Volatile
    var appThemeMode: ThemeMode = ThemeMode.SYSTEM
        private set

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
        WidgetThemeSource.refreshSystemDark(this)
        observeAppThemeMode()

        // Apply the stored refresh policy: event-driven unless the user chose the 15-minute
        // fallback. The fallback reads the last snapshot instead of networking.
        WidgetRefresh.applyRefreshPolicy(this)
    }

    /**
     * A theme change is a widget repaint, not a data refresh. The first emission only fills the
     * cache; later changes repaint every size from the stored snapshot.
     */
    private fun observeAppThemeMode() {
        startupScope.launch {
            var observed = false
            preferencesManager.displayPreferencesFlow
                .map { it.themeMode }
                .distinctUntilChanged()
                .catch { error ->
                    if (error is CancellationException) throw error
                    AppLog.warn("Theme mode observation stopped", error)
                }
                .collect { mode ->
                    val previous = appThemeMode
                    appThemeMode = mode
                    if (observed && previous != mode) {
                        WidgetRefresh.repaintAll(this@MagpieApplication)
                    }
                    observed = true
                }
        }
    }

    /** The launcher may keep the process while the phone switches between light and dark. */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        WidgetThemeSource.refreshSystemDark(this)
        WidgetRefresh.repaintAll(this)
    }

    companion object {
        private const val TIMING_TAG = "MagpieTiming"
        private const val LOG_TAG = "MagpiePocket"

        lateinit var instance: MagpieApplication
            private set
    }
}
