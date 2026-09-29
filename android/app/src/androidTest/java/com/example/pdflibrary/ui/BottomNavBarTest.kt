package com.example.pdflibrary.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.pdflibrary.theme.PDFLibraryTheme
import com.example.pdflibrary.ui.components.AppBottomNavBar
import com.example.pdflibrary.ui.components.MainTab
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Guards the "missing modules" regression: all four entries must render and respond. */
@RunWith(AndroidJUnit4::class)
class BottomNavBarTest {

    @get:Rule val rule = createComposeRule()

    @Test
    fun allFourModulesAreVisibleAndClickable() {
        var requestClicks = 0
        var selected: MainTab = MainTab.Home
        rule.setContent {
            PDFLibraryTheme {
                var tab by remember { mutableStateOf(MainTab.Home) }
                AppBottomNavBar(
                    selected = tab,
                    onTabSelected = { tab = it; selected = it },
                    onRequestClick = { requestClicks++ },
                )
            }
        }

        rule.onNodeWithTag("bottom_nav").assertIsDisplayed()
        rule.onNodeWithContentDescription("Home").assertIsDisplayed()
        rule.onNodeWithContentDescription("Library").assertIsDisplayed().performClick()
        rule.onNodeWithText("Request").assertIsDisplayed().performClick()
        rule.onNodeWithContentDescription("Profile").assertIsDisplayed().performClick()

        rule.runOnIdle {
            assertEquals(MainTab.Profile, selected)
            assertEquals(1, requestClicks)
        }
    }
}
