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
        }
        root.addView(overlayText)

        val overlayButton = button("APLICAR TEXTO NA PRÉVIA")
        overlayButton.setOnClickListener {
            val value = overlayText.text.toString().trim()
            overlayLabel.text = value
            overlayLabel.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
        }
        root.addView(overlayButton)

        val tickerInput = EditText(this).apply {
            hint = "Texto rolante (ex.: Acompanhe a programação da Rede Novelas)"
            textSize = 14f
        }
        root.addView(tickerInput)

        val tickerButton = button("APLICAR TEXTO ROLANTE")
        tickerButton.setOnClickListener {
            val value = tickerInput.text.toString().trim()
            tickerLabel.text = value
            tickerLabel.visibility = if (value.isEmpty()) View.GONE else View.VISIBLE
            tickerLabel.isSelected = true
        }
        root.addView(tickerButton)

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
            setText("rtmp://stmv7.voxtvhd.com.br:1935/lucas3274")
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
            text = "Taxa de vídeo + áudio"
            setTextColor(Color.DKGRAY)
            textSize = 12f
            setPadding(0, dp(6), 0, dp(2))
        }
        root.addView(bitrateLabel)

        bitrateSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(
                this@MainActivity,
                android.R.layout.simple_spinner_dropdown_item,
                listOf("2500 Kbps", "3500 Kbps", "4500 Kbps", "5500 Kbps", "7000 Kbps")
            )
            setSelection(4)
        }
        root.addView(bitrateSpinner)

        val outputButton = button("VALIDAR CONFIGURAÇÃO RTMP")
        outputButton.setOnClickListener { validateRtmpSettings() }
        root.addView(outputButton)

        val warning = TextView(this).apply {
            text = "IMPORTANTE: esta versão organiza os dados de saída, mas ainda NÃO envia vídeo por RTMP. " +
                "A próxima etapa é integrar codificação H.264/AAC e composição real dos gráficos. " +
                "Não use a mesma entrada M3U8 e a mesma saída do canal sem confirmar com a Vox: isso pode criar um loop ou interromper a transmissão."
            textSize = 11f
            setTextColor(Color.rgb(110, 55, 20))
            setPadding(dp(9), dp(9), dp(9), dp(9))
            setBackgroundColor(0xFFFFF0D8.toInt())
        }
        root.addView(warning)

        val footer = TextView(this).apply {
            text = "VERSÃO 0.4 • PRÉVIA GRÁFICA • REDE NOVELAS"
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

    private fun validateRtmpSettings() {
        val server = rtmpServerInput.text.toString().trim()
        val key = streamKeyInput.text.toString().trim()
        val bitrate = bitrateSpinner.selectedItem?.toString() ?: "não definido"

        if (!server.startsWith("rtmp://") && !server.startsWith("rtmps://")) {
            AlertDialog.Builder(this)
                .setTitle("Servidor inválido")
                .setMessage("Informe um endereço iniciado por rtmp:// ou rtmps://.")
                .setPositiveButton("OK", null)
                .show()
            return
        }
        if (key.isBlank()) {
            AlertDialog.Builder(this)
                .setTitle("Chave não informada")
                .setMessage("Digite a chave/token RTMP fornecida pelo painel Vox. Ela não é salva pelo aplicativo.")
                .setPositiveButton("OK", null)
                .show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Dados preenchidos")
            .setMessage(
                "Servidor: endereço informado\n" +
                "Chave: preenchida e ocultada\n" +
                "Taxa selecionada: $bitrate\n\n" +
                "Validação dos campos concluída. Isso ainda não testa a conexão nem inicia uma transmissão: o módulo de codificação e publicação RTMP ainda será integrado."
            )
            .setPositiveButton("OK", null)
            .show()
    }

    private fun loadPreview() {
        val url = sourceInput.text.toString().trim()
        if (!url.startsWith("https://") && !url.startsWith("http://")) {
            status.text = "Informe um endereço M3U8 válido."
            return
        }
        player?.release()
        player = ExoPlayer.Builder(this).build().also { exo ->
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
                contentResolver.takePersistableUriPermission(
                    it, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                logoOverlay.setImageURI(it)
                logoOverlay.visibility = View.VISIBLE
            }
        }
    }

    override fun onStop() {
        super.onStop()
        player?.pause()
    }

    override fun onDestroy() {
        playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
