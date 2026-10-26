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
 * API & data page: where Daily Reading comes from, what leaves the device, and
 * what the app does *not* have.
 *
 * The three links are the API's own pages, so a reader can check the claim
 * against the project rather than take the app's word for it. The Universalis
 * Goa reference is presented as a manual cross-check only — nothing from it is
 * bundled or copied — because that is exactly how the calendar corrections are
 * made.
 */
@Composable
fun AboutApiScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    AboutPageScaffold(
        title = stringResource(R.string.about_api_title),
        onBack = onBack,
        backTag = "about.api.back",
        screenTag = "about.api.screen",
        modifier = modifier
    ) {
        AboutLead(text = stringResource(R.string.about_api_lead))
        Spacer(modifier = Modifier.height(12.dp))
        AboutBody(text = stringResource(R.string.about_api_body))

        Spacer(modifier = Modifier.height(28.dp))
        AboutSectionLabel(text = stringResource(R.string.about_api_links_label))
        Spacer(modifier = Modifier.height(12.dp))

        AboutRowGroup {
            AboutLinkRow(
                label = stringResource(R.string.about_api_website),
                url = API_WEBSITE,
                tag = "about.api.website"
            )
            AboutRowDivider()
            AboutLinkRow(
                label = stringResource(R.string.about_api_repo),
                url = API_REPOSITORY,
                tag = "about.api.repo"
            )
            AboutRowDivider()
            AboutLinkRow(
                label = stringResource(R.string.about_api_license),
                url = API_LICENSE,
                tag = "about.api.license"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        AboutNote(text = stringResource(R.string.about_api_note))

        Spacer(modifier = Modifier.height(32.dp))
        AboutHairline()
        Spacer(modifier = Modifier.height(24.dp))

        AboutSectionLabel(
            text = stringResource(R.string.about_api_reference_label),
            modifier = Modifier.testTag("about.api.reference.label")
        )
        Spacer(modifier = Modifier.height(8.dp))
        AboutBody(text = stringResource(R.string.about_api_reference_body))
        Spacer(modifier = Modifier.height(12.dp))

        AboutRowGroup {
            AboutLinkRow(
                label = stringResource(R.string.about_api_reference_title),
                url = UNIVERSALIS_GOA_URL,
                tag = "about.api.reference"
            )
        }
    }
}

private const val API_WEBSITE = "https://cpbjr.github.io/catholic-readings-api/"
private const val API_REPOSITORY = "https://github.com/cpbjr/catholic-readings-api"
private const val API_LICENSE = "https://github.com/cpbjr/catholic-readings-api/blob/main/LICENSE"
