use std::collections::BTreeMap;

/// How loudly a code is presented. Not every failure deserves red: an offline
/// phone is expected and self-correcting, and painting that alarming teaches a
/// parent to ignore the colour that means something is actually wrong.
#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Weight {
    Urgent,
    Neutral,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord, serde::Deserialize)]
#[serde(rename_all = "lowercase")]
pub enum Surface {
    Web,
    Ios,
    Android,
}

/// Not named `Copy`: that shadows `std::marker::Copy` in a file that derives it
/// two types above, and the resulting error message is genuinely baffling.
#[derive(Debug, Clone, serde::Deserialize)]
pub struct Message {
    /// What happened, in the reader's terms.
    pub title: String,
    /// What to do about it. Never empty — an error with no next step is a
    /// dead end.
    pub action: String,
}

#[derive(Debug, Clone, serde::Deserialize)]
pub struct Entry {
    pub weight: Weight,
    /// Which surfaces can actually emit this code. `web` can never hit
    /// SZ-E603, and demanding it carry the string would be theatre.
    pub reach: Vec<Surface>,
    #[serde(flatten)]
    pub locales: BTreeMap<String, Message>,
}

#[derive(Debug, Clone, serde::Deserialize)]
pub struct Catalog {
    #[serde(flatten)]
    pub entries: BTreeMap<String, Entry>,
}

impl Catalog {
    pub fn load(path: impl AsRef<std::path::Path>) -> Result<Self, String> {
        let path = path.as_ref();
        let text = std::fs::read_to_string(path).map_err(|e| format!("{}: {e}", path.display()))?;
        toml::from_str(&text).map_err(|e| format!("{}: {e}", path.display()))
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use schirmziit_core::codes::ErrorCode;

    fn repo_catalog() -> Catalog {
        Catalog::load(concat!(
            env!("CARGO_MANIFEST_DIR"),
            "/../../copy/errors.toml"
        ))
        .expect("copy/errors.toml parses")
    }

    /// A code with no copy renders as a blank error, which is worse than a
    /// wrong one: the parent sees an empty box and cannot report anything.
    #[test]
    fn every_code_has_an_entry() {
        let catalog = repo_catalog();
        for code in ErrorCode::ALL {
            assert!(
                catalog.entries.contains_key(code.as_str()),
                "{} has no entry in copy/errors.toml",
                code.as_str()
            );
        }
    }

    /// A typo'd key would otherwise sit in the file forever, silently
    /// translating nothing.
    #[test]
    fn every_entry_names_a_real_code() {
        let catalog = repo_catalog();
        let known: std::collections::HashSet<&str> =
            ErrorCode::ALL.iter().map(|c| c.as_str()).collect();
        for key in catalog.entries.keys() {
            assert!(known.contains(key.as_str()), "{key} is not a known code");
        }
    }

    /// Four languages, always — the same rule the dashboard dictionaries live by.
    #[test]
    fn every_entry_has_all_four_locales() {
        let catalog = repo_catalog();
        for (key, entry) in &catalog.entries {
            for locale in ["de", "fr", "it", "en"] {
                let copy = entry.locales.get(locale);
                assert!(copy.is_some(), "{key} is missing {locale}");
                let copy = copy.unwrap();
                assert!(
                    !copy.title.trim().is_empty(),
                    "{key}.{locale}.title is empty"
                );
                assert!(
                    !copy.action.trim().is_empty(),
                    "{key}.{locale}.action is empty"
                );
            }
        }
    }

    /// Same rule the app and the site are already held to.
    #[test]
    fn no_locale_implies_secrecy() {
        let catalog = repo_catalog();
        let forbidden = ["heimlich", "sneak", "en cachette", "di nascosto"];
        for (key, entry) in &catalog.entries {
            for (locale, copy) in &entry.locales {
                let haystack = format!("{} {}", copy.title, copy.action).to_lowercase();
                for word in forbidden {
                    assert!(
                        !haystack.contains(word),
                        "{key}.{locale} contains the forbidden word {word:?}"
                    );
                }
            }
        }
    }

    /// Schweizer Hochdeutsch has no ß.
    #[test]
    fn german_never_uses_eszett() {
        let catalog = repo_catalog();
        for (key, entry) in &catalog.entries {
            let de = entry.locales.get("de").unwrap();
            assert!(
                !de.title.contains('ß') && !de.action.contains('ß'),
                "{key}.de uses ß"
            );
        }
    }

    /// An unreachable code with no surface would generate into nothing.
    #[test]
    fn every_entry_reaches_at_least_one_surface() {
        let catalog = repo_catalog();
        for (key, entry) in &catalog.entries {
            assert!(!entry.reach.is_empty(), "{key} reaches no surface");
        }
    }
}
