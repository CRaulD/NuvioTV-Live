package com.nuvio.tv.core.server

import android.content.Context
import fi.iki.elonen.NanoHTTPD
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class IptvConfigState(
    val m3uUrl: String = "",
    val epgUrl: String = ""
)

data class PendingIptvChange(
    val id: String = UUID.randomUUID().toString(),
    val m3uUrl: String,
    val epgUrl: String,
    var status: ChangeStatus = ChangeStatus.PENDING
)

enum class ChangeStatus { PENDING, CONFIRMED, REJECTED }

class IptvConfigServer(
    private val context: Context,
    private val currentConfigProvider: () -> IptvConfigState,
    private val onChangeProposed: (PendingIptvChange) -> Unit,
    private val logoProvider: (() -> ByteArray?)? = null,
    port: Int = 8082
) : NanoHTTPD(port) {

    private val pendingChanges = ConcurrentHashMap<String, PendingIptvChange>()

    fun confirmChange(id: String) {
        pendingChanges[id]?.status = ChangeStatus.CONFIRMED
    }

    fun rejectChange(id: String) {
        pendingChanges[id]?.status = ChangeStatus.REJECTED
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        return when {
            method == Method.GET && uri == "/" -> serveWebPage()
            method == Method.GET && uri == "/logo.png" -> serveLogo()
            method == Method.GET && uri == "/api/iptv" -> serveConfig()
            method == Method.POST && uri == "/api/iptv" -> handleConfigUpdate(session)
            method == Method.GET && uri.startsWith("/api/status/") -> serveChangeStatus(uri)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveWebPage(): Response {
        return newFixedLengthResponse(
            Response.Status.OK,
            "text/html; charset=utf-8",
            IptvWebPage.getHtml(context)
        )
    }

    private fun serveLogo(): Response {
        val bytes = logoProvider?.invoke()
        return if (bytes != null) {
            newFixedLengthResponse(
                Response.Status.OK,
                "image/png",
                java.io.ByteArrayInputStream(bytes),
                bytes.size.toLong()
            )
        } else {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveConfig(): Response {
        val config = currentConfigProvider()
        val json = """{"m3uUrl":"${config.m3uUrl.replace("\"", "\\\"")}","epgUrl":"${config.epgUrl.replace("\"", "\\\"")}"}"""
        return newFixedLengthResponse(Response.Status.OK, "application/json; charset=utf-8", json)
    }

    private fun handleConfigUpdate(session: IHTTPSession): Response {
        pendingChanges.values
            .filter { it.status == ChangeStatus.PENDING }
            .forEach { it.status = ChangeStatus.REJECTED }

        val bodyMap = HashMap<String, String>()
        session.parseBody(bodyMap)
        val body = bodyMap["postData"] ?: ""

        val change: PendingIptvChange = try {
            val parsed = com.google.gson.Gson().fromJson<Map<String, String>>(
                body, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            )
            PendingIptvChange(
                m3uUrl = parsed["m3uUrl"]?.trim() ?: "",
                epgUrl = parsed["epgUrl"]?.trim() ?: ""
            )
        } catch (e: Exception) {
            val error = mapOf("error" to "Invalid request body")
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json; charset=utf-8",
                com.google.gson.Gson().toJson(error)
            )
        }

        pendingChanges[change.id] = change
        onChangeProposed(change)

        val response = mapOf("status" to "pending_confirmation", "id" to change.id)
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json; charset=utf-8",
            com.google.gson.Gson().toJson(response)
        )
    }

    private fun serveChangeStatus(uri: String): Response {
        val id = uri.removePrefix("/api/status/")
        val change = pendingChanges[id]
        val status = change?.status?.name?.lowercase() ?: "not_found"
        val response = mapOf("status" to status)
        return newFixedLengthResponse(
            Response.Status.OK,
            "application/json; charset=utf-8",
            com.google.gson.Gson().toJson(response)
        )
    }

    companion object {
        fun startOnAvailablePort(
            context: Context,
            currentConfigProvider: () -> IptvConfigState,
            onChangeProposed: (PendingIptvChange) -> Unit,
            logoProvider: (() -> ByteArray?)? = null,
            startPort: Int = 8082,
            maxAttempts: Int = 10
        ): IptvConfigServer? {
            for (port in startPort until startPort + maxAttempts) {
                try {
                    val server = IptvConfigServer(
                        context = context,
                        currentConfigProvider = currentConfigProvider,
                        onChangeProposed = onChangeProposed,
                        logoProvider = logoProvider,
                        port = port
                    )
                    server.start(SOCKET_READ_TIMEOUT, false)
                    return server
                } catch (_: Exception) { }
            }
            return null
        }
    }
}
