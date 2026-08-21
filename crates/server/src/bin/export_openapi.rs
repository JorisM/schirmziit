use utoipa::OpenApi;

fn main() {
    let json = nestling_server::openapi::ApiDoc::openapi()
        .to_pretty_json()
        .expect("serialize openapi");
    println!("{json}");
}
