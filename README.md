# OBS Mobile RN — Rede Novelas (Android)

Aplicativo experimental para controlar pelo celular uma transmissão de **teste** da Rede Novelas, com prévia M3U8, gráficos, mixer de áudio e mesa de corte A/B.

## Instalação pelo celular Android

1. Abra **Actions** neste repositório e escolha a execução mais recente de **Compilar APK - OBS Mobile RN** que tenha um símbolo verde.
2. Na página da execução, role até **Artifacts** e toque no arquivo `OBS-Mobile-RN-v0.68-debug` (ou na versão mais recente).
3. Extraia o ZIP baixado. Dentro dele estará o arquivo `app-debug.apk`.
4. No celular, abra o APK e permita a instalação desse arquivo, se o Android solicitar. Depois de instalar, abra o aplicativo.

O GitHub pode exigir login para baixar Artifacts. Compilar com sucesso **não comprova** que a transmissão funciona no celular; os testes com o ZCast ainda são necessários.

## Teste recomendado, sem transmissão inicial

1. Abra o aplicativo e carregue uma fonte M3U8 da qual você tenha autorização.
2. Confira a imagem e o som na prévia.
3. Toque **VERIFICAR VÍDEO E ÁUDIO DA FONTE (SEM RTMP)**.
4. Só depois, caso queira testar a transmissão, informe a chave RTMP **exclusivamente do canal secundário ZCast** e inicie o teste.
5. Confira imagem e áudio no player receptor do canal ZCast. Use **PARAR TRANSMISSÃO** ao terminar.
6. Toque **COPIAR RELATÓRIO TÉCNICO (SEM CHAVES)** para compartilhar o diagnóstico com segurança.

## Limites de segurança

- **Não publicar no canal principal Vox.** Ele é somente fonte de entrada, nunca destino de testes.
- A saída de mídia RTMP do aplicativo está restrita a `rtmp://srv2.zcast.com.br:1935/redenovelas` e à chave digitada localmente.
- Não coloque chaves, tokens ou credenciais no GitHub nem nos relatórios compartilhados.
- A transmissão depende do aplicativo em primeiro plano: **não** substitui um servidor de processamento 24 horas.
- Cortes A/B são experimentais; o monitoramento local de pacotes H.264/AAC não confirma recepção externa.

## Código e verificações

O projeto Android está em `android-source/`. O workflow `.github/workflows/android.yml` executa testes unitários, compila o APK de depuração e verifica a integridade do arquivo gerado.
