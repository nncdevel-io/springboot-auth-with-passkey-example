-- Spring Security standard tables
CREATE TABLE IF NOT EXISTS users (
    username VARCHAR(50) NOT NULL PRIMARY KEY,
    password VARCHAR(500) NOT NULL,
    enabled  BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS authorities (
    username  VARCHAR(50) NOT NULL,
    authority VARCHAR(50) NOT NULL,
    CONSTRAINT fk_authorities_users FOREIGN KEY (username) REFERENCES users(username)
);
CREATE UNIQUE INDEX IF NOT EXISTS ix_auth_username ON authorities (username, authority);

-- WebAuthn tables (designed table names)
CREATE TABLE IF NOT EXISTS public_key_credential_user_entity (
    id           VARCHAR(1000) NOT NULL PRIMARY KEY,
    name         VARCHAR(200)  NOT NULL UNIQUE,
    display_name VARCHAR(200)
);

CREATE TABLE IF NOT EXISTS user_credentials (
    credential_id                VARCHAR(1000) NOT NULL PRIMARY KEY,
    user_entity_user_id          VARCHAR(1000) NOT NULL,
    public_key                   BLOB          NOT NULL,
    signature_count              BIGINT,
    uv_initialized               BOOLEAN,
    backup_eligible              BOOLEAN       NOT NULL,
    authenticator_transports     VARCHAR(1000),
    public_key_credential_type   VARCHAR(100),
    backup_state                 BOOLEAN       NOT NULL,
    attestation_object           BLOB,
    attestation_client_data_json BLOB,
    created                      TIMESTAMP,
    last_used                    TIMESTAMP,
    label                        VARCHAR(1000) NOT NULL,
    CONSTRAINT fk_user_credentials FOREIGN KEY (user_entity_user_id)
        REFERENCES public_key_credential_user_entity(id)
);