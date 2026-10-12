-- Existing accounts keep email login. A username can be assigned separately
-- and is unique regardless of letter case.
ALTER TABLE app_user ADD COLUMN username VARCHAR(64);
ALTER TABLE app_user ALTER COLUMN email DROP NOT NULL;

ALTER TABLE app_user ADD CONSTRAINT ck_app_user_login_identifier
    CHECK (email IS NOT NULL OR username IS NOT NULL);

ALTER TABLE app_user ADD CONSTRAINT ck_app_user_username_format
    CHECK (username IS NULL OR username ~ '^[A-Za-z][A-Za-z0-9._-]{2,63}$');

CREATE UNIQUE INDEX uk_app_user_username_lower
    ON app_user (lower(username)) WHERE username IS NOT NULL;
