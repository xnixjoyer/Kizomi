# Performance contract

Historical lag came from AniList N+1 searches, repeated full candidate scans/string normalization, repeated Room/Flow projections, main-thread transformations, timer proliferation, and wasteful CI invocations. The Phase-1 indexed prototype reduced its recorded local pass from about 12.1 seconds to about 2.6 seconds and increased safe matches from 58/87 to 77/87; those are historical measurements, not claims for the current branch.

Current safeguards are indexed canonical keys, bounded/cached candidate pools, no default per-title search, one shared off-main projection, one shared minute clock, single-flight refresh, atomic persistence, bounded diagnostics, and stable Compose keys. The settings screen uses one parent lazy scroller rather than fixed-height nested lazy lists. Density changes swap semantic tokens; they do not apply a global scale or rebuild network/database snapshots.

CI uses Gradle cache/parallelism, finite timeouts, cancellation of superseded runs, and failure-only report upload. Performance acceptance must use the optimized `stableRelease` APK on a device. Cold start, first composition, navigation latency, dialog opening/rendering, refresh/parse/match/persist timings, request counts, recomposition behavior, and memory remain device-validation measurements; Debug/LeakCanary results are not release evidence.
