package com.bayflow.auth.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.bayflow.auth.domain.Announcement;
import com.bayflow.auth.domain.Announcement.State;
import com.bayflow.auth.domain.Announcement.Style;
import com.bayflow.auth.infrastructure.AnnouncementRepository;
import com.bayflow.security.TenantContext;

/**
 * The current workshop's announcements (in {@link TenantContext}). The web app shows the live
 * ones as one line, the most important first, with "N more" for the rest.
 */
@Service
public class AnnouncementService {

    /** How many a workshop may keep (live, scheduled and ended together). */
    public static final int MAX_PER_WORKSHOP = 10;
    public static final int MAX_LENGTH = 200;

    /** Control characters and line separators: an announcement is one line of plain text. */
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cntrl}\\u2028\\u2029]");

    /** Important first, then the one ending soonest (open-ended last), then the oldest. */
    static final Comparator<Announcement> SHOWN_ORDER = Comparator
            .comparing((Announcement a) -> a.getStyle() == Style.IMPORTANT ? 0 : 1)
            .thenComparing(Announcement::getEndsAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Announcement::getCreatedAt)
            .thenComparing(Announcement::getId);

    public record Draft(String text, Style style, Instant startsAt, Instant endsAt) {
    }

    private final AnnouncementRepository announcements;
    private final Clock clock;

    public AnnouncementService(AnnouncementRepository announcements, Clock clock) {
        this.announcements = announcements;
        this.clock = clock;
    }

    /** Every announcement of the workshop, oldest first (the admin's list). */
    @Transactional(readOnly = true)
    public List<Announcement> all() {
        return announcements.findAllByOrderByCreatedAtAscIdAsc();
    }

    /** The ones live now, in the order they are shown. */
    @Transactional(readOnly = true)
    public List<Announcement> live() {
        Instant now = clock.instant();
        return announcements.findAllByOrderByCreatedAtAscIdAsc().stream()
                .filter(a -> a.stateAt(now) == State.LIVE)
                .sorted(SHOWN_ORDER)
                .toList();
    }

    @Transactional
    public Announcement create(Draft draft) {
        TenantContext.require();
        Draft checked = check(draft);
        if (announcements.count() >= MAX_PER_WORKSHOP) {
            throw ApiException.conflict("too-many-announcements", "A workshop can keep at most "
                    + MAX_PER_WORKSHOP + " announcements. Delete one you no longer need.");
        }
        return announcements.saveAndFlush(Announcement.create(checked.text(), checked.style(), checked.startsAt(),
                checked.endsAt(), clock.instant()));
    }

    /** Changes one; {@code version} must match what the caller read (409 otherwise). */
    @Transactional
    public Announcement update(UUID id, Draft draft, long version) {
        Draft checked = check(draft);
        Announcement announcement = find(id);
        if (announcement.getVersion() != null && announcement.getVersion() != version) {
            throw ApiException.conflict("concurrent-update",
                    "The announcement was changed by someone else. Reload and try again.");
        }
        announcement.update(checked.text(), checked.style(), checked.startsAt(), checked.endsAt(), clock.instant());
        return announcements.saveAndFlush(announcement);
    }

    @Transactional
    public void delete(UUID id) {
        announcements.delete(find(id));
    }

    private Announcement find(UUID id) {
        return announcements.findById(id).orElseThrow(() -> ApiException.notFound("The announcement"));
    }

    private static Draft check(Draft draft) {
        String text = text(draft.text());
        if (text == null) {
            throw ApiException.badRequest("invalid-announcement", "Write the announcement.");
        }
        if (draft.startsAt() != null && draft.endsAt() != null && !draft.startsAt().isBefore(draft.endsAt())) {
            throw ApiException.badRequest("invalid-announcement-window", "The announcement must end after it starts.");
        }
        return new Draft(text, draft.style() == null ? Style.INFO : draft.style(), draft.startsAt(), draft.endsAt());
    }

    /** One line of plain text, or null when empty. The web app shows it as text, never as HTML. */
    static String text(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String text = CONTROL.matcher(value).replaceAll(" ").trim().replaceAll(" {2,}", " ");
        if (text.length() > MAX_LENGTH) {
            throw ApiException.badRequest("invalid-announcement",
                    "An announcement may be at most " + MAX_LENGTH + " characters.");
        }
        return text.isEmpty() ? null : text;
    }
}
