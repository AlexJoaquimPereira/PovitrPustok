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
 * World English Bible (Catholic) disclosure page.
 *
 * The second reading edition gets its own page for the same reason every other
 * data source has one: its source, build source, licence and attribution are
 * specific to it, and none of them may be folded into the Konkani Source
 * edition page or repeated on Credits.
 *
 * The two claims a public-domain translation makes possible are stated exactly:
 * the text is redistributed unmodified (HTML wrapping only), and the name
 * "World English Bible" is a trademark of eBible.org that this unmodified text
 * may carry. The page also says what the app is not: produced or endorsed by
 * eBible.org.
 */
@Composable
fun AboutWebcScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    AboutPageScaffold(
        title = stringResource(R.string.about_webc_title),
        onBack = onBack,
        backTag = "about.webc.back",
        screenTag = "about.webc.screen",
        modifier = modifier
    ) {
        AboutLead(text = stringResource(R.string.about_webc_lead))
        Spacer(modifier = Modifier.height(12.dp))
        AboutBody(
            text = stringResource(R.string.about_webc_body),
            modifier = Modifier.testTag("about.webc.body")
        )

        Spacer(modifier = Modifier.height(20.dp))
        AboutRule()
        Spacer(modifier = Modifier.height(24.dp))

        AboutSectionLabel(
            text = stringResource(R.string.about_webc_licence_label),
            modifier = Modifier.testTag("about.webc.licence_label")
        )
        Spacer(modifier = Modifier.height(8.dp))
        AboutBody(
            text = stringResource(R.string.about_webc_licence),
            modifier = Modifier.testTag("about.webc.licence")
        )
        Spacer(modifier = Modifier.height(8.dp))
        AboutNote(
            text = stringResource(R.string.about_webc_attribution),
            modifier = Modifier.testTag("about.webc.attribution")
        )

        Spacer(modifier = Modifier.height(28.dp))
        AboutSectionLabel(
            text = stringResource(R.string.about_webc_links_label),
            modifier = Modifier.testTag("about.webc.links_label")
        )
        Spacer(modifier = Modifier.height(12.dp))

        AboutRowGroup {
            AboutLinkRow(
                label = stringResource(R.string.about_webc_source_label),
                url = WEBC_SOURCE,
                tag = "about.webc.source"
            )
            AboutRowDivider()
            AboutLinkRow(
                label = stringResource(R.string.about_webc_build_label),
                url = WEBC_BUILD,
                tag = "about.webc.build",
                meta = stringResource(R.string.about_webc_build_meta)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))
        AboutNote(
            text = stringResource(R.string.about_webc_note),
            modifier = Modifier.testTag("about.webc.note")
        )
    }
}

/** eBible.org's own catalogue entry for the Catholic edition. */
private const val WEBC_SOURCE = "https://ebible.org/Scriptures/details.php?id=eng-web-c"

/** The JSON document the bundled assets were generated from. */
private const val WEBC_BUILD = "https://bible.helloao.org/"
