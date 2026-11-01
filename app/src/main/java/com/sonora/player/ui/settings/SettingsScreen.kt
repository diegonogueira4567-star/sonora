package com.sonora.player.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.sonora.player.BuildConfig
import com.sonora.player.SonoraApp
import com.sonora.player.data.local.preferences.AppSettings
import com.sonora.player.data.local.preferences.SettingsStore
import com.sonora.player.domain.model.SortField
import com.sonora.player.domain.model.ThemeMode
import com.sonora.player.navigation.Routes
import com.sonora.player.playback.PlaybackController
import com.sonora.player.ui.components.ConfirmDialog
import com.sonora.player.ui.library.LibraryViewModel
import com.sonora.player.ui.player.SleepTimerDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsViewModel(val store: SettingsStore) : ViewModel() {
    val settings: StateFlow<AppSettings> = store.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    fun update(block: suspend SettingsStore.() -> Unit) {
        viewModelScope.launch { store.block() }
    }

    /** Runs [then] only after the preference was written (e.g. a rescan that depends on it). */
    fun updateThen(block: suspend SettingsStore.() -> Unit, then: () -> Unit) {
        viewModelScope.launch {
            store.block()
            then()
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as SonoraApp
                SettingsViewModel(app.container.settingsStore)
            }
        }
    }
}

