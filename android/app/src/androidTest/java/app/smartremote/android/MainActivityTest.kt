package app.smartremote.android

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import org.junit.Rule
import org.junit.Test

class MainActivityTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun connectionScreenIsVisibleOnLaunch() {
        val title = composeRule.activity.getString(R.string.connect_title)
        val connect = composeRule.activity.getString(R.string.connect)
        composeRule.onNodeWithText(title).assertIsDisplayed()
        composeRule.onNodeWithText(connect).assertIsDisplayed()
    }
}
