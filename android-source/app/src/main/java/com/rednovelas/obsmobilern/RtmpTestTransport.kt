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

    /**
     * Executa somente a negociação inicial em um servidor de teste.
     * Falha de forma explícita quando o servidor envia mensagens de controle
     * ainda não suportadas pelo leitor experimental.
     */
    fun negotiateConnectForTest(destination: RtmpDestination): RtmpNegotiation.Stage {
        sendConnectCommand(destination)
        val response = readConnectResponseForTest()
        val negotiation = RtmpNegotiation()
        val stage = negotiation.onCommand(response)
        check(stage == RtmpNegotiation.Stage.WAIT_STREAM) {
            "Servidor não confirmou o comando connect"
        }
        return stage
    }

    /**
     * Solicita ao servidor um ID de stream após connect confirmado.
     * Não inicia publicação e não envia chave de transmissão.
     */
    fun sendCreateStreamCommand() {
        check(!socket.isClosed) { "Conexão fechada" }
        val bytes = RtmpChunkWriter.encode(
            chunkStreamId = 3,
            messageStreamId = 0,
            messageTypeId = 20,
            timestampMs = 0,
            payload = RtmpPublishCommands.createStream()
        )
        socket.getOutputStream().write(bytes)
        socket.getOutputStream().flush()
    }

    /**
     * Teste restrito: conecta, solicita createStream e valida o ID retornado.
     * Não publica stream nem envia mídia. O leitor simples pode rejeitar
     * servidores reais que intercalam mensagens de controle RTMP.
     */
    fun negotiateStreamForTest(destination: RtmpDestination): Int {
        val negotiation = RtmpNegotiation()
        sendConnectCommand(destination)
        val connectResponse = readConnectResponseForTest()
        check(negotiation.onCommand(connectResponse) == RtmpNegotiation.Stage.WAIT_STREAM) {
            "Servidor RTMP recusou connect"
        }
        sendCreateStreamCommand()
        val message = RtmpSimpleChunkReader.read(socket.getInputStream())
        require(message.typeId == 20) {
            "Resposta createStream não é AMF0; leitor RTMP completo necessário"
        }
        val response = RtmpCommandResponse.parse(message.payload)
        check(negotiation.onCommand(response) == RtmpNegotiation.Stage.READY) {
            "Servidor RTMP não confirmou createStream"
        }
        return requireNotNull(negotiation.streamId)
    }

    /**
     * Envia publish apenas após receber um streamId válido.
     * Este método não verifica onStatus nem envia vídeo ou áudio.
     * Usar exclusivamente em servidor de teste autorizado.
     */
    fun sendPublishCommandForTest(destination: RtmpDestination, streamId: Int) {
        check(!socket.isClosed) { "Conexão fechada" }
        require(streamId > 0) { "ID da sessão RTMP inválido" }
        val message = RtmpChunkWriter.encode(
            chunkStreamId = 8,
            messageStreamId = streamId,
            messageTypeId = 20,
            timestampMs = 0,
            payload = RtmpPublishCommands.publish(destination.streamName)
        )
        socket.getOutputStream().write(message)
        socket.getOutputStream().flush()
    }

    /**
     * Confere a resposta de publish somente no protocolo simplificado de teste.
     * Servidores com mensagens intercaladas exigem um leitor RTMP completo.
     * A confirmação não significa que vídeo ou áudio estejam sendo enviados.
     */
    fun readPublishConfirmationForTest(expectedStreamId: Int): Boolean {
        check(!socket.isClosed) { "Conexão fechada" }
        require(expectedStreamId > 0)
        val message = RtmpSimpleChunkReader.read(socket.getInputStream())
        require(message.messageStreamId == expectedStreamId) {
            "Resposta pertence a outra sessão RTMP"
        }
        require(message.typeId == 20) {
            "Resposta de publicação não é comando AMF0"
        }
        val code = RtmpPublishStatus.code(message.payload)
        return when (code) {
            "NetStream.Publish.Start" -> true
            "NetStream.Publish.BadName", "NetStream.Publish.Denied" -> false
            else -> false
        }
    }

    /**
     * Executa a negociação e solicita publicação somente em ambiente de teste.
     * Nunca inicia o envio de vídeo/áudio. Não chamar na thread de UI.
     * O leitor simplificado ainda não suporta servidores com chunks intercalados.
     */
    fun requestPublishForTest(destination: RtmpDestination): Boolean {
        val streamId = negotiateStreamForTest(destination)
        sendPublishCommandForTest(destination, streamId)
        return readPublishConfirmationForTest(streamId)
    }

    override fun close() {
        socket.close()
    }
}
