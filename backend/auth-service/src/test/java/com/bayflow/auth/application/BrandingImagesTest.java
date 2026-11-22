package com.bayflow.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.bayflow.auth.domain.TenantAsset.Kind;

class BrandingImagesTest {

    @Test
    void aLargeLogoIsScaledDownAndStoredAsPngWithTransparency() throws IOException {
        byte[] upload = image(1600, 800, "png", true);

        BrandingImages.Processed logo = BrandingImages.process(Kind.LOGO, upload);

        assertThat(logo.contentType()).isEqualTo("image/png");
        assertThat(logo.width()).isEqualTo(512);
        assertThat(logo.height()).isEqualTo(256);
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(logo.content()));
        assertThat(stored.getColorModel().hasAlpha()).isTrue();
        assertThat(logo.sha256()).hasSize(64).isEqualTo(BrandingImages.sha256(logo.content()));
    }

    @Test
    void anIconIsMadeSquareOnATransparentBackgroundNotCropped() throws IOException {
        BrandingImages.Processed icon = BrandingImages.process(Kind.ICON, image(1200, 600, "jpeg", false));

        assertThat(icon.contentType()).isEqualTo("image/png");
        assertThat(icon.width()).isEqualTo(512);
        assertThat(icon.height()).isEqualTo(512);
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(icon.content()));
        assertThat(stored.getColorModel().hasAlpha()).isTrue();
        // The 512 x 256 picture sits in the middle: transparent above it, opaque in it.
        assertThat(stored.getRGB(256, 10) >>> 24).isZero();
        assertThat(stored.getRGB(100, 256) >>> 24).isEqualTo(255);
    }

    @Test
    void aDarkModeLogoFollowsTheLogoRules() throws IOException {
        BrandingImages.Processed logo = BrandingImages.process(Kind.LOGO_DARK, image(1600, 800, "png", true));
        assertThat(logo.contentType()).isEqualTo("image/png");
        assertThat(logo.width()).isEqualTo(512);
        assertThat(logo.height()).isEqualTo(256);
    }

    @Test
    void aSmallImageIsNeverScaledUp() throws IOException {
        BrandingImages.Processed logo = BrandingImages.process(Kind.LOGO, image(120, 40, "png", false));
        assertThat(logo.width()).isEqualTo(120);
        assertThat(logo.height()).isEqualTo(40);
    }

    @Test
    void aCoverBecomesAJpegAtMost2000PixelsWide() throws IOException {
        BrandingImages.Processed cover = BrandingImages.process(Kind.COVER, image(3000, 1500, "png", true));
        assertThat(cover.contentType()).isEqualTo("image/jpeg");
        assertThat(cover.width()).isEqualTo(2000);
        assertThat(cover.height()).isEqualTo(1000);
        assertThat(BrandingImages.isJpeg(cover.content())).isTrue();
    }

    @Test
    void metadataAndTrailingDataAreRemoved() throws IOException {
        byte[] jpeg = withExif(image(400, 300, "jpeg", false));
        byte[] withTrailer = concat(jpeg, "<script>alert(1)</script>".getBytes(StandardCharsets.US_ASCII));
        assertThat(contains(withTrailer, "Exif")).isTrue();

        BrandingImages.Processed cover = BrandingImages.process(Kind.COVER, withTrailer);

        assertThat(contains(cover.content(), "Exif")).isFalse();
        assertThat(contains(cover.content(), "GPS-SECRET")).isFalse();
        assertThat(contains(cover.content(), "<script>")).isFalse();
    }

    @Test
    void onlyPngAndJpegAreAccepted() throws IOException {
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"><script>alert(1)</script></svg>"
                .getBytes(StandardCharsets.UTF_8);
        assertRefused(Kind.LOGO, svg, HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-image");
        assertRefused(Kind.LOGO, image(10, 10, "gif", false), HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-image");
        assertRefused(Kind.LOGO, new byte[0], HttpStatus.BAD_REQUEST, "invalid-image");
    }

    @Test
    void aBrokenImageIsRefused() throws IOException {
        byte[] png = image(200, 200, "png", false);
        byte[] truncated = Arrays.copyOf(png, 40);
        assertRefused(Kind.LOGO, truncated, HttpStatus.BAD_REQUEST, "invalid-image");
    }

    @Test
    void eachKindHasItsOwnSizeLimit() {
        byte[] bigPng = new byte[1024 * 1024 + 1];
        System.arraycopy(new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0d, 0x0a, 0x1a, 0x0a }, 0, bigPng, 0, 8);
        assertRefused(Kind.LOGO, bigPng, HttpStatus.PAYLOAD_TOO_LARGE, "image-too-large");

        byte[] hugePng = new byte[3 * 1024 * 1024 + 1];
        System.arraycopy(bigPng, 0, hugePng, 0, 8);
        assertRefused(Kind.COVER, hugePng, HttpStatus.PAYLOAD_TOO_LARGE, "image-too-large");
    }

    @Test
    void anImageClaimingTooManyPixelsIsRefusedBeforeDecoding() throws IOException {
        byte[] png = image(10, 10, "png", false);
        // IHDR width and height (bytes 16..23): claim 50000 x 50000 pixels.
        byte[] bomb = png.clone();
        writeInt(bomb, 16, 50_000);
        writeInt(bomb, 20, 50_000);
        assertRefused(Kind.LOGO, bomb, HttpStatus.BAD_REQUEST, "invalid-image");
    }

    // ---- helpers ------------------------------------------------------------------

    static byte[] image(int width, int height, String format, boolean alpha) throws IOException {
        BufferedImage image = new BufferedImage(width, height,
                alpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(new Color(29, 78, 216, alpha ? 128 : 255));
        g.fillRect(0, 0, width / 2, height);
        g.setColor(Color.ORANGE);
        g.fillOval(width / 2, 0, width / 2, height);
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, format, out)) {
            throw new IllegalStateException("No writer for " + format);
        }
        return out.toByteArray();
    }

    /** Inserts an APP1 "Exif" segment with a pretend GPS value right after the JPEG's SOI marker. */
    static byte[] withExif(byte[] jpeg) {
        byte[] payload = concat("Exif\0\0".getBytes(StandardCharsets.US_ASCII),
                "MM\0*GPS-SECRET-51.5,-0.12".getBytes(StandardCharsets.US_ASCII));
        int length = payload.length + 2;
        byte[] segment = concat(new byte[] { (byte) 0xff, (byte) 0xe1, (byte) (length >> 8), (byte) length }, payload);
        return concat(concat(Arrays.copyOf(jpeg, 2), segment), Arrays.copyOfRange(jpeg, 2, jpeg.length));
    }

    static byte[] concat(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }

    static boolean contains(byte[] haystack, String needle) {
        return new String(haystack, StandardCharsets.ISO_8859_1).contains(needle);
    }

    private static void writeInt(byte[] b, int offset, int value) {
        b[offset] = (byte) (value >>> 24);
        b[offset + 1] = (byte) (value >>> 16);
        b[offset + 2] = (byte) (value >>> 8);
        b[offset + 3] = (byte) value;
    }

    private static void assertRefused(Kind kind, byte[] upload, HttpStatus status, String type) {
        assertThatThrownBy(() -> BrandingImages.process(kind, upload))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(status);
                    assertThat(e.getType()).isEqualTo(type);
                });
    }
}
