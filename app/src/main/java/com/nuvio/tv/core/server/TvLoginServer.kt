package com.nuvio.tv.core.server

import android.content.Context
import android.util.Log
import com.nuvio.tv.core.auth.AuthManager
import fi.iki.elonen.NanoHTTPD

/**
 * Embedded HTTP server that runs on the TV and serves a login page
 * accessible via QR code from a phone. Credentials are POSTed from
 * the phone to the TV, which calls AuthManager directly (never leaves local network).
 */
class TvLoginServer(
    private val context: Context,
    private val authManager: AuthManager,
    private val logoProvider: (() -> ByteArray?)? = null,
    port: Int = 8083
) : NanoHTTPD(port) {

    private var loginCallback: ((Boolean, String?) -> Unit)? = null

    fun setLoginCallback(callback: (Boolean, String?) -> Unit) {
        loginCallback = callback
    }

    override fun serve(session: IHTTPSession): Response {
        val uri = session.uri
        val method = session.method

        return when {
            method == Method.GET && uri == "/" -> serveWebPage()
            method == Method.GET && uri == "/logo.png" -> serveLogo()
            method == Method.POST && uri == "/api/login" -> handleLogin(session)
            else -> newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        }
    }

    private fun serveWebPage(): Response {
        return newFixedLengthResponse(
            Response.Status.OK,
            "text/html; charset=utf-8",
            TvLoginWebPage.getHtml(context)
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

    private fun handleLogin(session: IHTTPSession): Response {
        val bodyMap = HashMap<String, String>()
        session.parseBody(bodyMap)
        val body = bodyMap["postData"] ?: ""

        val email: String
        val password: String
        try {
            val parsed = com.google.gson.Gson().fromJson<Map<String, String>>(
                body, object : com.google.gson.reflect.TypeToken<Map<String, String>>() {}.type
            )
            email = parsed["email"]?.trim() ?: ""
            password = parsed["password"]?.trim() ?: ""
        } catch (e: Exception) {
            val error = mapOf("status" to "error", "message" to "Requisição inválida")
            return newFixedLengthResponse(
                Response.Status.BAD_REQUEST,
                "application/json; charset=utf-8",
                com.google.gson.Gson().toJson(error)
            )
        }

        if (email.isBlank() || password.isBlank()) {
            val error = mapOf("status" to "error", "message" to "Email e senha são obrigatórios")
            return newFixedLengthResponse(
                Response.Status.OK,
                "application/json; charset=utf-8",
                com.google.gson.Gson().toJson(error)
            )
        }

        Log.d("TvLoginServer", "Login attempt for: $email")
        return try {
            // Run signIn synchronously in the server thread (quick Supabase call)
            val result = kotlinx.coroutines.runBlocking {
                authManager.signInWithEmail(email, password)
            }
            result.fold(
                onSuccess = {
                    Log.d("TvLoginServer", "Login successful for: $email")
                    loginCallback?.invoke(true, null)
                    val response = mapOf("status" to "ok")
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json; charset=utf-8",
                        com.google.gson.Gson().toJson(response)
                    )
                },
                onFailure = { error ->
                    Log.e("TvLoginServer", "Login failed for: $email", error)
                    loginCallback?.invoke(false, error.message)
                    val message = error.message ?: "Erro ao fazer login"
                    val response = mapOf("status" to "error", "message" to message)
                    newFixedLengthResponse(
                        Response.Status.OK,
                        "application/json; charset=utf-8",
                        com.google.gson.Gson().toJson(response)
                    )
                }
            )
        } catch (e: Exception) {
            Log.e("TvLoginServer", "Login exception", e)
            val error = mapOf("status" to "error", "message" to "Erro interno")
            newFixedLengthResponse(
                Response.Status.OK,
                "application/json; charset=utf-8",
                com.google.gson.Gson().toJson(error)
            )
        }
    }

    companion object {
        fun startOnAvailablePort(
            context: Context,
            authManager: AuthManager,
            logoProvider: (() -> ByteArray?)? = null,
            startPort: Int = 8083,
            maxAttempts: Int = 10
        ): TvLoginServer? {
            for (port in startPort until startPort + maxAttempts) {
                try {
                    val server = TvLoginServer(
                        context = context,
                        authManager = authManager,
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
