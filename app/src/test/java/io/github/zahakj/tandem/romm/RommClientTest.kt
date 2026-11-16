package io.github.zahakj.tandem.romm

import io.github.zahakj.tandem.PrivateOffline
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import javax.net.ssl.SSLHandshakeException

class RommClientTest {
    private val config = RommConfig("https://romm.example.test/library", "synthetic-token", true)
    // A reduced synthetic OpenAPI fixture, matching verified FastAPI info and operation shapes.
    private fun spec(version: String = "5.4.1") = RommResponse(200, """{"info":{"version":"$version"},"paths":{"/api/platforms":{"get":{}}}}""")
    private val platforms = RommResponse(200, """[{"id":1,"name":"Synthetic platform","slug":"fixture","fs_slug":"fixture","rom_count":0,"future_field":true}]""")

    private class FakeTransport(vararg responses: RommResponse) : RommTransport {
        val calls = mutableListOf<Pair<String, String?>>()
        private val queue = responses.toMutableList()
        override fun get(url: String, bearer: String?): RommResponse {
            calls += url to bearer
            return queue.removeAt(0)
        }
    }

    @Test fun openApiIsAnonymousAndPlatformProbeUsesBearerAndPreservesSubpath() {
        val transport = FakeTransport(spec(), platforms)
        assertEquals(RommResult.Connected(1), RommClient(transport) {}.test(config))
        assertEquals(listOf("${config.server}/openapi.json" to null, "${config.server}/api/platforms" to config.token), transport.calls)
    }

    @Test fun emptyPlatformLibraryIsAValidConnection() {
        assertEquals(RommResult.Connected(0), RommClient(FakeTransport(spec(), RommResponse(200, "[]"))) {}.test(config))
    }

    @Test fun disabledByDefaultAndNoRequestsUntilEnabled() {
        val transport = FakeTransport()
        assertFalse(RommConfig().enabled)
        assertEquals(RommResult.Failed(RommProblem.Disabled), RommClient(transport) {}.test(config.copy(enabled = false)))
        assertTrue(transport.calls.isEmpty())
    }

    @Test fun incompleteOrUnsafeConfigurationNeverSendsRequests() {
        for (candidate in listOf(config.copy(token = ""), config.copy(token = "bad\r\nheader"), config.copy(server = "http://romm.example.test"))) {
            val transport = FakeTransport()
            assertEquals(RommResult.Failed(RommProblem.Configuration), RommClient(transport) {}.test(candidate))
            assertTrue(transport.calls.isEmpty())
        }
    }

    @Test fun privateModeBlocksEvenTheAnonymousRequest() {
        val transport = FakeTransport()
        assertEquals(RommResult.Failed(RommProblem.PrivateMode), RommClient(transport) { throw PrivateOffline() }.test(config))
        assertTrue(transport.calls.isEmpty())
    }

    @Test fun enteringPrivateModeBetweenRequestsPreventsTokenTransmission() {
        val transport = FakeTransport(spec())
        var checks = 0
        val client = RommClient(transport) { if (++checks == 2) throw PrivateOffline() }
        assertEquals(RommResult.Failed(RommProblem.PrivateMode), client.test(config))
        assertEquals(1, transport.calls.size)
        assertNull(transport.calls.single().second)
    }

    @Test fun unsupportedVersionsNeverReceiveToken() {
        for (version in listOf("5.4.0", "5.5.0", "development", "5.4.1-rc1")) {
            val transport = FakeTransport(spec(version))
            assertEquals(RommResult.Failed(RommProblem.Unsupported), RommClient(transport) {}.test(config))
            assertEquals(1, transport.calls.size)
            assertNull(transport.calls.single().second)
        }
    }

    @Test fun missingPlatformOperationNeverReceivesToken() {
        val transport = FakeTransport(RommResponse(200, """{"info":{"version":"5.4.1"},"paths":{}}"""))
        assertEquals(RommResult.Failed(RommProblem.Contract), RommClient(transport) {}.test(config))
        assertEquals(1, transport.calls.size)
    }

