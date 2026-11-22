package com.rednovelas.obsmobilern

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
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
    private var rtmpLiveMode = false
    private var selectedScene = SceneMode.PROGRAMACAO
    private var logoLayout = LogoLayout()
    private enum class SceneMode { PROGRAMACAO, SOMENTE_LOGO, TELA_LIMPA }
    private lateinit var startLiveButton: Button
    private lateinit var stopLiveButton: Button
    private lateinit var muteRtmpButton: Button
    private var rtmpAudioMuted = false
    private var rtmpAudioVolumePercent = 100
    private lateinit var broadcastMonitor: TextView
    private val broadcastHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var broadcastTick: Runnable? = null
    private var broadcastStartedAtMs = 0L
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
        fun sceneButton(title: String, mode: SceneMode, displayName: String) {
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
        }
        sceneButton("CENA 1 — PROGRAMAÇÃO COMPLETA", SceneMode.PROGRAMACAO, "PROGRAMAÇÃO COMPLETA")
        sceneButton("CENA 2 — SOMENTE LOGO", SceneMode.SOMENTE_LOGO, "SOMENTE LOGO")
        sceneButton("CENA 3 — TELA LIMPA", SceneMode.TELA_LIMPA, "TELA LIMPA")
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

        broadcastMonitor = TextView(this).apply {
            text = "FORA DO AR — ZCast de testes\nH.264: 0 quadros • AAC: 0 pacotes"
            textSize = 12f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setTextColor(Color.DKGRAY)
            setBackgroundColor(0xFFEFECE7.toInt())
            setPadding(dp(10), dp(10), dp(10), dp(10))
        }
        root.addView(broadcastMonitor)

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
            text = "VERSÃO 0.25 • POSIÇÃO E TAMANHO DA LOGO • REDE NOVELAS"
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
        button.isEnabled = false
        status.text = "RTMP: testando conexão, sem publicar..."
        Thread {
            val result = runCatching {
                RtmpTestTransport.connect(destination, timeoutMs = 5000).use { }
                true
            }
            runOnUiThread {
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
        button.isEnabled = false
        status.text = "PUBLICAÇÃO: verificando autorização no ZCast (sem áudio/vídeo)..."
        Thread {
            val result = runCatching {
                RtmpTestTransport.connect(destination, timeoutMs = 5000).use { transport ->
                    transport.requestPublishForTest(destination)
                }
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
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
        logoOverlay.visibility = if (selectedScene != SceneMode.TELA_LIMPA &&
            logoOverlay.drawable != null) View.VISIBLE else View.GONE
        overlayLabel.visibility = if (selectedScene == SceneMode.PROGRAMACAO &&
            overlayLabel.text.isNotBlank()) View.VISIBLE else View.GONE
        tickerLabel.visibility = if (selectedScene == SceneMode.PROGRAMACAO &&
            tickerLabel.text.isNotBlank()) View.VISIBLE else View.GONE
    }

    private fun currentSceneLogo(): android.graphics.Bitmap? =
        if (selectedScene == SceneMode.TELA_LIMPA) null else requireLogoBitmap()

    private fun currentSceneLowerThird(): String =
        if (selectedScene == SceneMode.PROGRAMACAO) overlayLabel.text.toString() else ""

    private fun currentSceneTicker(): String =
        if (selectedScene == SceneMode.PROGRAMACAO) tickerLabel.text.toString() else ""

    private fun refreshActiveBroadcastGraphics() {
        val session = rtmpVideoTestSession ?: return
        if (!rtmpVideoTestRunning) return
        runCatching {
            session.updateGraphics(
                currentSceneLogo(),
                currentSceneLowerThird(),
                currentSceneTicker(),
                logoLayout
            )
        }.onFailure {
            status.text = "GRÁFICOS: imagem não atualizada; confira a logo selecionada."
        }
    }

    private fun setRtmpControls(running: Boolean, live: Boolean) {
        startLiveButton.isEnabled = !running
        startLiveButton.alpha = if (running) 0.5f else 1f
        stopLiveButton.isEnabled = running && live
        stopLiveButton.alpha = if (running && live) 1f else 0.5f
        videoPublishTestButton.isEnabled = !running
    }

    private fun stopBroadcastMonitor() {
        broadcastTick?.let { broadcastHandler.removeCallbacks(it) }
        broadcastTick = null
        keepScreenActive(false)
        if (::broadcastMonitor.isInitialized) {
            broadcastMonitor.text = "FORA DO AR — ZCast de testes\nSessão RTMP encerrada."
            broadcastMonitor.setTextColor(Color.DKGRAY)
        }
    }

    private fun startBroadcastMonitor(session: HlsRtmpVideoSession) {
        stopBroadcastMonitor()
        broadcastStartedAtMs = android.os.SystemClock.elapsedRealtime()
        keepScreenActive(true)
        val tick = object : Runnable {
            private var previousVideoPackets = 0L
            private var lastVideoProgressMs = android.os.SystemClock.elapsedRealtime()
            override fun run() {
                if (rtmpVideoTestSession !== session || !rtmpVideoTestRunning) return
                val now = android.os.SystemClock.elapsedRealtime()
                val seconds = ((now - broadcastStartedAtMs) / 1000).coerceAtLeast(0)
                val currentVideoPackets = session.videoPacketCount
                if (currentVideoPackets > previousVideoPackets) {
                    previousVideoPackets = currentVideoPackets
                    lastVideoProgressMs = now
                }
                // Após dez segundos sem H.264, encerra o envio experimental.
                // O contador mede a codificação/envio local, não o recebimento
                // efetivo pelo público ou pelo player da ZCast.
                if (rtmpLiveMode && seconds >= 12 &&
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
                broadcastMonitor.text =
                    "RTMP ZCAST — SESSÃO ATIVA ($elapsed)\n" +
                    "H.264: ${currentVideoPackets} quadros ($videoStatus) • " +
                    "AAC: ${session.audioPacketCount} pacotes\n" +
                    "Áudio: ${if (session.isAudioMuted()) "MUDO" else "${rtmpAudioVolumePercent}%"}"
                broadcastMonitor.setTextColor(if (videoStatus == "QUADROS ATIVOS") red else gold)
                broadcastHandler.postDelayed(this, 1000L)
            }
        }
        broadcastTick = tick
        broadcastHandler.post(tick)
    }

    private fun startRtmpVideo(button: Button, continuous: Boolean) {
        if (rtmpVideoTestRunning || encoderDiagnostic != null || clipPipeline != null ||
            player?.playbackState != Player.STATE_READY
        ) {
            status.text = "TESTE RTMP: finalize outros testes e carregue a prévia primeiro."
            return
        }
        val server = rtmpServerInput.text.toString().trim()
        val key = streamKeyInput.text.toString().trim()
        val source = if (activeSource == "A") sourceInput.text.toString().trim() else sourceBInput.text.toString().trim()
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
        if (pcm.bytes == 0L || pcm.encoding != android.media.AudioFormat.ENCODING_PCM_16BIT ||
            pcm.sampleRate !in listOf(32000, 44100, 48000) || pcm.channels !in 1..2
        ) {
            status.text = "TESTE RTMP: áudio real ainda indisponível. Reproduza o M3U8 com som e teste novamente."
            return
        }
        rtmpVideoTestRunning = true
        rtmpLiveMode = continuous
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
                if (!rtmpVideoTestRunning || isFinishing || isDestroyed) {
                    result.getOrNull()?.first?.close()
                    rtmpVideoTestRunning = false
                    rtmpLiveMode = false
                    setRtmpControls(false, false)
                    return@runOnUiThread
                }
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
                                bitmap, currentSceneLowerThird(), currentSceneTicker(), logoLayout
                            )
                            startBroadcastMonitor(session)
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
                            setRtmpControls(false, false)
                            button.isEnabled = true
                            status.text = "TESTE RTMP: verifique logo e áudio do M3U8. Sessão fechada."
                        }
                    },
                    onFailure = {
                        rtmpVideoTestRunning = false
                        rtmpLiveMode = false
                        setRtmpControls(false, false)
                        button.isEnabled = true
                        status.text = "TESTE RTMP: publicação não concluída no ZCast."
                    }
                )
            }
        }.start()
    }

    private fun stopRtmpVideoTest(button: Button, message: String) {
        val session = rtmpVideoTestSession
        rtmpVideoTestSession = null
        stopBroadcastMonitor()
        rtmpLiveMode = false
        setRtmpControls(false, false)
        if (session == null) {
            rtmpVideoTestTransport?.close()
            rtmpVideoTestTransport = null
            rtmpVideoTestRunning = false
            button.isEnabled = true
            return
        }
        session.stopWhenClosed {
            val audioPackets = session.audioPacketCount
            rtmpVideoTestTransport?.close()
            rtmpVideoTestTransport = null
            rtmpVideoTestRunning = false
            if (!isFinishing && !isDestroyed) {
                button.isEnabled = true
                setRtmpControls(false, false)
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
    private fun selectVideoSource(source: String) {
        if (rtmpVideoTestRunning || rtmpVideoTestSession != null) {
            status.text = "FONTES: interrompa o teste ZCast antes de trocar de M3U8."
            return
        }
        val current = if (activeSource == "A") sourceInput else sourceBInput
        val next = if (source == "A") sourceInput else sourceBInput
        val address = next.text.toString().trim()
        if (!address.startsWith("https://") && !address.startsWith("http://")) {
            status.text = "FONTE $source: informe um endereço M3U8 HTTP(S) válido."
            return
        }
        graphicPrefs.edit()
            .putString("source_a", sourceInput.text.toString().trim())
            .putString("source_b", sourceBInput.text.toString().trim())
            .apply()
        activeSource = source
        activeSourceLabel.text = "FONTE ATIVA: $source"
        status.text = "FONTE $source selecionada. Carregando prévia..."
        loadPreview()
    }

    private fun loadPreview() {
        encoderDiagnostic?.close()
        encoderDiagnostic = null
        if (rtmpVideoTestRunning || rtmpVideoTestSession != null) {
            status.text = "FONTE: pare a transmissão ZCast antes de recarregar a prévia."
            return
        }
        val url = if (activeSource == "A") sourceInput.text.toString().trim()
            else sourceBInput.text.toString().trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            status.text = "Informe um endereço M3U8 válido."
            return
        }
        graphicPrefs.edit()
            .putString("source_a", sourceInput.text.toString().trim())
            .putString("source_b", sourceBInput.text.toString().trim())
            .apply()
        player?.release()
        player = ExoPlayer.Builder(this, HlsAudioRenderersFactory(this, pcmTap)).build().also { exo ->
            playerView.player = exo
            exo.setMediaItem(MediaItem.fromUri(url))
            exo.prepare()
            exo.playWhenReady = true
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    status.text = when (playbackState) {
                        Player.STATE_BUFFERING -> "PRÉVIA: conectando / carregando..."
                        Player.STATE_READY -> "PRÉVIA: reprodução pronta"
                        Player.STATE_ENDED -> "PRÉVIA: transmissão encerrada"
                        else -> "PRÉVIA: aguardando reprodução"
                    }
                }
                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    status.text = "Falha ao abrir M3U8: ${error.errorCodeName}"
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
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
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
