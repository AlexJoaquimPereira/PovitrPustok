package com.biblia.povitrpustok.feature.about

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.biblia.povitrpustok.R
import com.biblia.povitrpustok.ui.rememberAppVersionName

/**
 * About landing page.
 *
 * The single place Settings, Home and the Reader open. It states what the app
 * is (an independent publication, not a diocesan product), which version is
 * running, and then hands off to one page per disclosure type:
 * [AboutSourceEditionScreen], [AboutApiScreen], [AboutLicensesScreen] and
 * [AboutCreditsScreen]. Keeping the disclosures apart keeps every page short
 * and gives each claim its own address.
 *
 * A short privacy statement closes the page, because it is the question a
 * reader is most likely to have after learning the app talks to a readings
 * service.
 */
@Composable
fun AboutScreen(
    onBack: () -> Unit,
    onOpenSourceEdition: () -> Unit,
    onOpenApi: () -> Unit,
    onOpenLicenses: () -> Unit,
    onOpenCredits: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme
    val versionName = rememberAppVersionName()

    AboutPageScaffold(
        title = stringResource(R.string.about_title),
        onBack = onBack,
        backTag = "about.back",
        screenTag = "about.screen",
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.about_app_title),
            style = MaterialTheme.typography.headlineMedium,
            color = colorScheme.onBackground,
            modifier = Modifier.testTag("about.hero")
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = versionName?.let { stringResource(R.string.about_version_value, it) }
                ?: stringResource(R.string.about_version_unknown),
            style = MaterialTheme.typography.labelMedium,
            color = colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag("about.version")
        )

        Spacer(modifier = Modifier.height(20.dp))
        AboutRule()
        Spacer(modifier = Modifier.height(20.dp))

        AboutLead(text = stringResource(R.string.about_independent_note))

        Spacer(modifier = Modifier.height(28.dp))

        AboutRowGroup {
            AboutEntryRow(
                title = stringResource(R.string.about_source_title),
                subtitle = stringResource(R.string.about_source_subtitle),
                onClick = onOpenSourceEdition,
                tag = "about.source"
            )
            AboutRowDivider()
            AboutEntryRow(
                title = stringResource(R.string.about_api_title),
                subtitle = stringResource(R.string.about_api_subtitle),
                onClick = onOpenApi,
                tag = "about.api"
            )
            AboutRowDivider()
            AboutEntryRow(
                title = stringResource(R.string.about_licenses_title),
                subtitle = stringResource(R.string.about_licenses_subtitle),
                onClick = onOpenLicenses,
                tag = "about.licenses"
            )
            AboutRowDivider()
            AboutEntryRow(
                title = stringResource(R.string.about_credits_title),
                subtitle = stringResource(R.string.about_credits_subtitle),
                onClick = onOpenCredits,
                tag = "about.credits"
            )
        }

        Spacer(modifier = Modifier.height(28.dp))
        AboutHairline()
        Spacer(modifier = Modifier.height(16.dp))
        AboutNote(text = stringResource(R.string.about_privacy_note))
    }
}
