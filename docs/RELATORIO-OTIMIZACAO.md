# Sonora 1.1.0 — correções, desempenho e adaptação a dispositivos

Data: 31/10/2026. Base: Sonora 1.0.0 (relatório anterior em [RELATORIO.md](RELATORIO.md)).
Tudo o que está marcado como medido ou testado foi executado neste ambiente; o que não pôde ser
validado está na seção 7.

## 1. Entregáveis

| Item | Caminho |
|---|---|
| Código-fonte | `/home/diego/Documentos/apk musica /` (a pasta termina com espaço) |
| APK de lançamento | `entrega/Sonora-1.1.0-release.apk` — 2,6 MB, versionCode 2, SHA-256 `f4b9853371938afd44dc9a7a0c38e25c61d913bccdefb3d76ca694c93bf16cb9`, assinatura verificada (`apksigner verify`), zip íntegro |
| APK de depuração | `entrega/Sonora-1.1.0-debug.apk` |
| Testes | `entrega/relatorios-testes/` (unitários em HTML, saída dos instrumentados no Android 15 e no 8.0, lint) |
| Medições brutas | `docs/medicoes/antes/`, `docs/medicoes/depois/` |
| Capturas | `docs/screenshots/v1.1/` |
| Scripts | `tools/measure.sh` (medição), `tools/make_large_library.py` (biblioteca de 1.500 faixas), `tools/make_test_audio.py` |

O APK 1.1.0 foi instalado **como atualização** sobre o 1.0.0 no emulador: os dados do usuário
(favoritos) foram preservados e não houve falhas no log.

## 2. Dispositivos e versões efetivamente testados

| Ambiente | Android | Tela | Uso |
|---|---|---|---|
| Emulador Pixel 6 (x86_64, 1,8 GB) | 15 (API 35) | 1080×2400, 420 dpi | testes automatizados, medições, fluxos |
| Mesmo emulador redimensionado | 15 | 720×1280 a 320 dpi (360×640 dp, compacto), fonte 1,3× | layout compacto |
| Mesmo emulador redimensionado | 15 | 1600×2560 a 320 dpi (800×1280 dp, tablet) | layout de tela grande |
| Mesmo emulador, paisagem | 15 | 2400×1080, navegação por 3 botões | barras laterais e recorte |
| Mesmo emulador, navegação por gestos | 15 | retrato | barras do sistema |
| Emulador Pixel (x86_64, 1,5 GB) | **8.0 (API 26, versão mínima)** | 1080×1920 | testes automatizados + uso real |

**Não testado em aparelho físico.** Um moto g24 (Android 14, 4 GB) foi conectado por USB durante o
trabalho; a autorização para usá-lo foi dada, mas ele desconectou antes que qualquer arquivo fosse
copiado ou o app fosse instalado. Nada foi gravado no celular.

## 3. Bugs encontrados, causas e correções

