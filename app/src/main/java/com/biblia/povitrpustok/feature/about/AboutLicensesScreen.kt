package com.biblia.povitrpustok.feature.about

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.biblia.povitrpustok.R

/**
 * Software licences page.
 *
 * The app code is Apache-2.0 and each library keeps its own licence, so the
 * page separates runtime, test and build-time components instead of printing
 * one undifferentiated list. Every row links to the project's licence page;
 * the app's own full licence text lives on [AboutAppLicenseScreen], one tap
 * away, because Apache-2.0 requires the complete text to travel with the app.
 */
@Composable
fun AboutLicensesScreen(
    onBack: () -> Unit,
    onOpenAppLicense: () -> Unit,
    modifier: Modifier = Modifier
) {
    AboutPageScaffold(
        title = stringResource(R.string.about_licenses_title),
        onBack = onBack,
        backTag = "about.licenses.back",
        screenTag = "about.licenses.screen",
        modifier = modifier
    ) {
        AboutLead(text = stringResource(R.string.about_licenses_lead))

        Spacer(modifier = Modifier.height(20.dp))

        AboutRowGroup {
            AboutEntryRow(
                title = stringResource(R.string.about_licenses_app_title),
                subtitle = stringResource(R.string.about_licenses_app_subtitle),
                onClick = onOpenAppLicense,
                tag = "about.licenses.app"
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        AboutSectionLabel(
            text = stringResource(R.string.about_licenses_runtime),
            modifier = Modifier.testTag("about.licenses.runtime")
        )
        Spacer(modifier = Modifier.height(12.dp))
        LicenceList(components = runtimeLicences)

        Spacer(modifier = Modifier.height(32.dp))
        AboutSectionLabel(
            text = stringResource(R.string.about_licenses_test),
            modifier = Modifier.testTag("about.licenses.test")
        )
        Spacer(modifier = Modifier.height(8.dp))
        AboutNote(text = stringResource(R.string.about_licenses_test_note))
        Spacer(modifier = Modifier.height(12.dp))
        LicenceList(components = testLicences)

        Spacer(modifier = Modifier.height(32.dp))
        AboutSectionLabel(
            text = stringResource(R.string.about_licenses_build),
            modifier = Modifier.testTag("about.licenses.build")
        )
        Spacer(modifier = Modifier.height(8.dp))
        AboutNote(text = stringResource(R.string.about_licenses_build_note))
        Spacer(modifier = Modifier.height(12.dp))
        LicenceList(components = buildLicences)

        Spacer(modifier = Modifier.height(24.dp))
        AboutNote(text = stringResource(R.string.about_licenses_link_note))
    }
}

@Composable
private fun LicenceList(
    components: List<LicenceComponent>,
    modifier: Modifier = Modifier
) {
    AboutRowGroup(modifier = modifier) {
        components.forEachIndexed { index, component ->
            if (index > 0) {
                AboutRowDivider()
            }
            AboutLinkRow(
                label = component.name,
                url = component.url,
                tag = "about.licenses.component.${component.id}",
                meta = licenceMeta(component)
            )
        }
    }
}

/**
 * "2.2.10 · Apache License 2.0" — the version (or "transitive") and the
 * licence name, in the row's one metadata line.
 */
@Composable
private fun licenceMeta(component: LicenceComponent): String {
    val parts = buildList {
        if (component.transitive) {
            add(stringResource(R.string.about_licenses_transitive))
        } else if (component.version != null) {
            add(component.version)
        }
        add(stringResource(component.licence))
    }
    return parts.joinToString(" · ")
}
