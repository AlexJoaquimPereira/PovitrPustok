package com.biblia.povitrpustok.feature.about

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.biblia.povitrpustok.R

/**
 * Typeface licence page (F2-09): the complete SIL Open Font License 1.1 text
 * for the bundled Atkinson Hyperlegible face.
 *
 * The typeface is an optional reading aid, but it still ships inside the APK,
 * so its licence travels with it in full — read from the packaged asset and
 * shown in a selectable monospace panel, exactly like the app licence. The page
 * is reached from the TYPEFACES section of [AboutLicensesScreen]; the
 * runtime-library list is deliberately left alone, because a font is not a
 * library.
 */
@Composable
fun AboutTypefaceLicenseScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val licenseText = remember(context) { context.readAssetText(TYPEFACE_LICENSE_ASSET) }

    AboutPageScaffold(
        title = stringResource(R.string.about_typeface_license_title),
        onBack = onBack,
        backTag = "about.typeface_license.back",
        screenTag = "about.typeface_license.screen",
        modifier = modifier
    ) {
        AboutLead(text = stringResource(R.string.about_typeface_license_lead))

        Spacer(modifier = Modifier.height(16.dp))
        AboutHairline()
        Spacer(modifier = Modifier.height(20.dp))

        LicencePanel(
            text = licenseText ?: stringResource(R.string.about_app_license_unavailable),
            tag = "about.typeface_license.text"
        )

        Spacer(modifier = Modifier.height(24.dp))
        AboutNote(text = stringResource(R.string.about_typeface_license_note))
    }
}

private const val TYPEFACE_LICENSE_ASSET = "licenses/OFL-1.1.txt"