| # | Problema (como foi confirmado) | Causa | Correção |
|---|---|---|---|
| 1 | Alto uso de CPU com música tocando (medido: 5,67 s de CPU a cada 15 s na tela inicial) | A posição de reprodução era lida na raiz da interface: a árvore inteira recompunha 4×/s; além disso a posição era consultada 4×/s mesmo só com o mini player visível | Posição lida apenas nas folhas (barra do mini player desenhada em `Canvas`, barra de busca); consulta 1×/s com o mini player e 4×/s só com o player completo aberto |
| 2 | Biblioteca reexaminada inteira a cada abertura (medido: 0,43–0,67 s com 1.513 faixas) e de novo a cada rotação/troca de tema | Varredura disparada em `LaunchedEffect` da Activity, sem verificar se algo mudou | Varredura de inicialização uma vez por ViewModel; MediaStore só é relido quando a "impressão digital" do índice do Android muda (`MediaStore.getGeneration` no Android 11+, consulta leve de ids/datas antes); pastas SAF reexaminadas no máximo a cada 6 h ou ao atualizar manualmente |
| 3 | Pesquisa lenta (medido: 325–1.433 ms por tecla com 1.513 faixas) | A cada tecla, normalização Unicode de todos os campos e recálculo de álbuns/artistas; buscas antigas não eram canceladas | Índice pré-normalizado reconstruído só quando a biblioteca muda; `mapLatest` cancela a busca anterior |
| 4 | Última posição da fila e tempo ouvido podiam se perder ao encerrar o serviço | `onDestroy` lançava a gravação num escopo de corrotinas que era cancelado logo em seguida | Gravação síncrona da fila no `onDestroy`; estatísticas gravadas no escopo do app; gravações em rajada agrupadas (1 s) |
| 5 | Serviço acordava a cada 500 ms mesmo pausado | Laço periódico permanente | Laço só existe durante a reprodução ou com temporizador ativo |
| 6 | Troca de faixa de pasta SAF percorria a árvore inteira da pasta para procurar `.lrc` | Busca recursiva por arquivo irmão | Consulta direta do documento irmão pelo id |
| 7 | Cada evento do player reconstruía a fila inteira na interface (milhares de itens) | `refresh()` lia todos os `MediaItem` em qualquer evento | Fila reconstruída só quando a timeline ou a ordem aleatória mudam |
| 8 | Paisagem com navegação por 3 botões: botões do sistema sobre o conteúdo (captura `l1_land_home.png`) | Só as áreas superior/inferior eram tratadas | Recuo lateral `safeDrawing` (barras laterais e recorte da câmera) na raiz |
| 9 | Paisagem: player completo mostrava só a capa; controles fora da tela (`l3_land_player.png`) | Layout de coluna única | Player em dois painéis (capa à esquerda, controles à direita) quando a janela é larga |
| 10 | Tela compacta: play/pause abaixo da dobra, era preciso rolar (`c2_compact_player.png`) | Capa com tamanho fixo de até 440 dp | Controles medidos primeiro, capa ocupa o espaço restante (mínimo 140 dp); rolagem só com fontes muito grandes |
| 11 | Tela compacta com fonte ampliada: atalhos cortando palavras ("Adicionad / as recen…") | Grade fixa de 2 colunas | Número de colunas calculado pela largura e escala de fonte (1 a 4) |
| 12 | Janelas largas desperdiçavam altura com barra inferior + mini player (~40% da tela em paisagem) | Navegação só inferior | Trilho de navegação lateral a partir de 600 dp |
| 13 | Cartão "retomar reprodução" do sistema não funcionava (`Connection failed` / `No root for client com.android.systemui`) | A sessão concedia só comandos de sessão, não os de biblioteca exigidos pelo Media3 para responder ao navegador do sistema | `DEFAULT_SESSION_AND_LIBRARY_COMMANDS`; confirmado no log: `Can get resumable media` |
| 14 | Serviço de reprodução (ExoPlayer + sessão) criado na thread principal antes do primeiro quadro | Conexão do controller em `onStart` | Conexão adiada para depois do primeiro quadro; banco e DataStore pré-carregados em paralelo no `Application` |
| 15 | Mesma capa decodificada em até 5 tamanhos | Cache indexado pelo tamanho pedido | 3 faixas de tamanho; cache de 1/16 da memória em aparelhos de pouca RAM |
| 16 | Cor dominante da capa recalculada a cada recomposição do player | Cálculo direto na composição | `remember(bitmap)` |
| 17 | *(introduzido durante este trabalho e corrigido)* Biblioteca podia ficar vazia se o banco fosse recriado mantendo a impressão digital | Impressão digital guardada fora do banco | Nunca pular a varredura se o banco não tiver faixas do MediaStore; teste dedicado |
| 18 | Testes instrumentados não rodavam no Android 8 e quebravam com bibliotecas grandes | API de permissão do teste só existe no Android 9+; itens fora da área visível de listas preguiçosas | Concessão por `pm grant` em versões antigas; rolagem até o item antes de verificar |
| 19 | Build e emulador encerrados por falta de memória (exit 137) numa máquina de 7,6 GB | Gradle 3 GB + daemon Kotlin + emulador | `-Xmx2g`, compilador Kotlin no mesmo processo; builds com o emulador desligado |

Investigados e **não reproduzidos**: fechamentos inesperados e ANRs (nenhum registro no
`dropbox` nem no logcat após os testes de estabilidade); reprodução duplicada (confirmado um único
ExoPlayer por processo, inclusive após 15 ciclos de navegação, 10 minimizações e 4 rotações);
perda de playlists/favoritos (preservados após encerrar o app e após atualização do APK).

## 4. Desempenho — antes × depois

Mesmo emulador Android 15, mesma biblioteca de **1.513 faixas**, APKs de lançamento, mesmo
procedimento para os dois: instalação limpa → indexação inicial → compilação AOT forçada
(`cmd package compile -m speed-profile -f`, como numa instalação pela Play Store) →
`tools/measure.sh` → mesma sequência de 10 teclas na pesquisa. "Antes" = 1.0.0 com as mesmas
sondas de medição (sem mudança de comportamento).

| Métrica | Antes | Depois |
|---|---|---|
| Primeira abertura após instalar | 2.360 ms | 717 ms ¹ |
| Indexação inicial (1.513 faixas) | 1.806 ms | 392 ms ¹ |
| Abertura a frio (mediana de 5) | 728 ms | 618 ms |
| Biblioteca utilizável ("Fully drawn", mediana) | 1.693 ms | 1.592 ms |
| Reexame da biblioteca em cada abertura | 429–668 ms | 34–230 ms |
| Abertura a quente (mediana) | 141 ms | 137 ms |
| **Pesquisa, por tecla** | **325–1.433 ms** | **2–14 ms** |
| Memória (PSS, após rolar a lista) | 55,4 MB | 52,0 MB |
| Rolagem: quadros lentos / p90 | 50,7% / 69 ms | 49,5% / 69 ms ² |
| **CPU, 15 s tocando na tela inicial** (total / RenderThread / thread principal) | **5,67 s / 3,99 s / 0,38 s** | **2,75 s / 1,10 s / 0,19 s** ³ |
| CPU, 15 s com o player completo aberto | — | 5,50 s (barra de busca a 4 Hz) |
| CPU, 15 s tocando em segundo plano | — | 1,52 s (praticamente só decodificação) |
| CPU, 30 s pausado em segundo plano | 0,03 s | 0,01 s |

