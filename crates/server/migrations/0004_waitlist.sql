-- Addresses collected on the public site while the product is a private alpha.
--
-- Deliberately unrelated to `parents`: nobody on this list has an account, and
-- signing up must never create one. No family_id either — there is no family to
-- scope to, which is also why nothing in the API can read this table back.
--
-- `locale` is one of the four the site speaks, so the release mail goes out in
-- the language the person read the site in.
CREATE TABLE waitlist_signups (
    email      TEXT PRIMARY KEY,
    locale     TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
