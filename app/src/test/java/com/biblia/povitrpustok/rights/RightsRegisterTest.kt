package com.biblia.povitrpustok.rights

import com.biblia.povitrpustok.domain.model.BibleEditions
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeParseException
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * F1-02 build-time rights gate.
 *
 * Reads the table in `docs/rights.md` and fails when a required shipped row is
 * missing, a shipped edition or content pack is uncovered, a shipped/network
 * row has no confirmed licence, or a term has expired. Fixtures prove the
 * failure modes; the real register must pass.
 */
class RightsRegisterTest {

    @Test
    fun `real register covers every shipped edition and content pack`() {
        val root = repoRoot()
        val rows = validate(
            markdown = File(root, "docs/rights.md").readText(),
            editions = BibleEditions.all,
            packs = bundledPackIds(root),
            today = LocalDate.now()
        )

        assertTrue(rows.isNotEmpty())
        assertTrue(
            rows.any {
                it.id == "konkani-corpus" && BibleEditions.KONKANI_ID in it.covers
            }
        )
    }

    @Test
    fun `missing required row fails`() {
        expectFailure("missing required row 'app-code'") {
            validate(
                register(baseRows().filterNot { it["id"] == "app-code" }),
                emptySet(),
                emptySet()
            )
        }
    }

    @Test
    fun `missing shipped pack row fails`() {
        expectFailure("pack 'new-pack' has no rights row") {
            validate(register(baseRows()), emptySet(), setOf("new-pack"))
        }
    }

    @Test
    fun `uncovered edition fails`() {
        expectFailure("edition 'new-edition' has no rights row") {
            validate(register(baseRows()), setOf("new-edition"), emptySet())
        }
    }

    @Test
    fun `expired term fails`() {
        expectFailure("expired on 2020-01-01") {
            validate(
                register(baseRows() + row("expired", expiry = "2020-01-01")),
                emptySet(),
                emptySet()
            )
        }
    }

    @Test
    fun `shipped or network row cannot use a pending licence`() {
        expectFailure("has no confirmed licence") {
            validate(
                register(baseRows() + row("bad-licence", licence = "pending")),
                emptySet(),
                emptySet()
            )
        }
    }

    @Test
    fun `planned rows may reserve a pending licence`() {
        validate(
            register(baseRows() + row("future", status = "planned", licence = "pending")),
            emptySet(),
            emptySet()
        )
    }

    private fun validate(
        markdown: String,
        editions: Set<String>,
        packs: Set<String>,
        today: LocalDate = LocalDate.of(2027, 1, 15)
    ): List<Row> {
        val lines = markdown.lines()
        val start = lines.indexOfFirst { it.trim() == START_MARKER }
        val end = lines.indexOfFirst { it.trim() == END_MARKER }
        check(start >= 0 && end > start) { "rights-register markers missing" }

        val table = lines.subList(start + 1, end).filter { it.trim().startsWith("|") }
        check(table.size >= 2) { "rights-register table missing" }
        val header = cells(table[0])
        check(header == columns) { "unexpected columns: $header" }
        check(table[1].replace("|", "").replace("-", "").replace(":", "").isBlank()) {
            "rights-register table separator missing"
        }

        val rows = table.drop(2).map { line ->
            val values = cells(line)
            check(values.size == columns.size) {
                "row has ${values.size} cells, expected ${columns.size}: $line"
            }
            Row(columns.zip(values).toMap())
        }
        check(rows.isNotEmpty()) { "rights-register has no rows" }
        check(rows.map { it.id }.toSet().size == rows.size) { "duplicate rights-register ids" }

        REQUIRED_IDS.forEach { id ->
            check(rows.any { it.id == id }) { "missing required row '$id'" }
        }

        rows.forEach { row ->
            check(row.content.isNotBlank()) { "row '${row.id}' has no content" }
            check(row.status in STATUSES) { "row '${row.id}' has unknown status '${row.status}'" }
            check(row.owner.isNotBlank()) { "row '${row.id}' has no owner" }
            check(row.licence.isNotBlank()) { "row '${row.id}' has no licence" }
            check(row.attribution.isNotBlank()) { "row '${row.id}' has no attribution" }
            check(row.territory.isNotBlank()) { "row '${row.id}' has no territory" }
            check(row.evidence.isNotBlank()) { "row '${row.id}' has no evidence" }
            if (row.status == "shipped" || row.status == "network") {
                val licence = row.licence.lowercase()
                check(PENDING_LICENCE_MARKERS.none { licence.contains(it) }) {
                    "row '${row.id}' is ${row.status} but has no confirmed licence"
                }
            }
            if (row.expiry != "none") {
                val expiry = try {
                    LocalDate.parse(row.expiry)
                } catch (e: DateTimeParseException) {
                    throw IllegalStateException(
                        "row '${row.id}' has invalid expiry '${row.expiry}'",
                        e
                    )
                }
                check(!expiry.isBefore(today)) { "row '${row.id}' expired on $expiry" }
            }
        }

        val covered = rows.flatMap { it.covers }.toSet()
        editions.forEach { edition ->
            check(edition in covered) { "edition '$edition' has no rights row" }
        }
        packs.forEach { pack ->
            val row = rows.firstOrNull { pack in it.covers }
            check(row != null) { "pack '$pack' has no rights row" }
            check(row.status == "shipped") { "pack '$pack' row is not shipped" }
        }
        return rows
    }

