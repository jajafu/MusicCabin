# MusicCabin Release Guide

This guide covers the Foss Android release workflow. Routine code changes do not require release actions.

## GitHub Actions workflow

The [`Build & Release MusicCabin Foss`](../.github/workflows/build-debug.yml) workflow is started manually with `workflow_dispatch`. It builds and lints the Foss Release APK, uploads it as an artifact, and publishes or updates the GitHub Release when run from `main`.

The workflow reads the version from `app/build.gradle.kts` and expects the following GitHub Secrets:

- `RELEASE_KEYSTORE_BASE64`
- `RELEASE_STORE_PASSWORD`
- `RELEASE_KEY_ALIAS`
- `RELEASE_KEY_PASSWORD`

The release keystore and its passwords must never be committed to the repository.

## Release notes

Before starting the workflow, add a complete `## <version>` section to `changelog.md`. Describe the actual user-visible features, fixes, configuration changes, and upgrade notes for that version. The workflow uses this section to generate the GitHub Release notes and fails if it is missing.

The release APK must keep the existing package ID and signing key for in-place updates. Call out package or signing changes explicitly in the upgrade notes.

## Manual validation

For local pre-release validation, run:

```bash
./gradlew :app:assembleFossRelease :app:lintFossRelease --console=plain --warning-mode summary
```

Do not publish a release from a dirty or unintended branch. Verify the version, changelog section, generated APK, and GitHub Actions result before distributing it.
