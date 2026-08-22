# Schirmziit — iOS parent viewer

Reads a family's own Schirmziit server and shows a child's screen time. It does
**not** measure anything on this phone: reading another app's usage on iOS needs
Apple's Family Controls entitlement, which a free Personal Team cannot sign.
See the design doc for the child-agent story.

## Toolchain

| Tool | Version | Where |
|---|---|---|
| Xcode | **27.0** (27A5237l) | `~/Downloads/Xcode-beta.app` — *not* in `/Applications` |
| iOS SDK | 27.0 | in the beta only; `/Applications/Xcode.app` is 26.6 with SDK 26.5 |
| Device | iPhone 16 Pro on iOS 27.0 | must be paired in Xcode → Window → Devices |
| xcodegen | brew | regenerates `Schirmziit.xcodeproj` from `project.yml` |

The phone runs iOS 27, so builds must use the beta. No `sudo xcode-select`
needed — point `DEVELOPER_DIR` at it per command:

    export DEVELOPER_DIR=~/Downloads/Xcode-beta.app/Contents/Developer

## Build, test, install

    cd ios
    xcodegen generate                  # after editing project.yml

    # Unit tests on a simulator
    xcodebuild -project Schirmziit.xcodeproj -scheme Schirmziit \
      -destination 'platform=iOS Simulator,name=iPhone 17' test

    # Device build. DEVELOPMENT_TEAM is the certificate's OU, which is NOT the
    # value in parentheses in the identity's name:
    #   security find-certificate -a -c "Apple Development" -p \
    #     | openssl x509 -noout -subject
    #   → OU=S3JX3CJ9SS  ← team;  CN=… (6TCRCYDSQQ)  ← not the team
    xcodebuild -project Schirmziit.xcodeproj -scheme Schirmziit \
      -destination 'id=<device-udid>' \
      -allowProvisioningUpdates DEVELOPMENT_TEAM=S3JX3CJ9SS build

    xcrun devicectl list devices                       # find the device id
    xcrun devicectl device install app --device <id> \
      ~/Library/Developer/Xcode/DerivedData/Schirmziit-*/Build/Products/Debug-iphoneos/Schirmziit.app
    xcrun devicectl device process launch --device <id> ch.jorisda.schirmziit.viewer

First launch after a fresh install fails with `FBSOpenApplicationErrorDomain
error 3` until the profile is trusted on the phone: Settings → General → VPN &
Device Management → Developer App → Trust.

**Free Personal Team means a 7-day profile.** After a week the app refuses to
launch; rebuild and reinstall with the commands above. A paid account removes
that, and is also the prerequisite for ever building the iOS child agent.

## Screenshot / demo runs

Debug builds read a few environment variables so a run can sign in without
tapping (absent from release builds):

    SIMCTL_CHILD_SCHIRMZIIT_SERVER=https://schirmziit.example.ch \
    SIMCTL_CHILD_SCHIRMZIIT_EMAIL=… SIMCTL_CHILD_SCHIRMZIIT_PASSWORD=… \
    SIMCTL_CHILD_SCHIRMZIIT_AUTOLOGIN=1 SIMCTL_CHILD_SCHIRMZIIT_OPEN_FIRST_CHILD=1 \
    xcrun simctl launch <simulator-id> ch.jorisda.schirmziit.viewer

## Design

Apple's HIG for structure — grouped lists, navigation stack, SF Symbols, SF Pro,
pull-to-refresh — wearing the dashboard's palette, so the phone and the browser
read as one product. Dynamic colour stays off deliberately. Four languages
(de/fr/it/en) following the system language; `LocalizationTests` fails the build
if one falls behind.
