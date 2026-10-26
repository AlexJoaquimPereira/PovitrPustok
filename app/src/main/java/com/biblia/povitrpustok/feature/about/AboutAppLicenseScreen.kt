package com.biblia.povitrpustok.feature.about

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.biblia.povitrpustok.R

/**
 * App licence page: the complete Apache-2.0 text plus the NOTICE file.
 *
 * Apache-2.0 requires the full licence to accompany a distributed app, so the
 * text is read from the packaged asset and shown in full — no summary, no
 * link-only substitute, no truncation. It is rendered in a small monospace face
 * because the licence is a legal document whose clause numbering and
 * indentation carry meaning, and it is selectable so a reader can copy a
 * clause. The NOTICE follows, unchanged.
 */
@Composable
fun AboutAppLicenseScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val licenseText = remember(context) { context.readAssetText(APP_LICENSE_ASSET) }
    val noticeText = remember(context) { context.readAssetText(NOTICE_ASSET) }

    AboutPageScaffold(
        title = stringResource(R.string.about_app_license_title),
        onBack = onBack,
        backTag = "about.app_license.back",
        screenTag = "about.app_license.screen",
        modifier = modifier
    ) {
        AboutLead(text = stringResource(R.string.about_app_license_lead))

        Spacer(modifier = Modifier.height(16.dp))
        AboutHairline()
        Spacer(modifier = Modifier.height(20.dp))

        LicencePanel(
            text = licenseText ?: stringResource(R.string.about_app_license_unavailable),
            tag = "about.app_license.text"
        )

        Spacer(modifier = Modifier.height(32.dp))
        AboutSectionLabel(text = stringResource(R.string.about_app_license_notice_title))
        Spacer(modifier = Modifier.height(8.dp))
        AboutNote(text = stringResource(R.string.about_app_license_notice_lead))
        Spacer(modifier = Modifier.height(16.dp))

        LicencePanel(
            text = noticeText ?: stringResource(R.string.about_app_license_unavailable),
            tag = "about.app_license.notice"
        )
    }
}

private const val APP_LICENSE_ASSET = "licenses/Apache-2.0.txt"
private const val NOTICE_ASSET = "licenses/NOTICE.txt"

/** Legal text: small monospace, generous leading, selectable. */
private val LicenceTextStyle = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontSize = 12.sp,
    lineHeight = 18.sp,
    letterSpacing = 0.sp
)

@Composable
private fun LicencePanel(
    text: String,
    tag: String,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, colorScheme.outlineVariant)
    ) {
        SelectionContainer {
            Text(
                text = text,
                style = LicenceTextStyle,
                color = colorScheme.onSurface,
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .testTag(tag)
            )
        }
    }
}

/** Read a packaged text asset; `null` when it is missing or unreadable. */
private fun Context.readAssetText(path: String): String? = runCatching {
    assets.open(path).bufferedReader().use { it.readText() }
}.getOrNull()
