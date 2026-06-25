package io.muzoo.ssc.controlmap.health;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * JDBC implementation of {@link DatabaseHealthRepository}: runs a trivial {@code SELECT 1}
 * against the configured DataSource to confirm connectivity. A failed connection throws a
 * {@link org.springframework.dao.DataAccessException}, which we translate into {@code false}
 * so the health endpoint can report status without leaking internals.
 */
@Repository
public class JdbcDatabaseHealthRepository implements DatabaseHealthRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcDatabaseHealthRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public boolean isReachable() {
        try {
            Integer result = jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return result != null && result == 1;
        } catch (org.springframework.dao.DataAccessException ex) {
            return false;
        }
    }
}
