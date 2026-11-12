package com.aiyifan.app.feature.mine

import android.app.Application
import android.Manifest
import android.content.pm.PackageManager
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Looper
import android.provider.MediaStore
import android.view.View
import androidx.lifecycle.ViewModelProvider
import com.aiyifan.app.R
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(application = Application::class, sdk = [35])
class HeaderImageSaverTest {
    private val context get() = RuntimeEnvironment.getApplication()
    private lateinit var provider: GalleryProvider

    @Before fun registerGallery() {
        provider = GalleryProvider(File(context.cacheDir, "saved-test.webp"))
        provider.attachInfo(context, ProviderInfo().apply { authority = "media" })
        ShadowContentResolver.registerProviderInternal("media", provider)
    }

    @Test fun `save copies packaged bytes and publishes pending media row`() {
        HeaderImageSaver.save(context, "mine_header_001.webp")
        assertArrayEquals(context.assets.open("mine_header/mine_header_001.webp").use { it.readBytes() },
            provider.file.readBytes())
        assertEquals("image/webp", provider.inserted!!.getAsString(MediaStore.Images.Media.MIME_TYPE))
        assertEquals("Pictures/Aiyifan", provider.inserted!!.getAsString(MediaStore.Images.Media.RELATIVE_PATH))
        assertEquals(1, provider.inserted!!.getAsInteger(MediaStore.Images.Media.IS_PENDING))
        assertEquals(0, provider.updated!!.getAsInteger(MediaStore.Images.Media.IS_PENDING))
        assertFalse(provider.deleted)
    }

    @Test fun `write failure removes incomplete gallery item`() {
        shadowOf(context.contentResolver).registerOutputStream(
            Uri.parse("content://media/external/images/media/42"), object : java.io.OutputStream() {
                override fun write(value: Int) { throw IOException("Simulated full storage") }
            })
        assertThrows(IOException::class.java) { HeaderImageSaver.save(context, "mine_header_001.webp") }
        assertTrue(provider.deleted)
        assertNull(provider.updated)
    }

    @Test fun `publish failure removes incomplete gallery item`() {
        provider.failPublish = true
        assertThrows(IOException::class.java) { HeaderImageSaver.save(context, "mine_header_001.webp") }
        assertTrue(provider.deleted)
        assertFalse(provider.file.exists())
    }

    @Test fun `missing source creates no gallery item`() {
        assertThrows(IOException::class.java) { HeaderImageSaver.save(context, "mine_header_999.webp") }
        assertNull(provider.inserted)
    }

    @Test fun `save survives viewer recreation and rejects duplicate requests`() {
        provider.allowWrite = CountDownLatch(1)
        val controller = Robolectric.buildActivity(HeaderImageActivity::class.java,
            Intent().putExtra(HeaderImageActivity.EXTRA_IMAGE, "mine_header_001.webp"))
        controller.get().setTheme(R.style.AppTheme)
        controller.setup().visible()
        fun button() = controller.get().window.decorView.findViewWithTag<View>("header_image_download")
        try {
            button().performClick()
            shadowOf(Looper.getMainLooper()).idle()
            assertTrue(provider.writeStarted.await(5, TimeUnit.SECONDS))
            val model = ViewModelProvider(controller.get())[HeaderImageSaveViewModel::class.java]
            assertFalse(button().isEnabled)
            model.save(context, "mine_header_001.webp")
            controller.recreate()
            assertSame(model, ViewModelProvider(controller.get())[HeaderImageSaveViewModel::class.java])
            assertFalse(button().isEnabled)
            provider.allowWrite!!.countDown()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            while (!button().isEnabled && System.nanoTime() < deadline) {
                Thread.sleep(10)
                shadowOf(Looper.getMainLooper()).idle()
            }
            assertTrue(button().isEnabled)
            assertEquals(1, provider.insertCount)
            assertEquals(0, provider.updated!!.getAsInteger(MediaStore.Images.Media.IS_PENDING))
        } finally {
            provider.allowWrite!!.countDown()
            controller.pause().stop().destroy()
        }
    }

    @Test @Config(sdk = [28]) fun `legacy save preserves bytes in public pictures directory`() {
        val uri = HeaderImageSaver.save(context, "mine_header_001.webp")
        val file = File(uri.path!!)
        try {
            assertEquals("Aiyifan", file.parentFile!!.name)
            assertEquals("Pictures", file.parentFile!!.parentFile!!.name)
            assertArrayEquals(context.assets.open("mine_header/mine_header_001.webp").use { it.readBytes() }, file.readBytes())
        } finally { file.delete() }
    }

    @Test @Config(sdk = [28]) fun `legacy permission denial does not save an image`() {
        val controller = Robolectric.buildActivity(HeaderImageActivity::class.java,
            Intent().putExtra(HeaderImageActivity.EXTRA_IMAGE, "mine_header_001.webp"))
        controller.get().setTheme(R.style.AppTheme)
        controller.setup().visible()
        try {
            controller.get().window.decorView.findViewWithTag<View>("header_image_download").performClick()
            val request = shadowOf(controller.get()).lastRequestedPermission
            assertArrayEquals(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), request.requestedPermissions)
            controller.get().onRequestPermissionsResult(request.requestCode, request.requestedPermissions,
                intArrayOf(PackageManager.PERMISSION_DENIED))
            shadowOf(Looper.getMainLooper()).idle()
            assertNull(provider.inserted)
            assertEquals(context.getString(R.string.mine_image_save_permission),
                org.robolectric.shadows.ShadowToast.getTextOfLatestToast())
        } finally { controller.pause().stop().destroy() }
    }

    private class GalleryProvider(val file: File) : ContentProvider() {
        var inserted: ContentValues? = null
        var updated: ContentValues? = null
        var deleted = false
        var failPublish = false
        var insertCount = 0
        var allowWrite: CountDownLatch? = null
        val writeStarted = CountDownLatch(1)
        override fun onCreate() = true
        override fun insert(uri: Uri, values: ContentValues?): Uri {
            inserted = ContentValues(values)
            insertCount++
            return Uri.parse("content://media/external/images/media/42")
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
            writeStarted.countDown()
            if (allowWrite?.await(10, TimeUnit.SECONDS) == false) throw java.io.FileNotFoundException("Timed out")
            return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_CREATE or
                ParcelFileDescriptor.MODE_WRITE_ONLY or ParcelFileDescriptor.MODE_TRUNCATE)
        }
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
            updated = ContentValues(values)
            return if (failPublish) 0 else 1
        }
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
            deleted = true
            file.delete()
            return 1
        }
        override fun getType(uri: Uri) = "image/webp"
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? = null
    }
}
