package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.exifinterface.media.ExifInterface;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;

/**
 * Uploads are re-encoded from a bitmap, then the camera's EXIF is copied on. Copying the
 * camera's thumbnail pointer left IFD0 pointing past the end of the EXIF block; libexif
 * (sharp/libvips image proxies) then dropped Orientation and showed photos sideways.
 */
@RunWith(AndroidJUnit4.class)
public class ExifCopyDeviceTest {

    private static final int THUMB_OFFSET = 0x0201, THUMB_LENGTH = 0x0202, ORIENTATION = 0x0112;

    @Rule public TemporaryFolder tmp = new TemporaryFolder();

    @Test
    public void uploadKeepsOrientationAndCarriesNoDanglingThumbnailPointer() throws IOException {
        File dst = copyOfFixture("reencoded_plain.jpg");   // 32x16, as if downscaled from 64x32

        ExifCopy.write(cameraExif(), dst, 32, 16);

        Tiff t = Tiff.read(dst);
        assertFalse("thumbnail offset must not be copied", t.ifd0.containsKey(THUMB_OFFSET));
        assertFalse("thumbnail length must not be copied", t.ifd0.containsKey(THUMB_LENGTH));
        assertEquals(6, (int) t.ifd0.get(ORIENTATION));
        assertTrue("any thumbnail left must lie inside the EXIF block", t.thumbnailFits());

        ExifInterface out = new ExifInterface(dst.getAbsolutePath());
        assertEquals(ExifInterface.ORIENTATION_ROTATE_90,
                out.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0));
        assertEquals("TestCam", out.getAttribute(ExifInterface.TAG_MAKE));
        assertEquals(32, out.getAttributeInt(ExifInterface.TAG_PIXEL_X_DIMENSION, 0));
        assertEquals(16, out.getAttributeInt(ExifInterface.TAG_PIXEL_Y_DIMENSION, 0));
    }

    @Test
    public void copyingTheCamerasThumbnailPointerIsWhatBrokeUploads() throws IOException {
        // the old tag list copied these two; this proves the check above catches it
        // (relies on AndroidX ExifInterface 1.4.1 accepting these tags; delete it if a newer version refuses them)
        ExifInterface src = cameraExif();
        File dst = copyOfFixture("reencoded_plain.jpg");
        ExifInterface out = new ExifInterface(dst.getAbsolutePath());
        out.setAttribute(ExifInterface.TAG_ORIENTATION, src.getAttribute(ExifInterface.TAG_ORIENTATION));
        out.setAttribute(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT,
                src.getAttribute(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT));
        out.setAttribute(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH,
                src.getAttribute(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH));
        out.saveAttributes();

        Tiff t = Tiff.read(dst);
        assertTrue(t.ifd0.containsKey(THUMB_OFFSET) || !t.thumbnailFits());
    }

    private ExifInterface cameraExif() throws IOException {
        ExifInterface e = new ExifInterface(copyOfFixture("camera_orientation6_with_thumb.jpg").getAbsolutePath());
        // sanity: the fixture really has the camera's thumbnail pointer
        assertTrue(e.getAttributeInt(ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH, 0) > 0);
        return e;
    }

    private File copyOfFixture(String name) throws IOException {
        File f = tmp.newFile(name);
        try (InputStream in = getClass().getResourceAsStream("/exif/" + name)) {
            Files.copy(in, f.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
        return f;
    }

    /** Minimal reader of a JPEG's APP1 Exif TIFF: IFD0 short/long values and the IFD1 thumbnail pointer. */
    private static final class Tiff {
        final Map<Integer, Integer> ifd0 = new HashMap<>();
        long thumbOffset = -1, thumbLength = -1;
        int tiffLength;

        boolean thumbnailFits() {
            for (long v : new long[]{thumbOffset, thumbLength}) if (v < 0) return thumbOffset < 0 && thumbLength < 0;
            return thumbOffset + thumbLength <= tiffLength;
        }

        static Tiff read(File f) throws IOException {
            byte[] b = Files.readAllBytes(f.toPath());
            int i = 2;
            while (i + 4 < b.length && (b[i] & 0xff) == 0xff) {
                int marker = b[i + 1] & 0xff, len = ((b[i + 2] & 0xff) << 8) | (b[i + 3] & 0xff);
                if (marker == 0xe1 && new String(b, i + 4, 4, "ISO-8859-1").equals("Exif")) {
                    return parse(ByteBuffer.wrap(b, i + 10, len - 8).slice());
                }
                i += 2 + len;
            }
            throw new AssertionError("no Exif APP1 in output");
        }

        private static Tiff parse(ByteBuffer t) {
            t.order(t.get(0) == 'I' ? ByteOrder.LITTLE_ENDIAN : ByteOrder.BIG_ENDIAN);
            Tiff r = new Tiff();
            r.tiffLength = t.limit();
            int ifd0 = t.getInt(4);
            int next = readIfd(t, ifd0, r.ifd0);
            if (next != 0) {
                Map<Integer, Integer> ifd1 = new HashMap<>();
                readIfd(t, next, ifd1);
                if (ifd1.containsKey(THUMB_OFFSET)) r.thumbOffset = ifd1.get(THUMB_OFFSET);
                if (ifd1.containsKey(THUMB_LENGTH)) r.thumbLength = ifd1.get(THUMB_LENGTH);
            }
            if (r.ifd0.containsKey(THUMB_OFFSET)) r.thumbOffset = r.ifd0.get(THUMB_OFFSET);
            if (r.ifd0.containsKey(THUMB_LENGTH)) r.thumbLength = r.ifd0.get(THUMB_LENGTH);
            return r;
        }

        /** Reads one IFD's SHORT/LONG single values; returns the next IFD offset. */
        private static int readIfd(ByteBuffer t, int at, Map<Integer, Integer> into) {
            int n = t.getShort(at) & 0xffff;
            for (int k = 0; k < n; k++) {
                int e = at + 2 + 12 * k;
                int tag = t.getShort(e) & 0xffff, type = t.getShort(e + 2) & 0xffff;
                if (type == 3) into.put(tag, t.getShort(e + 8) & 0xffff);
                else if (type == 4) into.put(tag, t.getInt(e + 8));
                else into.put(tag, -1);
            }
            return t.getInt(at + 2 + 12 * n);
        }
    }
}
