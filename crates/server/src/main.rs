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

    axum::serve(listener, app_with_rate_limits(AppState::new(pool, config))).await?;
    Ok(())
}
