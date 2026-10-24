CREATE TABLE user_identity (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  issuer VARCHAR(255) NOT NULL,
  subject VARCHAR(255) NOT NULL,
  CONSTRAINT fk_user_identity_user FOREIGN KEY (user_id) REFERENCES `user` (id),
  CONSTRAINT uq_user_identity_issuer_subject UNIQUE (issuer, subject),
  CONSTRAINT uq_user_identity_user_issuer UNIQUE (user_id, issuer)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='External OIDC identity mapping';
