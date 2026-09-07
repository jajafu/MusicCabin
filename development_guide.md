# MusicCabin Dev Guide

This file outlines the process of setting up a local dev environment for MusicCabin.

## Contribution workflow

Before implementation work, inspect the current branch and worktree with `git status --short --branch`. Preserve unrelated changes and do not reset or overwrite them. Synchronize from the requested target branch only when the task requires it and the worktree is clean.

Use focused validation for the affected area. App code, resources, or dependency changes normally use:

```bash
./gradlew :app:assembleFossDebug
```

Documentation-only changes do not require an app build. Run additional tests or lint checks when the changed code or a failure makes them relevant. Commit or push only when the contributor explicitly requests it. See [`docs/release.md`](docs/release.md) for release procedures.

## Prerequisites

- JDK 21
- Android platform tools (if you don't have a keystore already)
- protobuf-compiler v3.21 or newer

## Basic setup

This has been tested on Linux, but should work on other platforms with some adjustments.

```bash
git clone --recurse-submodules https://github.com/jajafu/MusicCabin
cd MusicCabin
git submodule update --init --recursive
cd app
bash generate_proto.sh
cd ..
[ ! -f "app/persistent-debug.keystore" ] && keytool -genkeypair -v -keystore app/persistent-debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" || echo "Keystore already exists."
./gradlew :app:assembleFossDebug
ls app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

### GitHub Secrets Configuration

This project uses GitHub Secrets to securely store API keys for building releases. To set up the secrets:

1. Go to your GitHub repository settings
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Add the following repository secrets:
   - `LASTFM_API_KEY`: Your LastFM API key
   - `LASTFM_SECRET`: Your LastFM secret key

4. Get your LastFM API credentials from: https://www.last.fm/api/account/create

**Note:** These secrets are automatically injected into the build process via GitHub Actions and are not visible in the source code.
