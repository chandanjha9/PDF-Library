package com.example.pdflibrary.ui.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pdflibrary.BuildConfig
import com.example.pdflibrary.data.UserProfile
import com.example.pdflibrary.theme.Success
import com.example.pdflibrary.ui.components.AppCard
import com.example.pdflibrary.ui.components.appTextFieldColors
import kotlinx.coroutines.delay

@Composable
fun ProfileScreen(
    onReplayTour: () -> Unit,
    viewModel: ProfileViewModel = viewModel(factory = ProfileViewModel.Factory),
) {
    val saved by viewModel.profile.collectAsStateWithLifecycle()
    val serverStatus by viewModel.serverStatus.collectAsStateWithLifecycle()
    val savedServerUrl by viewModel.serverUrl.collectAsStateWithLifecycle()
    var serverInput by rememberSaveable(savedServerUrl) { mutableStateOf(savedServerUrl) }
    var serverInputError by remember { mutableStateOf(false) }

    var name by rememberSaveable(saved) { mutableStateOf(saved.name) }
    var bio by rememberSaveable(saved) { mutableStateOf(saved.bio) }
    var avatar by rememberSaveable(saved) { mutableStateOf(saved.avatar) }
    var justSaved by remember { mutableStateOf(false) }
    val dirty = name.trim() != saved.name || bio.trim() != saved.bio || avatar != saved.avatar

    LaunchedEffect(justSaved) {
        if (justSaved) { delay(2_000); justSaved = false }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Profile", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground, modifier = Modifier.fillMaxWidth())
        Text("How you appear in the app", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(24.dp))

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            modifier = Modifier.size(96.dp),
        ) {
            Box(contentAlignment = Alignment.Center) { Text(avatar, fontSize = 44.sp) }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            UserProfile.AVATARS.forEach { av ->
                val selected = av == avatar
                Surface(
                    onClick = { avatar = av },
                    shape = CircleShape,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    modifier = Modifier.size(44.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) { Text(av, fontSize = 20.sp) }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 40) name = it },
                    label = { Text("Display name") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = appTextFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = bio,
                    onValueChange = { if (it.length <= 80) bio = it },
                    label = { Text("Reading status") },
                    placeholder = { Text("e.g. Comic fan, preparing for boards") },
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = appTextFieldColors(),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = {
                        viewModel.save(UserProfile(name, bio, avatar))
                        justSaved = true
                    },
                    enabled = dirty,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                ) {
                    if (justSaved && !dirty) {
                        Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Saved")
                    } else {
                        Text("Save changes")
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Server", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    StatusDot(serverStatus)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        when (serverStatus) {
                            ServerStatus.Checking -> "Checking…"
                            ServerStatus.Online -> "Online"
                            ServerStatus.Offline -> "Unreachable"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    IconButton(onClick = viewModel::checkServer, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "Check server again", modifier = Modifier.size(18.dp))
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "Leave empty to use the built-in addresses. If the backend runs on your PC, enter its Wi-Fi IP (run ipconfig on the PC), e.g. 192.168.1.5:3000, or your ngrok URL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = serverInput,
                    onValueChange = { serverInput = it; serverInputError = false },
                    label = { Text("Server address") },
                    placeholder = { Text("192.168.1.5:3000") },
                    isError = serverInputError,
                    supportingText = if (serverInputError) { { Text("Not a valid address") } } else null,
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    colors = appTextFieldColors(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                FilledTonalButton(
                    onClick = { serverInputError = !viewModel.saveServerUrl(serverInput) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save & test connection") }
                if (serverStatus == ServerStatus.Offline) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Tried: " + viewModel.addressesTried().joinToString(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        AppCard(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(18.dp)) {
                Text("About", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))
                InfoRow("Catalogue", "Cloud PDF library")
                InfoRow("Reading pass", "20 minutes per request")
                InfoRow("App version", BuildConfig.VERSION_NAME)
            }
        }

        Spacer(Modifier.height(16.dp))

        OutlinedButton(
            onClick = onReplayTour,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Replay app tour")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun StatusDot(status: ServerStatus) {
    val color = when (status) {
        ServerStatus.Checking -> MaterialTheme.colorScheme.onSurfaceVariant
        ServerStatus.Online -> Success
        ServerStatus.Offline -> MaterialTheme.colorScheme.error
    }
    Surface(shape = CircleShape, color = color, modifier = Modifier.size(8.dp)) {}
}

