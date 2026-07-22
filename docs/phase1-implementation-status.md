# Phase 1 implementation status

Phase-1 PR #9 was merged into `main` on 2026-07-15 as commit `33556291150b1d2f1c1568f2f410c9ddcf3cbb79`. The former branch `fix/phase1-matching-library-signing` is historical, not remaining merge work.

The merged baseline contains parser 2, matcher 4, Room schema 2, the bounded current-calendar merge, indexed conservative matching, zero-request active-library fallback, atomic persistence, single-flight refresh, shared projection/clock, opt-in Beta fallbacks, manual mapping, diagnostics, and verified Stable Debug/Release CI.

The current stabilization branch keeps that architecture and advances matcher to version 5. It adds conservative movie/special/delayed-release recovery, bounded query caches/diagnostics, adaptive manual recovery, semantic UI density, configurable main navigation, optional detail edge-to-edge, and persistent-signing workflows.

Phase 1 itself is merged. Remaining release work belongs to the stabilization checklist: green checks for the new head, persistent certificate configuration, update installation, physical-device/UI matrix, and authenticated live diagnostic review.
