package com.rednovelas.obsmobilern

import java.io.Closeable
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.SSLSocketFactory

/**
 * Transporte RTMP experimental. Conecta e faz handshake em thread de trabalho,
 * pode negociar publish e enviar mídia codificada somente em testes controlados.
 *
 * Nunca chamar na thread principal do Android. Usar servidor de teste.
 */
internal class RtmpTestTransport private constructor(
    private val socket: Socket
) : Closeable {
    private val chunkReader = RtmpChunkStreamReader(socket.getInputStream())
    private var publishedStreamId: Int? = null

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
        val message = readNextCommandForTest()
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
        val message = readNextCommandForTest()
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
        val message = readNextCommandForTest()
        require(message.messageStreamId == expectedStreamId) {
            "Resposta pertence a outra sessão RTMP"
        }
        require(message.typeId == 20) {
            "Resposta de publicação não é comando AMF0"
        }
        val code = RtmpPublishStatus.code(message.payload)
        val accepted = code == "NetStream.Publish.Start"
        publishedStreamId = if (accepted) expectedStreamId else null
        return accepted
    }

    /**
     * Executa a negociação e solicita publicação somente em ambiente de teste.
     * Nunca inicia o envio de vídeo/áudio. Não chamar na thread de UI.
     * O leitor simplificado ainda não suporta servidores com chunks intercalados.
     */
    fun requestPublishForTest(destination: RtmpDestination): Boolean =
        requestPublishStreamIdForTest(destination) != null

    /**
     * Retorna o ID confirmado para que a ponte de mídia use exatamente
     * a sessão publicada. Null significa publicação recusada.
     * Somente para servidor de testes autorizado.
     */
    fun requestPublishStreamIdForTest(destination: RtmpDestination): Int? {
        publishedStreamId = null
        val streamId = negotiateStreamForTest(destination)
        sendPublishCommandForTest(destination, streamId)
        return if (readPublishConfirmationForTest(streamId)) streamId else null
    }

    /**
     * Ignora mensagens de controle simples enviadas antes da resposta AMF0.
     * Não suporta interleaving de chunks nem cabeçalhos fmt=1/2; limita a
     * quantidade de mensagens para evitar espera ilimitada.
     */
    private fun readNextCommandForTest(): RtmpSimpleChunkReader.Message {
        repeat(16) {
            val message = chunkReader.read()
            when (message.typeId) {
                1 -> {
                    require(message.payload.size == 4) { "Set Chunk Size inválido" }
                    val bytes = message.payload
                    val size = ((bytes[0].toInt() and 0x7f) shl 24) or
                        ((bytes[1].toInt() and 255) shl 16) or
                        ((bytes[2].toInt() and 255) shl 8) or
                        (bytes[3].toInt() and 255)
                    require(size in 1..(1024 * 1024)) { "Chunk Size fora do limite" }
                    chunkReader.chunkSize = size
                }
                3, 5, 6 -> Unit // Acknowledgement / Window / Peer Bandwidth
                4 -> {
                    // User Control: PingRequest (evento 6) requer PingResponse (7).
                    require(message.payload.size >= 2) { "User Control RTMP inválido" }
                    val event = ((message.payload[0].toInt() and 255) shl 8) or
                        (message.payload[1].toInt() and 255)
                    if (event == 6) {
                        require(message.payload.size == 6) { "PingRequest RTMP inválido" }
                        val response = byteArrayOf(0, 7) + message.payload.copyOfRange(2, 6)
                        val bytes = RtmpChunkWriter.encode(
                            chunkStreamId = 2,
                            messageStreamId = 0,
                            messageTypeId = 4,
                            timestampMs = 0,
                            payload = response
                        )
                        socket.getOutputStream().write(bytes)
                        socket.getOutputStream().flush()
                    }
                }
                20 -> return message
                else -> error("Tipo de mensagem RTMP ainda não suportado: ${message.typeId}")
            }
        }
        error("Servidor enviou mensagens de controle demais")
    }

    /**
     * Envia mídia já codificada somente após Publish.Start.
     * Não captura HLS nem codifica quadros; usar apenas servidor de teste.
     */
    fun sendEncodedMediaPacketForTest(streamId: Int, packet: ByteArray) {
        check(!socket.isClosed) { "Conexão fechada" }
        check(publishedStreamId == streamId && streamId > 0) {
            "Publicação RTMP não confirmada"
        }
        require(packet.size in 13..(1024 * 1024)) { "Pacote RTMP inválido" }
        require((packet[0].toInt() ushr 6) == 0) { "Cabeçalho RTMP inválido" }
        val type = packet[7].toInt() and 255
        require(type == 8 || type == 9) { "Somente áudio e vídeo" }
        val id = (packet[8].toInt() and 255) or
            ((packet[9].toInt() and 255) shl 8) or
            ((packet[10].toInt() and 255) shl 16) or
            ((packet[11].toInt() and 255) shl 24)
        require(id == streamId) { "Pacote de outra sessão RTMP" }
        socket.getOutputStream().write(packet)
        socket.getOutputStream().flush()
    }

    override fun close() {
        socket.close()
    }
}
