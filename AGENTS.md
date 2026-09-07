# MusicCabin AI Agent Instructions

MusicCabin is a Kotlin-based third-party YouTube Music client that follows Material 3 design guidelines.

## Project constraints

- Follow the human contributor's instructions and preserve existing work that is outside the current task.
- Edit app strings only in `app/src/main/res/values/metrolist_strings.xml`. Do not edit `app/src/main/res/values/strings.xml`, other language files, or other string resource files unless the task explicitly requires it.
- Do not change the app database schema unless the user explicitly requests a database migration.
- For user-visible app changes that materially affect documented behavior or features, update both `README.md` and `README.zh-TW.md`. Keep their structure and information aligned, translated rather than duplicated, and avoid expanding them for internal changes.
- Increase the app patch version only for app code, resources, or dependency changes. Documentation and GitHub Actions changes do not require a version bump.
- Whenever an app version changes, add or update the matching `## <version>` section in `changelog.md` in the same change. Include the actual user-visible features, fixes, configuration changes, and upgrade notes in both Chinese and English; do not defer this until release day.
- Prefer clear names and formatting. Add comments only for non-obvious logic, and consider performance, battery usage, and maintainability for app changes.

## Task boundaries and workflow

- For implementation tasks, inspect `git status --short --branch` before editing. Preserve unrelated uncommitted changes; do not reset, force-push, or overwrite them.
- Before editing, read this file completely and follow any directly referenced task or release documentation that applies to the requested change.
- Fetch and rebase from the requested target branch only when the task requires repository synchronization and the worktree is clean. Read-only reviews and documentation analysis do not require synchronization.
- Use Conventional Commit-style messages such as `feat(ui): add dark mode support` when the user asks for a commit. Commit and push only when the user explicitly requests those actions.
- Infer routine implementation details from the repository. Ask for clarification only when ambiguity could change behavior, data safety, an irreversible action, or an external side effect.
- For implementation requests, continue through the relevant validation and report what was completed; do not stop after describing the next step when the task is already actionable.

## Subagent delegation

> Maintenance note: This section is an optional cost and quality strategy. If token usage becomes too high, remove this entire section first and compare the cost and accuracy before restoring it.

- When Sol and Luna are available, Sol remains the primary agent and owns the complete Android feature implementation, architecture, final diff, and completion decision.
- Do not split related Android implementation across agents merely by file type. Keep Compose, ViewModel, repository, navigation, manifest, resources, and related changes with Sol when they belong to one feature.
- Use Luna only when the task is bounded and delegation is likely to reduce context cost, improve verification, or enable useful parallel work. If subagents are unavailable, Sol continues directly.

### Luna roles

- Scout: read-only repository exploration, symbol search, reference tracing, and project summaries. Do not modify files.
- Operator: run deterministic commands such as Git status, repository searches, Gradle builds, tests, lint, and log collection. Report exact failures and do not modify Android application logic.
- Mechanical Worker: make isolated, precisely specified, low-risk edits such as documentation, changelog, formatting, version updates, or exact text replacements. Verify the requested scope after editing.

### Handoff and failure rules

- Before delegation, define the scope, allowed files, read/write permissions, expected output, and stop condition.
- Avoid switching agents for every file or small operation.
- If Luna finds a failure, report the evidence to Sol. Sol analyzes the cause and performs implementation fixes; Luna may rerun verification.
- All subagents must follow this file's Git, database, resource, documentation, safety, and validation rules. No subagent may reset, force-push, commit, or push unless explicitly authorized by the user.
- Sol must review the final diff and validation results before declaring the task complete.

## Validation

- Match validation to the change. Documentation-only changes do not require an app build; app code, resources, or dependencies normally require the Foss Debug build:

  ```bash
  ./gradlew :app:assembleFossDebug
  ```

- Run focused tests or lint checks when they are relevant to the changed code. Broaden validation only when a failure or the change scope justifies it.
- Follow [`docs/release.md`](docs/release.md) for Foss Release builds, signing, changelog, and GitHub Release procedures.
