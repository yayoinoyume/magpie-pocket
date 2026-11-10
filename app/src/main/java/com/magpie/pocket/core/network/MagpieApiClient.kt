package com.magpie.pocket.core.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import okhttp3.Call
import okhttp3.Callback
import okhttp3.EventListener
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

open class MagpieApiException(message: String, val statusCode: Int? = null, cause: Throwable? = null) :
    Exception(message, cause)

class AuthenticationRequiredException(message: String) : MagpieApiException(message, 401)
class PermissionDeniedException(message: String) : MagpieApiException(message, 403)
class CloudflareAccessRequiredException(message: String) : MagpieApiException(message, 403)
class NetworkUnavailableException(message: String, cause: Throwable? = null) :
    MagpieApiException(message, null, cause)
class ResourceNotFoundException(message: String) : MagpieApiException(message, 404)
class UnexpectedResponseException(message: String) : MagpieApiException(message)

class MagpieApiClient(
    val cookieJar: MagpieCookieJar = MagpieCookieJar(),
    var cfAccessClientId: String? = null,
    var cfAccessClientSecret: String? = null
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val standardClient = httpClient(readTimeoutSeconds = 20)

    private val quotasClient = httpClient(readTimeoutSeconds = 25)

    private val longPollClient = httpClient(readTimeoutSeconds = 45)

    /** One builder for every request class; the listener times each call by path. */
    private fun httpClient(readTimeoutSeconds: Long): OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(readTimeoutSeconds, TimeUnit.SECONDS)
        .followRedirects(false)
        .eventListenerFactory(
            EventListener.Factory { call -> TimingEventListener(call.request().url.encodedPath) }
        )
        .build()

    suspend fun signIn(baseUrl: String, webKey: String): Boolean = withContext(Dispatchers.IO) {
        val cleanUrl = normalizeBaseUrl(baseUrl)
        val targetUrl = "$cleanUrl/_magpie/sign-in"
        val requestBody = FormBody.Builder()
            .add("key", webKey.trim())
            .build()

        val requestBuilder = Request.Builder()
            .url(targetUrl)
            .post(requestBody)

        if (!cfAccessClientId.isNullOrBlank()) {
            requestBuilder.addHeader("CF-Access-Client-Id", cfAccessClientId!!.trim())
        }
        if (!cfAccessClientSecret.isNullOrBlank()) {
            requestBuilder.addHeader("CF-Access-Client-Secret", cfAccessClientSecret!!.trim())
        }

        val request = requestBuilder.build()

        awaitCall(
            client = standardClient,
            request = request,
            mapIOException = { NetworkUnavailableException("认证请求失败，请检查网络与服务地址", it) }
        ) { response ->
            when {
                response.code == 401 -> false
                response.code == 403 -> throw PermissionDeniedException(
                    "认证被拒绝 (403)。若使用 Cloudflare Access，请检查 Service Token 权限。"
                )
                response.code in 300..399 -> {
                    val location = response.header("Location") ?: return@awaitCall false
                    val redirect = request.url.resolve(location) ?: return@awaitCall false
                    if (!sameOrigin(request.url, redirect)) {
                        throw CloudflareAccessRequiredException(
                            "请求被重定向到其他站点，通常是 Cloudflare Access 登录页。请检查 CF Access Service Token。"
                        )
                    }
                    // Only a same-origin Magpie redirect accompanied by a Magpie
                    // session cookie is considered a successful exchange.
                    hasMagpieSessionCookie(request)
                }
                response.isSuccessful -> {
                    val contentType = response.header("Content-Type") ?: ""
                    if (contentType.contains("text/html", ignoreCase = true)) {
                        throw CloudflareAccessRequiredException(
                            "服务端返回 HTML 登录页，需要先完成 Cloudflare Access 或 Magpie 认证。"
                        )
                    }
                    hasMagpieSessionCookie(request)
                }
                else -> false
            }
        }
    }

    private fun hasMagpieSessionCookie(request: Request): Boolean {
        return cookieJar.loadForRequest(request.url).any { it.name.startsWith("magpie_web_") }
    }

    suspend fun getState(baseUrl: String): UpstreamStateResponse = executeGet(baseUrl, "/api/state")

    suspend fun getGroups(baseUrl: String): UpstreamGroupsResponse = executeGet(baseUrl, "/api/groups")

    suspend fun getTraceSnapshot(baseUrl: String, afterSeq: Long = 0L): UpstreamTraceResponse {
        val path = if (afterSeq > 0) "/api/gateway/trace?after=$afterSeq&wait=1" else "/api/gateway/trace?after=0"
        val client = if (afterSeq > 0) longPollClient else standardClient
        return executeGet(baseUrl, path, client)
    }

    suspend fun getHistory(baseUrl: String, day: String? = null): UpstreamHistoryResponse {
        val path = if (!day.isNullOrBlank()) "/api/gateway/history?day=$day" else "/api/gateway/history"
        return executeGet(baseUrl, path)
    }

    suspend fun getRoute(baseUrl: String, id: String, day: String? = null): UpstreamRoute {
        val path = if (!day.isNullOrBlank()) "/api/gateway/route?id=$id&day=$day" else "/api/gateway/route?id=$id"
        return executeGet(baseUrl, path)
    }

    suspend fun getUsage(baseUrl: String, period: String = "today"): UpstreamUsageResponse {
        return executeGet(baseUrl, "/api/usage?period=$period")
    }

    suspend fun getLedgerRequests(
        baseUrl: String,
        period: String = "today",
        offset: Int = 0,
        limit: Int = 50,
        query: String? = null,
        failedOnly: Boolean = false
    ): UpstreamLedgerResponse {
        val builder = StringBuilder("/api/usage/requests?period=$period&offset=$offset&limit=$limit&via=through")
        if (!query.isNullOrBlank()) builder.append("&q=").append(query)
        if (failedOnly) builder.append("&failed=1")
        return executeGet(baseUrl, builder.toString())
    }

    suspend fun getQuotas(baseUrl: String): UpstreamQuotasResponse {
        return executeGet(baseUrl, "/api/usage/quotas", quotasClient)
    }

    suspend fun getProviders(baseUrl: String): UpstreamProvidersResponse =
        executeGet(baseUrl, "/api/providers")

    suspend fun getMaskingSummary(baseUrl: String, days: Int): UpstreamMaskingSummary =
        executeGet(baseUrl, "/api/masking/summary?days=$days")

    suspend fun getMaskingRecords(
        baseUrl: String,
        limit: Int = 50,
        before: String? = null,
        kind: String? = null,
        by: String? = null,
        agent: String? = null,
        provider: String? = null
    ): UpstreamMaskingRecords {
        val builder = StringBuilder("/api/masking/records?limit=$limit")
        appendQueryParam(builder, "before", before)
        appendQueryParam(builder, "kind", kind)
        appendQueryParam(builder, "by", by)
        appendQueryParam(builder, "agent", agent)
        appendQueryParam(builder, "provider", provider)
        return executeGet(baseUrl, builder.toString())
    }

    /**
     * The reveal endpoint answers with a bare JSON null once the value is no longer in
     * the upstream memory, so the raw element is decoded instead of a value object.
     */
    suspend fun getMaskingValue(baseUrl: String, placeholder: String): String? {
        val body = executeGet<JsonElement>(
            baseUrl,
            "/api/masking/value?p=${encodeQueryValue(placeholder)}"
        )
        if (body is JsonNull) return null
        val valueElement = (body as? JsonObject)?.get("value")
        return (valueElement as? JsonPrimitive)?.contentOrNull
    }

    private suspend inline fun <reified T> executeGet(
        baseUrl: String,
        pathWithQuery: String,
        client: OkHttpClient = standardClient
    ): T = withContext(Dispatchers.IO) {
        val cleanUrl = normalizeBaseUrl(baseUrl)
        val fullUrl = if (pathWithQuery.startsWith("/")) "$cleanUrl$pathWithQuery" else "$cleanUrl/$pathWithQuery"

        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .get()
            .addHeader("Accept", "application/json")

        if (!cfAccessClientId.isNullOrBlank()) {
            requestBuilder.addHeader("CF-Access-Client-Id", cfAccessClientId!!.trim())
        }
        if (!cfAccessClientSecret.isNullOrBlank()) {
            requestBuilder.addHeader("CF-Access-Client-Secret", cfAccessClientSecret!!.trim())
        }

        val request = requestBuilder.build()

        awaitCall(
            client = client,
            request = request,
            mapIOException = { NetworkUnavailableException("网络连接失败，请检查网络与服务地址", it) }
        ) { resp ->
            val code = resp.code
            val contentType = resp.header("Content-Type") ?: ""

            if (code == 401) {
                throw AuthenticationRequiredException("未授权或登录已过期 (401)")
            }
            if (code == 403) {
                throw PermissionDeniedException("访问被拒绝 (403)。若使用 Cloudflare Access，请检查 Service Token 权限。")
            }
            if (code == 404) {
                throw ResourceNotFoundException("数据未找到或已过保留期 (404)")
            }
            if (code in 300..399) {
                val location = resp.header("Location")
                val redirect = location?.let { resp.request.url.resolve(it) }
                if (redirect != null && !sameOrigin(resp.request.url, redirect)) {
                    throw CloudflareAccessRequiredException(
                        "请求被重定向到其他站点，通常是 Cloudflare Access 登录页。请检查 CF Access Service Token。"
                    )
                }
                throw AuthenticationRequiredException("服务端要求重新登录 (HTTP $code)")
            }

            // Check if HTML returned (e.g. redirected to login page)
            if (contentType.contains("text/html", ignoreCase = true)) {
                throw AuthenticationRequiredException("服务端返回了 HTML 登录页面，需要先完成认证")
            }

            if (!resp.isSuccessful) {
                throw MagpieApiException("HTTP 请求失败: 状态码 $code", code)
            }

            resp.body?.string() ?: throw UnexpectedResponseException("响应体为空")
        }.let { bodyString ->
            try {
                json.decodeFromString<T>(bodyString)
            } catch (e: Exception) {
                throw UnexpectedResponseException("服务响应格式不兼容，无法解析 JSON")
            }
        }
    }

    private suspend fun <T> awaitCall(
        client: OkHttpClient,
        request: Request,
        mapIOException: (IOException) -> MagpieApiException,
        transform: (Response) -> T
    ): T = suspendCancellableCoroutine { continuation ->
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }

        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                continuation.resumeWith(Result.failure(mapIOException(e)))
            }

            override fun onResponse(call: Call, response: Response) {
                val result = try {
                    response.use { Result.success(transform(it)) }
                } catch (e: IOException) {
                    Result.failure(mapIOException(e))
                } catch (e: Throwable) {
                    Result.failure(e)
                }
                continuation.resumeWith(result)
            }
        })
    }

    companion object {
        private fun sameOrigin(first: okhttp3.HttpUrl, second: okhttp3.HttpUrl): Boolean {
            return first.scheme == second.scheme &&
                first.host == second.host &&
                first.port == second.port
        }

        /** Query values are percent-encoded so placeholders and filters survive intact. */
        private fun encodeQueryValue(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        private fun appendQueryParam(builder: StringBuilder, name: String, value: String?) {
            if (value.isNullOrBlank()) return
            builder.append("&").append(name).append("=").append(encodeQueryValue(value))
        }

        fun normalizeBaseUrl(url: String): String {
            var trimmed = url.trim()
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) {
                trimmed = "https://$trimmed"
            }
            // Strip any trailing slash or query parameters like ?k=
            val httpUrl = trimmed.toHttpUrlOrNull() ?: return trimmed.trimEnd('/')
            return "${httpUrl.scheme}://${httpUrl.host}${if (httpUrl.port != 80 && httpUrl.port != 443) ":${httpUrl.port}" else ""}"
        }

        fun extractKeyFromUrl(url: String): String? {
            val httpUrl = url.toHttpUrlOrNull() ?: return null
            return httpUrl.queryParameter("k")
        }
    }
}

