package com.claymachinegames.bookofrecords.data

import android.content.Context
import android.content.pm.ProviderInfo
import androidx.documentfile.provider.DocumentFile
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowDocumentsAwareContentResolver::class])
class MoverRobolectricTest {

    @get:Rule val rootFolder = TemporaryFolder()

    private lateinit var context: Context
    private lateinit var mover: Mover
    private lateinit var root: DocumentFile

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        FakeDocumentsProvider.rootDir = rootFolder.root
        // create(String authority) builds a default ProviderInfo with exported = false, which
        // trips DocumentsProvider.attachInfo()'s real (unshadowed) "Provider must be exported"
        // check. SafLibraryTest works around the same constraint with an explicit ProviderInfo;
        // mirrored here rather than relying on the convenience overload.
        val providerInfo = ProviderInfo().apply {
            authority = FakeDocumentsProvider.AUTHORITY
            exported = true
            grantUriPermissions = true
            readPermission = android.Manifest.permission.MANAGE_DOCUMENTS
            writePermission = android.Manifest.permission.MANAGE_DOCUMENTS
        }
        Robolectric.buildContentProvider(FakeDocumentsProvider::class.java)
            .create(providerInfo)
        root = DocumentFile.fromTreeUri(context, FakeDocumentsProvider.treeUri())!!
        mover = Mover(context, RecordingRepository(context))
    }

    /** Both audio+meta live under the same fake provider (a "source" subdir) so that
     * moveOne()'s subsequent local.delete(entry) — which goes through ContentResolver, not
     * java.io — resolves to a real, deletable document instead of an unroutable file:// Uri. */
    private fun sourceEntry(baseName: String, audioBytes: String, metaBytes: String): RecordingEntry {
        val sourceDir = root.findFile("source") ?: root.createDirectory("source")!!
        val audio = sourceDir.createFile("audio/mp4", "$baseName.m4a")!!
        context.contentResolver.openOutputStream(audio.uri)!!.use { it.write(audioBytes.toByteArray()) }
        val meta = sourceDir.createFile("application/json", "$baseName.json")!!
        context.contentResolver.openOutputStream(meta.uri)!!.use { it.write(metaBytes.toByteArray()) }
        return RecordingEntry(
            audioUri = audio.uri, metaUri = meta.uri, baseName = baseName,
            addedAtEpochSec = 0, durationMs = 1000, markerCount = 0, dateGroup = "2026-07-08",
        )
    }

    @Test
    fun moveOneCopiesAudioAndMetaThenDeletesSourceWhenBothSucceed() {
        val entry = sourceEntry("a", "audio-bytes", """{"file":"a.m4a"}""")

        mover.moveOne(root, entry)

        val dateDir = root.findFile("2026-07-08")!!
        assertEquals("audio-bytes", File(rootFolder.root, "2026-07-08/a.m4a").readText())
        assertEquals("""{"file":"a.m4a"}""", File(rootFolder.root, "2026-07-08/a.json").readText())
        assertTrue(root.findFile("source")!!.listFiles().isEmpty())   // source deleted after successful move
    }

    @Test
    fun copyIfNeededSkipsRewriteWhenDestinationAlreadyMatchesSourceLength() {
        val entry = sourceEntry("b", "same-length!", "{}")
        val dir = root.createDirectory("2026-07-08")!!
        val existing = dir.createFile("audio/mp4", "b.m4a")!!
        context.contentResolver.openOutputStream(existing.uri)!!.use { it.write("same-length!".toByteArray()) }

        val result = mover.copyIfNeeded(dir, "b.m4a", entry.audioUri)

        assertTrue(result)
        assertEquals("same-length!", File(rootFolder.root, "2026-07-08/b.m4a").readText())
    }

    @Test
    fun findOrCreateDirReusesAnExistingDirectoryInsteadOfDuplicating() {
        val first = mover.findOrCreateDir(root, "2026-07-08")
        val second = mover.findOrCreateDir(root, "2026-07-08")

        assertEquals(first.uri, second.uri)
        assertEquals(1, root.listFiles().count { it.isDirectory && it.name == "2026-07-08" })
    }
}
