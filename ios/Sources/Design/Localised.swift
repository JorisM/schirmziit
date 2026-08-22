import SwiftUI

/// Text from this framework's own string tables.
///
/// SwiftUI's `Text("some.key")` resolves against `Bundle.main`, which is the app
/// when the app runs and the **test runner** when tests run — so plain
/// `Text("key")` renders the raw key in every snapshot, and a screen full of
/// `role.parent.title` is useless for reviewing either the design or the
/// translations. `L("key")` always resolves where the strings actually are.
func L(_ key: LocalizedStringKey) -> Text {
    Text(key, bundle: .schirmziitKit)
}

/// Same, for the places SwiftUI wants a plain `String` (field placeholders,
/// button labels inside alerts).
func S(_ key: String.LocalizationValue) -> String {
    String(localized: key)
}
