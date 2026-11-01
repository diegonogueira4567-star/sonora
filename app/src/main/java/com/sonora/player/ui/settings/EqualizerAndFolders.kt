package com.sonora.player.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.sonora.player.data.local.preferences.EqualizerSettings
import com.sonora.player.ui.LocalAppContainer
import com.sonora.player.ui.components.ConfirmDialog
import com.sonora.player.ui.components.EmptyState
import com.sonora.player.ui.components.songsLabel
import com.sonora.player.ui.library.LibraryViewModel
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun EqualizerScreen(vm: SettingsViewModel, onBack: () -> Unit, contentPadding: PaddingValues) {
    val effects = LocalAppContainer.current.audioEffects
    val caps by effects.capabilities.collectAsState()
    val settings by vm.settings.collectAsState()
    val eq = settings.equalizer
    val levels = remember { mutableStateListOf<Int>() }
    // Show real band positions (after a preset the device decides the levels).
    LaunchedEffect(caps, eq.preset, eq.enabled) {
        delay(50)
        val c = caps ?: return@LaunchedEffect
        val current = if (eq.preset < 0 && eq.bandLevels.size == c.bands.size) eq.bandLevels else effects.currentBandLevels()
        levels.clear()
        levels.addAll(current ?: List(c.bands.size) { 0 })
    }
    fun save(new: EqualizerSettings) = vm.update { setEqualizer(new) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
        item { TopBar("Equalizador", onBack) }
        val c = caps
        when {
            c == null -> item {
                EmptyState(Icons.Rounded.GraphicEq, "Reprodutor ainda não iniciado", "Abra o app com o serviço de reprodução ativo para ajustar o equalizador.")
            }
            !c.equalizerAvailable -> item {
                EmptyState(Icons.Rounded.GraphicEq, "Equalizador indisponível", c.error ?: "Este aparelho não oferece efeitos de áudio para apps.")
            }
            else -> {
                item { SwitchRow("Ativar equalizador", "Os ajustes alteram o áudio em tempo real", eq.enabled) { save(eq.copy(enabled = it)) } }
                item {
                    Text("Predefinições", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(start = 16.dp, top = 12.dp))
                    LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 8.dp)) {
                        item {
                            FilterChip(selected = eq.preset < 0, enabled = eq.enabled, onClick = { save(eq.copy(preset = -1, bandLevels = levels.toList())) }, label = { Text("Personalizado") })
                        }
                        itemsIndexed(c.presets) { i, name ->
                            FilterChip(selected = eq.preset == i, enabled = eq.enabled, onClick = { save(eq.copy(preset = i)) }, label = { Text(name) })
                        }
                    }
                }
                itemsIndexed(c.bands) { i, band ->
                    val value = levels.getOrNull(i) ?: 0
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 2.dp)) {
                        Row {
                            Text(formatHz(band.centerHz), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(String.format(Locale.ROOT, "%+.1f dB", value / 100f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Slider(
                            value = value.toFloat(),
                            onValueChange = { if (i < levels.size) levels[i] = it.toInt() },
                            onValueChangeFinished = { save(eq.copy(preset = -1, bandLevels = levels.toList())) },
                            valueRange = c.minLevel.toFloat()..c.maxLevel.toFloat(),
                            enabled = eq.enabled,
                            modifier = Modifier.semantics { contentDescription = "Banda ${formatHz(band.centerHz)}" },
                        )
                    }
                }
                if (c.bassBoostAvailable) {
                    item {
                        SliderRow("Reforço de graves", "${eq.bassBoost / 10}%", eq.bassBoost.toFloat(), 0f..1000f, 9) { save(eq.copy(bassBoost = it.toInt())) }
                    }
                }
                item {
                    InfoRow("Sobre", "Os efeitos são fornecidos pelo próprio Android do aparelho e se aplicam somente ao Sonora.")
                }
            }
        }
    }
}

private fun formatHz(hz: Int): String = if (hz >= 1000) String.format(Locale.ROOT, "%.1f kHz", hz / 1000f).replace(".0 ", " ") else "$hz Hz"

@Composable
fun FoldersScreen(vm: LibraryViewModel, onBack: () -> Unit, contentPadding: PaddingValues) {
    val folders by vm.folders.collectAsState()
    var removing by rememberSaveable { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) vm.addFolder(uri.toString())
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
        item { TopBar("Pastas selecionadas", onBack) }
        item {
            InfoRow(
                "Como funciona",
                "Além das músicas encontradas automaticamente, você pode escolher pastas específicas (inclusive em cartão SD). " +
                    "O Sonora pede apenas permissão de leitura, examina as subpastas e nunca altera os arquivos. " +
                    "O Android não permite selecionar algumas pastas do sistema (como Download raiz ou Android/data).",
            )
        }
        item {
            Button(onClick = { picker.launch(null) }, modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Rounded.CreateNewFolder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Adicionar pasta")
            }
        }
        if (folders.isEmpty()) {
            item { Text("Nenhuma pasta adicionada.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        items(folders, key = { it.treeUri }) { f ->
            Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (f.accessible) Icons.Rounded.Folder else Icons.Rounded.FolderOff, contentDescription = null,
                    tint = if (f.accessible) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.displayName, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        if (f.accessible) songsLabel(f.trackCount) else "Acesso revogado — remova e adicione a pasta novamente",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (f.accessible) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                    )
                }
                IconButton(onClick = { removing = f.treeUri }) { Icon(Icons.Rounded.Delete, contentDescription = "Remover pasta ${f.displayName}") }
            }
        }
    }
    removing?.let { uri ->
        val name = folders.firstOrNull { it.treeUri == uri }?.displayName ?: "pasta"
        ConfirmDialog(
            title = "Remover \"$name\"?",
            message = "As músicas desta pasta deixam de aparecer na biblioteca. Os arquivos não são apagados e as playlists são mantidas.",
            confirmLabel = "Remover",
            onConfirm = { vm.removeFolder(uri); removing = null },
            onDismiss = { removing = null },
        )
    }
}