/**
 * Reports one line per HTTP call: the path, the end-to-end duration and whether OkHttp had
 * to open a connection. A call that never reaches `connectStart` reused the pool, which is
 * the only way to tell a warm session from one that pays for DNS, TCP and TLS again.
 */
private class TimingEventListener(private val path: String) : EventListener() {

    private var startedAtNs = 0L
    private var connectStartedAtNs = 0L
    private var connectMs = 0L
    private var openedConnection = false

    override fun callStart(call: Call) {
        startedAtNs = System.nanoTime()
    }

    override fun connectStart(call: Call, inetSocketAddress: InetSocketAddress, proxy: Proxy) {
        connectStartedAtNs = System.nanoTime()
    }

    override fun connectEnd(
        call: Call,
        inetSocketAddress: InetSocketAddress,
        proxy: Proxy,
        protocol: Protocol?
    ) {
        openedConnection = true
        connectMs = (System.nanoTime() - connectStartedAtNs) / 1_000_000L
    }

    override fun callEnd(call: Call) {
        report("ok")
    }

    override fun callFailed(call: Call, ioe: IOException) {
        report("failed=${ioe.javaClass.simpleName}")
    }

    private fun report(outcome: String) {
        val elapsedMs = (System.nanoTime() - startedAtNs) / 1_000_000L
        val connection = if (openedConnection) "connect=${connectMs}ms" else "connection=reused"
        MagpieTiming.log("http $path ${elapsedMs}ms $connection $outcome")
    }
}
