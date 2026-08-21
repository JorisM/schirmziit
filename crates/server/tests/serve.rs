//! Drives a real bound socket rather than `oneshot`, because the bug this
//! guards against only exists when a request arrives without an
//! `X-Forwarded-For` header: the rate limiter needs a client IP, and without
//! `ConnectInfo` it answers 500 to every auth request. Every other test calls
//! `app()` (no limiter) or supplies the header, so none of them can see it.

use nestling_server::config::{Config, Registration};
use nestling_server::{AppState, app_with_rate_limits};
use sqlx::PgPool;
use std::io::{BufRead, BufReader, Write};
use std::net::{SocketAddr, TcpStream};
use std::time::Duration;

async fn spawn(pool: PgPool) -> SocketAddr {
    let mut config = Config::for_tests();
    config.allow_registration = Registration::Open;
    let router = app_with_rate_limits(AppState::new(pool, config));

    let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
    let addr = listener.local_addr().unwrap();
    tokio::spawn(async move {
        axum::serve(
            listener,
            router.into_make_service_with_connect_info::<SocketAddr>(),
        )
        .await
        .unwrap();
    });
    addr
}

/// Minimal HTTP/1.1 request over a raw socket; returns the status code.
///
/// Must run via `spawn_blocking`: `#[sqlx::test]` gives each test a
/// current-thread runtime, so blocking here on the runtime thread starves the
/// server task and the test hangs instead of failing.
fn post_status(addr: SocketAddr, path: &str, body: &str) -> u16 {
    let mut stream = TcpStream::connect_timeout(&addr, Duration::from_secs(5)).unwrap();
    stream
        .set_read_timeout(Some(Duration::from_secs(10)))
        .unwrap();
    let request = format!(
        "POST {path} HTTP/1.1\r\nHost: localhost\r\nContent-Type: application/json\r\n\
         Content-Length: {}\r\nConnection: close\r\n\r\n{body}",
        body.len()
    );
    stream.write_all(request.as_bytes()).unwrap();

    let mut reader = BufReader::new(stream);
    let mut status_line = String::new();
    reader.read_line(&mut status_line).unwrap();
    status_line
        .split_whitespace()
        .nth(1)
        .and_then(|code| code.parse().ok())
        .unwrap_or(0)
}

#[sqlx::test]
async fn auth_works_without_a_forwarded_for_header(pool: PgPool) {
    let addr = spawn(pool).await;
    let status = tokio::task::spawn_blocking(move || {
        post_status(
            addr,
            "/v1/auth/register",
            r#"{"email":"direct@example.com","password":"correct horse battery staple"}"#,
        )
    })
    .await
    .unwrap();
    assert_eq!(
        status, 201,
        "a direct request with no X-Forwarded-For must not 500 on key extraction"
    );
}

#[sqlx::test]
async fn login_without_a_forwarded_for_header_reaches_the_handler(pool: PgPool) {
    let addr = spawn(pool).await;
    let status = tokio::task::spawn_blocking(move || {
        post_status(
            addr,
            "/v1/auth/login",
            r#"{"email":"nobody@example.com","password":"correct horse battery staple"}"#,
        )
    })
    .await
    .unwrap();
    assert_eq!(
        status, 401,
        "expected the handler's 401, not a 500 from the limiter"
    );
}
