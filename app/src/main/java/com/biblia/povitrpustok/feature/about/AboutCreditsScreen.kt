package com.biblia.povitrpustok.feature.about

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.biblia.povitrpustok.R

/**
 * Credits page: the people and projects behind the app.
 *
 * Each credit names what it is for and links to the page that explains it, so
 * this page thanks without becoming a second copy of the disclosures. The last
 * two paragraphs are the honest boundary: thanks, and no claim of endorsement.
 */
@Composable
fun AboutCreditsScreen(
    onBack: () -> Unit,
    onOpenSourceEdition: () -> Unit,
    onOpenApi: () -> Unit,
    onOpenLicenses: () -> Unit,
    modifier: Modifier = Modifier
) {
    AboutPageScaffold(
        title = stringResource(R.string.about_credits_title),
        onBack = onBack,
        backTag = "about.credits.back",
        screenTag = "about.credits.screen",
        modifier = modifier
    ) {
        AboutLead(text = stringResource(R.string.about_credits_lead))

        Spacer(modifier = Modifier.height(20.dp))
        AboutRowGroup {
            AboutLinkRow(
                label = stringResource(R.string.about_credits_github),
                url = GITHUB_PROFILE,
                tag = "about.credits.github"
            )
        }

        Spacer(modifier = Modifier.height(28.dp))

        CreditBlock(
            title = stringResource(R.string.about_credits_source_title),
            body = stringResource(R.string.about_credits_source_body)
        ) {
            AboutInlineLink(
                text = stringResource(R.string.about_credits_source_link),
                onClick = onOpenSourceEdition,
                tag = "about.credits.source"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        AboutHairline()
        Spacer(modifier = Modifier.height(20.dp))

        CreditBlock(
            title = stringResource(R.string.about_credits_api_title),
            body = stringResource(R.string.about_credits_api_body)
        ) {
            AboutInlineLink(
                text = stringResource(R.string.about_credits_api_link),
                onClick = onOpenApi,
                tag = "about.credits.api"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        AboutHairline()
        Spacer(modifier = Modifier.height(20.dp))

        CreditBlock(
            title = stringResource(R.string.about_credits_licenses_title),
            body = stringResource(R.string.about_credits_licenses_body)
        ) {
            AboutInlineLink(
                text = stringResource(R.string.about_credits_licenses_link),
                onClick = onOpenLicenses,
                tag = "about.credits.licenses"
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        AboutHairline()
        Spacer(modifier = Modifier.height(20.dp))

        AboutBlockTitle(text = stringResource(R.string.about_credits_reference_title))
        Spacer(modifier = Modifier.height(6.dp))
        AboutBody(text = stringResource(R.string.about_credits_reference_body))
        Spacer(modifier = Modifier.height(12.dp))

        AboutRowGroup {
            AboutLinkRow(
                label = stringResource(R.string.about_api_reference_title),
                url = UNIVERSALIS_GOA_URL,
                tag = "about.credits.reference"
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
        AboutBody(text = stringResource(R.string.about_credits_thanks))

        Spacer(modifier = Modifier.height(16.dp))
        AboutHairline()
        Spacer(modifier = Modifier.height(16.dp))
        AboutNote(text = stringResource(R.string.about_credits_independent))
    }
}

@Composable
private fun CreditBlock(
    title: String,
    body: String,
    link: @Composable () -> Unit
) {
    AboutBlockTitle(text = title)
    Spacer(modifier = Modifier.height(6.dp))
    AboutBody(text = body)
    link()
}

private const val GITHUB_PROFILE = "https://github.com/AlexJoaquimPereira"
