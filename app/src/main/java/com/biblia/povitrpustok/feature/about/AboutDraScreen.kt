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
 * Douay-Rheims, American Edition 1899: disclosure page.
 *
 * The third reading edition, with its own source, build source, licence and
 * attribution. The translation follows the Latin Vulgate, so the page says
 * plainly that its numbering can differ from the Konkani edition — with the
 * Psalm 22 / Hebrew Psalm 23 case as the concrete example — because that is the
 * consequence a reader notices first when switching, and the reason bookmarks
 * are best effort across editions.
 */
@Composable
fun AboutDraScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    AboutPageScaffold(
        title = stringResource(R.string.about_dra_title),
        onBack = onBack,
        backTag = "about.dra.back",
        screenTag = "about.dra.screen",
        modifier = modifier
    ) {
        AboutLead(text = stringResource(R.string.about_dra_lead))
        Spacer(modifier = Modifier.height(12.dp))
        AboutBody(
            text = stringResource(R.string.about_dra_body),
            modifier = Modifier.testTag("about.dra.body")
        )

        Spacer(modifier = Modifier.height(20.dp))
        AboutRule()
        Spacer(modifier = Modifier.height(24.dp))

        AboutSectionLabel(
            text = stringResource(R.string.about_dra_licence_label),
            modifier = Modifier.testTag("about.dra.licence_label")
        )
        Spacer(modifier = Modifier.height(8.dp))
        AboutBody(
            text = stringResource(R.string.about_dra_licence),
            modifier = Modifier.testTag("about.dra.licence")
        )
        Spacer(modifier = Modifier.height(8.dp))
        AboutNote(
            text = stringResource(R.string.about_dra_attribution),
            modifier = Modifier.testTag("about.dra.attribution")
        )

        Spacer(modifier = Modifier.height(28.dp))
        AboutSectionLabel(
            text = stringResource(R.string.about_dra_links_label),
            modifier = Modifier.testTag("about.dra.links_label")
        )
        Spacer(modifier = Modifier.height(12.dp))

        AboutRowGroup {
            AboutLinkRow(
                label = stringResource(R.string.about_dra_source_label),
                url = DRA_SOURCE,
                tag = "about.dra.source"
            )
            AboutRowDivider()
            AboutLinkRow(
                label = stringResource(R.string.about_dra_build_label),
                url = DRA_BUILD,
                tag = "about.dra.build",
                meta = stringResource(R.string.about_dra_build_meta)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        AboutNote(
            text = stringResource(R.string.about_dra_note),
            modifier = Modifier.testTag("about.dra.note")
        )
    }
}

/** eBible.org's own catalogue entry for the 1899 American Edition. */
private const val DRA_SOURCE = "https://ebible.org/Scriptures/details.php?id=engDRA"

/** The JSON document the bundled assets were generated from. */
private const val DRA_BUILD = "https://bible.helloao.org/api/eng_dra/complete.json"
