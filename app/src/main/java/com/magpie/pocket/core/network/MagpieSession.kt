package com.magpie.pocket.core.network

import com.magpie.pocket.core.model.Capabilities

/**
 * One place for the session and capability rules used by both the connection sheet and
 * runtime startup. A restored connection must repeat the Magpie sign-in exchange on the
 * exact client that will perform subsequent requests; cookies are never persisted.
 */
object MagpieSession {

    data class ProbeResult(
        val capabilities: Capabilities,
        val state: UpstreamStateResponse,
        val groupsSupported: Boolean,
        val traceSupported: Boolean
    )

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
     * Signs in and verifies the protected management API. [signInRequired] is false when the
     * client already holds a session that was verified moments earlier, so saving a
     * connection does not repeat the exchange on the same cookie jar.
     * [onStateReady] runs as soon as `/api/state` answers, before the remaining probes.
     */
    suspend fun connect(
        client: MagpieApiClient,
        baseUrl: String,
        webKey: String?,
        signInRequired: Boolean = true,
        onStateReady: () -> Unit = {}
    ): ProbeResult {
        if (signInRequired) initialize(client, baseUrl, webKey)
        return try {
            probe(client, baseUrl, onStateReady)
        } catch (e: AuthenticationRequiredException) {
            if (webKey.isNullOrBlank()) {
                throw AuthenticationRequiredException(
                    "需要有效的 Magpie Web Key。请在连接设置中填写服务端 Web Key（不是 Cloudflare Access 凭证）。"
                )
            }
            throw e
        }
    }

    /**
     * `/api/state` is required and is what establishes the session, so [onStateReady] fires
     * before anything else is asked for. Groups and the trace snapshot are still probed here
     * because whole screens are gated on them. Usage, ledger, quotas, providers and masking
     * are loaded by the data stage instead: claiming them optimistically keeps a slow panel
     * endpoint out of the connection path, and a 404 there drops the capability.
     */
    suspend fun probe(
        client: MagpieApiClient,
        baseUrl: String,
        onStateReady: () -> Unit = {}
    ): ProbeResult {
        val state = client.getState(baseUrl)
        MagpieTiming.mark("probe-state")
        onStateReady()

        val groups = optional(client) { it.getGroups(baseUrl) }
        val trace = optional(client) { it.getTraceSnapshot(baseUrl, 0L) }

        val capabilities = Capabilities(
            trace = trace?.mine == true,
            groups = groups != null,
            // History and request-detail routes need an id/day, so they are not guessed.
            history = false,
            requestDetail = false,
            usage = true,
            ledger = true,
            quotas = true,
            providers = true,
            masking = true,
            statisticsTimeZone = state.timezone ?: "Asia/Shanghai",
            verifiedDeploymentVersion = state.version ?: "Magpie"
        )

        return ProbeResult(
            capabilities = capabilities,
            state = state,
            groupsSupported = groups != null,
            traceSupported = trace != null && trace.mine
        )
    }

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
