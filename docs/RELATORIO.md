# Relatório final — Sonora 1.0.0

Data: 31/10/2026. Tudo o que está marcado como "verificado" foi executado neste ambiente; o que não
pôde ser validado está listado explicitamente na seção 9.

## 1. Caminhos

| Item | Caminho |
|---|---|
| Código-fonte | `/home/diego/Documentos/apk musica /` (atenção: o nome da pasta termina com um espaço) |
| APK de lançamento (assinado) | `/home/diego/Documentos/apk musica /entrega/Sonora-1.0.0-release.apk` — 2,6 MB, SHA-256 `0ae4490e804082e501bef49eed7bd0cfba7c06200652df440aed5f3ec2c99548` |
| APK de depuração | `/home/diego/Documentos/apk musica /entrega/Sonora-1.0.0-debug.apk` |
| Relatórios de teste (HTML) | `entrega/relatorios-testes/unitarios/`, `entrega/relatorios-testes/instrumentados/`, `entrega/relatorios-testes/lint-release.html` |
| Esquema do banco (Room) | `app/schemas/com.sonora.player.data.local.database.SonoraDatabase/1.json` |
| Keystore de lançamento | `~/.android-keystores/sonora-release.jks` (fora do projeto; senha em `~/.gradle/gradle.properties`) |

Verificações do APK de lançamento: `apksigner verify` OK; integridade do zip OK; `minSdkVersion 26`,
`targetSdkVersion 35`, pacote `com.sonora.player`, versão 1.0.0 (1).

## 2. Tecnologias e versões

Kotlin 2.0.21 · AGP 8.7.3 · Gradle 8.10.2 · JDK 21 · compileSdk/targetSdk 35 · minSdk 26
Compose BOM 2024.12.01 (Material 3 1.3.1) · Media3 1.5.1 (ExoPlayer + Session) · Room 2.6.1 (KSP) ·
DataStore Preferences 1.1.1 · Navigation Compose 2.8.5 · Lifecycle 2.8.7 · Coroutines 1.9.0 ·
JUnit 4.13.2 · AndroidX Test (runner 1.6.2, ext-junit 1.2.1) · Compose UI Test.

Nenhuma biblioteca extra: capas, reordenação por arrasto, injeção de dependências e backup foram
implementados com APIs da plataforma. `org.json` é usado só como dependência de teste JVM.

## 3. Arquitetura

```
app/src/main/java/com/sonora/player/
├── SonoraApp.kt, MainActivity.kt, di/AppContainer.kt   (injeção manual, 1 instância por processo)
├── domain/
│   ├── model/        Track, Album, Artist, Playlist, ScanState, TextNormalizer…
│   ├── repository/   interfaces LibraryRepository, PlaylistRepository
│   └── usecase/      TrackSorter, SearchEngine, LibraryGrouping, HistoryPolicy, ListReorder (puros, testados na JVM)
├── data/
│   ├── local/database  Room: entidades, SonoraDatabase   local/dao  DAOs   local/preferences  SettingsStore (DataStore)
│   ├── media/        MediaStoreScanner, FolderAccessManager (SAF), MetadataReader, ArtworkLoader, LibraryMerger (puro)
│   └── repository/   LibraryRepositoryImpl, PlaylistRepositoryImpl, BackupCodec/TrackMatcher
├── playback/         PlaybackService (MediaLibraryService), PlaybackController (MediaController da UI),
│                     SonoraPlayer (ForwardingPlayer), QueueManager (ShuffleOrders, QueueSnapshot), ListenTracker,
│                     FadeCalculator, AudioEffectsManager, Lyrics (LrcParser, ID3 USLT), ArtworkBitmapLoader
├── widget/           PlayerWidgetProvider (RemoteViews)
├── navigation/       Routes, SonoraAppUi (scaffold, barra inferior, mini player, player sobreposto)
└── ui/               theme, components, home, search, library, playlists, player, settings, permissions
```

- **Um único player**: `PlaybackService` cria um ExoPlayer e uma sessão; a UI só fala com ele via
  `MediaController`. A interface mostra apenas o estado real do player (a posição é lida do
  controller; não há temporizadores simulando reprodução).
- **Ordem original × efetiva**: a fila do ExoPlayer guarda a ordem original; o modo aleatório usa
  uma `ShuffleOrder` própria que sempre começa pela faixa escolhida. "Tocar a seguir", "adicionar ao
  fim" e arrastar na fila operam na ordem efetiva sem alterar a original.
- **Índice nunca apaga linhas**: arquivos ausentes viram `available = false`; playlists, favoritos
  e histórico continuam apontando para eles. Arquivos movidos/renomeados são religados à mesma linha
  pela impressão digital (tamanho + título + artista).
