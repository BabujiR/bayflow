package com.bayflow.workshop.api;

/** Keeps page and size request parameters within sensible bounds. */
final class Paging {

    static final int MAX_SIZE = 100;

    private Paging() {
    }

    static int page(int page) {
        return Math.max(0, page);
    }

    static int size(int size) {
        return Math.min(Math.max(1, size), MAX_SIZE);
    }
}
