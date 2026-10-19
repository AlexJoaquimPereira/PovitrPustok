package com.biblia.povitrpustok.testutil

import org.junit.Assume.assumeFalse
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement

/**
 * Skips tests that require the real Bible corpus when the build is run without
 * a private corpus artifact.
 *
 * The corpus is copyrighted and is not committed to the repository. CI fetches
 * it from a private artifact store when configured; otherwise it runs with
 * `-PskipCorpusTests` so these tests are reported as skipped rather than
 * failing on missing assets. A local checkout without assets still fails
 * loudly: only the explicit property turns the skip on.
 */
class CorpusAvailabilityRule : TestRule {
    override fun apply(base: Statement, description: Description): Statement =
        object : Statement() {
            override fun evaluate() {
                assumeFalse(
                    "Real-corpus test skipped: no private corpus artifact in this run.",
                    System.getProperty(CORPUS_SKIP_PROPERTY) == "true",
                )
                base.evaluate()
            }
        }

    companion object {
        const val CORPUS_SKIP_PROPERTY = "corpus.skip"
    }
}
