# Actifit Android — Feature Roadmap (Single Source of Truth)

**Purpose.** One authoritative, reconciled planning document for the Actifit Android app.
It merges the four scattered planning docs and reconciles them against what has actually
shipped on `develop` (GitHub `actifit/actifit`) as of the reconcile date below. This file
**replaces** the four source docs as the canonical roadmap; the originals are kept for
history but should no longer be edited.

**Last reconciled:** 2026-08-24 (against `origin/develop`, commits since 2026-07-01).

**Merged from:**
- `tasks/feature-backlog.md` — port-to-native web-parity, net-new app-leads, prioritization, backend deps
- `tasks/feature-vision.md` — aspirational product vision ("From Tracker to Movement")
- `tasks/suggested_features.md` — growth & feature roadmap (RICE-scored)
- `tasks/tasklist.md` — dashboard enhancement tasklist (P1/P2/P3)
- Context only (not inlined): `docs/screen-redesign-mockups.md` (Material 3 mockups),
  `documentation/actifit_features_and_user_rank.md` (feature/rank reference).

> **House rule reflected here:** Challenges/Duels carry **no AFIT wagering / gambling**
> (per `f4fe231` — wager framing was deliberately removed). Competitive modes reward via
> bonus/prize pools funded by the platform, never user-staked bets.

---

## Status legend

| Marker | Meaning |
|---|---|
| ✅ | **Shipped** — merged to `develop` |
| 🚧 | **In progress** — partially landed or reopened for rework |
| 📋 | **Planned** — agreed/backlogged, not started |
| 💡 | **Idea / Vision** — aspirational, not yet committed |
| ❓ | **Verify** — status unclear from history; confirm before planning |

---

## 1. North-star vision — "From Tracker to Movement"

The thesis (from `feature-vision.md`): Actifit today is a **utility** (track → post → earn).
The apps people open daily (Duolingo, Strava, Pokémon GO, BeReal) are **identities and
games**. Three moves get us there — **Identity**, **Play**, **Belonging** — layered on top
of the *unchanged, fair* AFIT economy (excitement comes from feel and play, never from
inflating payouts). Each pillar is scored 🔥 Excite / 🪝 Hook in the source.

- **Living Fitness Identity (§1).** Profile as a character that visibly evolves with real
  activity: a **companion/aura** (Tamagotchi hook — thrives on streaks, dims when quiet,
  levels up at milestones), a shareable **trading-card profile** with rarity tiers,
  milestone-unlocked **dynamic themes/skins**, a Spotify-Wrapped-style **"Year/Week in
  Motion"** recap, and a **live pulse header**. The card + recap are built to leave the app
  as free word-of-mouth. _(Native profile + evolving companion have shipped — see §2.)_
- **Quests & Collectible Saga (§2).** Turn static badges into a living quest system:
  rotating **weekly/seasonal quests** with countdowns, **mystery/surprise badges**,
  **rarity tiers + a curated trophy case**, **evolving badges** (streak ranks up
  7→30→100→365), and a moonshot **on-chain collectible edition**.
