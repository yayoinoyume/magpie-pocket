package com.magpie.pocket.core.network

import com.magpie.pocket.core.model.Capabilities
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * One place for the session and capability rules used by both the connection sheet and
 * runtime startup. A restored connection must repeat the Magpie sign-in exchange on the
 * exact client that will perform subsequent requests; cookies are never persisted.
 */
object MagpieSession {

    /**
     * [groups] and [trace] are the bodies the probe already paid for. The data stage reuses
     * the group list instead of asking `/api/groups` again, and the trace loop starts from
     * [trace]'s sequence instead of repeating `after=0`.
     */
    data class ProbeResult(
        val capabilities: Capabilities,
        val state: UpstreamStateResponse,
        val groups: UpstreamGroupsResponse? = null,
        val trace: UpstreamTraceResponse? = null
    ) {
        val groupsSupported: Boolean get() = groups != null
        val traceSupported: Boolean get() = trace?.mine == true
    }

    /**
     * Establishes a Magpie session when a Web Key is configured. A blank key is allowed for
     * servers that do not require one, but the protected probe still decides whether access
     * is actually available.
     */
    suspend fun initialize(client: MagpieApiClient, baseUrl: String, webKey: String?) {
        val key = webKey?.trim().orEmpty()
        if (key.isEmpty()) return

        val signedIn = client.signIn(baseUrl, key)
        if (!signedIn) {
            throw AuthenticationRequiredException(
                "需要有效的 Magpie Web Key。请在连接设置中填写服务端 Web Key（不是 Cloudflare Access 凭证）。"
            )
        }
    }

    /**
     * Signs in and reads the required `/api/state`. [signInRequired] is false when the client
     * already holds a session that was verified moments earlier, so saving a connection does
     * not repeat the exchange on the same cookie jar.
     */
    suspend fun readState(
        client: MagpieApiClient,
        baseUrl: String,
        webKey: String? = null,
        signInRequired: Boolean = true
    ): UpstreamStateResponse {
        if (signInRequired) initialize(client, baseUrl, webKey)
        val state = try {
            client.getState(baseUrl)
        } catch (e: AuthenticationRequiredException) {
            if (webKey.isNullOrBlank()) {
                throw AuthenticationRequiredException(
                    "需要有效的 Magpie Web Key。请在连接设置中填写服务端 Web Key（不是 Cloudflare Access 凭证）。"
                )
            }
            throw e
        }
        MagpieTiming.mark("probe-state")
        return state
    }

    /**
     * Signs in and verifies the protected management API. [onStateReady] runs as soon as
     * `/api/state` answers, before the remaining probes. [onTraceProbed] runs when the trace
     * snapshot lands, still before the group list.
     */
    suspend fun connect(
        client: MagpieApiClient,
        baseUrl: String,
        webKey: String?,
        signInRequired: Boolean = true,
        onStateReady: () -> Unit = {},
        onTraceProbed: (UpstreamTraceResponse?) -> Unit = {}
    ): ProbeResult {
        val state = readState(client, baseUrl, webKey, signInRequired)
        onStateReady()
        val (groups, trace) = try {
            probeOptional(client, baseUrl, onTraceProbed)
        } catch (e: AuthenticationRequiredException) {
            // An optional endpoint answering 401 still means the key is missing or rejected.
            if (webKey.isNullOrBlank()) {
                throw AuthenticationRequiredException(
                    "需要有效的 Magpie Web Key。请在连接设置中填写服务端 Web Key（不是 Cloudflare Access 凭证）。"
                )
            }
            throw e
        }
        return ProbeResult(
            capabilities = capabilities(state, groups != null, trace?.mine == true),
            state = state,
            groups = groups,
            trace = trace
        )
    }

    /**
     * `/api/state` is required and is what establishes the session, so [onStateReady] fires
     * before anything else is asked for. Groups and the trace snapshot are probed because
     * whole screens are gated on them; they are asked for together so a slow group list
     * cannot delay the live trace. Usage, ledger, quotas, providers and masking are not
     * guessed here: they stay unknown until their first real request answers 200 or 404.
     */
    suspend fun probe(
        client: MagpieApiClient,
        baseUrl: String,
        onStateReady: () -> Unit = {},
        onTraceProbed: (UpstreamTraceResponse?) -> Unit = {}
    ): ProbeResult {
        val state = readState(client, baseUrl, signInRequired = false)
        onStateReady()
        val (groups, trace) = probeOptional(client, baseUrl, onTraceProbed)
        return ProbeResult(
            capabilities = capabilities(state, groups != null, trace?.mine == true),
            state = state,
            groups = groups,
            trace = trace
        )
    }

    /**
     * Reads the two optional probe bodies concurrently. [loadGroups] lets the repository
     * route the group read through its in-flight request sharing before a panel asks for it.
     */
    suspend fun probeOptional(
        client: MagpieApiClient,
        baseUrl: String,
        onTraceProbed: (UpstreamTraceResponse?) -> Unit = {},
        loadGroups: suspend () -> UpstreamGroupsResponse = { client.getGroups(baseUrl) }
    ): Pair<UpstreamGroupsResponse?, UpstreamTraceResponse?> = coroutineScope {
        val groupsAsync = async { optional { loadGroups() } }
        val trace = optional(client) { it.getTraceSnapshot(baseUrl, 0L) }
        // The trace answer seeds the live loop; hand it over before waiting for groups.
        onTraceProbed(trace)
        groupsAsync.await() to trace
    }

    /** Capabilities the probe can prove; panel endpoints stay unknown until they answer. */
    fun capabilities(
        state: UpstreamStateResponse,
        groupsSupported: Boolean,
        traceSupported: Boolean
    ): Capabilities = Capabilities(
        trace = traceSupported,
        groups = groupsSupported,
        // History and request-detail routes need an id/day, so they are not guessed.
        history = false,
        requestDetail = false,
        usage = null,
        ledger = null,
        quotas = null,
        providers = null,
        masking = null,
        statisticsTimeZone = state.timezone ?: "Asia/Shanghai",
        verifiedDeploymentVersion = state.version ?: "Magpie"
    )

    private suspend fun <T> optional(
        client: MagpieApiClient,
        block: suspend (MagpieApiClient) -> T
    ): T? {
        return try {
            block(client)
        } catch (e: ResourceNotFoundException) {
            null
        }
    }
}
