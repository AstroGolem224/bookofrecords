package com.claymachinegames.bookofrecords.data

import android.content.ContentResolver
import android.content.Context
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import androidx.test.core.app.ApplicationProvider
import com.claymachinegames.bookofrecords.domain.RecordingMeta
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
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

/**
 * Real Android's DocumentsProvider.query(Uri, String[], String, String[], String) is `final`
 * and unconditionally throws ("Pre-Android-O query format not supported") — it exists only so
 * legacy callers fail loudly; real clients are expected to go through
 * query(Uri, String[], Bundle, CancellationSignal). On a real device, ContentResolver's IPC
 * transport silently upgrades old-style calls to the Bundle form before they ever reach the
 * provider. Robolectric's ShadowContentResolver doesn't reproduce that transport-side shim: it
 * invokes ContentProvider#query(5-arg) directly, which lands on DocumentsProvider's throwing
 * override. androidx.documentfile's TreeDocumentFile.listFiles()/isDirectory()/exists() still use
 * that 5-arg call (true in both 1.0.1 and 1.1.0), so any DocumentFile-based SAF traversal fails
 * under Robolectric without this shim — independent of SafLibrary's own logic. This shadow
 * *extends* ShadowContentResolver (rather than replacing it via a bare @Implements) so every
 * other ContentResolver operation (call(), openFile(), etc. — used by DocumentsContract.rename,
 * openInputStream, etc.) keeps its normal Robolectric behavior; only the legacy 5-arg query for
 * our fake authority is redirected to the Bundle-based path DocumentsProvider actually accepts.
 */
@Implements(ContentResolver::class)
class ShadowDocumentsAwareContentResolver : ShadowContentResolver() {

    @Implementation
    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        if (uri.authority != FakeDocumentsProvider.AUTHORITY) {
            return super.query(uri, projection, selection, selectionArgs, sortOrder)
        }
        val provider = ShadowContentResolver.getProvider(uri)
            ?: error("No provider registered for ${FakeDocumentsProvider.AUTHORITY}")
        return provider.query(uri, projection, Bundle(), null)
    }

    // Same story as query() above: DocumentsProvider.delete(Uri, String, String[]) is also
    // `final` and unconditionally throws ("Pre-Android-O delete format not supported"). On a
    // real device, ContentResolver.delete() never actually reaches that legacy method for a
    // DocumentsProvider Uri: DocumentsContract's SAF machinery routes deletes through
    // ContentProvider#call(METHOD_DELETE_DOCUMENT, ...), which dispatches to
    // DocumentsProvider.deleteDocument(String) — the method providers are meant to implement.
    // ContentProvider's own delete(Uri, Bundle) default (which ContentResolver.delete(Uri,
    // String, String[]) also funnels through on a real device) just re-derives selection/args
    // and calls the throwing 3-arg delete() again, so redirecting there doesn't help either.
    // Skip straight to the real work: extract the document id and call deleteDocument(), same
    // as FakeDocumentsProvider.deleteDocument() (which already exists and is correct) expects.
    @Implementation
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<String>?): Int {
        if (uri.authority != FakeDocumentsProvider.AUTHORITY) {
            return super.delete(uri, selection, selectionArgs)
        }
        val provider = ShadowContentResolver.getProvider(uri) as? DocumentsProvider
            ?: error("No provider registered for ${FakeDocumentsProvider.AUTHORITY}")
        provider.deleteDocument(DocumentsContract.getDocumentId(uri))
        return 1
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], shadows = [ShadowDocumentsAwareContentResolver::class])
class SafLibraryTest {

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
        Robolectric.buildContentProvider(FakeDocumentsProvider::class.java)
            .create(providerInfo)
        library = SafLibrary(context, FakeDocumentsProvider.treeUri())
    }

    private fun dateDir(name: String): File = File(tempFolder.root, name).apply { mkdirs() }

    @Test
    fun listGroupsRecordingsByDateFolder() {
        val dir = dateDir("2026-07-08")
        File(dir, "a.m4a").writeText("audio")
        File(dir, "a.json").writeText(RecordingMeta(file = "a.m4a", startedAt = "x").toJson())

        val entries = library.list()

        assertEquals(1, entries.size)
        assertEquals("a", entries[0].baseName)
        assertEquals("2026-07-08", entries[0].dateGroup)
    }

    @Test
    fun listSkipsAudioFilesWithoutMatchingMeta() {
        val dir = dateDir("2026-07-08")
        File(dir, "a.m4a").writeText("audio")
        // no a.json

        val entries = library.list()

        assertEquals(1, entries.size)
        assertEquals(null, entries[0].metaUri)
        assertEquals(0, entries[0].markerCount)
    }

    @Test
    fun renameUpdatesBothAudioAndMetaFilesAndMetaFileContent() {
        val dir = dateDir("2026-07-08")
        File(dir, "old.m4a").writeText("audio")
        File(dir, "old.json").writeText(RecordingMeta(file = "old.m4a", startedAt = "x").toJson())
        val entry = library.list().first()

        library.rename(entry, "new")

        val renamedAudio = File(dir, "new.m4a")
        val renamedMeta = File(dir, "new.json")
        assertTrue(renamedAudio.exists())
        assertTrue(renamedMeta.exists())
        assertEquals("new.m4a", RecordingMeta.fromJson(renamedMeta.readText()).file)
    }

    @Test(expected = LibraryUnavailableException::class)
    fun listThrowsWhenUriIsNotAValidTreeUri() {
        // DocumentFile.fromTreeUri() doesn't return null here (that only happens pre-API-21) —
        // it calls DocumentsContract.getTreeDocumentId() unguarded, which throws
        // IllegalArgumentException on a structurally invalid tree Uri like this "document" (not
        // "tree") one. SafLibrary.list() catches that alongside SecurityException and reports it
        // as the same "folder unreachable" condition, since both mean the stored Uri is unusable.
        val notATreeUri = android.net.Uri.parse("content://${FakeDocumentsProvider.AUTHORITY}/document/not-a-tree")
        SafLibrary(context, notATreeUri).list()
    }
}
