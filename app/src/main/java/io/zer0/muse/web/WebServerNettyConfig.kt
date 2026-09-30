package io.zer0.muse.web

import io.ktor.server.engine.connector
import io.ktor.server.engine.sslConnector
import io.ktor.server.netty.NettyApplicationEngine

internal fun webServerNettyConfiguration(
    tls: WebServerTls.TlsMaterial?,
    bindHost: String,
    listenPort: Int,
): NettyApplicationEngine.Configuration.() -> Unit {
    return {
        if (tls != null) {
            sslConnector(
                keyStore = tls.keyStore,
                keyAlias = tls.alias,
                keyStorePassword = { tls.password },
                privateKeyPassword = { tls.password },
            ) {
                host = bindHost
                port = listenPort
            }
        } else {
            connector {
                host = bindHost
                port = listenPort
            }
        }
    }
}
