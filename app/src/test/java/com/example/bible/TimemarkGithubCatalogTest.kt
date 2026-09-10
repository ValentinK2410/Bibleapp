package com.example.bible

import com.example.bible.data.TimemarkGithubCatalog
import org.junit.Assert.assertEquals
import org.junit.Test

class TimemarkGithubCatalogTest {

    @Test
    fun projectId_isStable() {
        assertEquals("gh_syn_genesis_1", TimemarkGithubCatalog.projectId("SYN", "genesis", 1))
        assertEquals(
            "syn_genesis_1.json",
            TimemarkGithubCatalog.projectFileName("SYN", "genesis", 1),
        )
    }

    @Test
    fun projectUrls_pointToRepoTimemarks() {
        val urls = TimemarkGithubCatalog.projectUrls("projects/syn_genesis_1.json")
        assertEquals(3, urls.size)
        assertTrue(urls.all { it.contains("docs/timemarks/projects/syn_genesis_1.json") })
    }

    private fun assertTrue(condition: Boolean) {
        org.junit.Assert.assertTrue(condition)
    }
}
