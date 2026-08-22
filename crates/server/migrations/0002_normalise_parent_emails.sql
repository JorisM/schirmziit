-- Emails are stored normalised from now on (trimmed, lowercased). Existing rows
-- predate that, so bring them in line; the UNIQUE constraint on email is what
-- stops two spellings of one address from both surviving.
--
-- If this fails on a unique violation, an instance really does have the same
-- address twice in different casing — that needs a human to decide which family
-- keeps it, not a migration guessing.
UPDATE parents SET email = lower(btrim(email)) WHERE email <> lower(btrim(email));
