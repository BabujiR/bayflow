package com.bayflow.auth.domain;

/**
 * Google review requests at a location: {@code enabled} sends customers {@code url} (the location's
 * Google review link) a while after their invoice is paid; {@code delay} says how long.
 */
public record LocationReviews(boolean enabled, String url, Delay delay) {

    /** When the request goes out after the invoice is paid. */
    public enum Delay {
        TWO_HOURS, NEXT_MORNING, TWO_DAYS
    }

    /** Off, no link: every location until an admin sets them. */
    public static final LocationReviews OFF = new LocationReviews(false, null, Delay.NEXT_MORNING);
}
