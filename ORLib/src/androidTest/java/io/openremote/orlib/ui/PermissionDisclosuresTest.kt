package io.openremote.orlib.ui

import android.app.Activity
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.isDialog
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.openremote.orlib.R
import org.junit.Test
import org.junit.runner.RunWith

class PlatformThemeActivity : Activity()
class AppCompatThemeActivity : Activity()

/**
 * Regression test for https://github.com/openremote/console-android/issues/71:
 * the disclosure dialog must not crash when the host activity does not use a
 * Theme.AppCompat descendant, which is the case for consoles that supply their
 * own theme.
 */
@RunWith(AndroidJUnit4::class)
class PermissionDisclosuresTest {

    private fun showDisclosureAndAssertVisible(scenario: ActivityScenario<out Activity>) {
        scenario.onActivity { activity ->
            PermissionDisclosures.show(
                activity,
                R.string.location_disclosure_title,
                R.string.location_disclosure_body,
                onAccept = {}
            )
        }
        onView(withText(R.string.disclosure_continue))
            .inRoot(isDialog())
            .check(matches(isDisplayed()))
    }

    @Test
    fun showsDisclosureOnNonAppCompatThemedActivity() {
        ActivityScenario.launch(PlatformThemeActivity::class.java).use {
            showDisclosureAndAssertVisible(it)
        }
    }

    @Test
    fun showsDisclosureOnAppCompatThemedActivity() {
        ActivityScenario.launch(AppCompatThemeActivity::class.java).use {
            showDisclosureAndAssertVisible(it)
        }
    }
}
