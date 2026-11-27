package com.bayflow.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.bayflow.auth.domain.AuditEvent.Action;
import com.bayflow.auth.domain.User;
import com.bayflow.auth.domain.UserAvatar;
import com.bayflow.auth.infrastructure.UserAvatarRepository;
import com.bayflow.auth.infrastructure.UserRepository;

/**
 * How users show in the app: their initials (default), one of the built-in characters, or their
 * own photo. Users choose their own; a workshop admin can only put someone back to initials
 * (an unsuitable photo, say), never upload for them.
 */
@Service
public class AvatarService {

    /** The built-in characters' codes; the web app draws them. */
    public static final Set<String> CHARACTERS = Set.of("mechanic", "advisor", "manager", "owner", "customer",
            "wrench", "car", "truck", "bolt", "rocket", "fox", "owl");

    private static final Logger log = LoggerFactory.getLogger(AvatarService.class);

    private final UserRepository users;
    private final UserAvatarRepository photos;
    private final AuditLog audit;
    private final Clock clock;

    public AvatarService(UserRepository users, UserAvatarRepository photos, AuditLog audit, Clock clock) {
        this.users = users;
        this.photos = photos;
        this.audit = audit;
        this.clock = clock;
    }

    /** An admin puts someone back to their initials (an unsuitable photo, say): audited. */
    @Transactional
    public User resetByAdmin(UUID userId) {
        User user = useInitials(userId);
        audit.record(Action.AVATAR_RESET, user, null);
        return user;
    }

    @Transactional
    public User useInitials(UUID userId) {
        User user = find(userId);
        user.useInitials(clock.instant());
        photos.findById(userId).ifPresent(photos::delete);
        return user;
    }

    @Transactional
    public User useCharacter(UUID userId, String character) {
        if (character == null || !CHARACTERS.contains(character)) {
            throw ApiException.badRequest("invalid-character", "Choose one of the characters shown.");
        }
        User user = find(userId);
        user.useCharacter(character, clock.instant());
        photos.findById(userId).ifPresent(photos::delete);
        return user;
    }

    /** Checks and re-encodes the upload (PNG or JPEG, up to 2 MB): cut square, 256 px, no metadata. */
    @Transactional
    public User usePhoto(UUID userId, byte[] upload) {
        BrandingImages.Processed photo = BrandingImages.processAvatar(upload);
        User user = find(userId);
        Instant now = clock.instant();
        Optional<UserAvatar> existing = photos.findById(userId);
        if (existing.isPresent()) {
            existing.get().replace(photo.contentType(), photo.content(), photo.sha256(), now);
        } else {
            photos.save(UserAvatar.of(userId, photo.contentType(), photo.content(), photo.sha256(), now));
        }
        user.usePhoto(photo.sha256(), now);
        log.info("User {} set a profile photo", userId);
        return user;
    }

    /** The photo, when its address's version matches the current one (else none: an old or guessed address). */
    @Transactional(readOnly = true)
    public Optional<UserAvatar> photo(UUID userId, String version) {
        return photos.findById(userId).filter(p -> version(p.getSha256()).equals(version));
    }

    /** The photo's address (same workshop's address; the version makes it unguessable and cacheable). */
    public static String photoUrl(User user) {
        if (user.getAvatarKind() != User.AvatarKind.PHOTO || user.getAvatarSha256() == null) {
            return null;
        }
        return "/api/v1/tenants/current/avatars/" + user.getId() + "?v=" + version(user.getAvatarSha256());
    }

    static String version(String sha256) {
        return sha256.substring(0, 24);
    }

    private User find(UUID userId) {
        return users.findById(userId).orElseThrow(() -> ApiException.notFound("User"));
    }
}
