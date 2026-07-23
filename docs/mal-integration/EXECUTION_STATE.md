# MyAnimeList integration execution pointer

The authoritative resumable implementation state is:

`docs/mal-compliance/EXECUTION_STATE.md`

The active repository work is PR #3 from `compliance/mal-api-agreement-readiness` into `main`, based on `e44efaffae565b0d6a642547d5e37e0f402ea12e`.

Do not use historical phase numbering, old branches, old pull requests, or prior multi-provider architecture as current requirements. The product model is one app-wide state: `UNCONFIGURED`, `ANILIST_ONLY`, or `MAL_ONLY`.

The pull-request description, not a copied value in this file, is authoritative for the final exact head, workflow run/job, test count, artifact identifiers, archive digest, APK size, and APK SHA-256.

No implementation agent merges, approves, enables auto-merge, force-pushes, rebases, or rewrites history. The owner performs final review and merges with **Create a merge commit** only after all automated and external gates are satisfied.
