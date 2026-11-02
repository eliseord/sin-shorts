# Sin Shorts

*English · [Español](README.es.md)*

Open-source Android app that blocks short-form video **without blocking the apps that contain it**:

| App | What gets blocked | What keeps working |
|---|---|---|
| YouTube | The Shorts tab and any Short you open | Regular videos, subscriptions, search |
| Instagram | The Reels tab and the Reels viewer | Feed, stories, messages, profiles |
| Instagram: following only | Covers the home feed (endless suggestions) and locks scrolling on the home screen; one button opens the “Following” feed | “Following” feed, stories, messages, notifications, posting |
| Instagram: Explore | Covers the Explore grid | Searching for people |
| Facebook *(experimental)* | The Reels tab | Everything else |
| Snapchat *(experimental)* | Spotlight | Chats, camera, stories |
| Browsers | youtube.com/shorts, Reels and TikTok on the web | The rest of the web |
| TikTok *(off by default)* | The whole app | — |

When you enter one of those sections, the app presses Back for you. If the app insists, it sends you to the home screen.

**Privacy:** the app does not request the Internet permission, so it cannot send anything off the phone. It only reads the screen structure of the listed apps to recognise those sections.

The app is in English by default and can be switched to Spanish in the setup guide or in **Options → Language**.

📐 Technical dossier (architecture, timing, rule engine, iteration log): [`docs/`](docs/)

## Install

1. Play Protect blocks accessibility apps downloaded from a browser or file manager. To install it: Play Store → your profile picture → Play Protect → ⚙️ → turn off “Scan apps with Play Protect”, install `SinShorts.apk`, and **turn it back on**. Alternatively, run `adb install SinShorts.apk` from a computer.
2. Open **Sin Shorts**. On first launch a **step-by-step guide**:
   - takes you straight to the accessibility switch and detects when you’ve turned it on;
   - if Android shows “App was denied access” or “Restricted setting”, explains how to unlock it and opens the right screen;
   - optionally asks Android not to stop it to save battery;
   - lets you choose what to block;
   - opens a test Short and checks that it takes you out.

You can repeat the guide from **Options → Repeat the setup guide**. If the service gets turned off, the main screen tells you and offers a button to fix it.

## If an app updates and stops being blocked

YouTube, Instagram and others sometimes change their internal IDs. To fix it without touching code:

1. In the app, turn on **Diagnostic mode**.
2. Open the section you want to block (for example, the Reels tab) and go back to Sin Shorts.
3. Find the new ID (whatever is marked “Selected” is usually the active tab) and add a rule under **Custom rules**.

Rule format, one per line:

```
package[,package2] | type:value
```

| Type | Meaning |
|---|---|
| `id:NAME` | a visible view has that ID |
| `idsel:NAME` | that view is selected (active tab) |
| `text:TEXT` | a visible view’s text or description contains TEXT |
| `textsel:TEXT` | same, but selected |
| `url:TEXT` | the browser address bar contains TEXT |
| `app` | blocks the whole app |

Example: `com.google.android.youtube | id:reel_recycler`

The built-in rules live in `app/src/main/java/io/github/sinshorts/Rules.java`.

## Build

**Option A — without Android Studio (Linux, tested):**

```bash
sudo apt install openjdk-17-jdk aapt apksigner dalvik-exchange zipalign curl
./build.sh            # → build/SinShorts.apk
```

The first run creates a signing key, `sinshorts.keystore`. Keep it: installing a new version over an old one requires the same key.

**Option B — GitHub:** the `.github/workflows/build.yml` workflow builds the APK on every push and attaches it in the *Actions* tab.

**Option C — Android Studio:** open the project folder (it includes `build.gradle`).

## Structure

```
app/src/main/
  AndroidManifest.xml
  java/io/github/sinshorts/
    BlockerService.java      accessibility service: detects and exits
    Rules.java               built-in rules and rule format
    Curtain.java             overlay over Instagram's feed
    MainActivity.java        settings screen
    OnboardingActivity.java  setup guide
    L.java                   app language (English / Spanish)
    Diagnostics.java         diagnostic-mode captures
  res/xml/accessibility_service.xml
```

MIT licence.