    private data class Row(val values: Map<String, String>) {
        val id: String get() = values.getValue("id")
        val content: String get() = values.getValue("content")
        val status: String get() = values.getValue("status")
        val owner: String get() = values.getValue("owner")
        val licence: String get() = values.getValue("licence")
        val attribution: String get() = values.getValue("attribution")
        val covers: Set<String>
            get() = values.getValue("covers")
                .split(',')
                .map { it.trim() }
                .filter { it.isNotEmpty() && it != "-" }
                .toSet()
        val territory: String get() = values.getValue("territory")
        val expiry: String get() = values.getValue("expiry")
        val evidence: String get() = values.getValue("evidence")
    }

    private fun cells(line: String): List<String> =
        line.trim().removePrefix("|").removeSuffix("|").split('|').map { it.trim() }

    private fun baseRows(): List<Map<String, String>> = listOf(
        row("app-code"),
        row("konkani-corpus", covers = "konkani-romi"),
        row("catholic-readings-api", status = "network", licence = "MIT"),
        row("software-libraries")
    )

    private fun row(
        id: String,
        status: String = "shipped",
        licence: String = "Apache-2.0",
        covers: String = "-",
        expiry: String = "none"
    ): Map<String, String> = mapOf(
        "id" to id,
        "content" to "Content $id",
        "status" to status,
        "owner" to "Owner",
        "licence" to licence,
        "attribution" to "Attribution",
        "covers" to covers,
        "territory" to "worldwide",
        "expiry" to expiry,
        "evidence" to "Evidence"
    )

    private fun register(rows: List<Map<String, String>>): String = buildString {
        appendLine(START_MARKER)
        appendLine("| " + columns.joinToString(" | ") + " |")
        appendLine("|" + columns.joinToString("|") { "---" } + "|")
        rows.forEach { row ->
            appendLine("| " + columns.joinToString(" | ") { row[it] ?: "" } + " |")
        }
        appendLine(END_MARKER)
    }

    private fun repoRoot(): File =
        generateSequence(File(System.getProperty("user.dir"))) { it.parentFile }
            .firstOrNull { File(it, "docs/rights.md").isFile }
            ?: error("cannot locate docs/rights.md from ${System.getProperty("user.dir")}")

    private fun bundledPackIds(root: File): Set<String> =
        File(root, "app/src/main/assets/packs")
            .listFiles { file -> file.isDirectory }
            ?.map { it.name }
            ?.toSet()
            .orEmpty()

    private fun expectFailure(messagePart: String, block: () -> Unit) {
        try {
            block()
        } catch (e: IllegalStateException) {
            assertTrue("message was: ${e.message}", e.message.orEmpty().contains(messagePart))
            return
        }
        fail("expected failure containing '$messagePart'")
    }

    private companion object {
        const val START_MARKER = "<!-- rights-register:start -->"
        const val END_MARKER = "<!-- rights-register:end -->"
        val columns = listOf(
            "id",
            "content",
            "status",
            "owner",
            "licence",
            "attribution",
            "covers",
            "territory",
            "expiry",
            "evidence"
        )
        val REQUIRED_IDS = setOf(
            "app-code",
            "konkani-corpus",
            "catholic-readings-api",
            "software-libraries"
        )
        val STATUSES = setOf("shipped", "network", "pending", "planned")
        val PENDING_LICENCE_MARKERS = setOf("pending", "tbd", "unknown", "to confirm")
    }
}
