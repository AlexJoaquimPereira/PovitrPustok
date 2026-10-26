package com.biblia.povitrpustok.feature.about

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** The packaged NOTICE must match the user-facing disclosure: no permission number. */
@RunWith(RobolectricTestRunner::class)
class NoticeTextTest {

    @Test
    fun packaged_notice_has_no_permission_number() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val notice = context.assets.open("licenses/NOTICE.txt")
            .bufferedReader()
            .use { it.readText() }

        assertFalse("permission number must not appear in NOTICE", notice.contains("CP/2163/2017"))
        assertTrue(
            "NOTICE should state the rights-holder permission",
            notice.contains("permission of the rights holder")
        )
    }
}
