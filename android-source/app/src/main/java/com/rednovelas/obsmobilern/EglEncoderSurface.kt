package com.rednovelas.obsmobilern

import android.opengl.EGL14
import android.opengl.EGLExt
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLSurface
import android.view.Surface

/**
 * Superfície EGL para entregar quadros OpenGL ao MediaCodec H.264.
 * Criar, desenhar e fechar na MESMA thread. Não captura HLS por si só.
 */
internal class EglEncoderSurface(private val target: Surface) : AutoCloseable {
    private val display: EGLDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
    private var context: EGLContext = EGL14.EGL_NO_CONTEXT
    private var window: EGLSurface = EGL14.EGL_NO_SURFACE

    init {
        check(display != EGL14.EGL_NO_DISPLAY) { "EGL display indisponível" }
        try {
            val version = IntArray(2)
            check(EGL14.eglInitialize(display, version, 0, version, 1)) { "Falha ao iniciar EGL" }
            val attributes = intArrayOf(
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                0x3142, 1, // EGL_RECORDABLE_ANDROID
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, count, 0) && count[0] > 0) {
                "Configuração EGL incompatível com encoder"
            }
            context = EGL14.eglCreateContext(
                display, configs[0], EGL14.EGL_NO_CONTEXT,
                intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0
            )
            check(context != EGL14.EGL_NO_CONTEXT) { "Falha ao criar contexto EGL" }
            window = EGL14.eglCreateWindowSurface(
                display, configs[0], target, intArrayOf(EGL14.EGL_NONE), 0
            )
            check(window != EGL14.EGL_NO_SURFACE) { "Falha ao criar superfície EGL" }
        } catch (error: Throwable) {
            close()
            throw error
        }
    }

    fun makeCurrent() {
        check(EGL14.eglMakeCurrent(display, window, window, context)) {
            "Não foi possível ativar EGL"
        }
    }

    /** timestamp em nanossegundos de apresentação do quadro, não relógio de parede. */
    fun present(timestampNs: Long) {
        require(timestampNs >= 0)
        EGLExt.eglPresentationTimeANDROID(display, window, timestampNs)
        check(EGL14.eglSwapBuffers(display, window)) { "Falha ao entregar quadro ao codificador" }
    }

    override fun close() {
        if (display == EGL14.EGL_NO_DISPLAY) return
        EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
        if (window != EGL14.EGL_NO_SURFACE) {
            EGL14.eglDestroySurface(display, window)
            window = EGL14.EGL_NO_SURFACE
        }
        if (context != EGL14.EGL_NO_CONTEXT) {
            EGL14.eglDestroyContext(display, context)
            context = EGL14.EGL_NO_CONTEXT
        }
        EGL14.eglTerminate(display)
    }
}
