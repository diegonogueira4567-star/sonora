package com.sonora.player.ui.permissions

import android.Manifest
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

val AUDIO_PERMISSION: String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

/**
 * Explains why access is needed before asking. [permanentlyDenied] switches the primary action
 * to the system settings page, the only place the user can still grant it.
 */
@Composable
fun PermissionScreen(
    permanentlyDenied: Boolean,
    onGrant: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFolder: () -> Unit,
    onSkip: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f), MaterialTheme.colorScheme.background)))
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp)
            .testTag("permission_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.size(32.dp))
        Box(Modifier.size(96.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.GraphicEq, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.size(24.dp))
        Text("Bem-vindo ao Sonora", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.size(8.dp))
        Text(
            "Para montar sua biblioteca, o Sonora precisa ler os arquivos de áudio guardados neste aparelho.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(24.dp))
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth()) {
            Point(Icons.Rounded.Lock, "Somente leitura de áudio", "Não pedimos acesso a fotos, contatos, localização ou microfone.")
            Point(Icons.Rounded.CloudOff, "Tudo fica no aparelho", "Nenhum dado é enviado para a internet. Não há conta nem anúncios.")
            Point(Icons.Rounded.FolderOpen, "Prefere escolher pastas?", "Você pode selecionar só as pastas que quiser, sem conceder acesso geral.")
        }
        Spacer(Modifier.size(32.dp))
        if (permanentlyDenied) {
            Text(
                "A permissão foi negada. Para liberar, abra as configurações do app > Permissões > Música e áudio.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 12.dp),
            )
            Button(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Abrir configurações do app") }
        } else {
            Button(onClick = onGrant, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("grant_permission")) { Text("Permitir acesso às músicas") }
        }
        Spacer(Modifier.size(10.dp))
        OutlinedButton(onClick = onPickFolder, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) { Text("Escolher uma pasta") }
        TextButton(onClick = onSkip, modifier = Modifier.padding(top = 4.dp).testTag("skip_permission")) { Text("Agora não") }
    }
}

@Composable
private fun Point(icon: ImageVector, title: String, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
