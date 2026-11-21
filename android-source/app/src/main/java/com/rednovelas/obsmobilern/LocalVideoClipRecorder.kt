package com.rednovelas.obsmobilern

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Teste local, sem áudio e sem conexão RTMP. Escreve o H.264 já
 * composto (logo + textos) em Movies/OBS Mobile RN via MediaStore.
 * Requer Android 10+, para dispensar permissão de armazenamento.
 */
internal class LocalVideoClipRecorder private constructor(
    private val resolver: ContentResolver,
    val uri: Uri,
    private val descriptor: ParcelFileDescriptor,
    private val muxer: MediaMuxer
) {
    private val lock = Any()
    private var videoTrack = -1
    private var started = false
    private var closed = false
    private var frames = 0

    companion object {
        fun create(context: Context): LocalVideoClipRecorder {
            require(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                "Gravação na galeria requer Android 10 ou superior"
            }
            val resolver = context.contentResolver
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, "OBS_RN_${timestamp}.mp4")
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/OBS Mobile RN")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Não foi possível criar o vídeo na galeria")
            var descriptor: ParcelFileDescriptor? = null
            try {
                descriptor = resolver.openFileDescriptor(uri, "w")
                    ?: error("Não foi possível abrir o vídeo")
                val muxer = MediaMuxer(
                    descriptor.fileDescriptor, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4
                )
                return LocalVideoClipRecorder(resolver, uri, descriptor, muxer)
            } catch (error: Throwable) {
                runCatching { descriptor?.close() }
                runCatching { resolver.delete(uri, null, null) }
                throw error
            }
        }
    }

    fun onVideoFormat(format: MediaFormat) {
        synchronized(lock) {
            if (closed || started) return
            videoTrack = muxer.addTrack(format)
            muxer.start()
            started = true
        }
    }

    fun onVideoSample(sample: ByteBuffer, info: MediaCodec.BufferInfo) {
        synchronized(lock) {
            if (closed || !started || info.size <= 0 ||
                info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0
            ) return
            val copied = MediaCodec.BufferInfo().apply {
                set(0, sample.remaining(), info.presentationTimeUs, info.flags)
            }
            muxer.writeSampleData(videoTrack, sample.slice(), copied)
            frames++
        }
    }

    /** Retorna quadros escritos; se a gravação falhar, remove o arquivo incompleto. */
    fun finish(): Int {
        synchronized(lock) {
            check(!closed) { "Gravação já finalizada" }
            closed = true
            var failure: Throwable? = null
            try {
                if (started) muxer.stop()
            } catch (error: Throwable) {
                failure = error
            } finally {
                runCatching { muxer.release() }
                runCatching { descriptor.close() }
            }
            if (failure != null || frames == 0) {
                runCatching { resolver.delete(uri, null, null) }
                if (failure != null) throw IllegalStateException("Falha ao salvar MP4", failure)
                error("Nenhum quadro foi gravado")
            }
            val values = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
            resolver.update(uri, values, null, null)
            return frames
        }
    }

    fun discard() {
        synchronized(lock) {
            if (closed) return
            closed = true
            if (started) runCatching { muxer.stop() }
            runCatching { muxer.release() }
            runCatching { descriptor.close() }
            runCatching { resolver.delete(uri, null, null) }
        }
    }
}
