package io.nncdevel.example.auth.repository;

import java.sql.ResultSet;
import java.sql.SQLException;

import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.ImmutablePublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;

/**
 * {@link PublicKeyCredentialUserEntityRepository} implementation that uses the
 * table name {@code public_key_credential_user_entity} instead of the default
 * {@code user_entities} used by Spring Security's built-in JDBC implementation.
 */
public class JdbcCustomUserEntityRepository implements PublicKeyCredentialUserEntityRepository {

    private static final String TABLE = "public_key_credential_user_entity";

    private static final String FIND_BY_ID =
            "SELECT id, name, display_name FROM " + TABLE + " WHERE id = ?";

    private static final String FIND_BY_USERNAME =
            "SELECT id, name, display_name FROM " + TABLE + " WHERE name = ?";

    private static final String SAVE =
            "INSERT INTO " + TABLE + " (id, name, display_name) VALUES (?, ?, ?)";

    private static final String UPDATE =
            "UPDATE " + TABLE + " SET name = ?, display_name = ? WHERE id = ?";

    private static final String DELETE =
            "DELETE FROM " + TABLE + " WHERE id = ?";

    private final JdbcOperations jdbc;

    public JdbcCustomUserEntityRepository(JdbcOperations jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public PublicKeyCredentialUserEntity findById(Bytes id) {
        var results = this.jdbc.query(FIND_BY_ID, this::mapRow, id.toBase64UrlString());
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public PublicKeyCredentialUserEntity findByUsername(String username) {
        var results = this.jdbc.query(FIND_BY_USERNAME, this::mapRow, username);
        return results.isEmpty() ? null : results.get(0);
    }

    @Override
    public void save(PublicKeyCredentialUserEntity entity) {
        var existing = findById(entity.getId());
        if (existing != null) {
            this.jdbc.update(UPDATE, entity.getName(), entity.getDisplayName(),
                    entity.getId().toBase64UrlString());
        } else {
            this.jdbc.update(SAVE, entity.getId().toBase64UrlString(),
                    entity.getName(), entity.getDisplayName());
        }
    }

    @Override
    public void delete(Bytes id) {
        this.jdbc.update(DELETE, id.toBase64UrlString());
    }

    private PublicKeyCredentialUserEntity mapRow(ResultSet rs, int rowNum) throws SQLException {
        return ImmutablePublicKeyCredentialUserEntity.builder()
                .id(Bytes.fromBase64(rs.getString("id")))
                .name(rs.getString("name"))
                .displayName(rs.getString("display_name"))
                .build();
    }
}
