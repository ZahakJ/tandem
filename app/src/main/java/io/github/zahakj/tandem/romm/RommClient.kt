package io.github.zahakj.tandem.romm

import io.github.zahakj.tandem.PrivateOffline
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import javax.net.ssl.SSLException

/** Device-only configuration. Never include credentials in diagnostics or user-facing errors. */
@Serializable
data class RommConfig(val server: String = "", val token: String = "", val enabled: Boolean = false) {
    val configured: Boolean get() = server.isNotEmpty() && token.isNotEmpty()
    override fun toString() = "RommConfig(redacted, enabled=$enabled)"
}

object RommAddress {
    /** A website root, optionally under a reverse-proxy subpath; never credentials, query parameters or an API URL. */
    fun normalize(text: String): String {
        require(text.length <= 2048) { "Server URL is too long" }
        val uri = try { URI(text.trim()) } catch (_: Exception) { throw IllegalArgumentException("Enter a valid HTTPS server URL") }
        require(uri.scheme.equals("https", true) && !uri.host.isNullOrBlank() && uri.rawAuthority != null) { "Use an HTTPS server URL" }
        require(uri.rawUserInfo == null && uri.rawQuery == null && uri.rawFragment == null) { "The URL must not contain credentials, a query or a fragment" }
        require(uri.port == -1 || uri.port in 1..65535) { "Enter a valid server port" }
        val path = uri.rawPath.orEmpty().trimEnd('/')
        require(!path.contains('%') && !path.contains('\\') && path.split('/').none { it == "." || it == ".." }) { "Use a plain server root path" }
        require(!path.endsWith("/api") && !path.endsWith("/openapi.json")) { "Enter the website URL, without /api or /openapi.json" }
        return "https://${uri.rawAuthority}$path"
    }

    fun validToken(token: String): Boolean = token.length in 1..4096 && token.all { it.code in 33..126 }
}

data class RommResponse(val status: Int, val body: String)

/** Blocking, bounded GET. Only the explicitly configured origin is used; redirects and HTTP caching are disabled. */
fun interface RommTransport {
    fun get(url: String, bearer: String?): RommResponse
}

class RommHttpTransport(
    private val checkNetwork: () -> Unit,
    private val open: (URL) -> HttpURLConnection = { it.openConnection() as HttpURLConnection },
) : RommTransport {
    override fun get(url: String, bearer: String?): RommResponse {
        checkNetwork()
        val target = URL(url)
        require(target.protocol == "https") { "HTTPS required" }
        val connection = open(target)
        try {
            connection.instanceFollowRedirects = false
            connection.useCaches = false
            connection.connectTimeout = 12_000
            connection.readTimeout = 12_000
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/json")
            if (bearer != null) {
                require(RommAddress.validToken(bearer)) { "Invalid API token" }
                connection.setRequestProperty("Authorization", "Bearer $bearer")
            }
            checkNetwork()
            val status = connection.responseCode
            // Do not retain server error bodies: a proxy can echo credentials or private addresses.
            if (status != 200) return RommResponse(status, "")
            val out = ByteArrayOutputStream()
            connection.inputStream.use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    checkNetwork()
                    if (Thread.currentThread().isInterrupted) throw InterruptedException()
                    val count = input.read(buffer)
                    if (count < 0) break
                    if (out.size() + count > MAX_BYTES) throw IOException("Response too large")
                    out.write(buffer, 0, count)
                }
            }
            return RommResponse(status, out.toString(Charsets.UTF_8.name()))
        } finally {
            connection.disconnect()
        }
    }

    companion object { const val MAX_BYTES = 8 * 1024 * 1024 }
}

enum class RommProblem(val message: String) {
    Disabled("RoMM is disabled"),
    Configuration("Set an HTTPS server URL and API token first"),
    PrivateMode("Private mode is on; Tandem stays offline"),
    Authentication("Token rejected. Check that it is valid and has not expired"),
    Permission("Access denied. The token needs platforms.read permission"),
    Redirect("Server redirected the request. Enter its final HTTPS website URL"),
    Unsupported("This integration supports RoMM 5.4.1. Check the server version"),
    Contract("The server did not return the expected RoMM API. Check the website URL and proxy"),
    Timeout("Connection timed out. Check the network or Tailscale connection and try again"),
    Tls("TLS verification failed. Check the server certificate and device clock"),
    Network("Could not reach RoMM. Check the network, server URL and Tailscale connection"),
    Server("RoMM could not complete the request. Try again later"),
}

sealed interface RommResult {
    data class Connected(val platforms: Int) : RommResult
    data class Failed(val problem: RommProblem) : RommResult
}

/** Explicit connection probe only. No background sync and no writes to the server or local game library. */
class RommClient(private val transport: RommTransport, private val checkNetwork: () -> Unit) {
    private val json = Json { ignoreUnknownKeys = true }

    fun test(config: RommConfig): RommResult {
        if (!config.enabled) return RommResult.Failed(RommProblem.Disabled)
        val server = try { RommAddress.normalize(config.server) } catch (_: Exception) { return RommResult.Failed(RommProblem.Configuration) }
        if (!RommAddress.validToken(config.token)) return RommResult.Failed(RommProblem.Configuration)
        return try {
            checkNetwork()
            // FastAPI publishes the running version and paths here. Never send a token before validating them.
            val spec = get("$server/openapi.json", null)
            val root = json.parseToJsonElement(spec) as? JsonObject ?: return failed(RommProblem.Contract)
            val version = (root["info"] as? JsonObject)?.get("version")?.jsonPrimitive
            if (version?.isString != true || version.content.removePrefix("v") != "5.4.1") return failed(RommProblem.Unsupported)
            val paths = root["paths"] as? JsonObject
            if ((paths?.get("/api/platforms") as? JsonObject)?.get("get") !is JsonObject) return failed(RommProblem.Contract)
            checkNetwork()
            val response = get("$server/api/platforms", config.token)
            val platforms = json.parseToJsonElement(response) as? JsonArray ?: return failed(RommProblem.Contract)
            // Only fields needed to recognize the verified PlatformSchema. Unknown fields are ignored.
            for (platform in platforms) {
                val fields = platform as? JsonObject ?: return failed(RommProblem.Contract)
                val id = fields["id"]?.jsonPrimitive
                if (id == null || id.isString || id.content.toLongOrNull() == null || fields["name"]?.jsonPrimitive?.isString != true) return failed(RommProblem.Contract)
            }
            checkNetwork()
            RommResult.Connected(platforms.size)
        } catch (e: InterruptedException) {
            throw e
        } catch (_: PrivateOffline) {
            failed(RommProblem.PrivateMode)
        } catch (e: ProbeFailure) {
            failed(e.problem)
        } catch (_: SocketTimeoutException) {
            failed(RommProblem.Timeout)
        } catch (_: SSLException) {
            failed(RommProblem.Tls)
        } catch (_: IOException) {
            failed(RommProblem.Network)
        } catch (_: Exception) {
            failed(RommProblem.Contract)
        }
    }

    private fun get(url: String, token: String?): String {
        val response = transport.get(url, token)
        val problem = when (response.status) {
            200 -> return response.body
            401 -> RommProblem.Authentication
            403 -> RommProblem.Permission
            in 300..399 -> RommProblem.Redirect
            404 -> RommProblem.Contract
            else -> RommProblem.Server
        }
        throw ProbeFailure(problem)
    }

    private fun failed(problem: RommProblem) = RommResult.Failed(problem)
    private class ProbeFailure(val problem: RommProblem) : IOException(problem.message)
}
