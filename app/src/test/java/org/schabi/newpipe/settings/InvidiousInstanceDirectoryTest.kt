package org.schabi.newpipe.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class InvidiousInstanceDirectoryTest {
    @Test
    fun keepsOnlyHealthyOfficialHttpsApiInstances() {
        val entries = InvidiousInstanceDirectory.parse(
            """
            [
              ["good.example", {
                "flag": "🇩🇪",
                "region": "de",
                "api": true,
                "type": "https",
                "uri": "HTTPS://GOOD.EXAMPLE/",
                "monitor": {
                  "uptime": 99.5,
                  "down": false,
                  "enabled": true,
                  "published": true,
                  "last_status": 200,
                  "ssl": {"valid": true}
                }
              }],
              ["no-api.example", {
                "api": false,
                "type": "https",
                "uri": "https://no-api.example",
                "monitor": {
                  "uptime": 100,
                  "down": false,
                  "enabled": true,
                  "published": true,
                  "last_status": 200,
                  "ssl": {"valid": true}
                }
              }],
              ["down.example", {
                "api": true,
                "type": "https",
                "uri": "https://down.example",
                "monitor": {
                  "uptime": 99,
                  "down": true,
                  "enabled": true,
                  "published": true,
                  "last_status": 503,
                  "ssl": {"valid": true}
                }
              }],
              ["low-uptime.example", {
                "api": true,
                "type": "https",
                "uri": "https://low-uptime.example",
                "monitor": {
                  "uptime": 89.9,
                  "down": false,
                  "enabled": true,
                  "published": true,
                  "last_status": 200,
                  "ssl": {"valid": true}
                }
              }],
              ["bad-tls.example", {
                "api": true,
                "type": "https",
                "uri": "https://bad-tls.example",
                "monitor": {
                  "uptime": 99,
                  "down": false,
                  "enabled": true,
                  "published": true,
                  "last_status": 200,
                  "ssl": {"valid": false}
                }
              }]
            ]
            """.trimIndent()
        )

        assertEquals(1, entries.size)
        assertEquals("https://good.example", entries.single().url)
        assertEquals("good.example", entries.single().host)
        assertEquals("🇩🇪", entries.single().flag)
        assertEquals("DE", entries.single().region)
        assertEquals(99.5, entries.single().uptime, 0.0)
    }
}
