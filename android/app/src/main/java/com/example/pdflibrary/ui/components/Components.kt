package com.example.pdflibrary.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.example.pdflibrary.data.network.ApiClient
import com.example.pdflibrary.theme.Amber
import com.example.pdflibrary.theme.OnAmber
import com.example.pdflibrary.theme.TextLow
import com.example.pdflibrary.theme.coverGradientFor
import com.example.pdflibrary.ui.common.Formatters

/**
 * Generated book cover used everywhere a book is shown (carousel, lists, detail).
 * Previously each screen drew its own cover with different colours.
 * If the backend ever supplies an absolute cover_url it is loaded on top.
 */
@Composable
fun BookCover(
    bookId: Int,
    title: String,
    modifier: Modifier = Modifier,
    coverUrl: String? = null,
    cornerRadius: Dp = 10.dp,
    showTitle: Boolean = true,
    premium: Boolean = false,
) {
    val (top, bottom) = coverGradientFor(bookId)
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Brush.verticalGradient(listOf(top, bottom))),
    ) {
        // Spine highlight for a subtle "book" feel
        Box(
            Modifier
                .fillMaxHeight()
                .width(4.dp)
                .background(Color.White.copy(alpha = 0.10f))
        )
        if (showTitle) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 12.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontFamily = FontFamily.Serif,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    lineHeight = 16.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Box(
                    Modifier
                        .width(22.dp)
                        .height(2.dp)
                        .background(Color.White.copy(alpha = 0.55f))
                )
            }
        } else {
            Text(
                text = Formatters.initials(title),
                color = Color.White,
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        // Real cover photo (Telegram's first-page thumbnail, served by the backend
        // at /books/{id}/cover). Drawn over the generated cover, so if it's
        // missing or fails to load the generated one simply stays visible.
        val model = remember(coverUrl) { resolveCoverUrl(coverUrl) }
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }
        if (premium) {
            PremiumBadge(
                compact = !showTitle,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(4.dp),
            )
        }
    }
}

private fun resolveCoverUrl(url: String?): String? = when {
    url.isNullOrBlank() -> null
    url.startsWith("http://") || url.startsWith("https://") -> url
    url.startsWith("/") -> runCatching { ApiClient.resolve(url).toString() }.getOrNull()
    else -> null
}

/** Small amber "Premium" tag (crown only when space is tight). */
@Composable
fun PremiumBadge(modifier: Modifier = Modifier, compact: Boolean = false) {
    Surface(shape = RoundedCornerShape(6.dp), color = Amber, contentColor = OnAmber, modifier = modifier) {
        Row(
            modifier = Modifier.padding(horizontal = if (compact) 3.dp else 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.WorkspacePremium, contentDescription = "Premium", modifier = Modifier.size(12.dp))
            if (!compact) {
                Spacer(Modifier.width(3.dp))
                Text("PREMIUM", fontSize = 9.sp, fontWeight = FontWeight.Bold, lineHeight = 10.sp)
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f),
        )
        trailing?.invoke()
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(64.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(30.dp))
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Inline banner for network errors / offline cache notices, with optional retry. */
@Composable
fun NoticeBanner(
    message: String,
    modifier: Modifier = Modifier,
    isError: Boolean = true,
    onRetry: (() -> Unit)? = null,
) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.tertiaryContainer
    val content = if (isError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onTertiaryContainer
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = container,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (isError) Icons.Outlined.CloudOff else Icons.Outlined.Info,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = content,
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 8.dp),
            )
            if (onRetry != null) {
                TextButton(onClick = onRetry, colors = ButtonDefaults.textButtonColors(contentColor = content)) {
                    Text("Retry")
                }
            }
        }
    }
}

/** Small pill used for metadata (size, date, page). */
@Composable
fun MetaPill(text: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(text, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Shared text-field colours so every input looks identical. */
@Composable
fun appTextFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = MaterialTheme.colorScheme.primary,
    unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    focusedLabelColor = MaterialTheme.colorScheme.primary,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedPlaceholderColor = TextLow,
    unfocusedPlaceholderColor = TextLow,
    cursorColor = MaterialTheme.colorScheme.primary,
)

/** Card container used for list rows. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
    val border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, border = border, content = content)
    } else {
        Card(modifier = modifier, shape = MaterialTheme.shapes.large, colors = colors, border = border, content = content)
    }
}
