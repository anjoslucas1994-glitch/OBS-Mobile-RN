package com.rednovelas.obsmobilern

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView

class MainActivity : AppCompatActivity() {
    private var player: ExoPlayer? = null
    private val pcmTap = HlsAudioPcmTap()
    private var encoderDiagnostic: HlsVideoFramePipeline? = null
    private var clipPipeline: HlsVideoFramePipeline? = null
    private var clipRecorder: LocalVideoClipRecorder? = null
    private var rtmpVideoTestSession: HlsRtmpVideoSession? = null
    private var rtmpVideoTestTransport: RtmpTestTransport? = null
    private var rtmpVideoTestRunning = false
    private var rtmpClosing = false
    private var rtmpConnecting = false
    private var rtmpDiagnosticRunning = false
    private var rtmpDiagnosticGeneration = 0
    private var rtmpAttemptId = 0
    private lateinit var transmissionStateLabel: TextView
    private var rtmpLiveMode = false
    private var selectedScene = SceneMode.PROGRAMACAO
    private var logoLayout = LogoLayout()
    private var tickerSpeedPercent = 100
    private var tickerVisible = true
    private var logoVisible = true
    private var lowerThirdVisible = true
    private lateinit var lowerThirdVisibilityButton: Button
    private lateinit var logoVisibilityButton: Button
    private lateinit var tickerVisibilityButton: Button
    private enum class SceneMode { PROGRAMACAO, SOMENTE_LOGO, TELA_LIMPA }
    private lateinit var startLiveButton: Button
    private lateinit var stopLiveButton: Button
    private lateinit var muteRtmpButton: Button
    private lateinit var quickStartButton: Button
    private lateinit var quickStopButton: Button
    private lateinit var quickMuteButton: Button
    private lateinit var quickLogoButton: Button
    private lateinit var quickTickerButton: Button
    private lateinit var quickLowerThirdButton: Button
    private var rtmpAudioMuted = false
    private var rtmpAudioVolumePercent = 100
    private lateinit var broadcastMonitor: TextView
    private val broadcastHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var broadcastTick: Runnable? = null
    private var broadcastStartedAtMs = 0L
    private var lastTestVideoPackets = 0L
    private var lastTestAudioPackets = 0
    private var lastTestDurationSeconds = 0L
    private var lastTestEndReason = "Nenhum teste concluído"

    private fun restoreLastTestReport() {
        // Apenas métricas e motivos internos: não grava links ou credenciais.
        val prefs = getSharedPreferences("rn_local_test_report", MODE_PRIVATE)
        lastTestVideoPackets = prefs.getLong("video_packets", 0L)
        lastTestAudioPackets = prefs.getInt("audio_packets", 0)
        lastTestDurationSeconds = prefs.getLong("duration_seconds", 0L)
        lastTestEndReason = prefs.getString("end_reason", "Nenhum teste concluído")
            ?: "Nenhum teste concluído"
    }

    private fun rememberLastTest(session: HlsRtmpVideoSession?, reason: String) {
        if (session == null) return
        lastTestVideoPackets = session.videoPacketCount
        lastTestAudioPackets = session.audioPacketCount
        lastTestDurationSeconds = ((android.os.SystemClock.elapsedRealtime() -
            broadcastStartedAtMs) / 1000L).coerceAtLeast(0L)
        // Motivo interno pré-definido, sem URLs ou tokens.
        lastTestEndReason = reason
        getSharedPreferences("rn_local_test_report", MODE_PRIVATE).edit()
            .putLong("video_packets", lastTestVideoPackets)
            .putInt("audio_packets", lastTestAudioPackets)
            .putLong("duration_seconds", lastTestDurationSeconds)
            .putString("end_reason", lastTestEndReason)
            .apply()
    }
    private var hlsBufferingSinceMs = 0L
    private val previewRetryHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var previewGeneration = 0
    private var previewRetryCount = 0
    private fun keepScreenActive(active: Boolean) {
        if (active) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }
    private lateinit var videoPublishTestButton: Button
    private var encodedDiagnosticFrames = 0
    private lateinit var playerView: PlayerView
    private lateinit var sourceInput: EditText
    private lateinit var sourceBInput: EditText
    private lateinit var activeSourceLabel: TextView
    private var activeSource = "A"
    private var loadedSourceUrl = ""
    private var liveSourceSwitchPending = false
    private var sourceSwitchAudioFlushesAtStart = 0L
    private var sourceSwitchAudioBuffersAfterFlush = -1L
    private var sourceSwitchPcmReady = false
    private var sourceSwitchStartedAtMs = 0L
    private var sourceSwitchVideoReady = false
    private var sourceSwitchStage = "AGUARDANDO A NOVA FONTE"
    private var successfulSourceCuts = 0
    private fun showSourceStopped(message: String = "FORA DO AR — TESTE ZCAST ENCERRADO") {
        if (::quickSourceLabel.isInitialized) {
            quickSourceLabel.text = message
            quickSourceLabel.setTextColor(Color.DKGRAY)
        }
    }
    private var sourceSwitchVideoPacketsAtReady = 0L
    private var sourceSwitchAacPacketsAtReady = 0
    private var broadcastPcmSampleRate = 0
    private var broadcastPcmChannels = 0
    private val sourceSwitchTimeoutHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private lateinit var quickSourceLabel: TextView
    private lateinit var overlayText: EditText
    private lateinit var overlayLabel: TextView
    private lateinit var logoOverlay: ImageView
    private lateinit var tickerLabel: TextView
    private lateinit var status: TextView
    private lateinit var rtmpServerInput: EditText
    private lateinit var streamKeyInput: EditText
    private lateinit var bitrateSpinner: Spinner
    private var logoUri: Uri? = null
    private val graphicPrefs by lazy { getSharedPreferences("graphic_preview", MODE_PRIVATE) }

