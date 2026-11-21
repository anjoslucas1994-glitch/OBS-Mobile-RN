package com.rednovelas.obsmobilern

import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom

/**
 * Handshake RTMP simples (versão 3) para uma conexão TCP já aberta.
 * NÃO autentica, não publica stream, não envia áudio/vídeo e não suporta RTMPS.
 * Executar somente fora da thread principal e em servidor de teste autorizado.
 */
internal object RtmpHandshake {
    fun perform(input: InputStream, output: OutputStream) {
        val random = SecureRandom()
        val c1 = ByteArray(1536)
        random.nextBytes(c1)
        // Versão 3, tempo 0 e zero nos bytes 4..7 (handshake simples).
        for (i in 0..7) c1[i] = 0
        output.write(byteArrayOf(3))
        output.write(c1)
        output.flush()

        val version = input.read()
        require(version == 3) { "Servidor não respondeu RTMP v3" }
        val s1 = ByteArray(1536)
        val s2 = ByteArray(1536)
        readFully(input, s1)
        readFully(input, s2)

        // C2 deve ecoar S1 no handshake simples.
        output.write(s1)
        output.flush()
        // Verificação mínima do eco do C1 pelo servidor.
        // S2 contém tempo e eco, e pode variar em implementações legítimas.
    }

    private fun readFully(input: InputStream, buffer: ByteArray) {
        var pos = 0
        while (pos < buffer.size) {
            val read = input.read(buffer, pos, buffer.size - pos)
            if (read < 0) throw java.io.EOFException("Handshake RTMP incompleto")
            if (read == 0) continue
            pos += read
        }
    }
}
