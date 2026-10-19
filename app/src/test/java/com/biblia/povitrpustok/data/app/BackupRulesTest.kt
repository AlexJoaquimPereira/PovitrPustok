package com.biblia.povitrpustok.data.app

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * BACKUP-01: backup policy must be explicit — user data (the app database)
 * is backed up, while the regenerable prepared corpus (files/bible, ~25 MB)
 * is never copied into a backup.
 */
class BackupRulesTest {

    private fun rule(name: String): String {
        val file = File("src/main/res/xml/$name")
        assertTrue(
            "$name must exist (working dir ${File(".").absolutePath})",
            file.isFile
        )
        return file.readText()
    }

    @Test
    fun legacy_rules_back_up_only_the_database() {
        val xml = rule("backup_rules.xml")
        assertTrue(
            "the app database must be included",
            xml.contains("<include domain=\"database\" path=\".\"")
        )
        assertFalse(
            "whitelist must not include any file-domain data (e.g. files/bible)",
            xml.contains("<include domain=\"file\"")
        )
        assertFalse(
            "a whitelist needs no excludes",
            xml.contains("<exclude")
        )
    }

    @Test
    fun data_extraction_rules_cover_cloud_and_device_transfer_with_database_only() {
        val xml = rule("data_extraction_rules.xml")
        assertTrue("cloud backup section required", xml.contains("<cloud-backup>"))
        assertTrue("device transfer section required", xml.contains("<device-transfer>"))
        assertTrue(
            "the app database must be included",
            xml.contains("<include domain=\"database\" path=\".\"")
        )
        assertEquals(
            "the database must be included once in cloud-backup and once in device-transfer",
            2,
            Regex("<include\\s+domain=\"database\"\\s+path=\"\\.\"").findAll(xml).count()
        )
        assertFalse(
            "whitelist must not include any file-domain data (e.g. files/bible)",
            xml.contains("<include domain=\"file\"")
        )
    }

    @Test
    fun manifest_wires_the_backup_policy() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "allowBackup must remain enabled for user data",
            manifest.contains("android:allowBackup=\"true\"")
        )
        assertTrue(
            "legacy backup rules must be wired",
            manifest.contains("android:fullBackupContent=\"@xml/backup_rules\"")
        )
        assertTrue(
            "API 31+ data-extraction rules must be wired",
            manifest.contains("android:dataExtractionRules=\"@xml/data_extraction_rules\"")
        )
    }
}
