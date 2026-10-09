package org.schabi.newpipe.util

import java.net.Inet4Address
import java.net.InetAddress
import okhttp3.Dns

/**
 * An [okhttp3.Dns] that strips IPv6 (AAAA) addresses from lookups, forcing OkHttp to connect
 * over IPv4 only.
 *
 * Some networks and services (YouTube in particular) are known to behave badly over IPv6 —
 * see https://github.com/TeamNewPipe/NewPipe/issues/13332 and the issues linked from it. This
 * is an opt-in workaround, not a default, since it has no effect on a purely IPv6-only host:
 * if the system resolver returns no A records at all, the original (IPv6) result is used so
 * connectivity to such a host isn't broken entirely.
 */
object Ipv4OnlyDns : Dns {
    override fun lookup(hostname: String): List<InetAddress> {
        val systemResult = Dns.SYSTEM.lookup(hostname)
        val ipv4Only = systemResult.filterIsInstance<Inet4Address>()
        return ipv4Only.ifEmpty { systemResult }
    }
}
