package com.example.pdflibrary.ui.detail

import android.content.Intent
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.CurrencyRupee
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SmartDisplay
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.pdflibrary.data.model.Book
import com.example.pdflibrary.data.model.isPremium
import com.example.pdflibrary.theme.Amber
import com.example.pdflibrary.theme.OnAmber
import com.example.pdflibrary.theme.Success
import com.example.pdflibrary.ui.components.appTextFieldColors
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import com.example.pdflibrary.premium.RewardedAds
import com.example.pdflibrary.premium.UpiPayment
import androidx.compose.ui.platform.LocalContext
import com.example.pdflibrary.theme.coverGradientFor
import com.example.pdflibrary.ui.common.Formatters
import com.example.pdflibrary.ui.components.BookCover
import com.example.pdflibrary.ui.components.EmptyState
import com.example.pdflibrary.ui.components.MetaPill

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    bookId: Int,
    onBack: () -> Unit,
    onOpenPdf: (localPath: String) -> Unit,
) {
    // Keyed per book: combined with the nav-entry ViewModelStore this guarantees
    // each book gets its own ViewModel (previously the first book's ViewModel was
    // reused for every book opened afterwards).
    val viewModel: DetailViewModel = viewModel(
        key = "detail_$bookId",
        factory = DetailViewModel.factory(bookId),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    val snackbar = remember { SnackbarHostState() }

    // UPI app returns here; its reply is in the "response" extra (may be missing).
    val upiLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        viewModel.onUpiResult(result.data?.getStringExtra("response"))
    }

    val payWithUpi: () -> Unit = payWithUpi@{
        if (!viewModel.contactValidOrWarn()) return@payWithUpi
        val txnRef = UpiPayment.newTxnRef(bookId)
        val intent = UpiPayment.intent(bookId, txnRef)
        if (UpiPayment.hasUpiApp(context, intent)) {
            viewModel.onUpiStarted(txnRef)
            upiLauncher.launch(Intent.createChooser(intent, "Pay ₹10 with"))
        } else {
            viewModel.showMessage("No UPI app found. Install Google Pay, PhonePe, Paytm or BHIM.")
        }
    }

    val watchAd: () -> Unit = watchAd@{
        if (!viewModel.contactValidOrWarn()) return@watchAd
        if (activity == null) {
            viewModel.showMessage("Ads aren't available right now.")
        } else {
            viewModel.showMessage("Loading ad…")
            RewardedAds.show(
                activity = activity,
                onRewarded = viewModel::onAdRewarded,
                onDismissedWithoutReward = { viewModel.showMessage("Watch the full ad to unlock this book.") },
                onError = viewModel::showMessage,
            )
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            viewModel.messageShown()
        }
    }

    if (state.askPaymentConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.confirmPayment(false) },
            title = { Text("Did the payment go through?") },
            text = { Text("Your UPI app didn't report the result. If ₹10 was debited, tap \"Yes, I paid\" to unlock this book.") },
            confirmButton = { TextButton(onClick = { viewModel.confirmPayment(true) }) { Text("Yes, I paid") } },
            dismissButton = { TextButton(onClick = { viewModel.confirmPayment(false) }) { Text("No") } },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state.book != null) {
                        IconButton(onClick = viewModel::toggleFavorite) {
                            Icon(
                                imageVector = if (state.isFavorite) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = if (state.isFavorite) "Remove from saved" else "Save to library",
                                tint = if (state.isFavorite) Amber else LocalContentColor.current,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0f),
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        val book = state.book
        when {
            state.isLoading && book == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            book == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = "Couldn't load this book",
                    message = state.errorMessage ?: "Please try again.",
                    actionLabel = "Try again",
                    onAction = viewModel::loadBook,
                )
            }
            else -> DetailContent(
                book = book,
                state = state,
                topPadding = padding.calculateTopPadding(),
                onDownload = viewModel::download,
                onCancel = viewModel::cancelDownload,
                onOpenPdf = onOpenPdf,
                onPayUpi = payWithUpi,
                onWatchAd = watchAd,
                onRetryPremium = viewModel::checkPremium,
                onContactChange = viewModel::onContactChange,
            )
        }
    }
}

