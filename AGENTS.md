# Repository agent rules

1. The public application uses documented provider APIs only.
2. Calendar presentation depends on the provider-neutral calendar contract.
3. AniList is the default public calendar provider.
4. Additional providers belong in separate private modules and must not leak into this repository.
5. Keep OAuth credentials and access tokens out of source control and logs.
6. Run unit tests, lint, Stable Debug assembly, AndroidTest assembly, and source scans before review.
