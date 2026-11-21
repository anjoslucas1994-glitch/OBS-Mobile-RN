# OBS Mobile RN — plano técnico da transmissão RTMP (v0.6)

## Estado confirmado
- A versão v0.5 reproduz a entrada HLS/M3U8 via Media3 ExoPlayer.
- Os textos e a imagem do logotipo são desenhados como Views sobre a prévia.
- Essas Views **não fazem parte dos frames codificados**.
- Os botões de transmissão estão desabilitados; não existe publicador RTMP.
- O app usa Android minSdk 23, Java/Kotlin 17 e Media3 1.5.1.

## Pipeline necessária
1. **Entrada:** receber o HLS/M3U8 com áudio e vídeo, monitorando erros e reconexões.
2. **Composição:** renderizar os frames de vídeo numa superfície OpenGL/EGL e desenhar logo, texto fixo e ticker no mesmo frame (não apenas Views na interface).
3. **Vídeo:** enviar a superfície composta para MediaCodec H.264 (AVC), com bitrate e FPS configuráveis.
4. **Áudio:** obter PCM sincronizado da fonte, codificar AAC-LC via MediaCodec e preservar timestamps A/V.
5. **Empacotamento:** converter H.264/AAC para mensagens RTMP/FLV, publicar em um destino RTMP/RTMPS e lidar com reconexão.
6. **Controle:** estados Parado → Conectando → Ao vivo → Encerrando/Erro; botões só serão habilitados quando o pipeline for testado.

## Ponto de atenção
ExoPlayer com PlayerView serve para prévia, mas não entrega automaticamente os frames/PCM ao encoder. A implementação requer uma estratégia específica de decodificação/renderização e acesso ao áudio. Não basta adicionar uma biblioteca RTMP ou ligar os botões.

## Critérios de teste
- Primeiro transmitir para **servidor de teste separado**, jamais de volta para o mesmo canal Vox de origem.
- Verificar que logotipo e textos aparecem no vídeo **recebido** pelo destino, não só no celular.
- Verificar áudio sincronizado, estabilidade, consumo de bateria, interrupção de rede, fechamento do app e reconexão.
- Nunca gravar ou enviar chave RTMP em logs, capturas ou repositório.
- Antes de publicar no canal oficial, confirmar com o provedor Vox a URL, chave e permissão de retransmissão.

## Próxima implementação
Construir e testar um protótipo isolado de composição OpenGL → MediaCodec H.264 com fonte de teste, antes de integrar áudio e RTMP. Manter a v0.5 estável como referência.
