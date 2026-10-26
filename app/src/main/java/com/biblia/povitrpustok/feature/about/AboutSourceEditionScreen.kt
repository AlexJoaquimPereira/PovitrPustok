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

/**
 * Source edition page: who owns the Konkani Bible text and what that means.
 *
 * Only the first acknowledgement paragraph from the source edition is kept —
 * the centre's name and address. The permission number and the reproduction
 * paragraph are deliberately absent: the app states the rights holder and the
 * reservation of rights, and nothing more than that.
 */
@Composable
fun AboutSourceEditionScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colorScheme = MaterialTheme.colorScheme

    AboutPageScaffold(
        title = stringResource(R.string.about_source_title),
        onBack = onBack,
        backTag = "about.source.back",
        screenTag = "about.source.screen",
        modifier = modifier
    ) {
        Text(
            text = stringResource(R.string.about_source_centre),
            style = MaterialTheme.typography.titleLarge,
            color = colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.about_source_centre_english),
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(20.dp))
        AboutRule()
        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = stringResource(R.string.corpus_copyright_notice),
            style = MaterialTheme.typography.bodyLarge,
            color = colorScheme.onSurface,
            modifier = Modifier.testTag("about.source.copyright")
        )

        Spacer(modifier = Modifier.height(28.dp))
        AboutHairline()
        Spacer(modifier = Modifier.height(16.dp))
        AboutNote(text = stringResource(R.string.about_independent_note))
    }
}
