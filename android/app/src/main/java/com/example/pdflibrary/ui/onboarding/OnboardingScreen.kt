package com.example.pdflibrary.ui.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.pdflibrary.R
import com.example.pdflibrary.theme.LogoSky
import kotlinx.coroutines.launch

private data class OnboardingPage(val title: String, val description: String, val icon: ImageVector?)

private val pages = listOf(
    OnboardingPage(
        "Your digital library",
        "Books, comics, study guides and worksheets — searchable in one place, no account needed.",
        null, // uses the app logo
    ),
    OnboardingPage(
        "Request anything",
        "Can't find a title? Request it and get an instant 20-minute reading pass if it's in the catalogue.",
        Icons.Outlined.Timer,
    ),
    OnboardingPage(
        "Read offline",
        "Download a PDF once and read it anytime. Your page is remembered automatically.",
        Icons.Outlined.CloudDownload,
    ),
)

/** First-launch introduction. Previously defined but never reachable. */
@Composable
fun OnboardingScreen(onFinish: () -> Unit) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val isLast = pagerState.currentPage == pages.lastIndex

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .systemBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.weight(1f)) {
                repeat(pages.size) { i ->
                    val active = i == pagerState.currentPage
                    Box(
                        Modifier
                            .height(6.dp)
                            .width(if (active) 24.dp else 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
                    )
                }
            }
            TextButton(onClick = onFinish) { Text("Skip") }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (page.icon == null) {
                    Image(
                        painter = painterResource(R.drawable.app_logo),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(220.dp)
                            .clip(CircleShape)
                            .background(LogoSky),
                    )
                } else {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        modifier = Modifier.size(220.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(page.icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(88.dp))
                        }
                    }
                }
                Spacer(Modifier.height(36.dp))
                Text(page.title, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                Text(
                    page.description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 8.dp),
                )
            }
        }

        Button(
            onClick = {
                if (isLast) onFinish() else scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
            },
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
        ) {
            Text(if (isLast) "Get started" else "Next", style = MaterialTheme.typography.titleMedium)
        }
    }
}
