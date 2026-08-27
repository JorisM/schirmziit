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
| Parent and child role in one app | ✓ `RoleChoiceScreen`, `RoleStore` | ✓ role choice, `RoleStore` | `–` |
| Last fourteen days | ✓ `ChildDetailScreen.DayStrip` | ✓ `DayStripView` | ✓ `DayStrip` |
| One day, hour by hour | ✓ `HourRibbon`, `AppRows` | ✓ `DayRibbonView`, `AppRowsView` | ✓ `DayRibbon`, `AppBars` |
| The child's own numbers on the child's phone | ✓ `MyTimeScreen` | ✓ `AgentMyTimeView` | `–` device tokens have no browser session |
| Add and remove a child | ✓ `ChildrenScreen` | ✓ `ChildrenView` | ✓ `Children` |
| Disconnect a device | ✓ `ChildDetailScreen`, long press | ✓ `ChildDetailView.revokeDevice` | ✓ `ChildDetail` |
| Enrol a child phone without typing a code | ✓ `ParentSetup` | ✓ parent session → device token | `–` |
| Mint a pairing code for a child phone | ✓ `PairDeviceCard` | ✓ `PairDeviceView` | ✓ `PairDevice` |
| Delete a child's stored figures | ✓ `PurgeDataCard` | ✓ `PurgeDataView` | ✓ `PurgeData` |
| Help and the four Swiss services | ✓ incl. Beratung 147 | ✓ parent `HelpView`; child `AgentHelpView` incl. 147 | ✓ `Help` |
| de/fr/it/en | ✓ | ✓ | ✓ |

## Gaps worth naming

- **The pairing code is typed, not scanned.** `mint_enrollment` returns a
  `schirmziit://enroll?url=…&code=…` payload meant for a camera, and both
  `PairDevice` and `PairDeviceView` show the code and the server address as text
  instead: rendering a QR needs an encoder, and no dependency here has one. Until
  that lands, the second step says "type" in all four languages on both surfaces
  — the copy follows the code, not the intention.
- **Deleting a child's figures no longer needs a browser.** All three surfaces
  ask twice, and all three show the server's own `rows_affected` afterwards:
  "deleted" with nothing behind it is the one claim a family has no way to
  check. On both phones the control sits at the very foot of the child's screen,
  deliberately outside the usage load — a day that failed to fetch is one of the
  moments a parent is most likely to want the figures gone.
- **Nothing reads `/v1/children/{id}/summary`.** The route is in `openapi.json`
  and no client calls it.