    private val red = Color.rgb(165, 22, 37)
    private val gold = Color.rgb(181, 138, 50)
    private val white = Color.WHITE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(127, 16, 27)
        window.navigationBarColor = Color.rgb(127, 16, 27)
        restoreLastTestReport()
        buildScreen()
    }

    private fun buildScreen() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(248, 246, 242))
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }

        val header = TextView(this).apply {
            text = "OBS MOBILE RN\nREDE NOVELAS"
            textSize = 23f
            setTextColor(red)
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(4), 0, dp(12))
        }
        root.addView(header)

        val previewFrame = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        playerView = PlayerView(this).apply {
            useController = true
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, dp(210)
            )
        }
        previewFrame.addView(playerView)
        overlayLabel = TextView(this).apply {
            text = ""
            textSize = 16f
            setTextColor(white)
            setBackgroundColor(0x99000000.toInt())
            setPadding(dp(8), dp(5), dp(8), dp(5))
            visibility = View.GONE
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        }
        previewFrame.addView(overlayLabel)

        logoOverlay = ImageView(this).apply {
            visibility = View.GONE
            adjustViewBounds = true
            scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(dp(92), dp(58), Gravity.TOP or Gravity.END).apply {
                setMargins(dp(8), dp(8), dp(8), dp(8))
            }
        }
        previewFrame.addView(logoOverlay)
        graphicPrefs.getString("logo_uri", null)?.let { saved ->
            runCatching {
                logoUri = Uri.parse(saved)
                logoOverlay.setImageURI(logoUri)
                if (logoOverlay.drawable != null) logoOverlay.visibility = View.VISIBLE
            }
        }

        tickerLabel = TextView(this).apply {
            text = ""
            textSize = 14f
            setTextColor(white)
            setBackgroundColor(0xCC8E1421.toInt())
            setSingleLine(true)
            ellipsize = android.text.TextUtils.TruncateAt.MARQUEE
            marqueeRepeatLimit = -1
            isSelected = true
            visibility = View.GONE
            setPadding(dp(8), dp(6), dp(8), dp(6))
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP
            )
        }
        previewFrame.addView(tickerLabel)
        root.addView(previewFrame)

        val compactTitle = TextView(this).apply {
            text = "MESA RÁPIDA — CONTROLES DA TRANSMISSÃO"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(gold)
            setPadding(0, dp(9), 0, dp(4))
        }
        root.addView(compactTitle)
        val quickScenesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        fun quickControl(label: String, row: LinearLayout): Button {
            val control = button(label)
            control.textSize = 11f
            control.layoutParams = LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
            ).apply { setMargins(dp(2), 0, dp(2), 0) }
            row.addView(control)
            return control
        }
        val quickSceneProgram = quickControl("CENA 1", quickScenesRow)
        val quickSceneLogo = quickControl("CENA 2", quickScenesRow)
        val quickSceneClean = quickControl("LIMPA", quickScenesRow)
        root.addView(quickScenesRow)
        val quickSourcesRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        val quickSourceA = quickControl("CORTE FONTE A", quickSourcesRow)
        val quickSourceB = quickControl("CORTE FONTE B", quickSourcesRow)
        quickSourceLabel = TextView(this).apply {
            text = "FONTE SELECIONADA: A"
            textSize = 12f
            setTextColor(red)
            setPadding(dp(4), dp(3), dp(4), dp(5))
        }
        root.addView(quickSourcesRow)
        root.addView(quickSourceLabel)
        quickSourceA.setOnClickListener { selectVideoSource("A") }
        quickSourceB.setOnClickListener { selectVideoSource("B") }
        val quickStreamRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        quickStartButton = quickControl("INICIAR", quickStreamRow)
        quickStopButton = quickControl("PARAR", quickStreamRow)
        quickMuteButton = quickControl("SOM ON/OFF", quickStreamRow)
        root.addView(quickStreamRow)
        val quickGraphicsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        quickLogoButton = quickControl("LOGO ON/OFF", quickGraphicsRow)
        quickTickerButton = quickControl("LETREIRO ON/OFF", quickGraphicsRow)
        quickLowerThirdButton = quickControl("TEXTO ON/OFF", quickGraphicsRow)
        root.addView(quickGraphicsRow)

        transmissionStateLabel = TextView(this).apply {
            text = "● FORA DO AR — CANAL ZCAST DE TESTE"
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.DKGRAY)
            setPadding(dp(8), dp(7), dp(8), dp(5))
        }
        root.addView(transmissionStateLabel)

        broadcastMonitor = TextView(this).apply {
            text = "FORA DO AR — ZCast de testes\nH.264: 0 quadros • AAC: 0 pacotes"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.DKGRAY)
            setBackgroundColor(0xFFEFECE7.toInt())
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        root.addView(broadcastMonitor)
        val checkSourceButton = button("VERIFICAR VÍDEO E ÁUDIO DA FONTE (SEM RTMP)")
        checkSourceButton.setOnClickListener {
            val pcm = pcmTap.snapshot()
            val videoReady = player?.playbackState == Player.STATE_READY &&
                player?.isPlaying == true
            val now = android.os.SystemClock.elapsedRealtime()
            val freshAudio = pcm.lastBufferAtElapsedMs > 0L &&
                now - pcm.lastBufferAtElapsedMs in 0L..3_000L
            val supportedAudio = pcm.encoding == android.media.AudioFormat.ENCODING_PCM_16BIT &&
                pcm.sampleRate in listOf(32000, 44100, 48000) &&
                pcm.channels in 1..2
            val videoText = if (videoReady) "REPRODUZINDO" else "NÃO CONFIRMADO"
            val audioText = if (freshAudio && supportedAudio) "PCM RECENTE E COMPATÍVEL"
                else "AUSENTE, ANTIGO OU INCOMPATÍVEL"
            status.text = "TESTE LOCAL (SEM RTMP): VÍDEO $videoText • ÁUDIO $audioText. " +
                if (videoReady && freshAudio && supportedAudio)
                    "Fonte aparentemente pronta para um teste ZCast; recepção ainda não verificada."
                else "Carregue a prévia M3U8 com som e tente novamente."
        }
        root.addView(checkSourceButton)
        val copyTestReportButton = button("COPIAR RELATÓRIO TÉCNICO (SEM CHAVES)")
        copyTestReportButton.setOnClickListener {
            val pcm = pcmTap.snapshot()
            val stream = rtmpVideoTestSession
            // Nunca incluir link M3U8, endereço RTMP completo ou chave/token.
            // Os contadores são locais e não comprovam recepção no ZCast.
            val report = buildString {
                appendLine("OBS MOBILE RN v0.57 — RELATÓRIO DE TESTE")
                appendLine("Saída: canal secundário ZCast (experimental)")
                appendLine("Estado RTMP: " + when {
                    rtmpClosing -> "ENCERRANDO"
                    rtmpConnecting -> "CONECTANDO"
                    rtmpVideoTestRunning -> "SESSÃO LOCAL ATIVA"
                    else -> "FORA DO AR"
                })
                appendLine("Fonte selecionada: $activeSource")
                appendLine("Corte A/B pendente: $liveSourceSwitchPending")
                appendLine("Etapa do corte: $sourceSwitchStage")
                appendLine("Cortes localmente confirmados: $successfulSourceCuts")
                appendLine("Estado da prévia: ${player?.playbackState ?: "não iniciada"}")
                appendLine("H.264 nesta sessão: ${stream?.videoPacketCount ?: 0L} quadros locais")
                appendLine("AAC nesta sessão: ${stream?.audioPacketCount ?: 0} pacotes locais")
                appendLine("Último teste — H.264: $lastTestVideoPackets quadros locais")
                appendLine("Último teste — AAC: $lastTestAudioPackets pacotes locais")
                appendLine("Último teste — duração aproximada: ${lastTestDurationSeconds}s")
                appendLine("Último teste — encerramento: $lastTestEndReason")
                appendLine("PCM: ${pcm.sampleRate} Hz, ${pcm.channels} canais, ${pcm.buffers} buffers")
                appendLine("Áudio RTMP silenciado: $rtmpAudioMuted")
                appendLine("Cena: ${selectedScene.name}")
                appendLine("Endereços e credenciais: omitidos")
            }
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Relatório OBS Mobile RN", report))
            status.text = "RELATÓRIO COPIADO: cole aqui no ChatGPT para analisar o teste. Nenhuma chave RTMP foi incluída."
        }
        root.addView(copyTestReportButton)

        status = TextView(this).apply {
            text = "PRÉVIA: aguardando endereço M3U8"
            textSize = 12f
            setTextColor(red)
            setPadding(0, dp(8), 0, dp(8))
        }
        root.addView(status)

        sourceInput = EditText(this).apply {
            hint = "Endereço de entrada M3U8"
            textSize = 13f
            setSingleLine(true)
            setText(graphicPrefs.getString("source_a", "https://stmv7.voxtvhd.com.br/lucas3274/lucas3274/playlist.m3u8"))
        }
        root.addView(sourceInput)

        val sourcesTitle = TextView(this).apply {
            text = "FONTES DE VÍDEO — MESA DE ENTRADAS"
            textSize = 14f
            setTextColor(gold)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(4))
        }
        root.addView(sourcesTitle)

        sourceBInput = EditText(this).apply {
            hint = "FONTE B — endereço M3U8 alternativo (opcional)"
            textSize = 13f
            setSingleLine(true)
            setText(graphicPrefs.getString("source_b", ""))
        }
        root.addView(sourceBInput)

        activeSourceLabel = TextView(this).apply {
            text = "FONTE ATIVA: A"
            textSize = 12f
            setTextColor(red)
        }
        root.addView(activeSourceLabel)

        val inputRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        fun addInputButton(text: String, selected: String) {
            val control = button(text)
            control.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            control.setOnClickListener { selectVideoSource(selected) }
            inputRow.addView(control)
        }
        addInputButton("FONTE A", "A")
        addInputButton("FONTE B", "B")
        root.addView(inputRow)

        val loadButton = button("CARREGAR PRÉVIA")
        loadButton.setOnClickListener { loadPreview() }
        root.addView(loadButton)
        val reconnectButton = button("RECONECTAR FONTE M3U8 (SOMENTE PRÉVIA)")
        reconnectButton.setOnClickListener {
            if (rtmpVideoTestRunning || rtmpClosing || rtmpConnecting) {
                status.text = "RECONECTAR: pare a transmissão ZCast antes de reconectar a fonte."
            } else {
                loadPreview()
            }
        }
        root.addView(reconnectButton)

        // Diagnóstico local: verifica a captura do quadro real sem transmitir.
        val captureTestButton = button("TESTAR CAPTURA DO VÍDEO")
        captureTestButton.setOnClickListener {
            status.text = "PRÉVIA: verificando captura de quadro..."
            if (player?.playbackState != Player.STATE_READY) {
                status.text = "PRÉVIA: aguarde o vídeo carregar antes do teste."
                return@setOnClickListener
            }
            PlayerFrameCapture.request(
                playerView,
                320,
                180,
                android.os.Handler(android.os.Looper.getMainLooper())
            ) { result ->
                result.onSuccess { bitmap ->
                    status.text = "PRÉVIA: captura de vídeo concluída (320×180)"
                    bitmap.recycle()
                }.onFailure { error ->
                    status.text = "PRÉVIA: captura indisponível — ${error.message}"
                }
            }
        }
        root.addView(captureTestButton)

        val encoderTestButton = button("TESTAR CODIFICAÇÃO H.264 (5 SEGUNDOS)")
        encoderTestButton.setOnClickListener {
            if (encoderDiagnostic != null) {
                status.text = "PRÉVIA: teste de codificação já está em andamento."
                return@setOnClickListener
            }
            if (player?.playbackState != Player.STATE_READY) {
                status.text = "PRÉVIA: carregue o M3U8 antes do teste H.264."
                return@setOnClickListener
            }
            encodedDiagnosticFrames = 0
            val diagnostic = HlsVideoFramePipeline(
                playerView = playerView,
                onError = { error ->
                    status.text = "TESTE H.264: ${error.message}"
                    // A pipeline já agenda o próprio fechamento após erro.
                    encoderDiagnostic = null
                },
                onVideoFormat = { },
                onVideoSample = { _, info ->
                    if (info.size > 0 &&
                        info.flags and android.media.MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0
                    ) {
                        runOnUiThread {
                            if (encoderDiagnostic != null) {
                                encodedDiagnosticFrames++
                            }
                        }
                    }
                }
            )
            encoderDiagnostic = diagnostic
            try {
                // O teste usa os textos já aplicados na prévia.
                val testLogo = logoOverlay.drawable?.let { drawable ->
                    val width = drawable.intrinsicWidth.coerceIn(1, 512)
                    val height = drawable.intrinsicHeight.coerceIn(1, 512)
                    val bitmap = android.graphics.Bitmap.createBitmap(
                        width, height, android.graphics.Bitmap.Config.ARGB_8888
                    )
                    val canvas = android.graphics.Canvas(bitmap)
                    drawable.setBounds(0, 0, width, height)
                    drawable.draw(canvas)
                    bitmap
                }
                diagnostic.updateGraphics(
                    testLogo,
                    overlayLabel.text.toString(),
                    tickerLabel.text.toString()
                )
                diagnostic.start()
                status.text = "TESTE H.264: codificando localmente, sem RTMP..."
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    if (encoderDiagnostic === diagnostic) {
                        diagnostic.close()
                        encoderDiagnostic = null
                        status.text = "TESTE H.264: ${encodedDiagnosticFrames} quadros codificados em 5 segundos. Nenhuma transmissão enviada."
                    }
                }, 5000L)
            } catch (error: Exception) {
                diagnostic.close()
                encoderDiagnostic = null
                status.text = "TESTE H.264: ${error.message}"
            }
        }
        root.addView(encoderTestButton)

        val aacTestButton = button("TESTAR CODIFICAÇÃO AAC (ÁUDIO SINTÉTICO)")
        aacTestButton.setOnClickListener {
            aacTestButton.isEnabled = false
            status.text = "TESTE AAC: codificando sinal de teste local, sem RTMP..."
            Thread {
                val result = runCatching { AacEncoderSelfTest.run() }
                runOnUiThread {
                    aacTestButton.isEnabled = true
                    status.text = result.fold(
                        onSuccess = { "TESTE AAC: ${it.packets} quadros, ${it.totalBytes} bytes codificados. Sem transmissão." },
                        onFailure = { "TESTE AAC FALHOU: ${it.message}" }
                    )
                }
            }.start()
        }
        root.addView(aacTestButton)

        val hlsPcmButton = button("TESTAR ÁUDIO REAL DO M3U8 (5 SEGUNDOS)")
        hlsPcmButton.setOnClickListener {
            if (player?.playbackState != Player.STATE_READY) {
                status.text = "ÁUDIO HLS: carregue a prévia antes do teste."
            } else {
                pcmTap.resetCounters()
                hlsPcmButton.isEnabled = false
                status.text = "ÁUDIO HLS: verificando PCM da reprodução..."
                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                    hlsPcmButton.isEnabled = true
                    val result = pcmTap.snapshot()
                    status.text = if (result.bytes > 0L) {
                        "ÁUDIO HLS: ${result.buffers} buffers PCM, ${result.bytes} bytes, " +
                        "${result.sampleRate} Hz, ${result.channels} canais. Sem RTMP."
                    } else {
                        "ÁUDIO HLS: nenhum PCM recebido. Verifique se o canal tem som e " +
                        "se a reprodução está ativa."
                    }
                }, 5000L)
            }
        }
        root.addView(hlsPcmButton)

        val clipTestButton = button("GRAVAR MP4 COM LOGO E TEXTO (5S, SEM ÁUDIO)")
        clipTestButton.setOnClickListener { recordGraphicTest() }
        root.addView(clipTestButton)

        val section = TextView(this).apply {
            text = "ELEMENTOS GRÁFICOS"
            setTextColor(gold)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        }
        root.addView(section)

        overlayText = EditText(this).apply {
            hint = "Texto ou letreiro para aparecer na tela"
            textSize = 14f
            setText(graphicPrefs.getString("lower_third", ""))
        }
        root.addView(overlayText)

        val overlayButton = button("APLICAR TEXTO NA PRÉVIA")
        overlayButton.setOnClickListener {
            val value = overlayText.text.toString().trim()
            overlayLabel.text = value
            overlayLabel.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
            graphicPrefs.edit().putString("lower_third", value).apply()
            applySelectedSceneToPreview()
            refreshActiveBroadcastGraphics()
        }
        root.addView(overlayButton)

        lowerThirdVisible = graphicPrefs.getBoolean("lower_third_visible", true)
        lowerThirdVisibilityButton = button(if (lowerThirdVisible)
            "OCULTAR TEXTO INFERIOR"
        else "MOSTRAR TEXTO INFERIOR")
        lowerThirdVisibilityButton.setOnClickListener {
            lowerThirdVisible = !lowerThirdVisible
            graphicPrefs.edit().putBoolean("lower_third_visible", lowerThirdVisible).apply()
            lowerThirdVisibilityButton.text = if (lowerThirdVisible)
                "OCULTAR TEXTO INFERIOR" else "MOSTRAR TEXTO INFERIOR"
            applySelectedSceneToPreview()
            refreshActiveBroadcastGraphics()
            status.text = if (lowerThirdVisible)
                "TEXTO INFERIOR: visível na cena de programação."
            else "TEXTO INFERIOR: oculto, mantendo a mensagem salva."
        }
        root.addView(lowerThirdVisibilityButton)
        quickLowerThirdButton.setOnClickListener { lowerThirdVisibilityButton.performClick() }

        val tickerInput = EditText(this).apply {
            hint = "Texto rolante (ex.: Acompanhe a programação da Rede Novelas)"
            textSize = 14f
            setText(graphicPrefs.getString("ticker", ""))
        }
        root.addView(tickerInput)

        val tickerButton = button("APLICAR TEXTO ROLANTE")
        tickerButton.setOnClickListener {
            val value = tickerInput.text.toString().trim()
            tickerLabel.text = value
            tickerLabel.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
            tickerLabel.isSelected = true
            graphicPrefs.edit().putString("ticker", value).apply()
            applySelectedSceneToPreview()
            refreshActiveBroadcastGraphics()
        }
        root.addView(tickerButton)

        tickerVisible = graphicPrefs.getBoolean("ticker_visible", true)
        tickerVisibilityButton = button(if (tickerVisible)
            "OCULTAR LETREIRO DA TRANSMISSÃO"
        else "MOSTRAR LETREIRO NA TRANSMISSÃO")
        tickerVisibilityButton.setOnClickListener {
            tickerVisible = !tickerVisible
            graphicPrefs.edit().putBoolean("ticker_visible", tickerVisible).apply()
            tickerVisibilityButton.text = if (tickerVisible)
                "OCULTAR LETREIRO DA TRANSMISSÃO"
            else "MOSTRAR LETREIRO NA TRANSMISSÃO"
            applySelectedSceneToPreview()
            refreshActiveBroadcastGraphics()
            status.text = if (tickerVisible) "LETREIRO: ligado." else
                "LETREIRO: oculto, com o texto salvo para reativar."
        }
        root.addView(tickerVisibilityButton)
        quickTickerButton.setOnClickListener { tickerVisibilityButton.performClick() }

        tickerSpeedPercent = graphicPrefs.getInt("ticker_speed", 100).coerceIn(25, 250)
        val tickerSpeedLabel = TextView(this).apply {
            text = "VELOCIDADE DO LETREIRO: ${tickerSpeedPercent}%"
            textSize = 12f
            setTextColor(red)
            setPadding(0, dp(6), 0, dp(2))
        }
        root.addView(tickerSpeedLabel)
        val tickerSpeedSlider = SeekBar(this).apply {
            max = 225
            progress = tickerSpeedPercent - 25
        }
        tickerSpeedSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tickerSpeedPercent = progress + 25
                if (fromUser) graphicPrefs.edit().putInt("ticker_speed", tickerSpeedPercent).apply()
                tickerSpeedLabel.text = "VELOCIDADE DO LETREIRO: ${tickerSpeedPercent}%"
                refreshActiveBroadcastGraphics()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(tickerSpeedSlider)

        overlayLabel.text = overlayText.text.toString()
        overlayLabel.visibility = if (overlayLabel.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        tickerLabel.text = tickerInput.text.toString()
        tickerLabel.visibility = if (tickerLabel.text.isNullOrEmpty()) View.GONE else View.VISIBLE

        val logoButton = button("ESCOLHER LOGOTIPO")
        logoButton.setOnClickListener {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "image/*"
            }
            startActivityForResult(intent, 41)
        }
        root.addView(logoButton)

        logoVisible = graphicPrefs.getBoolean("logo_visible", true)
        logoVisibilityButton = button(if (logoVisible)
            "OCULTAR LOGO DA TRANSMISSÃO"
        else "MOSTRAR LOGO NA TRANSMISSÃO")
        logoVisibilityButton.setOnClickListener {
            logoVisible = !logoVisible
            graphicPrefs.edit().putBoolean("logo_visible", logoVisible).apply()
            logoVisibilityButton.text = if (logoVisible)
                "OCULTAR LOGO DA TRANSMISSÃO"
            else "MOSTRAR LOGO NA TRANSMISSÃO"
            applySelectedSceneToPreview()
            refreshActiveBroadcastGraphics()
            status.text = if (logoVisible) "LOGO: visível nas cenas com marca." else
                "LOGO: oculta; imagem preservada para reativar."
        }
        root.addView(logoVisibilityButton)
        quickLogoButton.setOnClickListener { logoVisibilityButton.performClick() }

        val logoControlsTitle = TextView(this).apply {
            text = "POSIÇÃO E TAMANHO DA LOGO — SAÍDA RTMP"
            textSize = 13f
            setTextColor(gold)
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(4))
        }
        root.addView(logoControlsTitle)

        val corners = listOf(
            "SUPERIOR DIREITO", "SUPERIOR ESQUERDO",
            "INFERIOR DIREITO", "INFERIOR ESQUERDO"
        )
        val cornerValues = listOf(
            LogoLayout.Corner.TOP_RIGHT, LogoLayout.Corner.TOP_LEFT,
            LogoLayout.Corner.BOTTOM_RIGHT, LogoLayout.Corner.BOTTOM_LEFT
        )
        val logoPositionSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item, corners)
        }
        logoPositionSpinner.onItemSelectedListener = object :
            android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?,
                view: View?, position: Int, id: Long) {
                logoLayout = logoLayout.copy(corner = cornerValues[position])
                applySelectedSceneToPreview()
                refreshActiveBroadcastGraphics()
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }
        root.addView(logoPositionSpinner)

        val logoSizeLabel = TextView(this).apply {
            text = "TAMANHO DA LOGO: 25%"
            setTextColor(red)
            textSize = 12f
        }
        root.addView(logoSizeLabel)
        val logoSizeControl = SeekBar(this).apply {
            max = 27
            progress = 17
        }
        logoSizeControl.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                logoLayout = logoLayout.copy(widthPercent = progress + 8)
                logoSizeLabel.text = "TAMANHO DA LOGO: ${logoLayout.widthPercent}%"
                applySelectedSceneToPreview()
                refreshActiveBroadcastGraphics()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(logoSizeControl)

        val scenesTitle = TextView(this).apply {
            text = "MESA DE CENAS — TOQUE PARA CORTAR"
            setTextColor(gold)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        }
        root.addView(scenesTitle)
        val currentSceneLabel = TextView(this).apply {
            text = "CENA NO AR: PROGRAMAÇÃO COMPLETA"
            textSize = 12f
            setTextColor(red)
            setPadding(0, dp(4), 0, dp(8))
        }
        root.addView(currentSceneLabel)
        fun sceneButton(title: String, mode: SceneMode, displayName: String): Button {
            val control = button(title)
            control.setOnClickListener {
                // A troca modifica a composição do próximo quadro enviado por RTMP.
                // Não muda a origem M3U8 e não reinicia a transmissão.
                selectedScene = mode
                applySelectedSceneToPreview()
                refreshActiveBroadcastGraphics()
                currentSceneLabel.text = "CENA NO AR: $displayName"
                status.text = if (rtmpVideoTestRunning) {
                    "CORTE DE CENA: $displayName aplicado ao canal de testes ZCast."
                } else {
                    "CORTE DE CENA: $displayName selecionado na prévia. Sem transmissão."
                }
            }
            root.addView(control)
            return control
        }
        val sceneProgramButton = sceneButton("CENA 1 — PROGRAMAÇÃO COMPLETA", SceneMode.PROGRAMACAO, "PROGRAMAÇÃO COMPLETA")
        val sceneLogoButton = sceneButton("CENA 2 — SOMENTE LOGO", SceneMode.SOMENTE_LOGO, "SOMENTE LOGO")
        val sceneCleanButton = sceneButton("CENA 3 — TELA LIMPA", SceneMode.TELA_LIMPA, "TELA LIMPA")
        quickSceneProgram.setOnClickListener { sceneProgramButton.performClick() }
        quickSceneLogo.setOnClickListener { sceneLogoButton.performClick() }
        quickSceneClean.setOnClickListener { sceneCleanButton.performClick() }
        applySelectedSceneToPreview()

        val outputTitle = TextView(this).apply {
            text = "CONFIGURAÇÃO DE SAÍDA RTMP"
            setTextColor(gold)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        }
        root.addView(outputTitle)

        rtmpServerInput = EditText(this).apply {
            hint = "Servidor RTMP (ex.: rtmp://servidor:1935/aplicativo)"
            textSize = 13f
            setSingleLine(true)
            setText("rtmp://srv2.zcast.com.br:1935/redenovelas")
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_URI
        }
        root.addView(rtmpServerInput)

        streamKeyInput = EditText(this).apply {
            hint = "Chave/token de transmissão (não compartilhe)"
            textSize = 13f
            setSingleLine(true)
            inputType = android.text.InputType.TYPE_CLASS_TEXT or
                android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        root.addView(streamKeyInput)

        val bitrateLabel = TextView(this).apply {
            text = "Perfil RTMP de referência (vídeo + áudio; ZCast em teste)"
            setTextColor(Color.DKGRAY)
            textSize = 12f
            setPadding(0, dp(6), 0, dp(2))
        }
        root.addView(bitrateLabel)

        bitrateSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("1200 Kbps", "1500 Kbps", "1900 Kbps (ZCast)", "2500 Kbps", "3500 Kbps", "7000 Kbps")
            )
            setSelection(2)
        }
        root.addView(bitrateSpinner)

        val outputButton = button("VALIDAR CONFIGURAÇÃO RTMP")
        outputButton.setOnClickListener { validateRtmpSettings() }
        root.addView(outputButton)

        val connectTestButton = button("TESTAR CONEXÃO RTMP (SEM TRANSMITIR)")
        connectTestButton.setOnClickListener {
            testRtmpHandshake(connectTestButton)
        }
        root.addView(connectTestButton)

        val publishTestButton = button("TESTAR AUTORIZAÇÃO RTMP NO ZCAST (SEM VÍDEO)")
        publishTestButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Teste de publicação no segundo canal")
                .setMessage(
                    "Este teste envia a chave ao servidor RTMP ZCast e solicita publicação por poucos segundos, " +
                    "mas NÃO envia vídeo ou áudio. Pode ativar temporariamente a sessão do canal de testes. " +
                    "A conexão RTMP comum não é criptografada. Confirme que este é o seu SEGUNDO canal."
                )
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("TESTAR") { _, _ -> testRtmpPublish(publishTestButton) }
                .show()
        }
        root.addView(publishTestButton)

        videoPublishTestButton = button("TESTAR VÍDEO + ÁUDIO RTMP NO ZCAST (5S)")
        videoPublishTestButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Enviar vídeo para o canal de testes?")
                .setMessage(
                    "O aplicativo tentará enviar 5 segundos de vídeo com logo, letreiro e áudio real do M3U8 ao SEGUNDO canal ZCast. " +
                    "Esta operação usa sua chave RTMP e pode ativar temporariamente o canal de teste. " +
                    "Não utiliza o RTMP da Rede Novelas principal."
                )
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("ENVIAR TESTE") { _, _ ->
                    testRtmpVideoFiveSeconds(videoPublishTestButton)
                }
                .show()
        }
        root.addView(videoPublishTestButton)

        val liveTitle = TextView(this).apply {
            text = "CONTROLE DE TRANSMISSÃO"
            setTextColor(gold)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(12), 0, dp(4))
        }
        root.addView(liveTitle)

        startLiveButton = button("INICIAR TRANSMISSÃO (ZCAST TESTE)")
        root.addView(startLiveButton)

        stopLiveButton = button("PARAR TRANSMISSÃO").apply {
            isEnabled = false
            alpha = 0.5f
        }
        startLiveButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Iniciar transmissão contínua de teste?")
                .setMessage("O aplicativo transmitirá vídeo com logo e textos e áudio AAC para o SEGUNDO canal ZCast enquanto permanecer aberto em primeiro plano. A Vox principal NÃO será alterada. Toque PARAR para encerrar. RTMP não é criptografado.")
                .setNegativeButton("CANCELAR", null)
                .setPositiveButton("INICIAR") { _, _ -> startRtmpVideo(startLiveButton, true) }
                .show()
        }
        stopLiveButton.setOnClickListener {
            if (rtmpVideoTestRunning) {
                rtmpLiveMode = false
                stopRtmpVideoTest(startLiveButton, "TRANSMISSÃO DE TESTE ENCERRADA.")
            }
        }
        root.addView(stopLiveButton)
        quickStartButton.setOnClickListener { startLiveButton.performClick() }
        quickStopButton.setOnClickListener { stopLiveButton.performClick() }
        quickStartButton.isEnabled = true
        quickStopButton.isEnabled = false
        quickStopButton.alpha = 0.5f

        val mixerTitle = TextView(this).apply {
            text = "MIXER DE ÁUDIO — SAÍDA RTMP"
            setTextColor(gold)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, dp(10), 0, dp(4))
        }
        root.addView(mixerTitle)
        muteRtmpButton = button("SILENCIAR ÁUDIO DO CANAL ZCAST")
        muteRtmpButton.setOnClickListener {
            rtmpAudioMuted = !rtmpAudioMuted
            rtmpVideoTestSession?.setAudioMuted(rtmpAudioMuted)
            muteRtmpButton.text = if (rtmpAudioMuted)
                "REATIVAR ÁUDIO DO CANAL ZCAST"
            else "SILENCIAR ÁUDIO DO CANAL ZCAST"
            status.text = if (rtmpAudioMuted)
                "MIXER: áudio RTMP silenciado; a prévia do celular continua com som."
            else "MIXER: áudio RTMP reativado."
        }
        root.addView(muteRtmpButton)
        quickMuteButton.setOnClickListener { muteRtmpButton.performClick() }

        val volumeLabel = TextView(this).apply {
            text = "VOLUME DO CANAL ZCAST: 100%"
            textSize = 12f
            setTextColor(red)
            setPadding(0, dp(8), 0, dp(3))
        }
        root.addView(volumeLabel)
        val volumeSlider = SeekBar(this).apply {
            max = 100
            progress = rtmpAudioVolumePercent
        }
        volumeSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                rtmpAudioVolumePercent = progress.coerceIn(0, 100)
                volumeLabel.text = "VOLUME DO CANAL ZCAST: ${rtmpAudioVolumePercent}%"
                rtmpVideoTestSession?.setAudioVolumePercent(rtmpAudioVolumePercent)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        root.addView(volumeSlider)

        val liveInfo = TextView(this).apply {
            text = "TRANSMISSÃO CONTÍNUA EXPERIMENTAL: a tela fica ligada enquanto envia ao segundo canal ZCast, com o app em primeiro plano. Parar ou sair encerra o envio. A Vox principal não é alterada."
            textSize = 12f
            setTextColor(Color.rgb(90, 60, 20))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        root.addView(liveInfo)

        val warning = TextView(this).apply {
            text = "IMPORTANTE: testes de 5 segundos e transmissão contínua só estão liberados no segundo canal ZCast. " +
                "A sincronização de áudio/vídeo e estabilidade prolongada ainda precisam ser validadas. " +
                "Não use a mesma entrada M3U8 e a mesma saída do canal sem confirmar com a Vox: isso pode criar um loop ou interromper a transmissão."
            textSize = 11f
            setTextColor(Color.rgb(110, 55, 20))
            setPadding(dp(9), dp(9), dp(9), dp(9))
            setBackgroundColor(0xFFFFF0D8.toInt())
        }
        root.addView(warning)

        val footer = TextView(this).apply {
            text = "VERSÃO 0.59 • VERIFICAÇÃO ANTES DE INICIAR RTMP • REDE NOVELAS"
            textSize = 10f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(footer)

        listOf(sourceInput, sourceBInput, overlayText, tickerInput, rtmpServerInput, streamKeyInput).forEach { field ->
            field.setTextColor(Color.rgb(35, 35, 35))
            field.setHintTextColor(Color.rgb(105, 105, 105))
        }
        val scroll = ScrollView(this).apply { addView(root) }
        setContentView(scroll)
    }

    /**
     * Grava um teste local de cinco segundos com os grafismos embutidos.
     * Não acessa a saída RTMP. Não contém áudio HLS.
     */
    private fun recordGraphicTest() {
        if (clipPipeline != null || encoderDiagnostic != null) {
            status.text = "Aguarde o teste atual terminar."
            return
        }
        if (android.os.Build.VERSION.SDK_INT < 29) {
            status.text = "Salvar MP4 na galeria requer Android 10 ou superior."
            return
        }
        if (player?.playbackState != Player.STATE_READY) {
            status.text = "Carregue e aguarde a prévia M3U8 antes de gravar."
            return
        }

        var recording: LocalVideoClipRecorder? = null
        try {
            recording = LocalVideoClipRecorder.create(this)
            val activeRecorder = recording
            val diagnostic = HlsVideoFramePipeline(
                playerView = playerView,
                onError = { error ->
                    if (clipRecorder === activeRecorder) {
                        clipPipeline = null
                        clipRecorder = null
                        activeRecorder.discard()
                        status.text = "GRAVAÇÃO MP4: ${error.message}"
                    }
                },
                onVideoFormat = activeRecorder::onVideoFormat,
                onVideoSample = activeRecorder::onVideoSample
            )
            val logoBitmap = logoOverlay.drawable?.let { drawable ->
                val w = drawable.intrinsicWidth.coerceIn(1, 512)
                val h = drawable.intrinsicHeight.coerceIn(1, 512)
                android.graphics.Bitmap.createBitmap(
                    w, h, android.graphics.Bitmap.Config.ARGB_8888
                ).also { bitmap ->
                    val canvas = android.graphics.Canvas(bitmap)
                    drawable.setBounds(0, 0, w, h)
                    drawable.draw(canvas)
                }
            }
            diagnostic.updateGraphics(
                logoBitmap,
                overlayLabel.text.toString(),
                tickerLabel.text.toString()
            )
            clipRecorder = activeRecorder
            clipPipeline = diagnostic
            diagnostic.start()
            status.text = "GRAVAÇÃO MP4: compondo logo e textos, sem áudio e sem RTMP..."
            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                if (clipPipeline === diagnostic && clipRecorder === activeRecorder) {
                    status.text = "GRAVAÇÃO MP4: finalizando arquivo..."
                    diagnostic.stopWhenClosed {
                        if (clipRecorder !== activeRecorder) return@stopWhenClosed
                        clipPipeline = null
                        clipRecorder = null
                        try {
                            val count = activeRecorder.finish()
                        status.text = "MP4 SALVO: $count quadros com gráficos. Abra o vídeo na galeria."
                        AlertDialog.Builder(this)
                            .setTitle("Vídeo de teste salvo")
                            .setMessage("Gravado na galeria em Filmes / OBS Mobile RN. O vídeo tem logo/textos, mas NÃO tem áudio e NÃO foi transmitido.")
                            .setNegativeButton("OK", null)
                            .setPositiveButton("ABRIR VÍDEO") { _, _ ->
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(activeRecorder.uri, "video/mp4")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                runCatching { startActivity(intent) }.onFailure {
                                    status.text = "Vídeo salvo. Abra pelo aplicativo Galeria ou Arquivos."
                                }
                            }
                            .show()
                        } catch (error: Exception) {
                            activeRecorder.discard()
                            status.text = "GRAVAÇÃO MP4: ${error.message}"
                        }
                    }
                }
            }, 5000L)
        } catch (error: Exception) {
            recording?.discard()
            clipRecorder = null
            clipPipeline = null
            status.text = "GRAVAÇÃO MP4: ${error.message}"
        }
    }

    /**
     * Testa somente TCP/TLS + handshake RTMP. Não faz connect AMF0,
     * createStream, publish nem envia chave, áudio ou vídeo.
     * Somente destinos separados do canal HLS atual são aceitos.
     */
    private fun testRtmpHandshake(button: Button) {
        if (rtmpVideoTestRunning || rtmpConnecting || rtmpClosing || rtmpDiagnosticRunning) {
            status.text = "DIAGNÓSTICO: finalize primeiro a transmissão ou o teste RTMP anterior."
            return
        }
        val server = rtmpServerInput.text.toString().trim()
        // O diagnóstico não precisa da chave real. Não a lê nem armazena.
        val placeholder = "diagnostico-sem-publicacao"
        val source = sourceInput.text.toString().trim()
        when (val safety = StreamDestinationGuard.inspect(source, server, placeholder)) {
            is StreamDestinationGuard.Result.Blocked -> {
                AlertDialog.Builder(this)
                    .setTitle("Teste RTMP bloqueado")
                    .setMessage(safety.reason + "\n\nA transmissão atual não foi alterada.")
                    .setPositiveButton("OK", null).show()
                return
            }
            StreamDestinationGuard.Result.AllowedForTesting -> Unit
        }
        // Nenhuma credencial será transmitida neste diagnóstico.
        val destination = try {
            RtmpDestination.parse(server, placeholder)
        } catch (error: IllegalArgumentException) {
            status.text = "RTMP: ${error.message}"
            return
        }
        rtmpDiagnosticRunning = true
        val diagnosticGeneration = ++rtmpDiagnosticGeneration
        button.isEnabled = false
        status.text = "RTMP: testando conexão, sem publicar..."
        Thread {
            val result = runCatching {
                RtmpTestTransport.connect(destination, timeoutMs = 5000).use { }
                true
            }
            runOnUiThread {
                if (diagnosticGeneration != rtmpDiagnosticGeneration || isFinishing || isDestroyed) return@runOnUiThread
                rtmpDiagnosticRunning = false
                button.isEnabled = true
                status.text = result.fold(
                    onSuccess = { "RTMP: conexão e handshake concluídos. Nenhum vídeo, áudio ou chave enviados." },
                    onFailure = { "RTMP: falha na conexão — ${it.message ?: "sem detalhes"}" }
                )
            }
        }.start()
    }

    /**
     * Testa autorização de publicação somente no servidor ZCast secundário.
     * Fecha o socket ao final. Não envia pacotes de áudio ou vídeo.
     */
    private fun testRtmpPublish(button: Button) {
        if (rtmpVideoTestRunning || rtmpConnecting || rtmpClosing || rtmpDiagnosticRunning) {
            status.text = "PUBLICAÇÃO: finalize primeiro a transmissão ou outro teste RTMP."
            return
        }
        val server = rtmpServerInput.text.toString().trim()
        val source = sourceInput.text.toString().trim()
        val key = streamKeyInput.text.toString().trim()
        val allowedServer = "rtmp://srv2.zcast.com.br:1935/redenovelas"
        if (!server.equals(allowedServer, ignoreCase = true)) {
            status.text = "PUBLICAÇÃO: somente o segundo canal ZCast é permitido no teste."
            return
        }
        when (val guard = StreamDestinationGuard.inspect(source, server, key)) {
            is StreamDestinationGuard.Result.Blocked -> {
                status.text = "PUBLICAÇÃO BLOQUEADA: ${guard.reason}"
                return
            }
            StreamDestinationGuard.Result.AllowedForTesting -> Unit
        }
        val destination = try {
            RtmpDestination.parse(server, key)
        } catch (error: IllegalArgumentException) {
            status.text = "PUBLICAÇÃO: ${error.message}"
            return
        }
        rtmpDiagnosticRunning = true
        val diagnosticGeneration = ++rtmpDiagnosticGeneration
        button.isEnabled = false
        status.text = "PUBLICAÇÃO: verificando autorização no ZCast (sem áudio/vídeo)..."
        Thread {
            val result = runCatching {
                RtmpTestTransport.connect(destination, timeoutMs = 5000).use { transport ->
                    transport.requestPublishForTest(destination)
                }
            }
            runOnUiThread {
                if (diagnosticGeneration != rtmpDiagnosticGeneration || isFinishing || isDestroyed) return@runOnUiThread
                rtmpDiagnosticRunning = false
                button.isEnabled = true
                status.text = result.fold(
                    onSuccess = { accepted ->
                        if (accepted) "PUBLICAÇÃO RTMP AUTORIZADA. Socket fechado; nenhum vídeo ou áudio enviado."
                        else "PUBLICAÇÃO RTMP RECUSADA pelo servidor de teste."
                    },
                    onFailure = { error ->
                        // Nunca exibir detalhes que possam conter a chave digitada.
                        "PUBLICAÇÃO RTMP: falha no protocolo ou resposta. Nenhuma mídia enviada."
                    }
                )
            }
        }.start()
    }

    /**
     * Diagnóstico de envio RTMP por 5 segundos SOMENTE no segundo canal ZCast.
     * Publica quadros H.264 compostos com logo/textos. Ainda não envia áudio.
     */
    /**
     * Converte a imagem efetivamente carregada no ImageView em um bitmap
     * independente; impede o teste RTMP de avançar com logo vazia.
     */
    private fun requireLogoBitmap(): android.graphics.Bitmap {
        val drawable = logoOverlay.drawable
            ?: error("Escolha uma logo antes de enviar vídeo ao ZCast")
        val originalBounds = android.graphics.Rect(drawable.bounds)
        val originalAlpha = drawable.alpha
        val width = drawable.intrinsicWidth.takeIf { it > 0 }
            ?: logoOverlay.drawable?.bounds?.width()?.takeIf { it > 0 }
            ?: 256
        val height = drawable.intrinsicHeight.takeIf { it > 0 }
            ?: logoOverlay.drawable?.bounds?.height()?.takeIf { it > 0 }
            ?: 128
        val ratio = minOf(1.0f, 512f / width, 512f / height)
        val outWidth = (width * ratio).toInt().coerceAtLeast(1)
        val outHeight = (height * ratio).toInt().coerceAtLeast(1)
        val bitmap = android.graphics.Bitmap.createBitmap(
            outWidth, outHeight, android.graphics.Bitmap.Config.ARGB_8888
        )
        try {
            drawable.alpha = 255
            drawable.setBounds(0, 0, outWidth, outHeight)
            drawable.draw(android.graphics.Canvas(bitmap))
        } finally {
            drawable.alpha = originalAlpha
            drawable.bounds = originalBounds
        }
        val pixels = IntArray(outWidth * outHeight)
        bitmap.getPixels(pixels, 0, outWidth, 0, 0, outWidth, outHeight)
        val visible = pixels.any { android.graphics.Color.alpha(it) > 30 }
        if (!visible) {
            bitmap.recycle()
            error("A logo selecionada está transparente ou vazia")
        }
        return bitmap
    }

    private fun testRtmpVideoFiveSeconds(button: Button) = startRtmpVideo(button, false)

    private fun applySelectedSceneToPreview() {
        if (!::logoOverlay.isInitialized || !::overlayLabel.isInitialized ||
            !::tickerLabel.isInitialized) return
        val gravity = when (logoLayout.corner) {
            LogoLayout.Corner.TOP_LEFT -> Gravity.TOP or Gravity.START
            LogoLayout.Corner.TOP_RIGHT -> Gravity.TOP or Gravity.END
            LogoLayout.Corner.BOTTOM_LEFT -> Gravity.BOTTOM or Gravity.START
            LogoLayout.Corner.BOTTOM_RIGHT -> Gravity.BOTTOM or Gravity.END
        }
        logoOverlay.layoutParams = FrameLayout.LayoutParams(
            dp(logoLayout.widthPercent * 4),
            dp(logoLayout.widthPercent * 3),
            gravity
        ).apply { setMargins(dp(8), dp(8), dp(8), dp(8)) }
        logoOverlay.visibility = if (logoVisible && selectedScene != SceneMode.TELA_LIMPA &&
            logoOverlay.drawable != null) View.VISIBLE else View.GONE
        overlayLabel.visibility = if (lowerThirdVisible && selectedScene == SceneMode.PROGRAMACAO &&
            overlayLabel.text.isNotBlank()) View.VISIBLE else View.GONE
        tickerLabel.visibility = if (tickerVisible && selectedScene == SceneMode.PROGRAMACAO &&
            tickerLabel.text.isNotBlank()) View.VISIBLE else View.GONE
    }

    private fun currentSceneLogo(): android.graphics.Bitmap? =
        if (!logoVisible || selectedScene == SceneMode.TELA_LIMPA) null else requireLogoBitmap()

    private fun currentSceneLowerThird(): String =
        if (lowerThirdVisible && selectedScene == SceneMode.PROGRAMACAO) overlayLabel.text.toString() else ""

    private fun currentSceneTicker(): String =
        if (tickerVisible && selectedScene == SceneMode.PROGRAMACAO) tickerLabel.text.toString() else ""

    private fun refreshActiveBroadcastGraphics() {
        val session = rtmpVideoTestSession ?: return
        if (!rtmpVideoTestRunning) return
        runCatching {
            session.updateGraphics(
                currentSceneLogo(),
                currentSceneLowerThird(),
                currentSceneTicker(),
                logoLayout,
                tickerSpeedPercent
            )
        }.onFailure {
            status.text = "GRÁFICOS: imagem não atualizada; confira a logo selecionada."
        }
    }

    private fun setTransmissionState(message: String, color: Int) {
        if (::transmissionStateLabel.isInitialized) {
            transmissionStateLabel.text = message
            transmissionStateLabel.setTextColor(color)
        }
    }

    private fun setRtmpControls(running: Boolean, live: Boolean) {
        startLiveButton.isEnabled = !running && !rtmpClosing
        startLiveButton.alpha = if (running || rtmpClosing) 0.5f else 1f
        stopLiveButton.isEnabled = running && (live || rtmpConnecting) && !rtmpClosing
        stopLiveButton.alpha = if (running && (live || rtmpConnecting) && !rtmpClosing) 1f else 0.5f
        videoPublishTestButton.isEnabled = !running && !rtmpClosing
        quickStartButton.isEnabled = !running && !rtmpClosing
        quickStartButton.alpha = if (running || rtmpClosing) 0.5f else 1f
        quickStopButton.isEnabled = running && (live || rtmpConnecting) && !rtmpClosing
        quickStopButton.alpha = if (running && (live || rtmpConnecting) && !rtmpClosing) 1f else 0.5f
    }

    private fun stopBroadcastMonitor() {
        broadcastTick?.let { broadcastHandler.removeCallbacks(it) }
        broadcastTick = null
        hlsBufferingSinceMs = 0L
        keepScreenActive(false)
        if (::broadcastMonitor.isInitialized) {
            broadcastMonitor.text = "FORA DO AR — ZCast de testes\nSessão RTMP encerrada."
            broadcastMonitor.setTextColor(Color.DKGRAY)
        }
    }

    private fun startBroadcastMonitor(session: HlsRtmpVideoSession) {
        stopBroadcastMonitor()
        broadcastStartedAtMs = android.os.SystemClock.elapsedRealtime()
        hlsBufferingSinceMs = 0L
        keepScreenActive(true)
        val tick = object : Runnable {
            private var previousVideoPackets = 0L
            private var lastVideoProgressMs = android.os.SystemClock.elapsedRealtime()
            private var previousAudioPackets = 0
            private var lastAudioProgressMs = android.os.SystemClock.elapsedRealtime()
            private var rateWindowStartedMs = android.os.SystemClock.elapsedRealtime()
            private var rateWindowVideoPackets = 0L
            private var rateWindowAudioPackets = 0
            private var videoFps = 0.0
            private var audioPacketsPerSecond = 0.0
            private var lowFpsSinceMs = 0L
            private var lowFpsWarning = false
            override fun run() {
                if (rtmpVideoTestSession !== session || !rtmpVideoTestRunning) return
                val now = android.os.SystemClock.elapsedRealtime()
                val seconds = ((now - broadcastStartedAtMs) / 1000).coerceAtLeast(0)
                val playbackState = player?.playbackState
                if (playbackState == Player.STATE_BUFFERING) {
                    if (hlsBufferingSinceMs == 0L) hlsBufferingSinceMs = now
                    if (rtmpLiveMode && now - hlsBufferingSinceMs >= 15_000L) {
                        stopRtmpVideoTest(
                            startLiveButton,
                            "ALERTA: fonte M3U8 sem reprodução por 15 segundos. Teste ZCast encerrado."
                        )
                        return
                    }
                } else {
                    hlsBufferingSinceMs = 0L
                }
                if (rtmpLiveMode && playbackState == Player.STATE_ENDED) {
                    stopRtmpVideoTest(startLiveButton,
                        "ALERTA: a fonte M3U8 terminou. Teste ZCast encerrado.")
                    return
                }
                val pcmState = pcmTap.snapshot()
                if (liveSourceSwitchPending &&
                    pcmState.flushes > sourceSwitchAudioFlushesAtStart &&
                    sourceSwitchAudioBuffersAfterFlush < 0L
                ) {
                    // Só confirmar bytes produzidos depois da reinicialização
                    // do sink de áudio da nova fonte, não buffers atrasados da anterior.
                    sourceSwitchAudioBuffersAfterFlush = pcmState.buffers
                }
                if (liveSourceSwitchPending && playbackState == Player.STATE_READY &&
                    sourceSwitchAudioBuffersAfterFlush >= 0L &&
                    pcmState.buffers > sourceSwitchAudioBuffersAfterFlush
                ) {
                    if (pcmState.encoding != android.media.AudioFormat.ENCODING_PCM_16BIT ||
                        pcmState.sampleRate != broadcastPcmSampleRate ||
                        pcmState.channels != broadcastPcmChannels
                    ) {
                        sourceSwitchTimeoutHandler.removeCallbacksAndMessages(null)
                        stopRtmpVideoTest(
                            startLiveButton,
                            "CORTE A/B: formato de áudio da nova fonte é diferente. Teste encerrado para evitar AAC incorreto."
                        )
                        return
                    }
                    if (!sourceSwitchPcmReady) {
                        sourceSwitchPcmReady = true
                        sourceSwitchStage = "ÁUDIO PCM PRONTO; AGUARDANDO H.264 E AAC"
                        sourceSwitchVideoPacketsAtReady = session.videoPacketCount
                        sourceSwitchAacPacketsAtReady = session.audioPacketCount
                        quickSourceLabel.text =
                            "FONTE $activeSource: ÁUDIO OK — VERIFICANDO H.264 E AAC..."
                    }
                }
                val currentVideoPackets = session.videoPacketCount
                if (currentVideoPackets > previousVideoPackets) {
                    previousVideoPackets = currentVideoPackets
                    lastVideoProgressMs = now
                }
                val currentAudioPackets = session.audioPacketCount
                if (currentAudioPackets > previousAudioPackets) {
                    previousAudioPackets = currentAudioPackets
                    lastAudioProgressMs = now
                }
                if (liveSourceSwitchPending) {
                    val switchSeconds = ((now - sourceSwitchStartedAtMs) / 1000L)
                        .coerceAtLeast(0L)
                    quickSourceLabel.text =
                        "CORTE $activeSource: $sourceSwitchStage (${switchSeconds}s / 12s)"
                }
                if (liveSourceSwitchPending && sourceSwitchPcmReady &&
                    playbackState == Player.STATE_READY &&
                    currentVideoPackets > sourceSwitchVideoPacketsAtReady &&
                    currentAudioPackets > sourceSwitchAacPacketsAtReady
                ) {
                    liveSourceSwitchPending = false
                    sourceSwitchPcmReady = false
                    sourceSwitchStage = "CORTE CONCLUÍDO LOCALMENTE"
                    // A pausa de carregamento A/B não deve ser contada como
                    // falha contínua do codificador após uma troca bem-sucedida.
                    lastVideoProgressMs = now
                    lastAudioProgressMs = now
                    lowFpsSinceMs = 0L
                    lowFpsWarning = false
                    successfulSourceCuts++
                    sourceSwitchTimeoutHandler.removeCallbacksAndMessages(null)
                    val cutDurationMs = now - sourceSwitchStartedAtMs
                    quickSourceLabel.text =
                        "CORTE $activeSource LOCALMENTE OK: ${cutDurationMs}ms • ${successfulSourceCuts} corte(s)"
                    quickSourceLabel.setTextColor(red)
                    activeSourceLabel.text = "FONTE RTMP ATIVA: $activeSource"
                    status.text =
                        "CORTE A/B: novos quadros H.264 e pacotes AAC enviados localmente. Confira a saída ZCast."
                }
                // Taxas locais medidas pelo crescimento dos contadores, não
                // por configuração de FPS ou pelo estado do player remoto.
                val windowDurationMs = now - rateWindowStartedMs
                if (windowDurationMs >= 1_000L) {
                    videoFps = (currentVideoPackets - rateWindowVideoPackets)
                        .coerceAtLeast(0L) * 1000.0 / windowDurationMs
                    audioPacketsPerSecond = (currentAudioPackets - rateWindowAudioPackets)
                        .coerceAtLeast(0) * 1000.0 / windowDurationMs
                    rateWindowVideoPackets = currentVideoPackets
                    rateWindowAudioPackets = currentAudioPackets
                    rateWindowStartedMs = now
                    // O codificador tenta produzir até 15 FPS. Alertar apenas
                    // depois de vários segundos abaixo de 8 FPS, evitando
                    // alarmes por oscilações momentâneas ou início de sessão.
                    if (!liveSourceSwitchPending && seconds >= 15 && videoFps < 8.0) {
                        if (lowFpsSinceMs == 0L) lowFpsSinceMs = now
                        lowFpsWarning = now - lowFpsSinceMs >= 10_000L
                    } else {
                        lowFpsSinceMs = 0L
                        lowFpsWarning = false
                    }
                }
                // Mudo e volume zero continuam codificando AAC silencioso.
                // Esta verificação só acusa parada do fluxo de pacotes,
                // não ausência de som no conteúdo original.
                if (rtmpLiveMode && !liveSourceSwitchPending && seconds >= 15 &&
                    now - lastAudioProgressMs >= 12_000L
                ) {
                    stopRtmpVideoTest(
                        startLiveButton,
                        "ALERTA: nenhum pacote AAC enviado por 12 segundos. Teste ZCast encerrado."
                    )
                    return
                }
                // Após dez segundos sem H.264, encerra o envio experimental.
                // O contador mede a codificação/envio local, não o recebimento
                // efetivo pelo público ou pelo player da ZCast.
                if (rtmpLiveMode && !liveSourceSwitchPending && seconds >= 12 &&
                    now - lastVideoProgressMs >= 10_000L
                ) {
                    stopRtmpVideoTest(
                        startLiveButton,
                        "ALERTA: nenhum quadro H.264 enviado por 10 segundos. Transmissão de teste interrompida."
                    )
                    return
                }
                val hours = seconds / 3600
                val minutes = (seconds % 3600) / 60
                val rest = seconds % 60
                val elapsed = String.format(java.util.Locale.ROOT,
                    "%02d:%02d:%02d", hours, minutes, rest)
                val videoStatus = if (now - lastVideoProgressMs > 3_000L)
                    "SEM QUADROS RECENTES" else "QUADROS ATIVOS"
                val audioStatus = if (now - lastAudioProgressMs > 4_000L)
                    "SEM PACOTES RECENTES" else "PACOTES ATIVOS"
                val measuredVideoRate = String.format(
                    java.util.Locale.ROOT, "%.1f", videoFps
                )
                val measuredAudioRate = String.format(
                    java.util.Locale.ROOT, "%.1f", audioPacketsPerSecond
                )
                val performanceAlert = if (lowFpsWarning)
                    "\nATENÇÃO: menos de 8 quadros/s por 10s. Verifique o aparelho e a rede."
                else ""
                broadcastMonitor.text =
                    "RTMP ZCAST — SESSÃO ATIVA ($elapsed)\n" +
                    "H.264: ${currentVideoPackets} quadros ($videoStatus)\n" +
                    "Ritmo do vídeo: ${measuredVideoRate} quadros/s\n" +
                    "AAC: ${currentAudioPackets} pacotes ($audioStatus)\n" +
                    "Ritmo do áudio: ${measuredAudioRate} pacotes/s\n" +
                    "Áudio: ${if (session.isAudioMuted()) "MUDO" else "${rtmpAudioVolumePercent}%"}" +
                    performanceAlert
                broadcastMonitor.setTextColor(if (!lowFpsWarning && videoStatus == "QUADROS ATIVOS" && audioStatus == "PACOTES ATIVOS") red else gold)
                broadcastHandler.postDelayed(this, 1000L)
            }
        }
        broadcastTick = tick
        broadcastHandler.post(tick)
    }

    private fun startRtmpVideo(button: Button, continuous: Boolean) {
        if (rtmpVideoTestRunning || rtmpConnecting || rtmpClosing ||
            rtmpDiagnosticRunning || encoderDiagnostic != null || clipPipeline != null ||
            player?.playbackState != Player.STATE_READY
        ) {
            status.text = "TESTE RTMP: finalize outros testes e carregue a prévia primeiro."
            return
        }
        val server = rtmpServerInput.text.toString().trim()
        val key = streamKeyInput.text.toString().trim()
        val source = if (activeSource == "A") sourceInput.text.toString().trim() else sourceBInput.text.toString().trim()
        if (source != loadedSourceUrl || loadedSourceUrl.isBlank()) {
            status.text = "TESTE RTMP BLOQUEADO: endereço da fonte foi alterado. Toque CARREGAR PRÉVIA antes de iniciar."
            return
        }
        if (player?.isPlaying != true) {
            status.text = "TESTE RTMP BLOQUEADO: a prévia não está reproduzindo. Confira o vídeo e tente novamente."
            return
        }
        if (!server.equals("rtmp://srv2.zcast.com.br:1935/redenovelas", ignoreCase = true)) {
            status.text = "TESTE RTMP: saída permitida somente no segundo canal ZCast."
            return
        }
        if (key.isBlank()) {
            status.text = "TESTE RTMP: informe a chave do segundo canal."
            return
        }
        when (val guard = StreamDestinationGuard.inspect(source, server, key)) {
            is StreamDestinationGuard.Result.Blocked -> {
                status.text = "TESTE RTMP BLOQUEADO: ${guard.reason}"
                return
            }
            StreamDestinationGuard.Result.AllowedForTesting -> Unit
        }
        val destination = try {
            RtmpDestination.parse(server, key)
        } catch (error: IllegalArgumentException) {
            status.text = "TESTE RTMP: dados de saída inválidos."
            return
        }
        val pcm = pcmTap.snapshot()
        val now = android.os.SystemClock.elapsedRealtime()
        val recentPcm = pcm.lastBufferAtElapsedMs > 0L &&
            now - pcm.lastBufferAtElapsedMs in 0L..3_000L
        if (!recentPcm || pcm.bytes == 0L ||
            pcm.encoding != android.media.AudioFormat.ENCODING_PCM_16BIT ||
            pcm.sampleRate !in listOf(32000, 44100, 48000) || pcm.channels !in 1..2
        ) {
            status.text = "TESTE RTMP: áudio PCM recente e compatível não detectado. Verifique a fonte M3U8 com som."
            return
        }
        broadcastPcmSampleRate = pcm.sampleRate
        broadcastPcmChannels = pcm.channels
        liveSourceSwitchPending = false
        sourceSwitchPcmReady = false
        sourceSwitchTimeoutHandler.removeCallbacksAndMessages(null)
        successfulSourceCuts = 0
        quickSourceLabel.text = "FONTE $activeSource — CONECTANDO AO ZCAST..."
        quickSourceLabel.setTextColor(gold)
        rtmpVideoTestRunning = true
        rtmpClosing = false
        rtmpConnecting = true
        val attempt = ++rtmpAttemptId
        rtmpLiveMode = continuous
        setTransmissionState("● CONECTANDO — ZCAST DE TESTE", gold)
        setRtmpControls(true, continuous)
        button.isEnabled = false
        status.text = "TESTE RTMP: autorizando segundo canal..."
        Thread {
            val result = runCatching {
                val transport = RtmpTestTransport.connect(destination, timeoutMs = 5000)
                try {
                    val id = transport.requestPublishStreamIdForTest(destination)
                        ?: error("Publicação não autorizada no servidor de teste")
                    Pair(transport, id)
                } catch (error: Exception) {
                    transport.close()
                    throw error
                }
            }
            runOnUiThread {
                if (attempt != rtmpAttemptId || !rtmpVideoTestRunning || isFinishing || isDestroyed) {
                    result.getOrNull()?.first?.close()
                    if (attempt != rtmpAttemptId) return@runOnUiThread
                    rtmpConnecting = false
                    rtmpVideoTestRunning = false
                    rtmpLiveMode = false
                    setTransmissionState("● FORA DO AR — ZCAST DE TESTE", Color.DKGRAY)
                    showSourceStopped()
                    setRtmpControls(false, false)
                    return@runOnUiThread
                }
                rtmpConnecting = false
                setRtmpControls(true, continuous)
                result.fold(
                    onSuccess = { (transport, streamId) ->
                        rtmpVideoTestTransport = transport
                        try {
                            val session = HlsRtmpVideoSession(
                                playerView = playerView,
                                transport = transport,
                                publishedStreamId = streamId,
                                bitrate = 1_600_000,
                                audioTap = pcmTap,
                                onError = { error ->
                                    if (rtmpVideoTestSession != null) {
                                        stopRtmpVideoTest(button,
                                            "TESTE RTMP: falha na codificação ou envio. Sessão encerrada.")
                                    }
                                }
                            )
                            // A imagem da prévia fica em um ImageView separado
                            // da superfície do vídeo. Por isso, desenhar uma
                            // cópia legível no codificador RTMP é obrigatório.
                            val bitmap = currentSceneLogo()
                            rtmpVideoTestSession = session
                            session.setAudioMuted(rtmpAudioMuted)
                            session.setAudioVolumePercent(rtmpAudioVolumePercent)
                            session.start(
                                bitmap, currentSceneLowerThird(), currentSceneTicker(), logoLayout, tickerSpeedPercent
                            )
                            startBroadcastMonitor(session)
                            quickSourceLabel.text = "FONTE $activeSource — PUBLICANDO LOCALMENTE (TESTE)"
                            quickSourceLabel.setTextColor(red)
                            setTransmissionState("● PUBLICANDO — ZCAST DE TESTE (VERIFICAR RECEPÇÃO)", red)
                            if (continuous) {
                                status.text = "AO VIVO NO ZCAST TESTE: áudio + vídeo + logo. Use PARAR TRANSMISSÃO para encerrar."
                            } else {
                                status.text = "TESTE RTMP: enviando vídeo + áudio AAC por 5 segundos..."
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    if (rtmpVideoTestSession === session) {
                                        stopRtmpVideoTest(
                                            button,
                                            "TESTE RTMP: áudio e vídeo de 5s encerrados. Verifique a imagem e o som no SEGUNDO canal ZCast."
                                        )
                                    }
                                }, 5000L)
                            }
                        } catch (error: Exception) {
                            rtmpVideoTestSession?.close()
                            rtmpVideoTestSession = null
                            rtmpVideoTestTransport?.close()
                            rtmpVideoTestTransport = null
                            rtmpVideoTestRunning = false
                            rtmpLiveMode = false
                            stopBroadcastMonitor()
                            setTransmissionState("● FALHA — ZCAST DE TESTE", red)
                            showSourceStopped("FALHA NA INICIALIZAÇÃO — SEM CORTE")
                            setRtmpControls(false, false)
                            button.isEnabled = true
                            status.text = "TESTE RTMP: verifique logo e áudio do M3U8. Sessão fechada."
                        }
                    },
                    onFailure = {
                        rtmpVideoTestRunning = false
                        rtmpLiveMode = false
                        setTransmissionState("● FALHA DE CONEXÃO — ZCAST DE TESTE", red)
                        showSourceStopped("FALHA DE CONEXÃO — SEM CORTE")
                        setRtmpControls(false, false)
                        button.isEnabled = true
                        status.text = "TESTE RTMP: publicação não concluída no ZCast."
                    }
                )
            }
        }.start()
    }

    private fun stopRtmpVideoTest(button: Button, message: String) {
        if (rtmpClosing) return
        if (rtmpConnecting) {
            // O resultado tardio do socket será descartado após o cancelamento.
            rtmpAttemptId++
            rtmpConnecting = false
            rtmpVideoTestRunning = false
            rtmpLiveMode = false
            setRtmpControls(false, false)
            setTransmissionState("● CONEXÃO CANCELADA — ZCAST DE TESTE", Color.DKGRAY)
            status.text = "Conexão cancelada. Nenhuma sessão de mídia foi iniciada."
            showSourceStopped("CONEXÃO RTMP CANCELADA — SEM CORTE")
            button.isEnabled = true
            return
        }
        rtmpClosing = true
        val cutWasPendingAtStop = liveSourceSwitchPending
        if (::quickSourceLabel.isInitialized) {
            quickSourceLabel.text = if (liveSourceSwitchPending)
                "CORTE A/B NÃO CONFIRMADO — TESTE ZCAST ENCERRANDO"
            else "FONTE $activeSource — TRANSMISSÃO ENCERRANDO"
            quickSourceLabel.setTextColor(gold)
        }
        liveSourceSwitchPending = false
        sourceSwitchTimeoutHandler.removeCallbacksAndMessages(null)
        val session = rtmpVideoTestSession
        rememberLastTest(session, if (cutWasPendingAtStop)
            "Corte A/B não confirmado; encerramento do teste"
        else "Encerramento solicitado ou proteção automática")
        rtmpVideoTestSession = null
        stopBroadcastMonitor()
        rtmpLiveMode = false
        setTransmissionState("● ENCERRANDO — ZCAST DE TESTE", gold)
        setRtmpControls(true, false)
        if (session == null) {
            rtmpVideoTestTransport?.close()
            rtmpVideoTestTransport = null
            rtmpVideoTestRunning = false
            rtmpClosing = false
            button.isEnabled = true
            setRtmpControls(false, false)
            setTransmissionState("● FORA DO AR — ZCAST DE TESTE", Color.DKGRAY)
            showSourceStopped()
            return
        }
        session.stopWhenClosed {
            val audioPackets = session.audioPacketCount
            rtmpVideoTestTransport?.close()
            rtmpVideoTestTransport = null
            rtmpVideoTestRunning = false
            rtmpClosing = false
            if (!isFinishing && !isDestroyed) {
                button.isEnabled = true
                setRtmpControls(false, false)
                setTransmissionState("● FORA DO AR — ZCAST DE TESTE", Color.DKGRAY)
                showSourceStopped()
                status.text = "$message Pacotes AAC enviados: $audioPackets."
            }
        }
    }

    private fun validateRtmpSettings() {
        val server = rtmpServerInput.text.toString().trim()
        val key = streamKeyInput.text.toString().trim()
        val source = sourceInput.text.toString().trim()
        val bitrate = bitrateSpinner.selectedItem?.toString() ?: "não definido"

        when (val result = StreamDestinationGuard.inspect(source, server, key)) {
            is StreamDestinationGuard.Result.Blocked -> {
                AlertDialog.Builder(this)
                    .setTitle("Verifique a configuração")
                    .setMessage(result.reason + "\n\nNenhuma transmissão foi iniciada.")
                    .setPositiveButton("OK", null)
                    .show()
            }
            StreamDestinationGuard.Result.AllowedForTesting -> {
                AlertDialog.Builder(this)
                    .setTitle("Campos verificados")
                    .setMessage(
                        "Endereços: formato aceito\n" +
                        "Chave: preenchida e oculta\n" +
                        "Perfil de referência selecionado: $bitrate\n\n" +
                        "Esta verificação não testa a conexão, não garante ausência de loop e não transmite vídeo. " +
                        "Use um destino de teste separado."
                    )
                    .setPositiveButton("OK", null)
                    .show()
            }
        }
    }

    /**
     * Seleciona uma fonte de vídeo M3U8 fora do ar. Alternância durante
     * publicação está bloqueada porque trocar o player ao vivo pode causar
     * interrupção, perda de áudio e falha no encoder RTMP.
     */
    private fun invalidHlsAddressReason(address: String): String? {
        val value = address.trim()
        if (value.isBlank()) return "link M3U8 vazio"
        if (value.any { it.isWhitespace() || it.isISOControl() }) {
            return "link contém espaços ou caracteres de controle"
        }
        val parsed = runCatching { android.net.Uri.parse(value) }.getOrNull()
            ?: return "endereço não reconhecido"
        if (parsed.scheme !in listOf("http", "https")) return "use HTTP ou HTTPS"
        if (parsed.host.isNullOrBlank() || parsed.host!!.contains(" ")) {
            return "domínio do servidor ausente ou inválido"
        }
        if (parsed.port > 65535) return "porta do servidor inválida"
        // A extensão .m3u8 não é obrigatória: alguns servidores HLS
        // fornecem o manifesto por URLs com tokens e rotas dinâmicas.
        return null
    }

    private fun selectVideoSource(source: String) {
        if (rtmpClosing || rtmpConnecting || liveSourceSwitchPending) {
            status.text = "FONTES: aguarde o término da conexão, troca ou encerramento RTMP."
            return
        }
        if (rtmpVideoTestRunning) {
            // Corte experimental na MESMA instância ExoPlayer, para não
            // desmontar PlayerView/PixelCopy nem a ponte de áudio PCM.
            if (!rtmpLiveMode || rtmpVideoTestSession == null) {
                status.text = "FONTES: troca ao vivo disponível somente em transmissão contínua."
                return
            }
            if (source == activeSource) {
                status.text = "FONTE $source já está selecionada."
                return
            }
            val nextUrl = (if (source == "A") sourceInput else sourceBInput)
                .text.toString().trim()
            val invalidReason = invalidHlsAddressReason(nextUrl)
            if (invalidReason != null) {
                status.text = "CORTE BLOQUEADO: fonte $source — $invalidReason. A fonte atual foi mantida."
                quickSourceLabel.text = "CORTE RECUSADO — FONTE $activeSource MANTIDA"
                return
            }
            if (nextUrl == loadedSourceUrl) {
                status.text = "CORTE NÃO NECESSÁRIO: fontes A e B usam o mesmo M3U8. Informe outro link antes de cortar."
                quickSourceLabel.text = "FONTE $activeSource MANTIDA — MESMO ENDEREÇO M3U8"
                return
            }
            val server = rtmpServerInput.text.toString().trim()
            val key = streamKeyInput.text.toString().trim()
            if (!server.equals("rtmp://srv2.zcast.com.br:1935/redenovelas", true) ||
                StreamDestinationGuard.inspect(nextUrl, server, key)
                    !is StreamDestinationGuard.Result.AllowedForTesting
            ) {
                status.text = "CORTE BLOQUEADO: verifique a fonte e a saída ZCast de testes."
                return
            }
            val exo = player ?: return
            sourceSwitchAudioFlushesAtStart = pcmTap.snapshot().flushes
            sourceSwitchAudioBuffersAfterFlush = -1L
            sourceSwitchPcmReady = false
            sourceSwitchVideoReady = false
            sourceSwitchStage = "AGUARDANDO A NOVA FONTE"
            sourceSwitchStartedAtMs = android.os.SystemClock.elapsedRealtime()
            liveSourceSwitchPending = true
            quickSourceLabel.text = "CORTE A/B: CARREGANDO FONTE $source..."
            sourceSwitchTimeoutHandler.removeCallbacksAndMessages(null)
            sourceSwitchTimeoutHandler.postDelayed({
                if (liveSourceSwitchPending && rtmpVideoTestSession != null &&
                    rtmpLiveMode && !rtmpClosing
                ) {
                    stopRtmpVideoTest(startLiveButton,
                        "CORTE A/B: tempo limite de 12s na etapa '$sourceSwitchStage' da fonte $source. Teste ZCast encerrado.")
                }
            }, 12_000L)
            status.text = "CORTE EXPERIMENTAL: carregando fonte $source na saída ZCast..."
            graphicPrefs.edit()
                .putString("source_a", sourceInput.text.toString().trim())
                .putString("source_b", sourceBInput.text.toString().trim())
                .apply()
            activeSource = source
            loadedSourceUrl = nextUrl
            activeSourceLabel.text = "FONTE RTMP: $source — CARREGANDO..."
            runCatching {
                exo.setMediaItem(MediaItem.fromUri(nextUrl))
                exo.prepare()
                exo.playWhenReady = true
            }.onFailure {
                sourceSwitchTimeoutHandler.removeCallbacksAndMessages(null)
                stopRtmpVideoTest(startLiveButton,
                    "CORTE: falha ao abrir fonte $source. Teste ZCast encerrado.")
            }
            return
        }
        val current = if (activeSource == "A") sourceInput else sourceBInput
        val next = if (source == "A") sourceInput else sourceBInput
        val address = next.text.toString().trim()
        val invalidReason = invalidHlsAddressReason(address)
        if (invalidReason != null) {
            status.text = "FONTE $source: $invalidReason."
            return
        }
        graphicPrefs.edit()
            .putString("source_a", sourceInput.text.toString().trim())
            .putString("source_b", sourceBInput.text.toString().trim())
            .apply()
        activeSource = source
        activeSourceLabel.text = "FONTE ATIVA: $source"
        quickSourceLabel.text = "FONTE SELECIONADA: $source"
        status.text = "FONTE $source selecionada. Carregando prévia..."
        loadPreview()
    }

    private fun loadPreview(autoRetry: Boolean = false) {
        if (rtmpVideoTestRunning || rtmpClosing || rtmpConnecting ||
            rtmpVideoTestSession != null) {
            status.text = "FONTE: pare a transmissão ZCast antes de recarregar a prévia."
            return
        }
        encoderDiagnostic?.close()
        encoderDiagnostic = null
        if (!autoRetry) previewRetryCount = 0
        previewRetryHandler.removeCallbacksAndMessages(null)
        val generation = ++previewGeneration
        val url = if (activeSource == "A") sourceInput.text.toString().trim()
            else sourceBInput.text.toString().trim()
        val invalidReason = invalidHlsAddressReason(url)
        if (invalidReason != null) {
            status.text = "PRÉVIA: $invalidReason."
            return
        }
        graphicPrefs.edit()
            .putString("source_a", sourceInput.text.toString().trim())
            .putString("source_b", sourceBInput.text.toString().trim())
            .apply()
        player?.release()
        loadedSourceUrl = url
        player = ExoPlayer.Builder(this, HlsAudioRenderersFactory(this, pcmTap)).build().also { exo ->
            playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.playWhenReady = true
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (player !== exo) return
                    if (liveSourceSwitchPending && playbackState == Player.STATE_READY) {
                        sourceSwitchVideoReady = true
                        sourceSwitchStage = "VÍDEO PRONTO; AGUARDANDO ÁUDIO PCM"
                        quickSourceLabel.text = "FONTE $activeSource: VÍDEO PRONTO — VERIFICANDO ÁUDIO..."
                    }
                    if (rtmpVideoTestRunning && !rtmpClosing &&
                        playbackState == Player.STATE_ENDED && rtmpVideoTestSession != null
                    ) {
                        stopRtmpVideoTest(startLiveButton,
                            "FONTE M3U8: reprodução encerrada. Transmissão de teste interrompida.")
                        return
                    }
                    if (rtmpVideoTestSession != null) return
                    status.text = when (playbackState) {
                        Player.STATE_BUFFERING -> "PRÉVIA: conectando / carregando..."
                        Player.STATE_READY -> "PRÉVIA: reprodução pronta"
                        Player.STATE_ENDED -> "PRÉVIA: transmissão encerrada"
                        else -> "PRÉVIA: aguardando reprodução"
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    if (player !== exo || previewGeneration != generation) return
                    if (rtmpVideoTestSession != null && !rtmpClosing) {
                        sourceSwitchTimeoutHandler.removeCallbacksAndMessages(null)
                        stopRtmpVideoTest(startLiveButton,
                            "FONTE M3U8: falha na reprodução (${error.errorCodeName}). Teste interrompido.")
                        return
                    }
                    if (rtmpVideoTestRunning || rtmpClosing || rtmpConnecting) {
                        status.text = "PRÉVIA: fonte falhou durante conexão RTMP; aguarde ou cancele o teste."
                        return
                    }
                    if (previewRetryCount < 3 && !isFinishing && !isDestroyed) {
                        previewRetryCount++
                        val delay = 2000L * (1L shl (previewRetryCount - 1))
                        status.text = "PRÉVIA: falha M3U8. Reconexão ${previewRetryCount}/3 em ${delay / 1000}s (sem RTMP)."
                        previewRetryHandler.postDelayed({
                            if (previewGeneration == generation && !rtmpVideoTestRunning &&
                                !rtmpClosing && !rtmpConnecting && !isFinishing && !isDestroyed
                            ) loadPreview(autoRetry = true)
                        }, delay)
                    } else {
                        status.text = "PRÉVIA: falha M3U8 (${error.errorCodeName}). Toque RECONECTAR."
                    }
                }
            })
        }
    }

    private fun button(label: String): Button = Button(this).apply {
        text = label
        isAllCaps = false
        setTextColor(white)
        setBackgroundColor(red)
        textSize = 13f
        setPadding(dp(8), dp(5), dp(8), dp(5))
        val params = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT
        )
        params.topMargin = dp(5)
        layoutParams = params
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 41 && resultCode == Activity.RESULT_OK) {
            logoUri = data?.data
            status.text = if (logoUri != null)
                "Logotipo carregado na prévia e pronto para compor o vídeo RTMP."
            else status.text
            logoUri?.let {
                val permissionGranted = runCatching {
                    contentResolver.takePersistableUriPermission(
                        it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }.isSuccess
                logoOverlay.setImageURI(it)
                logoOverlay.visibility = if (logoOverlay.drawable != null) View.VISIBLE else View.GONE
                applySelectedSceneToPreview()
                refreshActiveBroadcastGraphics()
                if (permissionGranted && logoOverlay.drawable != null) {
                    graphicPrefs.edit().putString("logo_uri", it.toString()).apply()
                } else {
                    status.text = "Logo disponível apenas nesta sessão ou arquivo inacessível."
                }
            }
        }
    }

    override fun onStop() {
        previewGeneration++
        previewRetryHandler.removeCallbacksAndMessages(null)
        rememberLastTest(rtmpVideoTestSession, "Aplicativo saiu do primeiro plano")
        stopBroadcastMonitor()
        rtmpVideoTestSession?.close()
        rtmpVideoTestSession = null
        rtmpVideoTestTransport?.close()
        rtmpVideoTestTransport = null
        rtmpVideoTestRunning = false
        rtmpClosing = false
        rtmpConnecting = false
        // Impede que um diagnóstico concluído depois de onStop modifique
        // o painel ou cancele o bloqueio de um diagnóstico novo.
        rtmpDiagnosticGeneration++
        rtmpDiagnosticRunning = false
        if (::startLiveButton.isInitialized) setRtmpControls(false, false)
        if (::quickSourceLabel.isInitialized) {
            quickSourceLabel.text = "FORA DO AR — TESTE ZCAST ENCERRADO"
            quickSourceLabel.setTextColor(Color.DKGRAY)
        }
        liveSourceSwitchPending = false
        sourceSwitchTimeoutHandler.removeCallbacksAndMessages(null)
        rtmpAttemptId++
        clipPipeline?.close()
        clipPipeline = null
        clipRecorder?.discard()
        clipRecorder = null
        encoderDiagnostic?.close()
        encoderDiagnostic = null
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        sourceSwitchTimeoutHandler.removeCallbacksAndMessages(null)
        previewGeneration++
        previewRetryHandler.removeCallbacksAndMessages(null)
        stopBroadcastMonitor()
        rtmpVideoTestSession?.close()
        rtmpVideoTestSession = null
        rtmpVideoTestTransport?.close()
        rtmpVideoTestTransport = null
        rtmpVideoTestRunning = false
        clipPipeline?.close()
        clipPipeline = null
        clipRecorder?.discard()
        clipRecorder = null
        encoderDiagnostic?.close()
        encoderDiagnostic = null
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
