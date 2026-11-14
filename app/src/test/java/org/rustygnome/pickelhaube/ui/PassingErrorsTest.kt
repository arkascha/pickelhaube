package org.rustygnome.pickelhaube.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rustygnome.pickelhaube.tr064.AuthenticationException
import org.rustygnome.pickelhaube.tr064.FritzConfig
import org.rustygnome.pickelhaube.tr064.HttpStatusException
import org.rustygnome.pickelhaube.tr064.Tr064Exception
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class PassingErrorsTest {

    private fun passing(e: Throwable) = describeError(e, FritzConfig()).passing

    @Test
    fun `the box not answering, not found or busy is likely to pass`() {
        listOf(
            SocketTimeoutException(), ConnectException(), NoRouteToHostException(), UnknownHostException("fritz.box"),
            HttpStatusException(503),
        ).forEach { assertTrue(it.javaClass.simpleName, passing(it)) }
    }

    @Test
    fun `a wrong password, missing rights or another answer of the box is not`() {
        listOf(
            AuthenticationException(), HttpStatusException(404), Tr064Exception(606, "Action Not Authorized", "606"),
        ).forEach { assertFalse(it.javaClass.simpleName, passing(it)) }
    }
}
