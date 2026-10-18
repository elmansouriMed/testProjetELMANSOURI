-- createdBy ELMANSOURI Mohamed
-- Les anciennes métadonnées restent conservées, mais ne sont plus collectées par l'application.
ALTER TABLE payment_message
    ALTER COLUMN payload_size DROP NOT NULL,
    ALTER COLUMN redelivered DROP NOT NULL;