- **Duplicatas**: o mesmo arquivo visto pelo MediaStore e por uma pasta SAF fica uma única vez
  visível (preferência para a cópia do MediaStore).

## 4. Funcionalidades concluídas (implementadas e exercitadas)

Biblioteca e acesso
- Detecção automática via MediaStore com permissão `READ_MEDIA_AUDIO` (ou `READ_EXTERNAL_STORAGE` ≤ Android 12).
- Tela de explicação de permissão, estado de negação, negação permanente (leva às configurações do
  app) e re-verificação ao voltar das configurações. *Verificado no emulador.*
- Seleção de pastas pelo seletor do Android (SAF), com permissão persistente somente-leitura,
  subpastas, remoção, e marcação de pasta com acesso revogado. *Verificado no emulador.*
- Reindexação automática quando o índice de mídia do Android muda (observador com debounce),
  atualização manual (botão e "puxar para atualizar").
- Músicas, álbuns (com "artista do álbum"), artistas, gêneros, pastas, playlists, favoritas,
  adicionadas recentemente, histórico, estatísticas.
- Ordenação por nome, artista, álbum, duração e data, crescente/decrescente (persistida).
- Capas embutidas (MediaStore thumbnails / MediaMetadataRetriever) com cache em memória e capa
  gerada quando não há imagem. Faixas sem tags exibem "Artista/Álbum desconhecido".
- Detalhes do arquivo: formato, taxa de bits, taxa de amostragem, profundidade, tamanho, origem.
- Opções "Incluir outros áudios", "Ignorar arquivos curtos", "Ocultar duplicadas".

Reprodução
- Play/pause/retomar, anterior/próxima, barra de busca arrastável, tempo decorrido e restante,
  aleatório, repetição (desligada / fila / faixa), volume de mídia do sistema, favoritar no player.
- Reprodução em segundo plano com serviço em primeiro plano, notificação de mídia, controles da tela
  bloqueada e do sistema, teclas de mídia (fones/Bluetooth chegam pelo mesmo caminho da sessão).
- Foco de áudio (chamadas/outros apps pausam o Sonora), pausa ao desconectar fones
  (`setHandleAudioBecomingNoisy`), arquivos corrompidos/ausentes/formatos não suportados com
  mensagem em português e pulo automático (configurável), limite de 8 erros consecutivos.
- Retomada controlada: a fila, a posição, o aleatório e a repetição são salvos e restaurados ao
  abrir o app (sem tocar sozinho); suporte a "retomar" pelos controles de mídia do sistema.
- Fila: ver atual e próximas, tocar a seguir, adicionar ao fim, remover, reordenar por arrasto
  (alça dedicada, não interfere na rolagem), limpar, tocar um item da fila.
- Mini player (arrastar para os lados troca de faixa, arrastar para cima abre) e player em tela
  cheia (capa deslizável, arrastar para baixo minimiza, fundo tingido pela cor da capa). Todos os
  gestos podem ser desligados e todos têm botão equivalente.
- Temporizador (5–90 min ou fim da faixa), com tempo restante e cancelamento.
- Equalizador do aparelho (bandas reais, predefinições, reforço de graves), persistido.
- Transição suave (fade de saída/entrada, 0–12 s); reprodução sem intervalos nativa do ExoPlayer
  quando a transição está desligada.
- Letras embutidas (ID3 USLT, Vorbis LYRICS) e arquivos `.lrc` ao lado da música em pastas SAF;
  letras no formato LRC são sincronizadas com destaque da linha atual. *Verificado no emulador.*
- Widget da tela inicial (capa, título, artista, anterior/play-pause/próxima).
- Abrir arquivos de áudio de outros apps (intent `VIEW audio/*`).

Playlists, favoritos, histórico, dados
- Criar, renomear, excluir (com confirmação), adicionar (sem duplicar), remover, reordenar
  (arrasto ou "mover para cima/baixo"), tocar, contagem e duração total.
- Favoritos sincronizados em todas as telas. Histórico com regra documentada (seção 6),
  limpeza com confirmação, opção de não registrar. Estatísticas locais (reproduções, tempo ouvido).
- Backup/restauração de playlists e favoritos em JSON (via seletor de arquivos), com
  reassociação por URI, impressão digital ou tags.
- Pesquisa local: título, artista, álbum, playlist; ignora maiúsculas, acentos e espaços extras;
  atualiza enquanto digita (debounce 150 ms); estado vazio claro.
- Tema escuro (padrão), claro, do sistema; 7 cores de destaque; cores dinâmicas (Android 12+).

## 5. Funcionalidades parciais