¹ A 1.1.0 inclui um perfil de inicialização (baseline profile) do próprio código; parte da
diferença da primeira abertura e da indexação simultânea vem da ausência de JIT/compilação na 1.0.0
nesse primeiro momento. Não é uma comparação puramente algorítmica.
² O emulador renderiza por software (SwiftShader); quadros lentos refletem isso e não mudaram.
Não foi possível medir fluidez com GPU real.
³ Medido por thread em `/proc/<pid>/task` após a mudança do intervalo adaptativo. A tabela
completa de `measure.sh` "depois" (em `docs/medicoes/depois`) foi tirada antes dessa última
otimização e registrou 5,62 s/20 s contra 6,39 s/20 s do "antes".

O tempo até a biblioteca ficar utilizável (~1,6 s) quase não melhorou: as marcações de tempo mostram
que ele é dominado pela inicialização do Compose e pela primeira composição no emulador (o mesmo
tempo com 13 ou 1.513 faixas), não pela leitura de dados.

Não medido separadamente: tempo de carregamento de capas e contagem exata de recomposições
(avaliadas indiretamente pelo uso de CPU da thread principal e da RenderThread).

## 5. Testes executados

| Conjunto | Resultado |
|---|---|
| Unitários JVM (`testDebugUnitTest`) | **60/60 aprovados** |
| Instrumentados, Android 15 | **36/36 aprovados** (execução final, após a última correção) ⁴ |
| Instrumentados, Android 8.0 | **36/36 aprovados** |
| Lint do lançamento | 0 erros, 44 avisos |

⁴ O arquivo `instrumentados-android15.txt` salvo é da execução imediatamente anterior à correção
nº 13; a execução final, após ela, também terminou com `OK (36 tests)` (vista no terminal, não salva).

Novos testes: reexame sem mudanças não relê a biblioteca e o forçado relê; player completo em
360×640 dp com fonte 1,3× mantém play/pause, barra de busca, anterior e próxima visíveis sem rolar.

Validações manuais no emulador: paisagem com 3 botões, tela compacta com fonte 1,3×, tamanho de
tablet, navegação por gestos; Android 8.0: permissão pelo diálogo real, capas, reprodução, segundo
plano e tela desligada (posição avançou de 3,0 s para 17,9 s com a tela apagada), notificação;
estabilidade: 15 ciclos de navegação entre abas, 16 teclas rápidas de pesquisa e apagamento,
10 minimizar/reabrir, 4 rotações — sem falhas, sem ANR, um único player.

## 6. Compatibilidade entre versões

- Mínima Android 8.0 (API 26), alvo Android 15 (API 35); testados os dois extremos.
- Permissão de áudio: `READ_MEDIA_AUDIO` (13+) ou `READ_EXTERNAL_STORAGE` (≤ 12), escolhida em tempo de execução.
- Capas: miniaturas do MediaStore no Android 10+, `MediaMetadataRetriever` antes.
- Gênero do MediaStore só no Android 11+; impressão digital do índice por geração (11+) ou consulta leve (8–10).
- Android 15 nega foco de áudio a apps em segundo plano; a reprodução sempre começa com o app
  visível ou pelo caminho de botão de mídia do serviço em primeiro plano (notificação, widget,
  controles do sistema), que foi validado.
- Comportamento de fabricantes (otimização agressiva de bateria que encerra serviços) não foi
  testado e pode variar.

## 7. Limitações e pendências

- Sem teste em aparelho físico (o moto g24 desconectou antes de ser usado).
- Conexão/desconexão de fones Bluetooth e troca de saída de som não testáveis no emulador
  (sem Bluetooth; `AUDIO_BECOMING_NOISY` é protegido). O ExoPlayer está configurado para pausar ao
  desconectar (`setHandleAudioBecomingNoisy`).
- Fluidez de rolagem não medida com GPU real.
- A tabela de faixas é carregada inteira em memória (sem paginação); adequado para milhares de faixas
  (1.513 medidas), não medido com dezenas de milhares.
- Modo "pouca RAM" (`isLowRamDevice`) não foi emulado; o código reduz o cache nesse caso.
- Em janelas muito pequenas em paisagem (multijanela) o player usa o layout de coluna e pode exigir rolagem.
- As sondas de medição (`SonoraPerf`, apenas durações e contagens) permanecem no app.
- 44 avisos de lint (nenhum erro) não foram tratados.
