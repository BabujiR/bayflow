package com.bayflow.auth.api;

import java.io.IOException;
import java.io.UncheckedIOException;

import com.bayflow.auth.application.BrandingImagesFixtures;

/** Small generated images for branding tests. */
final class ImageFixtures {

    private ImageFixtures() {
    }

    static byte[] png(int width, int height) {
        try {
            return BrandingImagesFixtures.image(width, height, "png", true);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static byte[] jpegWithExif(int width, int height) {
        try {
            return BrandingImagesFixtures.withExif(BrandingImagesFixtures.image(width, height, "jpeg", false));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
