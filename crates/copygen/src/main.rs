mod catalog;
mod emit;

use catalog::Catalog;

/// Writes every generated dictionary. `just gen-copy-check` then diffs them
/// against git, so a copy change that is not regenerated and committed fails
/// the gate — the same contract `schema.d.ts` already lives by.
fn main() {
    let root = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    let catalog = Catalog::load(root.join("copy/errors.toml")).unwrap_or_else(|e| {
        eprintln!("{e}");
        std::process::exit(1);
    });

    write(&root.join("web/src/i18n/errors.ts"), &emit::web(&catalog));

    for locale in ["de", "fr", "it", "en"] {
        write(
            &root.join(format!(
                "ios/Sources/Resources/{locale}.lproj/ErrorCopy.strings"
            )),
            &emit::ios(&catalog, locale),
        );
        // Android's default resource dir carries English; the others are
        // qualified. Matches the existing values/ + values-de/fr/it layout.
        let dir = if locale == "en" {
            "values".to_string()
        } else {
            format!("values-{locale}")
        };
        write(
            &root.join(format!("android/app/src/main/res/{dir}/error_copy.xml")),
            &emit::android(&catalog, locale),
        );
    }
}

fn write(path: &std::path::Path, contents: &str) {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).expect("create output directory");
    }
    std::fs::write(path, contents).unwrap_or_else(|e| panic!("{}: {e}", path.display()));
}
