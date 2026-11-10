package com.magpie.pocket.app

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import com.magpie.pocket.core.data.DemoRepository
import com.magpie.pocket.core.network.MagpieApiClient
import com.magpie.pocket.core.network.MagpieTiming
import com.magpie.pocket.core.security.CredentialStore
import com.magpie.pocket.core.security.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class MagpieApplication : Application() {

    lateinit var credentialStore: CredentialStore
        private set

    lateinit var preferencesManager: PreferencesManager
        private set

    lateinit var demoRepository: DemoRepository
        private set

    lateinit var apiClient: MagpieApiClient
        private set

    /** Process-lifetime home for startup disk work that must not touch the main thread. */
    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** One process-owned runtime shared by every Activity and the foreground service. */
    val appViewModel: AppViewModel by lazy {
        AppViewModel(
            credentialStore = CredentialStoreConnectionSource(credentialStore),
            preferencesManager = PreferencesManagerDisplaySource(preferencesManager),
            demoRepo = demoRepository,
            apiClient = apiClient,
            keepaliveController = AndroidKeepaliveController(this)
        )
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Timing lines are the only diagnostic for connection latency; keep them in
        // debuggable builds so a release APK stays quiet.
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            MagpieTiming.logger = { line -> Log.d(TIMING_TAG, line) }
        }

        credentialStore = CredentialStore(this)
        // The keystore unlock, the legacy-plaintext wipe and the first decryption are disk
        // work; start them away from the main thread before an Activity asks for them.
        startupScope.launch { credentialStore.prepare() }

        preferencesManager = PreferencesManager(this)
        demoRepository = DemoRepository(this)
        apiClient = MagpieApiClient()
    }

    companion object {
        private const val TIMING_TAG = "MagpieTiming"

        lateinit var instance: MagpieApplication
            private set
    }
}