- **Crossfade**: implementado como *fade-out + fade-in* sequencial no mesmo player, não como
  sobreposição real de duas faixas (exigiria dois players e uma sessão customizada). Documentado na tela.
- **Reprodução sem intervalos**: depende dos metadados de atraso do codificador no arquivo
  (LAME/iTunSMPB). Não foi medida com arquivos reais com encoder delay.
- **Letras**: SYLT (letra sincronizada binária do ID3) e o átomo `©lyr` de M4A não foram
  implementados/verificados; `.lrc` lateral só funciona em pastas adicionadas via SAF (o Android não
  dá acesso a arquivos não-áudio pelo MediaStore).
- **Gêneros**: lidos do MediaStore apenas no Android 11+; em versões anteriores aparecem como
  "Gênero desconhecido" para músicas vindas do MediaStore.
- **Internacionalização**: interface somente em português, com textos no código Kotlin (não em
  `strings.xml`).

## 6. Regra do histórico

Uma faixa conta como tocada depois de **30 s realmente ouvidos** (tempo pausado não conta) ou de
metade da duração quando ela tem menos de 1 minuto (mínimo de 3 s). Cada reprodução válida soma 1 nas
estatísticas. Se a última entrada do histórico for a mesma faixa e tiver menos de 30 minutos, só o
horário é atualizado (o "repetir faixa" não inunda o histórico). Implementada em
`domain/usecase/HistoryPolicy.kt` e `playback/ListenTracker.kt`.

## 7. Testes executados e resultados

Todos executados neste ambiente (Linux, emulador Android 15 / API 35, x86_64, 1080×2400).

| Conjunto | Testes | Resultado |
|---|---|---|
| Unitários JVM (`testDebugUnitTest`) | 60 | **60 aprovados, 0 falhas** |
| Instrumentados (`connectedDebugAndroidTest`) | 34 | **34 aprovados, 0 falhas** |
| Lint (`lintRelease`) | — | 0 erros (44 avisos, ver relatório) |

Unitários: `LibraryMergerTest` (biblioteca vazia, arquivos válidos, metadados ausentes, duplicados,
removidos, movidos/renomeados, reaparecimento, escopo que falhou, pasta revogada), `ScannedTrackTest`,
`TrackSorterTest`, `SearchEngineTest`, `LibraryGroupingTest`, `HistoryPolicyTest`, `ListenTrackerTest`,
`ShuffleOrdersTest`, `QueueSnapshotTest`, `FadeCalculatorTest`, `LyricsTest`, `BackupCodecTest`,
`TextNormalizerTest`, `ListReorderTest`.

Instrumentados:
- `PlaybackServiceTest` (serviço real + ExoPlayer + arquivos WAV reais): play/pause/busca/próxima/
  anterior; fim da faixa e avanço automático; repetir faixa; repetir fila; aleatório começa pela
  faixa escolhida sem alterar a ordem original; mover/remover/limpar fila; arquivo corrompido pulado;
  arquivo inexistente reporta erro; perda de foco de áudio pausa; teclas de mídia do sistema
  (`AudioManager.dispatchMediaKeyEvent`) pausam/retomam; temporizador "fim da faixa"; fila de
  3.000 itens.
- `PersistenceTest` (Room + DataStore + repositórios reais): CRUD/reordenação de playlist, nome
  vazio rejeitado, playlist mantém faixa indisponível, favoritos, regra do histórico, backup ida e
  volta, banco sobrevive a fechar/reabrir, preferências sobrevivem a nova instância do DataStore,
  pasta sem permissão marcada como inacessível, varredura real do MediaStore sem duplicar ao repetir.
- `UiFlowTest` (app completo): navegação inferior, pesquisa com resultado e estado vazio, abas
  Álbuns/Artistas, tocar → mini player → player completo → pausar → minimizar, criar playlist,
  abrir configurações.
- `ComponentUiTest`: tela de permissão (normal e negada), estado vazio, mini player com fonte 2× em
  320 dp, faixa indisponível sinalizada, player completo em 320×560 dp com fonte 1,6× (todos os
  controles alcançáveis).

Validação manual no emulador (comandos `adb`, capturas em `docs/screenshots/`):
- APK de depuração **e** APK de lançamento instalados, abertos, músicas reais tocadas
  (sessão do sistema em `PLAYING` com título/artista/álbum corretos).
- Permissão negada duas vezes → tela "Abrir configurações"; concedida → biblioteca carregada.
- 13 arquivos reais indexados (MP3 com tags/capa/letra, MP3 sem tags, WAV, Opus, MP3 de jogo,
  arquivo corrompido). Capas embutidas exibidas.
- Segundo plano: app na tela inicial continua tocando; **tela bloqueada por 10 s continua tocando e
  avança para a próxima faixa**; notificação de mídia presente; teclas de mídia pause/play/next.
