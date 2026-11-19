package com.bayflow.auth.application;

import java.io.IOException;

/** Public access to {@link BrandingImagesTest}'s image helpers for tests in other packages. */
public final class BrandingImagesFixtures {

    private BrandingImagesFixtures() {
    }

    public static byte[] image(int width, int height, String format, boolean alpha) throws IOException {
        return BrandingImagesTest.image(width, height, format, alpha);
    }

    public static byte[] withExif(byte[] jpeg) {
        return BrandingImagesTest.withExif(jpeg);
    }
}
