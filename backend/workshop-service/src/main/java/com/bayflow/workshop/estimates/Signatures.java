package com.bayflow.workshop.estimates;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Iterator;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import com.bayflow.workshop.application.ApiException;

/**
 * A drawn signature from the approval page: a PNG data URL, checked and re-encoded (so only
 * pixels are kept, nothing else the browser put in the file).
 */
final class Signatures {

    private static final String PREFIX = "data:image/png;base64,";
    /** Base64 of a signature this size is far more than a pad ever draws. */
    private static final int MAX_DATA_URL = 300_000;
    private static final int MAX_WIDTH = 2000;
    private static final int MAX_HEIGHT = 1000;
    private static final int MAX_BYTES = 200 * 1024;

    private Signatures() {
    }

    static byte[] png(String dataUrl) {
        if (dataUrl == null || !dataUrl.startsWith(PREFIX) || dataUrl.length() > MAX_DATA_URL) {
            throw invalid();
        }
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(dataUrl.substring(PREFIX.length()));
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (bytes.length < 8 || (bytes[0] & 0xff) != 0x89 || bytes[1] != 'P' || bytes[2] != 'N' || bytes[3] != 'G') {
            throw invalid();
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
            if (input == null || !readers.hasNext()) {
                throw invalid();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                // The size from the header, before decoding: a small file can claim a huge image.
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width > MAX_WIDTH || height > MAX_HEIGHT || width < 20 || height < 10) {
                    throw invalid();
                }
                BufferedImage image = reader.read(0);
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                ImageIO.write(image, "png", out);
                if (out.size() > MAX_BYTES) {
                    throw invalid();
                }
                return out.toByteArray();
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return ApiException.badRequest("invalid-signature", "Sign in the box, then confirm.");
    }
}
