package com.biblia.povitrpustok.feature.about

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.biblia.povitrpustok.R

/**
 * Credits page: the project and its maintainer.
 *
 * This is not a second disclosure index. The About landing already lists the
 * Source edition, API & data and Software licences pages; repeating them here
 * made Credits a longer copy of the previous screen. The page keeps the only
 * content with no other home: the maintainer's GitHub link and the API
 * non-affiliation note. The diocesan acknowledgement lives only on the Source
 * edition page.
 */
@Composable
fun AboutCreditsScreen(
    onBack: () -> Unit,
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

        Spacer(modifier = Modifier.height(24.dp))
        AboutNote(text = stringResource(R.string.about_credits_independent))
    }
}

private const val GITHUB_PROFILE = "https://github.com/AlexJoaquimPereira"
