# What runs where

The developer-side copy of the table on schirmziit.ch. Statuses for the site live
in `site/src/content/matrix.ts` (labels per language in `site/src/content/*.ts`);
**change one, change the other in the same commit** — a site that claims more
than the code does is the one lie this repo cannot afford.

`✓` works · `~` partly, see the note · `·` not yet · `–` not meant to do this

## Measuring, on a child's phone

| | Android | iPhone |
|---|---|---|
| Per-app foreground time, per hour | ✓ | ✓ |
| Time of day (the hourly ribbon) | ✓ | ✓ |
| Unlocks | ✓ `KEYGUARD_HIDDEN` | `~` no unlock count on iOS; `totalPickupsWithoutApplicationActivity` is uploaded as `unlock_count` |
| `background_ms` (audio with the screen off) | `~` MediaSession, needs the notification-listener grant; `background_measured` says which | `·` iOS exposes nothing equivalent, so `background_measured: false` |
| App display names | ✓ | `~` iOS withholds some, and the bundle id is uploaded instead |
| Offline queue, nothing lost | ✓ | ✓ |
| Minimum OS | Android 8 (`minSdk = 26`) | iOS 17 |
| Install without a developer machine | `~` signed APK from the release workflow, no Play Store listing | `·` TestFlight/App Store need **Family Controls (Distribution)**, which Apple grants per bundle id and which is still outstanding |
| Wire format from `crates/core` | ✓ | ✓ |

The entitlement line is the one people ask about: a paid Apple Developer Program
membership signs **Family Controls (Development)** and App Groups, so measuring
on an iPhone works on our own devices today. Distribution is a separate,
Apple-reviewed request per bundle id (`ch.jorisda.schirmziit`,
`…schirmziit.monitor`, `…schirmziit.report`). Until it lands, an iPhone can be
measured only by someone who builds and installs the app themselves.

## Looking at it

| | Android app | iPhone app | Dashboard |
|---|---|---|---|
| Parent and child role in one app | ✓ `RoleChoiceScreen`, `RoleStore` | ✓ role choice, `RoleStore` | `–` |
| Last fourteen days | ✓ `ChildDetailScreen.DayStrip` | ✓ `DayStripView` | ✓ `DayStrip` |
| One day, hour by hour | ✓ `HourRibbon`, `AppRows` | ✓ `DayRibbonView`, `AppRowsView` | ✓ `DayRibbon`, `AppBars` |
| Last week against the one before | ✓ `WeekInsightCard` | ✓ `WeekInsightView` | ✓ `WeekInsight` |
| The child's own numbers on the child's phone | ✓ `MyTimeScreen` | ✓ `AgentMyTimeView` | `–` device tokens have no browser session |
| Add and remove a child | ✓ `ChildrenScreen` | ✓ `ChildrenView` | ✓ `Children` |
| Disconnect a device | ✓ `ChildDetailScreen`, long press | ✓ `ChildDetailView.revokeDevice` | ✓ `ChildDetail` |
| Enrol a child phone without typing a code | ✓ `ParentSetup` | ✓ parent session → device token | `–` |
| Mint a pairing code for a child phone | ✓ `PairDeviceCard` | ✓ `PairDeviceView` | ✓ `PairDevice` |
| Show that code as a QR | ✓ `QrMatrixImage` | ✓ `QrMatrixView` | ✓ `QrCode` |
| Enrol by scanning it | ✓ in-app scanner (zxing) and the `schirmziit://enroll` deep link | ✓ in-app scanner (`QrScannerSheet`, AVFoundation) and the same deep link | `–` |
| Delete a child's stored figures | ✓ `PurgeDataCard` | ✓ `PurgeDataView` | ✓ `PurgeData` |
| Help and the four Swiss services | ✓ incl. Beratung 147 | ✓ parent `HelpView`; child `AgentHelpView` incl. 147 | ✓ `Help` |
| de/fr/it/en | ✓ | ✓ | ✓ |

## Gaps worth naming

- **The pairing code is drawn once, in Rust, and painted three times.**
  `crates/core::qr` encodes the `schirmziit://enroll?url=…&code=…` payload and
  the server ships the matrix with the code, so no client owns an encoder —
  three encoders would be three chances to hand a family a square that scans as
  something else. The quiet zone travels inside the matrix rather than as a
  reminder in three renderers, and every surface draws dark on light in both
  themes: an inverted QR is refused outright by some scanners. `qr` is nullable
  and a client that gets null shows the code and the address as text, which is
  what pairing was before the square existed.
- **Both phones scan in the app, and neither pairs on the scan.** A square fills
  the address and the code in and stops there: the code is one-shot, so a screen
  that paired on arrival would spend it on a mis-scan, and the phone still has
  no name until someone gives it one. Either phone can also be reached from the
  outside — both register `schirmziit://`, so the system camera opens the app
  with the link and it lands in the same place. On the iPhone the live camera
  itself is the one part with no test behind it (a simulator has none), so
  everything that could be decided away from it was: `ScanReader` answers a
  square once rather than thirty times a second, and `ScanAccess` separates a
  refused camera from a phone that has none — a child told to grant access on a
  device with no camera goes looking for a switch that is not there.
- **One week is compared with the one before it, and only the server does it.**
  `crates/core::insight` compares; `/v1/children/{id}/insight` serves the
  comparison; three renderers print it. A week is the seven *complete* local
  days ending yesterday — the day a parent is standing in is not compared,
  because a day still being lived is always shorter than the one it is measured
  against, and an insight that reads "down three hours" every morning is worse
  than no insight. Evenings are counted from 21:00 by the child's own clock,
  `background_ms` is not in the comparison at all, and `previous_measured`
  travels with the numbers so a week against silence reads as a first week
  rather than a doubling. It names what moved and never who: no target, no
  streak, no score.

- **The Android release is signed, and that key is the app's identity.** What
  the release workflow published before was build output with a name that read
  like a download: an unsigned APK cannot be installed on any phone. It is
  signed now, by a key that lives in the password manager and reaches CI as
  `ANDROID_KEYSTORE_B64`. Three things guard the failure that is invisible
  until a phone refuses the file: `buildSrc/ReleaseSigning.kt` refuses half a
  keystore instead of falling back to unsigned, the artifact keeps its
  `-unsigned` suffix when there is no key, and the workflow reads the
  certificate fingerprint back off the finished APK before it uploads anything.
  A fork has no secret and gets the unsigned build deliberately, rather than a
  red workflow. Losing the key is the one unrecoverable failure here: every
  installed copy recognises that key and no other, so a replacement means
  uninstalling, and uninstalling loses the pairing on that phone. v3 signing is
  switched on for the same reason — it is what makes a later key rotation
  possible at all.

- **Deleting a child's figures no longer needs a browser.** All three surfaces
  ask twice, and all three show the server's own `rows_affected` afterwards:
  "deleted" with nothing behind it is the one claim a family has no way to
  check. On both phones the control sits at the very foot of the child's screen,
  deliberately outside the usage load — a day that failed to fetch is one of the
  moments a parent is most likely to want the figures gone.
