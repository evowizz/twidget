package com.tjg.twidget.settings

import androidx.lifecycle.Lifecycle
import androidx.preference.Preference
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tjg.twidget.R
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HapticsDebugInstrumentedTest {
    @Test fun previewsCanBeInterruptedAndPageCanBeReopened() {
        ActivityScenario.launch(HapticsDebugActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                activity.supportFragmentManager.executePendingTransactions()
                val page = activity.supportFragmentManager.findFragmentById(R.id.preference_fragment_container)
                    as HapticsDebugPreferenceFragment
                val screen = page.preferenceScreen
                // Exercise the real click handlers, including unsupported hardware rows.
                for (index in 0 until screen.preferenceCount) {
                    val preference = screen.getPreference(index)
                    if (preference.isEnabled && preference.isSelectable) preference.performClick()
                }
                page.findPreference<Preference>("haptics_hold")!!.performClick()
                page.findPreference<Preference>("haptics_stop")!!.performClick()
                page.findPreference<Preference>("haptics_hold")!!.performClick()
            }
            // Leaving during a ramp must cancel its delayed pop and allow a clean return.
            scenario.moveToState(Lifecycle.State.CREATED)
            scenario.moveToState(Lifecycle.State.RESUMED)
            scenario.onActivity { activity ->
                val page = activity.supportFragmentManager.findFragmentById(R.id.preference_fragment_container)
                    as HapticsDebugPreferenceFragment
                assertNotNull(page.findPreference<Preference>("haptics_status")?.summary)
                page.findPreference<Preference>("haptics_enter")!!.performClick()
            }
        }
    }
}
