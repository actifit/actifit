# Actifit Android v0.14.2 (versionCode 66) — Release Notes

**Baseline:** first production release since **v0.14.0 (versionCode 64)**, which shipped to Google Play on 2026-05-18. v0.14.1 and v0.14.2 were internal-testing bumps; this is the first time all of the work below reaches production.

**Scope:** 108 files changed (+10,040 / −5,571) across ~23 shipped commits.

---

## ✨ New features

- **Living companion & native profile screen** — a companion aura that evolves into a fitness spirit animal with per-tier flourishes, on a redesigned native profile screen.
- **Multi-metric activity rings** — steps, distance and calories shown as animated concentric rings, with animated animal artwork.
- **Unified dashboard across all tracking modes** — the same multi-ring visual now renders whether you track via phone sensors, Fitbit, or Health Connect.
- **Real Fitbit distance & calories** — Fitbit now feeds actual distance and calories into the dashboard (sensor estimates are marked with ≈).
- **AI Assistant** — an in-app Gemini-powered chat popup on the post screen for fitness tips and posting help, plus improved exercise matching.
- **DHF proposal vote prompt** — surfaces the Actifit DHF proposal to non-voters with an in-app prompt.

## 🩺 Health Connect improvements

- Fixed **step double-counting** by switching to proper aggregation.
- Fixed **15-minute intervals** being broadcast as identical values.
- Improved dashboard **readability**, added **distance & calories**, fixed the **0-calorie** display and the notification **badge** cut-off.
- Added **field diagnostics** for Health Connect access and reward-claim errors, so support can act on a single screenshot.

## 📏 Measurement & localization

- **Distance and pace now respect the US / metric setting** (previously always metric).
- **13 languages** updated throughout — More menu, DHF vote prompt, Hive Transactions, and profile/companion strings.

## 🔒 Security & stability

- Signature verification modernized to a **SHA-256 certificate allow-list** (supports multiple certs) with fail-closed enforcement.
- Security checks consolidated and **driven by build type** for cleaner release hardening.
- Fixed a **snap/comment vote crash**, a blanking comment list, and double-counted votes.
- Various crash and stability fixes.

---

## Play Store "What's new" (≤500 chars)

See `release_notes_v0.14.2_playstore.md`.

## Not included

- The signup wizard (PR #81) was merged then **reverted** — it does not meet the backend `confirmPayment` contract and is being reworked in PR #85. It is **not** part of this release.
