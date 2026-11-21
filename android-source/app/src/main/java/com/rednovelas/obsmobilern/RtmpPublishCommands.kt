package com.rednovelas.obsmobilern

/**
 * Prepara mensagens de comando RTMP para uma sessão de publicação.
 * A conexão e as respostas do servidor precisam ser tratadas por outro componente.
 * Nunca registre ou exponha a chave de transmissão em logs.
 */
internal object RtmpPublishCommands {
    fun connect(app: String, tcUrl: String): ByteArray {
        require(app.isNotBlank())
        require(tcUrl.startsWith("rtmp://") || tcUrl.startsWith("rtmps://"))
        val properties = linkedMapOf(
            "app" to Amf0Writer.string(app),
            "type" to Amf0Writer.string("nonprivate"),
            "flashVer" to Amf0Writer.string("FMLE/3.0"),
            "tcUrl" to Amf0Writer.string(tcUrl),
            "fpad" to Amf0Writer.boolean(false),
            "capabilities" to Amf0Writer.number(15.0),
            "audioCodecs" to Amf0Writer.number(0x0400.toDouble()),
            "videoCodecs" to Amf0Writer.number(0x0080.toDouble()),
            "videoFunction" to Amf0Writer.number(1.0)
        )
        return Amf0Writer.command("connect", 1.0, Amf0Writer.objectValue(properties))
    }

    fun releaseStream(streamName: String): ByteArray =
        Amf0Writer.command(
            "releaseStream", 2.0, Amf0Writer.nullValue(), Amf0Writer.string(streamName)
        )

    fun fcPublish(streamName: String): ByteArray =
        Amf0Writer.command(
            "FCPublish", 3.0, Amf0Writer.nullValue(), Amf0Writer.string(streamName)
        )

    fun createStream(): ByteArray =
        Amf0Writer.command("createStream", 4.0, Amf0Writer.nullValue())

    fun publish(streamName: String): ByteArray =
        Amf0Writer.command(
            "publish", 0.0, Amf0Writer.nullValue(),
            Amf0Writer.string(streamName), Amf0Writer.string("live")
        )
}
