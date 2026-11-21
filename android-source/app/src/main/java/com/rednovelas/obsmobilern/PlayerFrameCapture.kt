package com.rednovelas.obsmobilern

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.media3.ui.PlayerView

/**
 * Captura assíncrona de um quadro de vídeo exibido no SurfaceView do Media3.
 * API 26+. Executar chamadas na thread principal, com Handler dessa thread.
 *
 * A captura não inclui os TextViews sobrepostos: os gráficos são compostos
 * separadamente pelo VideoFrameCompositor antes de codificar.
 * Não captura áudio e não executa transmissão RTMP.
 */
internal object PlayerFrameCapture {
    fun request(
        playerView: PlayerView,
        width: Int,
        height: Int,
        handler: Handler,
        onResult: (Result<Bitmap>) -> Unit
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            onResult(Result.failure(UnsupportedOperationException("PixelCopy requer Android 8 ou superior")))
            return
        }
        if (width <= 0 || height <= 0) {
            onResult(Result.failure(IllegalArgumentException("Dimensões de captura inválidas")))
            return
        }
        val surfaceView = playerView.videoSurfaceView as? SurfaceView
        if (surfaceView == null || !surfaceView.holder.surface.isValid) {
            onResult(Result.failure(IllegalStateException("Prévia de vídeo ainda não disponível")))
            return
        }
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(surfaceView, bitmap, { status ->
                if (status == PixelCopy.SUCCESS) {
                    onResult(Result.success(bitmap))
                } else {
                    bitmap.recycle()
                    onResult(Result.failure(IllegalStateException("Falha PixelCopy: $status")))
                }
            }, handler)
        } catch (error: Exception) {
            bitmap.recycle()
            onResult(Result.failure(error))
        }
    }
}
