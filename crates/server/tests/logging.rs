//! What the server writes down when something fails.
//!
//! The reference on a parent's screenshot is only useful if `grep` finds it,
//! and a log line is only safe if it holds nothing that identifies a family.

mod helpers;
use helpers::TestApp;
use sqlx::PgPool;
use std::sync::{Arc, Mutex};
use tracing_subscriber::prelude::*;

/// Collects everything written while the guard is alive.
#[derive(Clone, Default)]
struct Captured(Arc<Mutex<Vec<u8>>>);

impl std::io::Write for Captured {
    fn write(&mut self, buf: &[u8]) -> std::io::Result<usize> {
        self.0.lock().unwrap().extend_from_slice(buf);
        Ok(buf.len())
    }
    fn flush(&mut self) -> std::io::Result<()> {
        Ok(())
    }
}

impl Captured {
    fn text(&self) -> String {
        String::from_utf8(self.0.lock().unwrap().clone()).unwrap()
    }
}

fn capture() -> (Captured, tracing::subscriber::DefaultGuard) {
    let sink = Captured::default();
    let writer = sink.clone();
    let subscriber = tracing_subscriber::registry().with(
        tracing_subscriber::fmt::layer()
            .with_writer(move || writer.clone())
            .with_ansi(false),
    );
    let guard = tracing::subscriber::set_default(subscriber);
    (sink, guard)
}

#[sqlx::test]
async fn a_failed_request_logs_its_reference_and_code(pool: PgPool) {
    let (sink, _guard) = capture();
    let app = TestApp::new(pool);
    let (_, json, headers) = app.get_with_headers("/v1/children").await;

    let logged = sink.text();
    let short = json["ref"].as_str().unwrap();
    let full = headers.get("x-request-id").unwrap().to_str().unwrap();

    assert!(
        logged.contains(short),
        "grep {short} found nothing in:\n{logged}"
    );
    assert!(
        logged.contains(full),
        "the full id belongs in the log too:\n{logged}"
    );
    assert!(
        logged.contains("SZ-E102"),
        "the code belongs in the log:\n{logged}"
    );
}

#[sqlx::test]
async fn a_failed_sign_in_never_logs_the_email(pool: PgPool) {
    // A log that records every attempted address is an account-enumeration
    // list, sitting in a family's own journalctl.
    let (sink, _guard) = capture();
    let app = TestApp::new(pool);
    app.post_json(
        "/v1/auth/login",
        serde_json::json!({ "email": "someone@example.com", "password": "wrong" }),
    )
    .await;

    let logged = sink.text();
    assert!(
        !logged.contains("someone@example.com"),
        "the log holds an attempted email:\n{logged}"
    );
    assert!(
        logged.contains("SZ-E101"),
        "but it must say what failed:\n{logged}"
    );
}
