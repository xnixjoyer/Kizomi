# Exact-head CI evidence

## Current verification head

- Implementation parent: `6c7981ae739f936251d7dbfce19880d01a930c98`
- Exact documentation head: resolve from PR #3 after this commit
- Base: `e44efaffae565b0d6a642547d5e37e0f402ea12e`

## Previous run finding

Workflow run `30057402066` (run 117) was not completion evidence. It identified and led to fixes for:

1. Kotlin wildcard annotation placement in `CalendarExtensionModule`;
2. use of plain string preferences in the extension settings implementation;
3. two non-screen infrastructure files being classified as screen entry points by the screenshot-protection source scanner.

The implementation now stores extension state in a Preferences DataStore under `noBackupFilesDir`, uses the Kotlin-version-compatible function annotation, and keeps the screen scanner strict while excluding only the exact two non-UI infrastructure paths.

## Completion rule

This file becomes final only after the exact published PR head passes every workflow gate and the diagnostic artifact is independently downloaded and verified. A newer documentation commit invalidates older exact-head evidence.
