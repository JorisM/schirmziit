use nestling_server::{AppState, app_with_rate_limits, config::Config, db, retention};

#[tokio::main]
async fn main() -> Result<(), Box<dyn std::error::Error>> {
    tracing_subscriber::fmt()
        .with_env_filter(tracing_subscriber::EnvFilter::from_default_env())
        .init();

    let config = Config::from_env()?;
    let url = std::env::var("DATABASE_URL")?;
    let pool = db::connect(&url).await?;

    // Migrations run on startup: upgrading is `docker pull` plus a restart.
    sqlx::migrate!("./migrations").run(&pool).await?;

    retention::spawn(pool.clone(), config.clone());

    let bind = std::env::var("BIND_ADDR").unwrap_or_else(|_| "0.0.0.0:8080".into());
    let listener = tokio::net::TcpListener::bind(&bind).await?;
    tracing::info!(%bind, "nestling-server listening");

    // into_make_service_with_connect_info is load-bearing, not boilerplate: the
    // rate limiter's key extractor needs a client IP. Behind Traefik it uses
    // X-Forwarded-For, but a direct hit (Gatus, a port-forward, local dev) has
    // no such header and would 500 on every auth request without the peer
    // address to fall back to.
    axum::serve(
        listener,
        app_with_rate_limits(AppState::new(pool, config))
            .into_make_service_with_connect_info::<std::net::SocketAddr>(),
    )
    .await?;
    Ok(())
}
