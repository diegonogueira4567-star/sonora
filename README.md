# Sonora — reprodutor de música offline para Android

Reprodutor de músicas armazenadas no próprio celular. Kotlin + Jetpack Compose + Media3/ExoPlayer.
Funciona sem internet, sem conta, sem anúncios e sem rastreamento — o APK de lançamento **não declara
a permissão `INTERNET`**.

[![Baixar APK](https://img.shields.io/badge/Baixar-Sonora.apk-1ED2A4?style=for-the-badge&logo=android&logoColor=white)](https://github.com/diegonogueira4567-star/sonora/releases/latest/download/Sonora.apk)
[![Licença MIT](https://img.shields.io/badge/licen%C3%A7a-MIT-blue?style=for-the-badge)](LICENSE)

## ⬇️ Baixar

**[Baixar Sonora.apk (versão mais recente)](https://github.com/diegonogueira4567-star/sonora/releases/latest/download/Sonora.apk)**
— Android 8.0 ou superior, 2,6 MB. Todas as versões: [Releases](https://github.com/diegonogueira4567-star/sonora/releases).

Para instalar no celular: abra o link acima no próprio aparelho, baixe o arquivo e toque nele.
Na primeira vez o Android pede para permitir a instalação de apps desta fonte ("instalar apps
desconhecidos") — permita apenas para o navegador ou gerenciador de arquivos que você usou.

- Relatório da versão 1.1.0 (bugs, desempenho, adaptação): [docs/RELATORIO-OTIMIZACAO.md](docs/RELATORIO-OTIMIZACAO.md)
- Relatório da versão 1.0.0 (arquitetura, funcionalidades): [docs/RELATORIO.md](docs/RELATORIO.md)
- Capturas de tela: [docs/screenshots](docs/screenshots)

## Requisitos de compilação

| Ferramenta | Versão usada |
|---|---|
| JDK | 21 (Temurin 21.0.13) — `~/Android/jdk-21` |
| Gradle | 8.10.2 (wrapper incluído) |
| Android Gradle Plugin | 8.7.3 |
| Kotlin | 2.0.21 (KSP 2.0.21-1.0.28) |
| Android SDK | compileSdk/targetSdk 35, build-tools 35.0.0 |

## Compilar

```bash
export JAVA_HOME=~/Android/jdk-21 ANDROID_HOME=~/Android/Sdk
./gradlew assembleDebug          # app/build/outputs/apk/debug/app-debug.apk
./gradlew assembleRelease        # app/build/outputs/apk/release/app-release.apk
```

### Assinatura do APK de lançamento

Nenhuma chave fica no repositório. O build lê quatro propriedades de `~/.gradle/gradle.properties`
(ou variáveis de ambiente):

```
SONORA_STORE_FILE=/caminho/para/sonora-release.jks
SONORA_STORE_PASSWORD=...
SONORA_KEY_ALIAS=sonora
SONORA_KEY_PASSWORD=...
```

Neste computador o keystore foi criado em `~/.android-keystores/sonora-release.jks` e as
propriedades foram gravadas em `~/.gradle/gradle.properties` (permissão 600). **Faça backup desse
keystore**: atualizações futuras do app precisam ser assinadas com a mesma chave. Sem as
propriedades, o `assembleRelease` gera um APK não assinado.

## Testes

```bash
./gradlew testDebugUnitTest                 # 60 testes JVM
./gradlew connectedDebugAndroidTest         # 36 testes em emulador/aparelho
```

Os testes instrumentados de interface esperam os arquivos de áudio de exemplo no aparelho:

```bash
python3 tools/make_test_audio.py /tmp/sonora-audio
adb shell mkdir -p /sdcard/Music/Sonora
adb push /tmp/sonora-audio/. /sdcard/Music/Sonora/
adb shell content call --uri content://media --method scan_volume --arg external_primary
```

Em máquinas com 8 GB de RAM, compile com o emulador desligado ou rode os testes instrumentados com
`adb shell am instrument -w com.sonora.player.debug.test/androidx.test.runner.AndroidJUnitRunner`
depois de `./gradlew installDebug installDebugAndroidTest` e `./gradlew --stop`.

## Medir desempenho

```bash
python3 tools/make_large_library.py /tmp/sonora-grande 1500
adb shell mkdir -p /sdcard/Music/Grande
adb push /tmp/sonora-grande/. /sdcard/Music/Grande/
tools/measure.sh rotulo
```

## Instalar pelo computador

```bash
adb install -r Sonora.apk
```

Android 8.0 (API 26) ou superior.

## Licença

Distribuído sob a licença MIT — veja [LICENSE](LICENSE).
