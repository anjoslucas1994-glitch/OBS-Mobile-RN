package com.rednovelas.obsmobilern

import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

/**
 * Transporte RTMP experimental. Conecta e faz handshake em thread de trabalho,
 * mas NÃO executa publish nem envia vídeo/áudio.
 *
 * Nunca chamar na thread principal do Android. Usar servidor de teste.
 */
internal class RtmpTestTransport private constructor(
    private val socket: Socket
) : Closeable {
    companion object {
        fun connect(destination: RtmpDestination, timeoutMs: Int = 5000): RtmpTestTransport {
            require(timeoutMs in 1000..30000)
            val uri = java.net.URI(destination.serverUrl)
            val scheme = uri.scheme.lowercase()
            require(scheme == "rtmp" || scheme == "rtmps")
            val host = requireNotNull(uri.host) { "Servidor RTMP inválido" }
            val port = if (uri.port > 0) uri.port else if (scheme == "rtmps") 443 else 1935
            val socket: Socket = if (scheme == "rtmps") {
                SSLSocketFactory.getDefault().createSocket() as Socket
            } else Socket()
            try {
                socket.soTimeout = timeoutMs
                socket.connect(InetSocketAddress(host, port), timeoutMs)
                if (scheme == "rtmps") {
                    (socket as javax.net.ssl.SSLSocket).startHandshake()
                }
                RtmpHandshake.perform(socket.getInputStream(), socket.getOutputStream())
                return RtmpTestTransport(socket)
            } catch (error: Exception) {
                socket.close()
                throw error
            }
        }
    }


    /**
     * Envia apenas o comando AMF0 connect.
     * Não executa publish nem inicia transmissão.
     */
    fun sendConnectCommand(destination: RtmpDestination) {
        check(!socket.isClosed) { "Conexão fechada" }
        val payload = RtmpPublishCommands.connect(destination.app, destination.tcUrl)
        val message = RtmpChunkWriter.encode(
            chunkStreamId = 3,
            messageStreamId = 0,
            messageTypeId = 20,
            timestampMs = 0,
            payload = payload
        )
        socket.getOutputStream().write(message)
        socket.getOutputStream().flush()
    }

    override fun close() {
        socket.close()
    }
}
