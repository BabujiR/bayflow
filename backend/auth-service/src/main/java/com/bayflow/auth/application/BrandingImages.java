package com.bayflow.auth.application;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Iterator;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.http.HttpStatus;

import com.bayflow.auth.domain.TenantAsset.Kind;

/**
 * Checks and re-encodes uploaded branding images. Only PNG and JPEG are accepted, recognised by
 * their content (never by file name or declared type). Every image is decoded and written again,
 * which drops metadata such as EXIF location and anything hidden after the image data, and is
 * scaled down to the size the app shows it at. SVG is refused: it can carry scripts.
 */
final class BrandingImages {

    /** The upload limit and the largest stored size of each kind. */
    record Rules(long maxBytes, int maxWidth, int maxHeight, String contentType, String format) {
    }

    static final Rules LOGO = new Rules(1024 * 1024, 512, 512, "image/png", "png");
    static final Rules COVER = new Rules(3 * 1024 * 1024, 2000, 2000, "image/jpeg", "jpeg");
    /** Square: anything else is centred on a transparent square, never cropped. */
    static final Rules ICON = new Rules(1024 * 1024, 512, 512, "image/png", "png");

    /** Refuse images whose header claims more pixels than this before decoding (decompression bombs). */
    static final long MAX_SOURCE_PIXELS = 40_000_000L;

    /** A checked, re-encoded image. */
    record Processed(String contentType, byte[] content, String sha256, int width, int height) {
    }

    private BrandingImages() {
    }

    static Rules rulesFor(Kind kind) {
        return switch (kind) {
            case LOGO, LOGO_DARK -> LOGO;
            case ICON -> ICON;
            case COVER -> COVER;
        };
    }

    static Processed process(Kind kind, byte[] upload) {
        Rules rules = rulesFor(kind);
        if (upload == null || upload.length == 0) {
            throw invalid("The file is empty.");
        }
        if (upload.length > rules.maxBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "image-too-large", "Payload Too Large",
                    "The " + label(kind) + " may be at most " + (rules.maxBytes() / (1024 * 1024)) + " MB.");
        }
        if (!isPng(upload) && !isJpeg(upload)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "unsupported-image",
                    "Unsupported Media Type", "Upload a PNG or JPEG image.");
        }
        BufferedImage source = decode(upload);
        BufferedImage scaled = scale(source, rules, kind != Kind.COVER);
        if (kind == Kind.ICON) {
            scaled = squared(scaled);
        }
        byte[] content = encode(scaled, rules);
        return new Processed(rules.contentType(), content, sha256(content), scaled.getWidth(), scaled.getHeight());
    }

    static boolean isPng(byte[] b) {
        return b.length >= 8 && (b[0] & 0xff) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0d && b[5] == 0x0a && b[6] == 0x1a && b[7] == 0x0a;
    }

    static boolean isJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xff) == 0xff && (b[1] & 0xff) == 0xd8 && (b[2] & 0xff) == 0xff;
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static BufferedImage decode(byte[] upload) {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            if (in == null) {
                throw invalid("The image could not be read.");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) {
                throw invalid("The image could not be read.");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels <= 0 || pixels > MAX_SOURCE_PIXELS) {
                    throw invalid("The image is too large in pixels. Use one under 40 megapixels.");
                }
                BufferedImage image = reader.read(0);
                if (image == null) {
                    throw invalid("The image could not be read.");
                }
                return image;
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException api) {
                throw api;
            }
            throw invalid("The image could not be read. Save it again as PNG or JPEG (RGB) and retry.");
        }
    }

    /** Scales down to fit the rules (never up), keeping transparency for logos. */
    private static BufferedImage scale(BufferedImage source, Rules rules, boolean keepAlpha) {
        double factor = Math.min(1.0, Math.min((double) rules.maxWidth() / source.getWidth(),
                (double) rules.maxHeight() / source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * factor));
        int height = Math.max(1, (int) Math.round(source.getHeight() * factor));
        BufferedImage target = new BufferedImage(width, height,
                keepAlpha ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = target.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (!keepAlpha) {
                // JPEG has no transparency: transparent parts of a PNG cover become white.
                g.setColor(java.awt.Color.WHITE);
                g.fillRect(0, 0, width, height);
            }
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return target;
    }

    /** Centres the image on a transparent square as large as its longer side. */
    private static BufferedImage squared(BufferedImage image) {
        int side = Math.max(image.getWidth(), image.getHeight());
        if (image.getWidth() == side && image.getHeight() == side) {
            return image;
        }
        BufferedImage square = new BufferedImage(side, side, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = square.createGraphics();
        try {
            g.drawImage(image, (side - image.getWidth()) / 2, (side - image.getHeight()) / 2, null);
        } finally {
            g.dispose();
        }
        return square;
    }

    private static byte[] encode(BufferedImage image, Rules rules) {
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            ImageWriter writer = ImageIO.getImageWritersByFormatName(rules.format()).next();
            try (ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
                writer.setOutput(out);
                ImageWriteParam param = writer.getDefaultWriteParam();
                if ("jpeg".equals(rules.format())) {
                    param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                    param.setCompressionQuality(0.85f);
                }
                writer.write(null, new IIOImage(image, null, null), param);
            } finally {
                writer.dispose();
            }
            return bytes.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode image", e);
        }
    }

    private static String label(Kind kind) {
        return switch (kind) {
            case LOGO -> "logo";
            case LOGO_DARK -> "dark-mode logo";
            case ICON -> "icon";
            case COVER -> "cover photo";
        };
    }

    private static ApiException invalid(String detail) {
        return ApiException.badRequest("invalid-image", detail);
    }
}
