// Shared by every integration test file; each test binary uses a subset.
#![allow(dead_code)]

use axum::Router;
use axum::body::Body;
use axum::http::{HeaderValue, Request, StatusCode, header};
use http_body_util::BodyExt;
use nestling_server::config::Config;
use nestling_server::{AppState, app};
use sqlx::PgPool;
use tower::ServiceExt;

pub struct Response {
    pub status: StatusCode,
    pub json: serde_json::Value,
}

pub struct TestApp {
    pub router: Router,
    pub cookie: Option<HeaderValue>,
    pub pool: PgPool,
}

impl TestApp {
    pub fn new(pool: PgPool) -> Self {
        Self {
            router: app(AppState::new(pool.clone(), Config::for_tests())),
            cookie: None,
            pool,
        }
    }

    /// A registered parent with an active session. The email is unique per call
    /// so two TestApps can share one database.
    pub async fn registered(pool: PgPool) -> Self {
        let mut app = Self::new(pool);
        let email = format!("{}@example.com", uuid::Uuid::new_v4());
        let (status, json, cookie) = app
            .send(
                Request::builder()
                    .method("POST")
                    .uri("/v1/auth/register")
                    .header(header::CONTENT_TYPE, "application/json")
                    .body(Body::from(
                        serde_json::json!({
                            "email": email,
                            "password": "correct horse battery staple"
                        })
                        .to_string(),
                    ))
                    .unwrap(),
            )
            .await;
        assert_eq!(status, StatusCode::CREATED, "registration failed: {json}");
        app.cookie = Some(cookie.expect("register must set a session cookie"));
        app
    }

    async fn send(
        &self,
        request: Request<Body>,
    ) -> (StatusCode, serde_json::Value, Option<HeaderValue>) {
        let response = self.router.clone().oneshot(request).await.unwrap();
        let status = response.status();
        let set_cookie = response.headers().get(header::SET_COOKIE).cloned();
        let bytes = response.into_body().collect().await.unwrap().to_bytes();
        let json = serde_json::from_slice(&bytes).unwrap_or(serde_json::Value::Null);
        (status, json, set_cookie)
    }

    fn with_session(
        &self,
        mut builder: axum::http::request::Builder,
    ) -> axum::http::request::Builder {
        if let Some(cookie) = &self.cookie {
            builder = builder.header(header::COOKIE, cookie.clone());
        }
        builder
    }

    pub async fn get(&self, uri: &str) -> Response {
        let request = self
            .with_session(Request::builder().uri(uri))
            .body(Body::empty())
            .unwrap();
        let (status, json, _) = self.send(request).await;
        Response { status, json }
    }

    pub async fn get_anonymous(&self, uri: &str) -> Response {
        let request = Request::builder().uri(uri).body(Body::empty()).unwrap();
        let (status, json, _) = self.send(request).await;
        Response { status, json }
    }

    pub async fn post_json(&self, uri: &str, body: serde_json::Value) -> Response {
        let request = self
            .with_session(
                Request::builder()
                    .method("POST")
                    .uri(uri)
                    .header(header::CONTENT_TYPE, "application/json"),
            )
            .body(Body::from(body.to_string()))
            .unwrap();
        let (status, json, _) = self.send(request).await;
        Response { status, json }
    }

    pub async fn delete(&self, uri: &str) -> Response {
        let request = self
            .with_session(Request::builder().method("DELETE").uri(uri))
            .body(Body::empty())
            .unwrap();
        let (status, json, _) = self.send(request).await;
        Response { status, json }
    }

    /// Bearer-token request, for device-authenticated calls.
    pub async fn post_as_device(
        &self,
        uri: &str,
        token: &str,
        body: serde_json::Value,
    ) -> Response {
        let request = Request::builder()
            .method("POST")
            .uri(uri)
            .header(header::CONTENT_TYPE, "application/json")
            .header(header::AUTHORIZATION, format!("Bearer {token}"))
            .body(Body::from(body.to_string()))
            .unwrap();
        let (status, json, _) = self.send(request).await;
        Response { status, json }
    }

    /// GET with a device bearer token and no session cookie: proves the parent
    /// surface refuses a device identity.
    pub async fn get_as_device(&self, uri: &str, token: &str) -> Response {
        let request = Request::builder()
            .uri(uri)
            .header(header::AUTHORIZATION, format!("Bearer {token}"))
            .body(Body::empty())
            .unwrap();
        let (status, json, _) = self.send(request).await;
        Response { status, json }
    }

    /// Create a child and return its id.
    pub async fn create_child(&self, name: &str) -> String {
        let child = self
            .post_json("/v1/children", serde_json::json!({ "display_name": name }))
            .await;
        assert_eq!(child.status, StatusCode::CREATED, "{}", child.json);
        child.json["id"].as_str().unwrap().to_string()
    }

    /// Mint an enrollment code for a child.
    pub async fn mint_code(&self, child_id: &str) -> String {
        let enrollment = self
            .post_json(
                &format!("/v1/children/{child_id}/enrollments"),
                serde_json::json!({}),
            )
            .await;
        assert_eq!(
            enrollment.status,
            StatusCode::CREATED,
            "{}",
            enrollment.json
        );
        enrollment.json["code"].as_str().unwrap().to_string()
    }

    /// Enroll a device for a child and return `(device_id, token)`.
    pub async fn enroll_device(&self, child_id: &str) -> (String, String) {
        let code = self.mint_code(child_id).await;
        let device = self
            .post_as_device(
                "/v1/enroll",
                "",
                serde_json::json!({
                    "code": code, "platform": "android", "model": "FP4", "label": "phone"
                }),
            )
            .await;
        assert_eq!(device.status, StatusCode::CREATED, "{}", device.json);
        (
            device.json["device_id"].as_str().unwrap().to_string(),
            device.json["token"].as_str().unwrap().to_string(),
        )
    }
}
