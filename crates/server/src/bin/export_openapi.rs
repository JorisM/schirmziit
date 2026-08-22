use utoipa::OpenApi;

fn main() {
    let json = schirmziit_server::openapi::ApiDoc::openapi()
        .to_pretty_json()
        .expect("serialize openapi");
    println!("{json}");
}
