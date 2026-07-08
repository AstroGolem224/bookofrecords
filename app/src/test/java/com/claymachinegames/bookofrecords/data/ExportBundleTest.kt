package com.claymachinegames.bookofrecords.data

import android.content.Context
import android.content.pm.ProviderInfo
import androidx.test.core.app.ApplicationProvider
import com.claymachinegames.bookofrecords.domain.Marker
import com.claymachinegames.bookofrecords.domain.RecordingMeta
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.File
import java.util.zip.ZipInputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowDocumentsAwareContentResolver::class])
class ExportBundleTest {

    @get:Rule val tempFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var library: SafLibrary

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FakeDocumentsProvider.rootDir = tempFolder.root
        val providerInfo = ProviderInfo().apply {
            authority = FakeDocumentsProvider.AUTHORITY
            exported = true
            grantUriPermissions = true
            readPermission = android.Manifest.permission.MANAGE_DOCUMENTS
            writePermission = android.Manifest.permission.MANAGE_DOCUMENTS
        }
        Robolectric.buildContentProvider(FakeDocumentsProvider::class.java).create(providerInfo)
        library = SafLibrary(context, FakeDocumentsProvider.treeUri())
    }

    @Test
    fun exportZipBytesBundlesAudioJsonAndLabelsPerEntry() = runBlocking {
        val dir = File(tempFolder.root, "2026-07-08").apply { mkdirs() }
        File(dir, "a.m4a").writeText("audio-bytes")
        val meta = RecordingMeta(
            file = "a.m4a", startedAt = "x",
            markers = listOf(Marker(timeMs = 1000, label = "Alice")),
        )
        File(dir, "a.json").writeText(meta.toJson())
        val entry = library.list().first()

        val zipBytes = exportZipBytes(context, library, listOf(entry))

        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) { names.add(e.name); e = zip.nextEntry }
        }
        assertEquals(
            setOf("2026-07-08/a.m4a", "2026-07-08/a.json", "2026-07-08/a.labels.txt"),
            names,
        )
    }

    @Test
    fun exportZipBytesBundlesMultipleEntriesUnderTheirOwnDateGroups() = runBlocking {
        val dirA = File(tempFolder.root, "2026-07-08").apply { mkdirs() }
        File(dirA, "a.m4a").writeText("audio-a")
        File(dirA, "a.json").writeText(RecordingMeta(file = "a.m4a", startedAt = "x").toJson())
        val dirB = File(tempFolder.root, "2026-07-07").apply { mkdirs() }
        File(dirB, "b.m4a").writeText("audio-b")
        File(dirB, "b.json").writeText(RecordingMeta(file = "b.m4a", startedAt = "x").toJson())
        val entries = library.list()
        assertEquals(2, entries.size)

        val zipBytes = exportZipBytes(context, library, entries)

        val names = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
            var e = zip.nextEntry
            while (e != null) { names.add(e.name); e = zip.nextEntry }
        }
        assertEquals(
            setOf(
                "2026-07-08/a.m4a", "2026-07-08/a.json", "2026-07-08/a.labels.txt",
                "2026-07-07/b.m4a", "2026-07-07/b.json", "2026-07-07/b.labels.txt",
            ),
            names,
        )
    }
}
