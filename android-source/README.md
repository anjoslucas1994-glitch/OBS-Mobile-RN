# OBS Mobile RN — versão 0.3

Projeto Android em Kotlin para a Web TV Rede Novelas.

## Recursos presentes
- Prévia de uma fonte HLS/M3U8 usando AndroidX Media3.
- Campos para texto inferior, letreiro e seleção de logotipo na prévia local.
- Configuração visual de servidor RTMP, chave/token (campo oculto) e taxa de bits.
- Validação básica dos campos RTMP, sem armazenar a chave/token.

## Limitações importantes
Esta versão ainda **não publica uma transmissão RTMP**. O botão valida somente o preenchimento dos campos. Os gráficos aparecem apenas na prévia local; ainda não são incorporados ao vídeo enviado.

Para cumprir o fluxo desejado (receber HLS, compor logo/textos, codificar H.264/AAC e publicar RTMP), ainda é necessário integrar e testar um pipeline de composição, codificação e mux RTMP em Android. O projeto não foi compilado nem testado em aparelho nesta entrega; é código-fonte para abrir no Android Studio, não um APK instalado.

## Atenção ao servidor Vox
Não coloque credenciais reais em código-fonte, prints ou mensagens. A chave RTMP deve ser mantida privada e, como foi compartilhada anteriormente na conversa, recomenda-se gerar uma nova no painel Vox.

Antes de retransmitir, confirme com a Vox se o endereço M3U8 de entrada e o destino RTMP são fluxos separados. Ler o próprio canal e publicar de volta no mesmo destino pode criar um loop ou interromper a transmissão.

## Abrir
1. Extraia o ZIP.
2. Abra a pasta no Android Studio.
3. Aguarde a sincronização do Gradle.
4. Execute em um aparelho Android com conexão estável.
