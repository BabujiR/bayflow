package com.bayflow.ops;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Counts in PostgreSQL (the service's {@code <prefix>_rate_limits} table), shared by every
 * instance of the service. Keys are stored hashed: no e-mail addresses or IPs in the table. Each hit commits on its own connection, so a request that fails (a
 * wrong password, say) still counts. If the database can't be reached the hit is let through
 * (logged): the service's own database being down fails the request anyway.
 */
public class JdbcRateLimitStore implements RateLimitStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcRateLimitStore.class);
    private static final Pattern TABLE = Pattern.compile("([a-z_]{1,40}\\.)?[a-z_]{1,40}");
    /** Old windows are deleted every this many hits. */
    private static final int CLEANUP_EVERY = 500;
    /** Windows older than this are deleted (the longest limit is one hour). */
    private static final Duration KEEP = Duration.ofHours(2);

    private final DataSource dataSource;
    private final String table;
    private final AtomicInteger sinceCleanup = new AtomicInteger();

    public JdbcRateLimitStore(DataSource dataSource, String table) {
        if (!TABLE.matcher(table).matches()) {
            throw new IllegalArgumentException("Bad rate limit table name: " + table);
        }
        this.dataSource = dataSource;
        this.table = table;
    }

    @Override
    public int hit(String key, Instant now, Duration window) {
        String sql = "insert into " + table + " as r (bucket, window_start, hits) values (?, ?, 1) "
                + "on conflict (bucket, window_start) do update set hits = r.hits + 1 returning hits";
        try (Connection connection = autocommit(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, hash(key));
            statement.setTimestamp(2, Timestamp.from(RateLimitStore.windowStart(now, window)));
            try (ResultSet result = statement.executeQuery()) {
                result.next();
                int hits = result.getInt(1);
                if (sinceCleanup.incrementAndGet() >= CLEANUP_EVERY) {
                    sinceCleanup.set(0);
                    cleanUp(connection, now);
                }
                return hits;
            }
        } catch (SQLException e) {
            log.warn("Rate limit not counted for a request: {}", e.getMessage());
            return 0;
        }
    }

    @Override
    public void clear() {
        try (Connection connection = autocommit();
                PreparedStatement statement = connection.prepareStatement("delete from " + table)) {
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void cleanUp(Connection connection, Instant now) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "delete from " + table + " where window_start < ?")) {
            statement.setTimestamp(1, Timestamp.from(now.minus(KEEP)));
            statement.executeUpdate();
        }
    }

    private static String hash(String key) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private Connection autocommit() throws SQLException {
        Connection connection = dataSource.getConnection();
        connection.setAutoCommit(true);
        return connection;
    }
}