@Composable
fun SettingsScreen(
    vm: SettingsViewModel,
    library: LibraryViewModel,
    playback: PlaybackController,
    navigate: (String) -> Unit,
    onBack: () -> Unit,
    onRequestPermission: () -> Unit,
    contentPadding: PaddingValues,
) {
    val s by vm.settings.collectAsState()
    val playerState by playback.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirm by rememberSaveable { mutableStateOf<String?>(null) }
    var showSleep by rememberSaveable { mutableStateOf(false) }
    val hasPermission = library.hasAudioPermission()

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val ok = runCatching {
                val json = library.exportBackup()
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray()) } ?: error("sem saída") }
            }.isSuccess
            library.message(if (ok) "Backup salvo" else "Não foi possível salvar o backup")
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() } }.getOrNull()
            }
            if (text == null) library.message("Não foi possível ler o arquivo") else library.importBackup(text)
        }
    }

    LazyColumn(Modifier.fillMaxSize().testTag("settings_list"), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
        item { TopBar("Configurações", onBack) }

        item { Group("Aparência") }
        item {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                Text("Tema", style = MaterialTheme.typography.bodyLarge)
                Spacer(Modifier.size(8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val options = listOf(ThemeMode.DARK to "Escuro", ThemeMode.LIGHT to "Claro", ThemeMode.SYSTEM to "Sistema")
                    options.forEachIndexed { i, (mode, label) ->
                        SegmentedButton(
                            selected = s.themeMode == mode,
                            onClick = { vm.update { setThemeMode(mode) } },
                            shape = SegmentedButtonDefaults.itemShape(i, options.size),
                        ) { Text(label) }
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(vertical = 8.dp)) {
                Text("Cor de destaque", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(horizontal = 16.dp))
                LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 8.dp)) {
                    items(AppSettings.ACCENTS) { c ->
                        val selected = s.accentColor == c && !s.dynamicColor
                        Box(
                            Modifier.size(44.dp).background(Color(c), CircleShape)
                                .border(if (selected) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                                .clickable { vm.update { setAccentColor(c); setDynamicColor(false) } }
                                .semantics { role = Role.RadioButton; this.selected = selected; contentDescription = "Cor ${AppSettings.ACCENTS.indexOf(c) + 1}" },
                            contentAlignment = Alignment.Center,
                        ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.Black) }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            item { SwitchRow("Cores do papel de parede", "Usa as cores dinâmicas do Android (Material You)", s.dynamicColor) { v -> vm.update { setDynamicColor(v) } } }
        }

        item { Group("Biblioteca") }
        item {
            NavRow(
                "Acesso às músicas do aparelho",
                if (hasPermission) "Permitido" else "Não permitido — toque para conceder",
            ) {
                if (hasPermission) {
                    context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
                } else onRequestPermission()
            }
        }
        item { NavRow("Pastas selecionadas", "Adicionar ou remover pastas monitoradas") { navigate(Routes.FOLDERS) } }
        item { NavRow("Atualizar biblioteca agora", "Procura arquivos novos, removidos ou alterados") { library.rescan(userInitiated = true) } }
        item {
            val labels = mapOf(SortField.TITLE to "Nome", SortField.ARTIST to "Artista", SortField.ALBUM to "Álbum", SortField.DURATION to "Duração", SortField.DATE_ADDED to "Data de adição")
            var open by remember { mutableStateOf(false) }
            Box {
                NavRow("Ordenação padrão", "${labels[s.defaultSort.field]} • ${if (s.defaultSort.ascending) "crescente" else "decrescente"}") { open = true }
                androidx.compose.material3.DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    labels.forEach { (field, label) ->
                        listOf(true, false).forEach { asc ->
                            androidx.compose.material3.DropdownMenuItem(
                                text = { Text("$label ${if (asc) "↑" else "↓"}") },
                                onClick = { vm.update { setDefaultSort(com.sonora.player.domain.model.SortOrder(field, asc)) }; open = false },
                            )
                        }
                    }
                }
            }
        }
        item { SwitchRow("Ocultar duplicadas", "Mostra só uma cópia do mesmo arquivo encontrado em locais diferentes", s.hideDuplicates) { v -> vm.update { setHideDuplicates(v) } } }
        item {
            SwitchRow("Incluir outros áudios", "Inclui gravações e áudios não marcados como música pelo Android", s.includeNonMusicAudio) { v ->
                vm.updateThen({ setIncludeNonMusicAudio(v) }) { library.rescan(userInitiated = false, force = true) }
            }
        }
        item {
            SliderRow("Ignorar arquivos curtos", if (s.minDurationSec == 0) "Mostrar todos" else "Menos de ${s.minDurationSec} s", s.minDurationSec.toFloat(), 0f..60f, 11) { v ->
                vm.update { setMinDurationSec(v.toInt()) }
            }
        }

        item { Group("Reprodução") }
        item { NavRow("Equalizador", "Ajustes de frequência e reforço de graves do aparelho") { navigate(Routes.EQUALIZER) } }
        item {
            val active = playerState.sleepEndsAtMs > 0 || playerState.sleepAtEndOfTrack
            NavRow("Temporizador", if (active) "Ativo" else "Parar a reprodução após um tempo") { showSleep = true }
        }
        item {
            SliderRow(
                "Transição suave entre faixas",
                if (s.fadeDurationMs == 0) "Desativada (reprodução sem intervalos)" else "Fade de ${s.fadeDurationMs / 1000} s ao trocar de faixa",
                (s.fadeDurationMs / 1000).toFloat(), 0f..12f, 11,
            ) { v -> vm.update { setFadeDurationMs(v.toInt() * 1000) } }
        }
        item {
            InfoRow(
                "Reprodução sem intervalos",
                "Ativa automaticamente quando a transição suave está desligada. Funciona em arquivos que informam o atraso do codificador " +
                    "(MP3 com cabeçalho LAME/Xing, AAC/M4A com iTunSMPB, FLAC, WAV). Outros formatos podem ter uma pequena pausa entre faixas.",
            )
        }
        item { SwitchRow("Retomar a última fila", "Ao abrir o app, prepara a fila e a posição da última sessão (sem tocar sozinho)", s.restoreQueue) { v -> vm.update { setRestoreQueue(v) } } }
        item { SwitchRow("Pular faixas com erro", "Avança para a próxima quando um arquivo não pode ser reproduzido", s.skipOnError) { v -> vm.update { setSkipOnError(v) } } }

        item { Group("Gestos") }
        item { SwitchRow("Deslizar no mini player", "Deslize para os lados para trocar de faixa", s.swipeMiniPlayer) { v -> vm.update { setSwipeMiniPlayer(v) } } }
        item { SwitchRow("Deslizar na capa", "No reprodutor, deslize a capa para trocar de faixa", s.swipeArtwork) { v -> vm.update { setSwipeArtwork(v) } } }
        item { SwitchRow("Deslizar para baixo para minimizar", "Arraste o reprodutor para baixo para fechá-lo", s.swipeDownToClose) { v -> vm.update { setSwipeDownToClose(v) } } }
        item { InfoRow("Botões sempre disponíveis", "Todas as ações de gesto também têm botões equivalentes na tela.") }

        item { Group("Dados e privacidade") }
        item { SwitchRow("Registrar histórico", "Guarda as músicas tocadas apenas neste aparelho", s.historyEnabled) { v -> vm.update { setHistoryEnabled(v) } } }
        item { NavRow("Limpar histórico", "Apaga a lista de músicas tocadas") { confirm = "history" } }
        item { NavRow("Zerar estatísticas", "Apaga contagens de reprodução e tempo ouvido") { confirm = "stats" } }
        item {
            NavRow("Fazer backup das playlists", "Salva playlists e favoritas em um arquivo JSON (sem músicas)") {
                val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(Date())
                exportLauncher.launch("sonora-backup-$stamp.json")
            }
        }
        item { NavRow("Restaurar backup", "Importa playlists e favoritas de um arquivo de backup") { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) } }

        item { Group("Sobre") }
        item {
            InfoRow(
                "Sonora ${BuildConfig.VERSION_NAME}",
                "Reprodutor de música local. Funciona sem internet, sem conta e sem anúncios. " +
                    "O app não envia nomes de arquivos, histórico nem playlists para nenhum servidor e nunca altera ou apaga suas músicas.",
            )
        }
    }

    when (confirm) {
        "history" -> ConfirmDialog("Limpar histórico?", "O histórico de reprodução será apagado.", "Limpar",
            onConfirm = { library.clearHistory(); confirm = null }, onDismiss = { confirm = null })
        "stats" -> ConfirmDialog("Zerar estatísticas?", "As contagens de reprodução serão apagadas.", "Zerar",
            onConfirm = { library.clearStats(); confirm = null }, onDismiss = { confirm = null })
    }
    if (showSleep) SleepTimerDialog(playerState, playback, onDismiss = { showSleep = false })
}

@Composable
fun TopBar(title: String, onBack: () -> Unit) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Voltar") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun Group(title: String) {
    Column {
        HorizontalDivider(Modifier.padding(top = 12.dp), color = MaterialTheme.colorScheme.outlineVariant)
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp))
    }
}

@Composable
fun NavRow(title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(role = Role.Switch) { onChange(!checked) }.heightIn(min = 56.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun SliderRow(title: String, subtitle: String, value: Float, range: ClosedFloatingPointRange<Float>, steps: Int, onChange: (Float) -> Unit) {
    var local by remember(value) { mutableFloatStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Slider(value = local, onValueChange = { local = it }, onValueChangeFinished = { onChange(local) }, valueRange = range, steps = steps,
            modifier = Modifier.semantics { contentDescription = title })
    }
}

@Composable
fun InfoRow(title: String, text: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
