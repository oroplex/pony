package app.pony.companion.brain

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Calls a saved brain. Headers are never logged. The key is not written to disk here. */
object ChatClient {
    private val http = OkHttpClient.Builder()
        .callTimeout(60, TimeUnit.SECONDS)
        .build()
    private val json = "application/json".toMediaType()

    fun complete(
        record: ProviderRecord,
        apiKey: String,
        messages: List<LoopMessage>,
        tools: Boolean,
    ): ModelTurn {
        val request = AdapterRequests.build(
            record.preset,
            record.model,
            record.baseUrl,
            apiKey,
            messages,
            tools,
        )
        return AdapterRequests.parse(record.preset.kind, post(request, apiKey))
    }

    fun probe(record: ProviderRecord, apiKey: String): String {
        val request = AdapterRequests.probe(record.preset, record.model, record.baseUrl, apiKey)
        return try {
            post(request, apiKey)
            "Key works."
        } catch (err: Exception) {
            AdapterRequests.sanitize(err.message ?: "Key was rejected.", apiKey).take(300)
        }
    }

    private fun post(request: AdapterRequest, apiKey: String): String {
        val builder = Request.Builder()
            .url(request.url)
            .post(request.body.toRequestBody(json))
        request.headers.forEach { (name, value) -> builder.header(name, value) }
        http.newCall(builder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val safe = AdapterRequests.sanitize(text.ifBlank { "HTTP ${response.code}" }, apiKey)
                throw ProviderHttpException(
                    status = response.code,
                    body = safe.take(300),
                    retryAfter = response.header("Retry-After"),
                )
            }
            return text
        }
    }
}