- **Leagues, Rivals & Ghosts (§3).** Replace the demotivating global top-N with
  **leagues/divisions** (Bronze→Diamond, weekly promotion/relegation), **"people near you"**
  rank slices, weekly **rivals**, **ghost races** (your best week / a friend's ghost), and
  **city/country boards** for local pride and community recruiting.
- **Movement, Live (§4).** Make the feed feel alive: a **live activity pulse**
  ("1,204 actifiters moving now"), lightweight **cheers/reactions** (a cheer can carry a
  micro-AFIT tip via existing `tipAccount` rails), **kudos & shout-outs**, ephemeral
  **Activity Stories** (24h), and **walking buddies / co-walks**.
- **The Dopamine Economy (§5).** Wrap the fair economy in immediate, surprising feedback:
  **daily spin / loot drop**, **streak freeze / repair** (Duolingo's killer anti-churn
  feature), **surprise AFIT rain / power hours**, **combos & multipliers**, and a moonshot
  **Treasure Walk** (geo-placed AFIT/badge drops collected by physically walking there).
- **The Arena (§6) — the marquee bet.** Flagship social-competitive mode: **live 1v1 duels**
  (24h step duel, real-time dual progress bar, AI trash-talk), **team raids / boss battles**,
  **City vs City** tournaments, and **brackets**. No AFIT wagering (house rule). Needs new
  backend (duel lifecycle, escrow-free prize resolution) — scope early.
- **AI Coach with a Personality (§7).** A **named coach persona** chosen at signup (voice,
  attitude, memory): proactive check-ins, voice morning briefing + evening retro, in-duel
  commentary, adaptive multi-week memory, multiple personalities (drill sergeant / zen /
  cheerleader). _(Base AI assistant has shipped — see §5 of inventory.)_
- **Win in 60 seconds onboarding (§8).** A playable first minute: pick coach + companion,
  grant step access → instantly see today's steps + a first AFIT drop + first badge, a
  3-tap "first quest," and optional Health-Connect backfill of yesterday's steps.

**Signature bets (pick ~3 to become famous for):** Living Companion profile · Leagues +
Rivals · The Arena (duels & city battles) · Daily spin + streak freeze · Year/Week in
Motion recap · Treasure Walk.

---

## 2. Feature inventory (merged, deduped)

Each entry: status · one-line description · effort/impact (where a source gave it) ·
backend dependency · source doc(s). Duplicates across docs are collapsed into one entry.

### A. Port-to-native / web-parity (backend already live — best ROI)

| Status | Feature | Notes | Impact / Effort | Backend | Sources |
|---|---|---|---|---|---|
| ✅ | **Native In-App Profile** | `ProfileActivity` replaces CustomTab to actifit.io; tabs mirror web (About/Fitness/Community/Wallet/Badges). Shipped w/ living-companion aura (`285a7e8`). | High / Med | None | backlog 2.1, vision §1 |
| ✅ | **Living-companion aura on profile** | `CompanionUtil` — evolves into a fitness spirit animal with per-tier flourishes; animated multi-metric rings (`09a8bd8`, `936e8f5`). | — | None | vision §1 |
| 📋 | **Friends (mutual friend-request system)** | Full surface: friends list, sent/received requests, accept/cancel/unfriend, suggested friends. **Partial:** friend-name **autocomplete** shipped for gadget beneficiary (`FriendAccountAdapter`, #92/#88). Uses Actifit's own friend API, *not* Hive follow. | Very High / Med | Ready (`/userFriends`, `/addFriend`, `/acceptFriend`, `/cancelFriendRequest`, `/dropFriendship`, `/userFriendRequests`) | backlog 2.2 (0.1) |
| 📋 | **Badges / Achievements (existing catalog)** | Surface existing `iso`, `rewarded_activity_lev_N`, `doubledup`, `charity` badges natively with claimable/claimed/missed states on profile Badges tab; fire milestone celebration on unlock. | Med-High / Low-Med | Ready (`/userBadges`, `/allUserBadges`, `/claimBadge`, `/isoParticipant`, `/charityDonor`, `/luckyWinner`) | backlog 2.3 |
| 📋 | **AFIT Rewards Store / Redemption** | Native store mirroring web `market.vue`: spend AFIT on gadgets/boosters, consultations, ebooks, physical products; prize-ticket cycle. (Existing `MarketActivity` is token trading only.) Also satisfies dashboard gadgets empty-state CTA. | High / Med-High | Ready (`/products`, `/mintProducts`, `/purchaseRealProduct`, `/confirmPayment`, gadget buy/activate family, `/buyAFITHive`, `/downEbook`, ticket endpoints) | backlog 2.4 |
| 📋 | **Tracked Referral Program** | Native referral screen: personal link/code, referred-accounts list, reward status, free-signup claims. (Current `refer_friend` is a plain share.) | Med / Med | Ready (`/referrals`, `/signups`, `/signupInfo`, `/activeRefReward`, `/myFreeSignupLinks`, `/claimableFreeAccounts`, `/claimFreeSignupAccounts`) | backlog 2.5 |
| 📋 | **Notification Preferences (per-type opt-in)** | Per-category toggles (friend request/accept, upvote, reward, …) mirroring web `settings.vue`. FCM push already works; no preference UI yet. | Med / Low-Med | Ready (`/notificationTypes`, `/userSettings`, `/activeNotifications`, `/markRead`, `/markAllRead`, `/registerUserNotification`) | backlog 2.6 |
| 📋 | **Body-Metrics Trend Charts** | Native weight + measurements (chest/waist/thighs/bodyfat) trend charts over time; reuse `ChartManager`. Composer collects them but never charts in-app. **Open Trello #45.** | Med / Low-Med | Ready (`/trackedMeasurements/:user`) | backlog 2.7, tasklist context |
| 📋 | **Dark Mode / Theming** | Persisted dark-mode toggle (web parity); saves OLED battery given all-day foreground service. Redesign spec targets Material 3 + Actifit Red. | Med / Med | None | backlog 2.8, mockups |

### B. Net-new / app-leads (little or no web equivalent)

| Status | Feature | Notes | Impact / Effort | Backend | Sources |
|---|---|---|---|---|---|
| 📋 | **Challenges / Duels / Teams ("The Arena")** | 1v1 duels → group/team challenges → City vs City → brackets. Marquee engagement bet. **No AFIT wagering (house rule).** Fully greenfield incl. backend. | Very High / High | 🧱 New (duel/challenge lifecycle, prize resolution) | backlog 3.1, vision §6, suggested Phase 4 |
| 📋 | **Friends-only Feed & Leaderboard filters** | Feed filter is cheap once Friends exists (client-side filter). Friends **activity** leaderboard needs a new endpoint (only token-holder/delegator rankings exist today). | High / Low (feed), Med (board) | Feed none; leaderboard 🧱 new | backlog 3.2, vision §3 |
| 📋 | **GPS Route Polish + Shareable Maps** | Extend existing `RouteRecordingService`/`RouteMapActivity` (web is viewer-only — mobile advantage): auto-pause, splits, elevation, shareable route snapshot image. Routes persist in post/workout JSON (local/embedded). | Med / Med | None | backlog 3.3 |
| 📋 | **Multi-Activity Types from Health Connect** | Read HC `ExerciseSessionRecord`/distance/calories so cyclists/swimmers/workout users earn too (`verified_posts` already stores `activity_type`). | High / Med-High | 🧱 Partial (non-step reward scoring rules) | backlog 3.4 |
| 💡 | **Water & Sleep Quick-Log** | Lightweight daily trackers (or read sleep from HC) to add touchpoints + enrich AI context. Local-only first. | Low-Med / Med | 🧱 if persisted server-side | backlog 3.5 |
| 📋 | **Post Scheduling & Draft Manager** | Save drafts + schedule posting to hit daily cadence; extends `PostSteemitActivity`. Local drafts first; server scheduling later. | Med / Med | 🧱 for server-side scheduling | backlog 3.6 |
| 📋 | **Home-screen / Lock-screen Widget** | Glanceable steps + goal ring + streak from `StepsDBHelper`, pushed by `ActivityMonitorService`. Strong daily-retention lever. | High / Med | None | backlog 3.7 |
| 💡 | **Wear OS Companion** | Live step count, goal ring, start/stop GPS route on the watch. | Med / High | None | backlog 3.8 |
| 📋 | **Adaptive Reminders** | Make fixed-time `ReminderNotificationService` context-aware ("1,200 steps short with 2h daylight left") using local data. | Med / Low-Med | None | backlog 3.9 |

### C. Dashboard & UX enhancements (`tasklist.md`)

Most of this list has shipped. Remaining items carried into prioritization below.

| Status | ID | Item | Notes |
|---|---|---|---|
| ✅ | P1-1 | Step-progress arc ring | Replaced pie chart; milestone ticks 5K/7K/10K |
| ✅ | P1-2 | "Today's Goal" progress label | Step count as headline number |
| ✅ | P1-3 | Contextual action banner / smart nudge | Replaced static "Rewards Cycle" panel |
| ✅ | P1-4 | Condense footer to 5 items | Home · Social · Market · Leaderboard · More |
| ✅ | P1-5 | Earnings clarity card (estimated AFIT) | Server-side `/getEstimatedReward` scoring endpoint |
| ✅ | P2-1 | 7-day streak / consistency strip | From `StepsDBHelper` |
| ✅ | P2-2 | Personalized AI insight card (Gemini) | Daily insight below hero |
| ✅ | P2-3 | Gadgets empty-state → conversion CTA | "Boost earnings up to 2× · Browse Market" |
| ✅ | P2-4 | Milestone celebration animations | Confetti / badge-unlock on 5K/7K/10K crossing |
| ✅ | P2-5 | Monthly activity heatmap | GitHub-style contribution graph |
| ✅ | P3-3 | Community feed preview strip | 3 recent posts above footer |
| ✅ | P3-4 | Configurable daily step goal | **Shipped** via #89/#91 + guardrails + i18n #86; drives ring + aura (was pending in source) |
| 📋 | P3-1 | Reorderable / customizable dashboard cards | Long-press drag + visibility toggles; store order in prefs |
| ❓ | P3-2 | Health-metrics chips (HC) | Distance/calories rings shipped (#82/#83); **Resting HR + Active Minutes chips — verify** whether the dedicated chip row landed |

### D. Gamification & social (vision §1–6 net-new mechanics)

| Status | Feature | Notes | Sources |
|---|---|---|---|
| ✅ | **Companion / aura identity** | Shipped on native profile (evolving spirit animal, per-tier flourishes) | vision §1 |
| 💡 | **Trading-card profile (shareable, rarity tiers)** | Holo/animated card from rank+streak+lifetime steps+badges; built to share | vision §1 |
| 💡 | **Dynamic milestone themes/skins** | e.g. 1M lifetime steps → "Marathon Gold" skin | vision §1 |
| 💡 | **"Year / Week in Motion" recap** | Spotify-Wrapped-style animated, shareable recap | vision §1, suggested (Visual Progress Cards overlaps) |
| 💡 | **Visual Progress "Flex" Cards** | Branded shareable image of daily/weekly steps + AFIT + rank (viral loop). RICE **Critical (24.0)**. Overlaps the trading-card/recap ideas. | suggested Phase 1 |
| 💡 | **Weekly/seasonal quests + countdowns** | Rotating goals ("Walk 3 sunrises this week") | vision §2 |
| 💡 | **Mystery / surprise badges** | Unexpected unlocks ("Night Owl", "Globetrotter") | vision §2 |
| 💡 | **Rarity tiers + curated trophy case** | Badge tiers, holo art, showcase | vision §2 |
| 💡 | **Evolving streak badge** | 7→30→100→365 visual rank-up | vision §2 |
| 💡 | **On-chain collectible badges (moonshot)** | Limited seasonal badges minted on Hive/BSC | vision §2 |
| 💡 | **Leagues / divisions (promotion/relegation)** | Bronze→Diamond weekly race everyone can win | vision §3 |
| 💡 | **"People near you" rank slice** | Rank window around the user, not just top-N | vision §3 |
| 💡 | **Weekly rivals / nemesis** | Nominated rival at your level; beat for bonus AFIT | vision §3 |
| 💡 | **Ghost races** | Race your own best week or a friend's ghost | vision §3 |
| 💡 | **City / country leaderboards** | Local pride; community recruiting | vision §3 |
| 💡 | **Live activity pulse / globe** | "N actifiters moving now" social proof | vision §4 |
| 💡 | **Cheers & reactions (+ micro-AFIT tip)** | Claps/fire/💪 beyond upvotes; tip via `tipAccount` rails | vision §4 |
| 💡 | **Kudos & shout-outs** | Real-time social feedback ("Sara cheered your 15K") | vision §4 |
| 💡 | **Activity Stories (ephemeral 24h)** | BeReal/IG-style low-pressure sharing | vision §4 |
| 💡 | **Walking buddies / co-walks** | Opt-in synced session w/ live progress bars | vision §4 |
| 💡 | **Daily spin / loot drop** | Free daily pull (AFIT/boosters/shards/streak-freeze) | vision §5 |
| 💡 | **Streak freeze / repair** | Save a missed day; attacks #1 churn moment. Low-Med effort, high ROI | vision §5 |
| 💡 | **Surprise AFIT rain / power hours** | Unannounced bonus drops / temporary multipliers | vision §5 |
| 💡 | **Combos & multipliers** | Visible consecutive-day multiplier | vision §5 |
| 💡 | **Treasure Walk (geo-AFIT, moonshot)** | Pokémon-GO-style physical collection | vision §5 |

### E. AI

| Status | Feature | Notes | Sources |
|---|---|---|---|
| ✅ | **AI Assistant / post composer (Gemini)** | Chat popup drafting in post composer (#73) + enhancements #87/#90 (cancel-on-close, retry ladder, session persistence, "Insert" button i18n). Satisfies "Smart Post Drafts." | backlog §1, suggested Phase 5 |
| ✅ | **Dashboard AI insight card (Gemini)** | Daily insight (tasklist P2-2) | tasklist P2-2, suggested Phase 2 (partial) |
| ✅ | **AI workout generation (Workout Wizard)** | Pre-existing `AiService` plan generation | features doc §3 |
| 📋 | **AI "Morning Briefing" & Evening Retro** | Proactive greeting analyzing last 7 days; drive mid-day engagement. RICE **High (18.0)**. Voice version = vision §7. | suggested Phase 2, vision §7 |
| 💡 | **AI Coach persona (named, voice, memory)** | Choose personality at signup; in-duel commentary; adaptive multi-week memory | vision §7 |
| 📋 | **AFIT "Earnings Estimator"** | Real-time dashboard calculator of potential reward by steps + rank. RICE **High (12.0)**. Overlaps shipped P1-5 estimated-reward card — reconcile/possibly done. ❓ verify if further calculator work remains. | suggested Phase 3 |

### F. Infrastructure / onboarding

| Status | Feature | Notes | Sources |
|---|---|---|---|
| 🚧 | **Signup wizard** | Native multi-step onboarding replacing "Create account" hyperlink. #81 implemented → reverted (`6cd8cc3`) → **reworked & reopened as #85 (`0f00dfc`, "REWORK REQUIRED")**. `SignupStateStore` present. Blocked/paired with Trello #51 (server must decide payment sufficiency). | backlog §1, vision §8 |
| ✅ | **Configurable step goal + guardrails + i18n** | See P3-4 above (#89/#91/#86) | backlog 3.10, tasklist P3-4 |
| ✅ | **Multi-ring dashboard across tracking modes** | Unified rings + Fitbit/HC distance & calories (#82/#83) | tasklist context |
| ✅ | **Post editor height/clipping + measurements chevron** | #93 | recent fix |
| 💡 | **"Win in 60 seconds" playable onboarding** | Pick coach+companion, instant first steps + first AFIT drop + first badge, 3-tap first quest, HC backfill | vision §8 |

---

## 3. Prioritization (not-yet-shipped only)

Reconciled from `feature-backlog.md` cycles, `tasklist.md` priorities, and
`suggested_features.md` RICE scores. Shipped items are omitted.

**Cycle 1 — native ports of live web features (highest ROI, low backend risk):**
1. **Friends system** (full surface) — reuse existing friend-request API; autocomplete already in.
2. **Badges** + **Body-Metrics charts** (Trello #45) — quick native surfaces on ready endpoints.
3. **Home-screen widget** — cheap daily-retention win.
4. Finish/verify **Health-metrics chips** (P3-2) and **Signup wizard** rework (🚧 #85).

**Cycle 2 — parity + app-lead differentiators:**
5. **AFIT Rewards Store** + **Referrals** — monetization/growth, endpoints ready.
6. **Notification preferences**, **Dark mode / Material 3 theming**.
7. **Post scheduling / draft manager**, **Friends-only feed**, **GPS route polish**.
8. **AI Morning Briefing** (RICE 18.0), **Visual Progress / Flex cards** (RICE 24.0 — highest single ROI in suggested doc).

**Cycle 3 — bigger bets (need backend build):**
9. **Challenges / Duels / Teams — "The Arena"** 🧱 (marquee; no wagering) — start backend during Cycle 2.
10. **Multi-activity types** 🧱, **Leagues/Rivals**, **Daily spin + streak freeze** (cheap, high retention — consider pulling earlier).
11. **Water/Sleep**, **Wear OS**, moonshots (**Treasure Walk**, on-chain badges).

**Best-ROID quick wins to consider front-loading** (cheap + high hook, from vision toolbox):
milestone juice (✅ done), **daily spin**, **streak freeze/repair**, **shareable card**.

---

## 4. Backend dependency summary

Which *pending* features need `actifit-bot` / API work vs. are client-only.

| Ready to build now (endpoints live) | Needs new/changed backend (🧱) |
|---|---|
| Friends (`/userFriends`, `/addFriend`, `/acceptFriend`, `/cancelFriendRequest`, `/dropFriendship`, `/userFriendRequests`) | Challenges / duels / teams — no API (internal `team` collections only) |
| Badges (`/userBadges`, `/allUserBadges`, `/claimBadge`) | Friends **activity** leaderboard — no activity-ranking endpoint |
| Rewards store (`/products`, `/purchaseRealProduct`, gadget buy/activate, `/buyAFITHive`) | Non-step activity reward scoring (multi-activity types) |
| Referrals (`/referrals`, `/signups`, `/activeRefReward`, free-signup links) | Server-side post scheduling; server-side water/sleep persistence |
| Notifications + prefs (`/notificationTypes`, `/activeNotifications`, `/markRead`, `/userSettings`, `/registerUserNotification`) | Signup payment-sufficiency decision (**Trello #51** — actifit-bot) |
| Body-metrics (`/trackedMeasurements/:user`) | Leagues / rivals / ghost / city boards (new ranking + weekly cohort logic) |
| Estimated/pending rewards (`/getEstimatedReward`, `/pendingRewards`, `/getPostReward`) | On-chain collectible badges; Treasure Walk geo-drops |
| Wallet/tokens (`/user/:user?fullBalance`, `/afitxData`, swaps, prices, BSC bridge) | — |
| Workouts CRUD (`/saveworkout`, `/workouts`, `/workouts/:id`) | — |
| **Client-only (no backend):** step goal (done), widget, Wear OS, adaptive reminders, GPS route polish, dark mode, AI composer/insight (Gemini direct), local drafts, shareable cards, dashboard reorder | — |

> Note: the **activity-posting** endpoint (`p0stact1f1t` / `p0stact1f1t_Js0n`) lives on a
> separate service (`actifit-pst-cr3at0r.herokuapp.com`), not `actifitbot`. `actifitbot`
> (`api2.actifit.io`) serves the read side (`verified_posts`) and everything above.

---

## 5. Open Trello items

Two cards currently open on the Actifit Android board:

- **#45 — Body-metrics trend charts (weight + measurements).** Native trend charts reusing
  `ChartManager` on `/trackedMeasurements/:user`. See inventory §A. Endpoint ready; pure
  client build. Status: 📋 Planned.
- **#51 — Signup: server must decide payment sufficiency (actifit-bot).** Backend gate the
  signup wizard (🚧 #85) depends on — the server, not the client, must decide whether a
  free/paid signup is sufficient. Status: 📋 Planned (backend). Blocks final signup-wizard sign-off.

---

## Appendix — reconcile notes (what changed vs. the source docs)

- **Shipped since the mid-July docs:** Native profile (`ProfileActivity`) + living companion
  aura; AI assistant chat/composer (#73, enhanced #87/#90) + dashboard AI insight;
  configurable daily step goal with guardrails + i18n (#86/#89/#91); gadget-beneficiary
  friend autocomplete (#88/#92); post editor height + measurements-chevron fix (#93);
  multi-ring dashboard + Fitbit/HC distance & calories (#82/#83); all `tasklist.md` P1/P2 +
  P3-3, and **P3-4 (configurable step goal) which was still "pending" in the source**.
- **🚧 In progress:** Signup wizard — implemented (#81), reverted, reopened for rework (#85);
  gated by Trello #51.
- **❓ Verify flags:** (1) `tasklist` **P3-2 health-metrics chips** — distance/calories rings
  landed via #82/#83, but the dedicated **Resting HR / Active Minutes** chip row is not
  clearly confirmed. (2) **AFIT Earnings Estimator** (suggested Phase 3) heavily overlaps the
  shipped P1-5 estimated-reward card — confirm whether any additional real-time calculator
  work remains or it should be marked done.
- **Duplicates collapsed:** "Step Duels" (suggested Phase 4) = "Challenges/Duels" (backlog
  3.1) = "The Arena" (vision §6) → one entry. "Smart Post Drafts" (suggested Phase 5) =
  "AI post composer" (backlog §1) → shipped AI assistant. "Visual Progress Cards"
  (suggested Phase 1) overlaps the "Trading-card profile" + "Year/Week in Motion" ideas
  (vision §1) — kept as related entries under Gamification.
