-- Each visit has a different possession secret. Reprinting the physical QR
-- after a visit closes prevents a previous guest's photo from authorizing
-- orders on the next visit with the same command number.
ALTER TABLE command ADD COLUMN qr_token UUID NOT NULL DEFAULT gen_random_uuid();
ALTER TABLE command ALTER COLUMN qr_token DROP DEFAULT;
CREATE UNIQUE INDEX uk_command_qr_token ON command (qr_token);
