\set ON_ERROR_STOP on
-- Run locally as the database owner, after registering your account.
-- Supply psql -v owner=your_username. Existing owned tasks are never reassigned.
BEGIN;
UPDATE tasks SET owner_id = (SELECT id FROM app_users WHERE username = :'owner')
WHERE owner_id IS NULL AND EXISTS (SELECT 1 FROM app_users WHERE username = :'owner');
COMMIT;
