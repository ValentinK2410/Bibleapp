package com.example.bible

import com.example.bible.data.AppUpdateCatalog
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCatalogTest {

    @Test
    fun networkErrorMessage_dnsIsHumanReadable() {
        val msg = AppUpdateCatalog.networkErrorMessage(
            "Unable to resolve host \"raw.githubusercontent.com\": No address associated with hostname",
        )
        assertTrue(msg.contains("интернет") || msg.contains("VPN"))
    }
}