    @Test fun authenticationAndPermissionFailuresAreDistinct() {
        for ((code, problem) in listOf(401 to RommProblem.Authentication, 403 to RommProblem.Permission)) {
            assertEquals(RommResult.Failed(problem), RommClient(FakeTransport(spec(), RommResponse(code, "private data"))) {}.test(config))
        }
    }

    @Test fun redirectsAreErrorsOnEitherRequest() {
        for (code in listOf(301, 302, 307, 308)) {
            val beforeToken = FakeTransport(RommResponse(code, ""))
            assertEquals(RommResult.Failed(RommProblem.Redirect), RommClient(beforeToken) {}.test(config))
            assertEquals(1, beforeToken.calls.size)
            assertEquals(RommResult.Failed(RommProblem.Redirect), RommClient(FakeTransport(spec(), RommResponse(code, ""))) {}.test(config))
        }
    }

    @Test fun wrongPathsAndServerFailuresAreUsefulErrors() {
        for ((code, problem) in listOf(404 to RommProblem.Contract, 429 to RommProblem.Server, 500 to RommProblem.Server, 503 to RommProblem.Server)) {
            assertEquals(RommResult.Failed(problem), RommClient(FakeTransport(spec(), RommResponse(code, ""))) {}.test(config))
        }
    }

    @Test fun invalidJsonAndUnexpectedShapesCannotReportConnected() {
        for (body in listOf("<html>login</html>", "{}", "null", "[{}]", "[{\"id\":\"1\",\"name\":\"bad\"}]", "[{\"id\":1,\"name\":null}]")) {
            assertEquals(RommResult.Failed(RommProblem.Contract), RommClient(FakeTransport(spec(), RommResponse(200, body))) {}.test(config))
        }
    }

    @Test fun timeoutTlsAndNetworkFailuresNeverExposeExceptionDetails() {
        for ((exception, problem) in listOf(SocketTimeoutException(config.token) to RommProblem.Timeout, SSLHandshakeException(config.token) to RommProblem.Tls, IOException(config.token) to RommProblem.Network)) {
            val client = RommClient(RommTransport { _, _ -> throw exception }) {}
            val result = client.test(config)
            assertEquals(RommResult.Failed(problem), result)
            assertFalse(result.toString().contains(config.token))
        }
    }

    @Test(expected = InterruptedException::class) fun cancellationIsNotReportedAsConnectionFailure() {
        RommClient(RommTransport { _, _ -> throw InterruptedException() }) {}.test(config)
    }

    @Test fun credentialsAreRedacted() {
        assertFalse(config.toString().contains(config.token))
        assertFalse(config.toString().contains(config.server))
    }

    @Test fun addressesAcceptHttpsDnsIpv6PortsAndSubpaths() {
        assertEquals("https://romm.example.test", RommAddress.normalize(" https://romm.example.test/ "))
        assertEquals("https://romm.example.test:8443/romm", RommAddress.normalize("https://romm.example.test:8443/romm/"))
        assertEquals("https://[::1]:8443", RommAddress.normalize("https://[::1]:8443/"))
    }

    @Test fun addressesRejectCredentialsQueriesFragmentsAndTraversal() {
        for (address in listOf("", "romm.example.test", "http://romm.example.test", "file:///romm", "https://u:p@romm.example.test", "https://romm.example.test/?token=private", "https://romm.example.test/#private", "https://romm.example.test/a/../b", "https://romm.example.test/%2e%2e", "https://romm.example.test/api/", "https://romm.example.test/openapi.json", "https://romm.example.test:0", "https://romm.example.test:99999")) {
            assertThrows(IllegalArgumentException::class.java) { RommAddress.normalize(address) }
        }
        assertThrows(IllegalArgumentException::class.java) { RommAddress.normalize("https://romm.example.test/" + "a".repeat(2048)) }
    }
}
