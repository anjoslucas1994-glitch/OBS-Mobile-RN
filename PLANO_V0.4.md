# OBS Mobile RN v0.4 — plano de implementação

## Estado atual verificado
- O GitHub Actions extrai `OBS_Mobile_RN_v0.3_Android_Studio.zip` para `projeto/OBS_Mobile_RN`.
- Compila `assembleDebug` e publica o artefato `OBS-Mobile-RN-APK`.
- O código Android está dentro do ZIP, não em arquivos fonte editáveis diretamente no repositório.

## Requisitos v0.4
1. Extrair e versionar o projeto Android em diretórios normais do repositório, mantendo backup da v0.3.
2. Melhorar contraste e legibilidade da interface.
3. Compor logotipo e texto nos quadros de vídeo efetivamente codificados, não só na prévia.
4. Implementar entrada de mídia (arquivo/câmera/M3U8) e saída RTMP/RTMPS usando pipeline Android apropriado, com gerenciamento de áudio e reconexão.
5. Adicionar cenas e controle de corte.
6. Configurar credenciais de transmissão localmente, sem gravá-las no GitHub ou logs.
7. Testar com destino RTMP independente antes de conectar ao canal de produção.

## Atenção ao loop de transmissão
Não usar o M3U8 do mesmo canal como entrada e o RTMP desse mesmo canal como saída ao mesmo tempo: isso pode criar realimentação. Usar uma fonte independente ou um canal de teste.

## Critérios de aceite
- Logotipo e texto visíveis também no sinal recebido pelo destino RTMP.
- Áudio sincronizado; início/parada e reconexão confiáveis.
- Sem interrupção da transmissão atual durante os testes.
- APK gerado pelo GitHub Actions e testado em dispositivo Android real.

## Próximo passo de código
Extrair o ZIP, inspecionar `MainActivity.kt`, `app/build.gradle.kts` e recursos; migrar fontes para o repositório e então implementar o compositor e encoder.
