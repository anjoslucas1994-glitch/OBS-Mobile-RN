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
    private var encodedDiagnosticFrames = 0
    private lateinit var playerView: PlayerView
    private lateinit var sourceInput: EditText
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
            setText("https://stmv7.voxtvhd.com.br/lucas3274/lucas3274/playlist.m3u8")
        }
        root.addView(sourceInput)

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
            text = "Perfil planejado (vídeo + áudio; transmissão ainda indisponível)"
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

        val videoPublishTestButton = button("TESTAR VÍDEO RTMP NO ZCAST (5S, SEM ÁUDIO)")
        videoPublishTestButton.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Enviar vídeo para o canal de testes?")
                .setMessage(
                    "O aplicativo enviará 5 segundos de vídeo com logo e letreiro ao SEGUNDO canal ZCast. " +
                    "Não haverá áudio. Esta operação usa sua chave RTMP e pode ativar temporariamente o canal de teste. " +
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

        val startLiveButton = button("INICIAR TRANSMISSÃO AO VIVO").apply {
            isEnabled = false
            alpha = 0.5f
        }
        root.addView(startLiveButton)

        val stopLiveButton = button("PARAR TRANSMISSÃO").apply {
            isEnabled = false
            alpha = 0.5f
        }
        root.addView(stopLiveButton)

        val liveInfo = TextView(this).apply {
            text = "TRANSMISSÃO CONTÍNUA INDISPONÍVEL. O teste de 5 segundos envia somente vídeo ao segundo canal ZCast; o áudio real ainda não está integrado."
            textSize = 12f
            setTextColor(Color.rgb(90, 60, 20))
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        root.addView(liveInfo)

        val warning = TextView(this).apply {
            text = "IMPORTANTE: somente o botão de teste envia vídeo por RTMP durante 5 segundos ao canal de testes ZCast. " +
                "A próxima etapa é integrar codificação H.264/AAC e composição real dos gráficos. " +
                "Não use a mesma entrada M3U8 e a mesma saída do canal sem confirmar com a Vox: isso pode criar um loop ou interromper a transmissão."
            textSize = 11f
            setTextColor(Color.rgb(110, 55, 20))
            setPadding(dp(9), dp(9), dp(9), dp(9))
            setBackgroundColor(0xFFFFF0D8.toInt())
        }
        root.addView(warning)

        val footer = TextView(this).apply {
            text = "VERSÃO 0.15 • CORREÇÃO DA LOGO NA SAÍDA RTMP • REDE NOVELAS"
            textSize = 10f
            setTextColor(Color.GRAY)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
        }
        root.addView(footer)

        listOf(sourceInput, overlayText, tickerInput, rtmpServerInput, streamKeyInput).forEach { field ->
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

    private fun testRtmpVideoFiveSeconds(button: Button) {
        if (rtmpVideoTestRunning || encoderDiagnostic != null || clipPipeline != null ||
            player?.playbackState != Player.STATE_READY
        ) {
            status.text = "TESTE RTMP: finalize outros testes e carregue a prévia primeiro."
            return
        }
        val server = rtmpServerInput.text.toString().trim()
        val key = streamKeyInput.text.toString().trim()
        val source = sourceInput.text.toString().trim()
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
        rtmpVideoTestRunning = true
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
                            val bitmap = requireLogoBitmap()
                            rtmpVideoTestSession = session
                            session.start(
                                bitmap, overlayLabel.text.toString(), tickerLabel.text.toString()
                            )
                            status.text = "TESTE RTMP: enviando 5 segundos de vídeo SEM ÁUDIO..."
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                if (rtmpVideoTestSession === session) {
                                    stopRtmpVideoTest(
                                        button,
                                        "TESTE RTMP: envio de vídeo de 5s encerrado. Verifique o SEGUNDO canal ZCast."
                                    )
                                }
                            }, 5000L)
                        } catch (error: Exception) {
                            rtmpVideoTestSession?.close()
                            rtmpVideoTestSession = null
                            rtmpVideoTestTransport?.close()
                            rtmpVideoTestTransport = null
                            rtmpVideoTestRunning = false
                            button.isEnabled = true
                            status.text = "TESTE RTMP: verifique se a logo está carregada e visível. Sessão fechada."
                        }
                    },
                    onFailure = {
                        rtmpVideoTestRunning = false
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
        if (session == null) {
            rtmpVideoTestTransport?.close()
            rtmpVideoTestTransport = null
            rtmpVideoTestRunning = false
            button.isEnabled = true
            return
        }
        session.stopWhenClosed {
            rtmpVideoTestTransport?.close()
            rtmpVideoTestTransport = null
            rtmpVideoTestRunning = false
            if (!isFinishing && !isDestroyed) {
                button.isEnabled = true
                status.text = message
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

    private fun loadPreview() {
        encoderDiagnostic?.close()
        encoderDiagnostic = null
        val url = sourceInput.text.toString().trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            status.text = "Informe um endereço M3U8 válido."
            return
        }
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
                "Logotipo exibido na prévia. Ainda não é incorporado à saída RTMP."
            else status.text
            logoUri?.let {
                val permissionGranted = runCatching {
                    contentResolver.takePersistableUriPermission(
                        it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                }.isSuccess
                logoOverlay.setImageURI(it)
                logoOverlay.visibility = if (logoOverlay.drawable != null) View.VISIBLE else View.GONE
                if (permissionGranted && logoOverlay.drawable != null) {
                    graphicPrefs.edit().putString("logo_uri", it.toString()).apply()
                } else {
                    status.text = "Logo disponível apenas nesta sessão ou arquivo inacessível."
                }
            }
        }
    }

    override fun onStop() {
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
