mod catalog;

use catalog::Catalog;

/// Reads the copy source and reports what is in it. Task 3 turns this into the
/// generator; until then it is what proves the file parses outside the tests.
fn main() {
    let root = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("../..");
    let catalog = Catalog::load(root.join("copy/errors.toml")).unwrap_or_else(|e| {
        eprintln!("{e}");
        std::process::exit(1);
    });

    for (code, entry) in &catalog.entries {
        // English, because this output is for whoever is reading the catalog
        // over, not for a parent. The four-locale check lives in the tests.
        let en = &entry.locales["en"];
        println!(
            "{code}  {:?}  {:?}\n    {}\n    {}",
            entry.weight, entry.reach, en.title, en.action
        );
    }
}