- Retomar pela notificação com o app em segundo plano e pausado.
- **Modo avião**: biblioteca, reprodução, favoritos, criação de playlist, fila — tudo funcionou;
  após `force-stop` e reabertura (ainda offline) playlist com 3 faixas e favorito estavam salvos e a
  fila foi restaurada na posição.
- Arrastar na fila reordenou as faixas; arquivo corrompido na playlist mostrou "Formato de arquivo
  não suportado. Pulando para a próxima." e tocou a seguinte.
- Pasta `Download/Extras` adicionada via SAF; as 2 cópias foram reconhecidas como duplicatas
  (banco: `FOLDER|duplicate=1|2`), a contagem continuou 13.
- Equalizador: efeitos anexados à sessão de áudio do player (confirmado em `dumpsys media.audio_flinger`).

## 8. Erros encontrados e correções

| Problema | Correção |
|---|---|
| Textos escuros sobre o tema escuro na 1ª tela (sem `Surface` na raiz) | Raiz envolvida em `Surface` com a cor de fundo do tema |
| Ícones da barra de status escuros no tema escuro | `enableEdgeToEdge` reconfigurado conforme o tema efetivo |
| Conteúdo aparecendo atrás da barra inferior transparente | Gradiente mais curto e fundo quase opaco |
| **Android 15 nega foco de áudio a apps em segundo plano** (`HardeningEnforcer: Focus request DENIED`) — testes não tocavam | Testes passam a iniciar a reprodução com a Activity visível (como o usuário); widget trocado para o mesmo mecanismo da notificação (intent de botão de mídia via serviço em primeiro plano), validado com retomada pela notificação |
| SystemUI não conseguia oferecer "retomar" (`Subscribe error ... MediaLibraryService`) | Serviço convertido para `MediaLibraryService` expondo só a última faixa |
| Lint: APIs instáveis do Media3 sem `@OptIn`, constante errada de `SessionResult`, BOM no código, `produceState` | Todos corrigidos; lint sem erros |
| `ACCESS_NETWORK_STATE` herdado do Media3 | Removido no manifesto (`tools:node="remove"`) |
| Testes com expectativa errada (dados de duplicata, fluxo com valor em cache, seletor ambíguo, repetição) | Testes corrigidos e reexecutados |
| Emulador encerrado por falta de memória durante o build R8 (7 GB de RAM) | Daemons do Gradle parados, emulador reiniciado com 1,8 GB; testes refeitos |

## 9. O que NÃO foi validado / limitações

- **Aparelho físico**: todo o teste foi em emulador Android 15. Não foi testado em Android 8–14.
- **Bluetooth real, troca de saída de áudio e reconexão de fones**: o emulador não tem saída
  Bluetooth; `ACTION_AUDIO_BECOMING_NOISY` é protegido e não pode ser enviado por teste. O
  comportamento vem da configuração do ExoPlayer (`setHandleAudioBecomingNoisy(true)`); o caminho
  das teclas de mídia foi validado via sessão do sistema.
- **Chamada telefônica real**: validada indiretamente (outro app tomando o foco de áudio pausa o
  Sonora); não foi feita uma ligação.
- **Widget**: implementado, mas não foi posicionado na tela inicial do emulador (não automatizável
  com as ferramentas disponíveis); usa o mesmo mecanismo da notificação, que foi validado.
- **Android Auto**: não implementado.
- **Sessões longas e memória**: maior teste contínuo foi de alguns minutos e fila de 3.000 itens;
  não houve perfil de memória nem sessão de horas.
- **Tablets/telas grandes**: layout adaptável (grade de álbuns adaptativa, largura máxima no
  player), mas só testado em 1080×2400 e em 320 dp nos testes de componente.
- **Pesquisa** é feita em memória; foi pensada para bibliotecas de alguns milhares de faixas, não
  medida com dezenas de milhares.
- O teste `mediaStoreScan_findsRealFiles...` e os de `UiFlowTest` dependem dos arquivos de exemplo
  no aparelho (`tools/make_test_audio.py`).

## 10. Compilar, instalar e executar

Ver [README.md](../README.md). Resumo:

```bash
cd "/home/diego/Documentos/apk musica "
export JAVA_HOME=~/Android/jdk-21 ANDROID_HOME=~/Android/Sdk
./gradlew assembleRelease testDebugUnitTest
adb install -r entrega/Sonora-1.0.0-release.apk
```

Ao abrir: permitir acesso às músicas (ou escolher uma pasta) → a biblioteca é montada
automaticamente. Versão mínima Android 8.0 (API 26); alvo Android 15 (API 35).
