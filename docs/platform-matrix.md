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
| Install without a developer machine | `~` APK, no Play Store listing | `·` TestFlight/App Store need **Family Controls (Distribution)**, which Apple grants per bundle id and which is still outstanding |
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
| Parent and child role in one app | `·` child phone only | ✓ role choice, `RoleStore` | `–` |
| Last fourteen days | `–` | ✓ `DayStripView` | ✓ `DayStrip` |
| One day, hour by hour | `–` | ✓ `DayRibbonView`, `AppRowsView` | ✓ `DayRibbon`, `AppBars` |
| The child's own numbers on the child's phone | ✓ `MyTimeScreen` | ✓ `AgentMyTimeView` | `–` device tokens have no browser session |
| Add and remove a child | `–` | ✓ `ChildrenView` | ✓ `Children` |
| Disconnect a device | `–` | ✓ `ChildDetailView.revokeDevice` | ✓ `ChildDetail` |
| Enrol a child phone without typing a code | ✓ `ParentSetup` | ✓ parent session → device token | `–` |
| Mint a pairing code for a child phone | `–` | ✓ `PairDeviceView` | ✓ `PairDevice` |
| Delete a child's stored figures | `–` | `·` | ✓ `PurgeData` |
| Help and the four Swiss services | ✓ incl. Beratung 147 | ✓ parent `HelpView`; child `AgentHelpView` incl. 147 | ✓ `Help` |
| de/fr/it/en | ✓ | ✓ | ✓ |

## Gaps worth naming

- **The pairing code is typed, not scanned.** `mint_enrollment` returns a
  `schirmziit://enroll?url=…&code=…` payload meant for a camera, and both
  `PairDevice` and `PairDeviceView` show the code and the server address as text
  instead: rendering a QR needs an encoder, and no dependency here has one. Until
  that lands, the second step says "type" in all four languages on both surfaces
  — the copy follows the code, not the intention.
- **The iPhone parent mode deletes no data.** `PurgeData` exists in the dashboard
  and `ChildDetailView` owes the same control. Minting a code is no longer on
  this list: `PairDeviceView` sits at the foot of the same screen, deliberately
  outside the usage load so a family whose phone never reported can still enrol
  one.
- **Nothing reads `/v1/children/{id}/summary`.** The route is in `openapi.json`
  and no client calls it.
