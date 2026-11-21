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
            val rawSocket = Socket()
            var socket: Socket = rawSocket
            try {
                rawSocket.soTimeout = timeoutMs
                rawSocket.connect(InetSocketAddress(host, port), timeoutMs)
                if (scheme == "rtmps") {
                    // Passar host explícito permite SNI e validação do certificado.
                    val tlsSocket = SSLSocketFactory.getDefault()
                        .createSocket(rawSocket, host, port, true) as javax.net.ssl.SSLSocket
                    socket = tlsSocket
                    tlsSocket.soTimeout = timeoutMs
                    val params = tlsSocket.sslParameters
                    params.endpointIdentificationAlgorithm = "HTTPS"
                    tlsSocket.sslParameters = params
                    tlsSocket.startHandshake()
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


    /**
     * Lê uma única resposta AMF0 de connect em servidores que enviam fmt=0/3.
     * Leitor limitado: não processa mensagens de controle intercaladas.
     * Nunca interpreta este resultado como autorização para publicar mídia.
     */
    fun readConnectResponseForTest(): RtmpCommandResponse.Response {
        check(!socket.isClosed) { "Conexão fechada" }
        val message = RtmpSimpleChunkReader.read(socket.getInputStream())
        require(message.typeId == 20) {
            "Resposta recebida não é comando AMF0; leitor RTMP completo ainda necessário"
        }
        val response = RtmpCommandResponse.parse(message.payload)
        require(response.transactionId == 1.0) {
            "Resposta não corresponde ao comando connect"
        }
        return response
    }

    override fun close() {
        socket.close()
    }
}
