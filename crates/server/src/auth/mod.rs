pub mod routes;

use crate::AppState;
use crate::error::ApiError;
use argon2::Argon2;
use axum::extract::FromRequestParts;
use axum::http::request::Parts;
use axum_extra::extract::CookieJar;
use password_hash::{PasswordHash, PasswordHasher, PasswordVerifier, SaltString};
use rand::RngCore;
use sha2::{Digest, Sha256};
use uuid::Uuid;

pub const SESSION_COOKIE: &str = "nestling_session";

pub fn hash_password(plain: &str) -> Result<String, ApiError> {
    // Salt from `rand` 0.9 rather than `SaltString::generate`: that path wants
    // rand_core 0.6's OsRng, which would mean carrying a second RNG stack.
    let mut salt_bytes = [0u8; 16];
    rand::rng().fill_bytes(&mut salt_bytes);
    let salt =
        SaltString::encode_b64(&salt_bytes).map_err(|e| ApiError::Validation(e.to_string()))?;
    Argon2::default()
        .hash_password(plain.as_bytes(), &salt)
        .map(|h| h.to_string())
        .map_err(|e| ApiError::Validation(e.to_string()))
}

pub fn verify_password(plain: &str, hash: &str) -> bool {
    PasswordHash::new(hash)
        .map(|parsed| {
            Argon2::default()
                .verify_password(plain.as_bytes(), &parsed)
                .is_ok()
        })
        .unwrap_or(false)
}

/// 32 random bytes, hex-encoded. High entropy, so `hash_token` uses SHA-256:
/// argon2 on a 256-bit random value buys nothing and would put a KDF on the hot
/// path of every ingest request.
pub fn random_token() -> String {
    let mut bytes = [0u8; 32];
    rand::rng().fill_bytes(&mut bytes);
    hex(&bytes)
}

pub fn hash_token(token: &str) -> String {
    hex(&Sha256::digest(token.as_bytes()))
}

fn hex(bytes: &[u8]) -> String {
    use std::fmt::Write;
    bytes.iter().fold(String::new(), |mut out, b| {
        let _ = write!(out, "{b:02x}");
        out
    })
}

#[derive(Debug, Clone)]
pub struct Parent {
    pub id: Uuid,
    pub family_id: Uuid,
    pub email: String,
}

/// Taking `Parent` as a handler argument is what makes tenant scoping hard to
/// forget: a handler cannot read family data without first proving who is asking.
impl FromRequestParts<AppState> for Parent {
    type Rejection = ApiError;

    async fn from_request_parts(
        parts: &mut Parts,
        state: &AppState,
    ) -> Result<Self, Self::Rejection> {
        let jar = CookieJar::from_headers(&parts.headers);
        let raw = jar
            .get(SESSION_COOKIE)
            .map(|c| c.value().to_string())
            .ok_or(ApiError::Unauthenticated)?;

        let row = sqlx::query!(
            r#"
            SELECT p.id, p.family_id, p.email
            FROM sessions s
            JOIN parents p ON p.id = s.parent_id
            WHERE s.token_hash = $1 AND s.expires_at > now()
            "#,
            hash_token(&raw)
        )
        .fetch_optional(&state.pool)
        .await?
        .ok_or(ApiError::Unauthenticated)?;

        Ok(Parent {
            id: row.id,
            family_id: row.family_id,
            email: row.email,
        })
    }
}
