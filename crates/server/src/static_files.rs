use axum::body::Body;
use axum::http::{StatusCode, Uri, header};
use axum::response::{IntoResponse, Response};

/// The built dashboard, compiled into the binary. `web/dist` may be empty in a
/// dev checkout (rust-embed only requires the directory to exist), in which case
/// every asset lookup misses and the handler 404s - which is what the tests
/// assert against.
#[derive(rust_embed::Embed)]
#[folder = "../../web/dist"]
struct Assets;

pub async fn handler(uri: Uri) -> Response {
    let path = uri.path().trim_start_matches('/');

    // API paths never fall back to the SPA. Otherwise every frontend bug turns
    // into "the API returned HTML".
    if path.starts_with("v1/") || path == "healthz" {
        return StatusCode::NOT_FOUND.into_response();
    }

    let requested = if path.is_empty() { "index.html" } else { path };

    // Guess the MIME from the file actually served, not from the URL. A deep
    // link like /children/abc falls back to index.html, and typing it from the
    // request path yields application/octet-stream - which makes the browser
    // download the page instead of rendering it.
    let (file, served_name) = match Assets::get(requested) {
        Some(file) => (file, requested),
        None => match Assets::get("index.html") {
            Some(file) => (file, "index.html"),
            None => return StatusCode::NOT_FOUND.into_response(),
        },
    };

    let mime = mime_guess::from_path(served_name).first_or_octet_stream();
    (
        [(header::CONTENT_TYPE, mime.as_ref())],
        Body::from(file.data.to_vec()),
    )
        .into_response()
}