@Composable
private fun DetailContent(
    book: Book,
    state: DetailUiState,
    topPadding: androidx.compose.ui.unit.Dp,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpenPdf: (String) -> Unit,
    onPayUpi: () -> Unit,
    onWatchAd: () -> Unit,
    onRetryPremium: () -> Unit,
    onContactChange: (String) -> Unit,
) {
    val (tint, _) = coverGradientFor(book.id)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Hero: tinted backdrop fading into the page, cover centred.
        Box(
            Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(tint.copy(alpha = 0.45f), MaterialTheme.colorScheme.background)))
                .padding(top = topPadding + 8.dp, bottom = 24.dp),
            contentAlignment = Alignment.Center,
        ) {
            BookCover(
                bookId = book.id,
                title = book.title,
                coverUrl = book.coverUrl,
                premium = book.isPremium,
                cornerRadius = 12.dp,
                modifier = Modifier.size(width = 150.dp, height = 210.dp),
            )
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            Text(
                book.title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            if (!book.author.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    "by ${book.author}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                modifier = Modifier.fillMaxWidth(),
            ) {
                MetaPill(Formatters.fileSize(book.fileSize), icon = Icons.Outlined.Description)
                val date = Formatters.dateFromEpochSeconds(book.dateAdded)
                if (date.isNotBlank()) MetaPill(date, icon = Icons.Outlined.CalendarToday)
            }

            Spacer(Modifier.height(24.dp))
            val premium = state.premium
            if (state.downloadState is DownloadState.Done || premium == PremiumState.NotPremium || premium == PremiumState.Unlocked) {
                DownloadSection(state.downloadState, onDownload, onCancel, onOpenPdf)
            } else {
                PremiumSection(
                    state = premium,
                    fileSize = book.fileSize,
                    contact = state.contact,
                    onContactChange = onContactChange,
                    isUnlocking = state.isUnlocking,
                    onPayUpi = onPayUpi,
                    onWatchAd = onWatchAd,
                    onRetry = onRetryPremium,
                )
            }

            if (!book.description.isNullOrBlank()) {
                Spacer(Modifier.height(28.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(20.dp))
                Text("About this book", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(8.dp))
                Text(book.description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(32.dp))
            Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun DownloadSection(
    dl: DownloadState,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onOpenPdf: (String) -> Unit,
) {
    val buttonModifier = Modifier
        .fillMaxWidth()
        .height(52.dp)
    when (dl) {
        DownloadState.Idle -> Button(onClick = onDownload, modifier = buttonModifier) {
            Icon(Icons.Outlined.Download, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Download & read")
        }
        is DownloadState.InProgress -> Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    dl.progress?.let { "Downloading… ${(it * 100).toInt()}%" } ?: "Downloading…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
            Spacer(Modifier.height(6.dp))
            val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
            val progress = dl.progress
            if (progress != null) {
                LinearProgressIndicator(progress = { progress }, trackColor = trackColor, modifier = Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(trackColor = trackColor, modifier = Modifier.fillMaxWidth())
            }
        }
        is DownloadState.Done -> Button(onClick = { onOpenPdf(dl.localPath) }, modifier = buttonModifier) {
            Icon(Icons.AutoMirrored.Outlined.MenuBook, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Read now")
        }
        is DownloadState.Failed -> Column(Modifier.fillMaxWidth()) {
            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(dl.reason, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onDownload, modifier = buttonModifier) {
                Icon(Icons.Outlined.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Try again")
            }
        }
    }
}

@Composable
private fun PremiumSection(
    state: PremiumState,
    fileSize: Long,
    contact: String,
    onContactChange: (String) -> Unit,
    isUnlocking: Boolean,
    onPayUpi: () -> Unit,
    onWatchAd: () -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.WorkspacePremium, contentDescription = null, tint = Amber, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(8.dp))
                Text("Premium book", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                Spacer(Modifier.weight(1f))
                Text(Formatters.fileSize(fileSize), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Spacer(Modifier.height(8.dp))
            when (state) {
                PremiumState.Checking -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = Amber)
                    Spacer(Modifier.width(10.dp))
                    Text("Checking access…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                is PremiumState.Ordered -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.CheckCircle, contentDescription = null, tint = Success, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.sent) "Sent! Check your WhatsApp/Telegram." else "Order received",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        if (state.sent) "The book was sent to ${state.contact ?: "your contact"}."
                        else "The book will be sent to ${state.contact ?: "you"} on WhatsApp/Telegram, usually within a few hours.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    Spacer(Modifier.height(4.dp))
                    TextButton(onClick = onRetry) { Text("Refresh status", color = Amber) }
                }
                is PremiumState.Error -> {
                    Text(state.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    Spacer(Modifier.height(8.dp))
                    TextButton(onClick = onRetry) { Text("Try again", color = Amber) }
                }
                else -> {
                    val manual = state is PremiumState.Locked && state.manual
                    Text(
                        if (manual) "Pay ₹10 or watch a short ad. We'll send the full PDF to your WhatsApp or Telegram."
                        else "Unlock once to download and read offline.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                    if (manual) {
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = contact,
                            onValueChange = onContactChange,
                            label = { Text("WhatsApp number or Telegram @username") },
                            placeholder = { Text("+91 98765 43210  or  @yourname") },
                            singleLine = true,
                            shape = MaterialTheme.shapes.medium,
                            colors = appTextFieldColors(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = onPayUpi,
                        enabled = !isUnlocking,
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = OnAmber),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        if (isUnlocking) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = OnAmber)
                        } else {
                            Icon(Icons.Outlined.CurrencyRupee, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Pay ₹10 with UPI")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onWatchAd,
                        enabled = !isUnlocking,
                        border = BorderStroke(1.dp, Amber),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Amber),
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                    ) {
                        Icon(Icons.Outlined.SmartDisplay, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Watch an ad — free")
                    }
                }
            }
        }
    }
}
