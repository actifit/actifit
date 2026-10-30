package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.exifinterface.media.ExifInterface;

import org.junit.Test;

import java.util.Arrays;

/** Upload EXIF: only picture tags are copied (the real copy runs on a device, see ExifCopyDeviceTest). */
public class ExifCopyTest {

    @Test
    public void noLayoutTagIsCopied() {
        String[] layout = {
                ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT, ExifInterface.TAG_JPEG_INTERCHANGE_FORMAT_LENGTH,
                ExifInterface.TAG_THUMBNAIL_IMAGE_WIDTH, ExifInterface.TAG_THUMBNAIL_IMAGE_LENGTH,
                ExifInterface.TAG_STRIP_OFFSETS, ExifInterface.TAG_STRIP_BYTE_COUNTS, ExifInterface.TAG_ROWS_PER_STRIP,
                ExifInterface.TAG_ORF_THUMBNAIL_IMAGE, ExifInterface.TAG_ORF_PREVIEW_IMAGE_START,
                ExifInterface.TAG_PIXEL_X_DIMENSION, ExifInterface.TAG_PIXEL_Y_DIMENSION};
        for (String tag : layout) {
            for (String copied : ExifCopy.PICTURE_TAGS) assertFalse(tag, tag.equals(copied));
        }
    }

    @Test
    public void orientationIsStillCopied() {
        assertTrue(Arrays.asList(ExifCopy.PICTURE_TAGS).contains(ExifInterface.TAG_ORIENTATION));
    }

    @Test
    public void locationIsNeverCopied() {
        for (String copied : ExifCopy.PICTURE_TAGS) assertFalse(copied, copied.startsWith("GPS"));
    }

    @Test
    public void identifyingTextIsNeverCopied() {
        String[] identifying = {
                ExifInterface.TAG_ARTIST, ExifInterface.TAG_CAMERA_OWNER_NAME, ExifInterface.TAG_IMAGE_DESCRIPTION,
                ExifInterface.TAG_IMAGE_UNIQUE_ID, ExifInterface.TAG_RELATED_SOUND_FILE, ExifInterface.TAG_USER_COMMENT,
                ExifInterface.TAG_BODY_SERIAL_NUMBER, ExifInterface.TAG_LENS_SERIAL_NUMBER, ExifInterface.TAG_COPYRIGHT};
        for (String tag : identifying) assertFalse(tag, Arrays.asList(ExifCopy.PICTURE_TAGS).contains(tag));
    }
}
