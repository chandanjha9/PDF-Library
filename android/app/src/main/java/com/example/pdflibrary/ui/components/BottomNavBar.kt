package com.example.pdflibrary.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.pdflibrary.ui.common.TourTarget
import com.example.pdflibrary.ui.common.tourTarget

enum class MainTab(val label: String, val selectedIcon: ImageVector, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Library("Library", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    Profile("Profile", Icons.Filled.Person, Icons.Outlined.PersonOutline),
}

/**
 * Persistent floating navigation bar: Home · Library · Request · Profile.
 *
 * Fix: the bar now pads itself by the system navigation-bar inset. With
 * edge-to-edge enabled, the old bar was drawn *underneath* the 3-button /
 * gesture bar, hiding Library, Request and Profile on most devices.
 */
@Composable
fun AppBottomNavBar(
    selected: MainTab,
    onTabSelected: (MainTab) -> Unit,
    onRequestClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("bottom_nav"),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            shadowElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .tourTarget(TourTarget.BottomNav),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                NavItem(MainTab.Home, selected == MainTab.Home) { onTabSelected(MainTab.Home) }
                NavItem(MainTab.Library, selected == MainTab.Library) { onTabSelected(MainTab.Library) }
                RequestButton(onRequestClick)
                NavItem(MainTab.Profile, selected == MainTab.Profile) { onTabSelected(MainTab.Profile) }
            }
        }
    }
}

@Composable
private fun RowScope.NavItem(tab: MainTab, selected: Boolean, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(if (selected) scheme.primaryContainer else Color.Transparent, label = "navBg")
    val fg by animateColorAsState(if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant, label = "navFg")

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = bg,
        modifier = Modifier
            .weight(1f, fill = false)
            .heightIn(min = 44.dp)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab),
    ) {
        Row(
            modifier = Modifier
                .animateContentSize()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (selected) tab.selectedIcon else tab.icon,
                contentDescription = tab.label,
                tint = fg,
                modifier = Modifier.size(22.dp),
            )
            if (selected) {
                Spacer(Modifier.width(6.dp))
                Text(tab.label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Clip)
            }
        }
    }
}

@Composable
private fun RequestButton(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        modifier = Modifier
            .heightIn(min = 44.dp)
            .tourTarget(TourTarget.Request)
            .testTag("nav_request"),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(4.dp))
            Text("Request", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}
