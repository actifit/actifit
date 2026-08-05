# Actifit Android — Feature Backlog & Suggestions

A prioritized, grounded catalog of proposed features for the Actifit Android app,
**reconciled against the web app (`actifit.io` / `actifit-landingpage`) and the backend
API (`actifitbot`, base `https://api2.actifit.io/`).**

The key finding: most features I'd have called "new" **already exist on the web and are
backed by live endpoints**. For those, the Android work is a *native port that reuses an
existing API* — high value, low backend risk — not greenfield. A smaller set is genuinely
net-new (no web feature, often no endpoint) where the app would lead.

**Related docs (do not duplicate):**
- [`tasklist.md`](tasklist.md) — dashboard UX enhancements (mostly ✅ done).
- [`suggested_features.md`](suggested_features.md) — earlier growth roadmap (Visual Progress Cards, AI Morning Briefing, Earnings Estimator, Step Duels, Smart Post Drafts).

**Status legend:** 🔨 In progress · 🌐 Exists on web · 🔌 Backend endpoint ready · 🧱 Needs new backend · 🆕 Net-new (app leads)

---

## 0. Two important corrections to my earlier draft

1. **"Friends" is NOT Hive follow.** Actifit has its own **mutual friend-request system**
   with dedicated endpoints (`/userFriends/:user`, `/addFriend…`, `/acceptFriend…`,
   `/dropFriendship…`, `/userFriendRequests/:user`) and a full web UI
   (`pages/_username/friends.vue`, `FriendshipModal.vue`). Android should integrate **that**
   system, not broadcast a Hive `follow` op. (Hive-native follower/following *counts* are
   shown separately on web — a nice-to-have, secondary.)
2. **Native profile needs no new backend.** Every stat is already queryable for *any* user:
   `/user/:user`, `/getRank/:user`, `/trackedActivity/:user`, `/trackedMeasurements/:user`,
   `/userBadges/:user`, `/userFriends/:user`. So the in-app profile is a pure client build.

---

## 1. Currently Under Development (for context)

| Feature | Notes | Web / API |
| :--- | :--- | :--- |
| 🔨 **Signup wizard** | Native multi-step onboarding to replace the "Create account" hyperlink in `LoginActivity`. | 🌐 Web has a 3-step wizard (`pages/signup.vue`) 🔌 account-creation + `/signupInfo`, free-signup-link endpoints exist. Mirror the web flow. |
| 🔨 **AI post composer** | Add `AiService` (Gemma-3-27b) drafting to `PostSteemitActivity`. | 🆕 Web AI is translation-only — **Android would lead here.** No backend needed (Gemini direct). |

---

## 2. Port-to-Native (web parity, backend already live) — **best ROI**

These are shipped on web and fully served by `api2.actifit.io`. Android just lacks the
native screen. High impact, low risk.

### 2.1 🌐🔌 Native In-App Profile
**Now:** every avatar tap → `UiHelper.openUserAccount()` → Chrome CustomTab to `actifit.io/<user>`.
**Proposal:** native `ProfileActivity` mirroring the web tabs (About · Fitness · Community · Wallet · Badges).
- **Endpoints:** `/user/:user?fullBalance=1`, `/getRank/:user`, `/trackedActivity/:user`, `/trackedMeasurements/:user`, `/userBadges/:user`, `/userFriends/:user`.
- **Reuse:** `PostAdapter`/`SingleHivePostModel` (posts), `ChartManager` (measurement trends), wallet send dialogs, `ApiManager`.
- Replace the ~5 `UiHelper.openUserAccount()` call sites; keep "Open on actifit.io" as an overflow fallback.
- **Impact:** High · **Effort:** Medium · **Backend:** none needed.

### 2.2 🌐🔌 Friends (mutual friend-request system)
**Now:** nonexistent in-app (only a `refer_friend` share button).
**Proposal:** native friends surface matching web `friends.vue`:
- Friends list, pending sent/received requests, accept / cancel / unfriend, **suggested friends**.
- Add/accept/drop actions from the profile (2.1) and feed.
- **Endpoints:** `/userFriends/:user`, `/userFriendRequests/:user`, `/addFriend…` (+ `addFriendHiveKeychain/:userA/:userB/:blockNo/:trxID/:bchain`), `/acceptFriend…`, `/cancelFriendRequest…`, `/dropFriendship…`.
- **Impact:** Very High · **Effort:** Medium · **Backend:** ready.

### 2.3 🌐🔌 Badges / Achievements (existing catalog)
**Now:** only a 7-day streak strip in `MainActivity`.
**Proposal:** surface the **existing** badge system natively (don't invent a new catalog):
`iso`, `rewarded_activity_lev_N`, `doubledup`, `charity`, with claimable/claimed/missed states.
- **Endpoints:** `/userBadges/:user`, `/allUserBadges/`, `/claimBadge/`; status flags `/isoParticipant/:user`, `/charityDonor/:user`, `/luckyWinner/:user`.
- Show on the profile Badges tab; fire the milestone celebration animation (`tasklist.md` P2-4) on unlock.
- **Impact:** Medium-High · **Effort:** Low-Medium · **Backend:** ready.

### 2.4 🌐🔌 AFIT Rewards Store / Redemption (native)
**Now:** dashboard shows "gadgets" + a `MarketActivity` for token trading, but no native product/redemption store.
**Proposal:** native store mirroring web `market.vue`: spend AFIT on gadgets (boosters), consultations, ebooks, physical products; prize-ticket cycle.
- **Endpoints:** `/products`, `/mintProducts`, `/purchaseRealProduct/`, `/confirmPayment`, gadget family (`buyGadget(Keychain)`, `activateGadget`, `activeGadgetsByUser/:user`, …), `/buyAFITHive/…`, `/downEbook`, ticket endpoints (`/userActiveGadgetBuyTickets/:user`).
- Also cleanly satisfies `tasklist.md` P2-3 (gadgets empty-state CTA) and a "gadget/boost hub".
- **Impact:** High · **Effort:** Medium-High · **Backend:** ready.

### 2.5 🌐🔌 Tracked Referral Program (native)
**Now:** `refer_friend` button is a plain share.
**Proposal:** native referral screen: personal referral link/code, referred-accounts list, reward status, free-signup-account claims.
- **Endpoints:** `/referrals/:user`, `/signups/:user`, `/signupInfo/:user`, `/activeRefReward/:referred`, `/myFreeSignupLinks/`, `/claimableFreeAccounts/:user`, `/claimFreeSignupAccounts/:user`.
- **Impact:** Medium · **Effort:** Medium · **Backend:** ready.

### 2.6 🌐🔌 Notification Preferences (per-type opt-in)
**Now:** FCM push works (`ActifitFirebaseMessagingService`), `NotificationsActivity` lists Hive notifs, `ReminderNotificationService` for local reminders — but **no per-type preference UI**.
**Proposal:** per-category notification toggles (friend request/accept, upvote, reward, etc.) mirroring web `settings.vue`.
- **Endpoints:** `/notificationTypes/`, prefs stored via `/userSettings` (`GET /userSettings/:user`, `updateSettings`), `/activeNotifications/:user`, `/markRead/:notif_id`, `/markAllRead/`; FCM token already via `POST /registerUserNotification`.
- **Impact:** Medium · **Effort:** Low-Medium · **Backend:** ready.

### 2.7 🌐🔌 Body-Metrics Trend Charts
**Now:** the post composer collects weight/chest/waist/thighs/bodyfat per report but never charts them in-app.
**Proposal:** native trend charts (weight + measurements over time), matching web `MeasureChartModal`.
- **Endpoint:** `/trackedMeasurements/:user`. **Reuse:** `ChartManager`.
- **Impact:** Medium · **Effort:** Low-Medium · **Backend:** ready.

### 2.8 🌐 Dark Mode / Theming
Web has a persisted dark-mode toggle (`UserMenu.vue`, Vuex). Parity feature; also saves OLED battery given the all-day foreground service.
- **Impact:** Medium · **Effort:** Medium · **Backend:** none.

---

## 3. App-Leads / Net-New (little or no web equivalent)

Where the mobile app can genuinely lead — but note the backend dependency.

### 3.1 🆕🧱 Challenges / Duels / Teams
**No web feature and no API** (only internal `team`/`team_transactions` collections; `CompetitionAnnounce.vue` is a static banner). Step Duels in [`suggested_features.md`](suggested_features.md) is therefore **fully greenfield including backend**.
- 1v1 duels → group/team challenges; ties to Friends (2.2) and AFIT wagering/escrow.
- **Impact:** Very High · **Effort:** High · **Backend:** 🧱 must be built (duel/challenge lifecycle, escrow, resolution) — scope early.

### 3.2 🆕 Friends-only Feed & Leaderboard filters
Not on web. Feed filter is cheap once Friends exists (fetch `/userFriends/:user`, filter client-side). A friends **activity** leaderboard is trickier — **no generic activity-leaderboard endpoint exists** in `actifitbot` (only token-holder/delegator rankings via `/topAFITHolders`, `/topDelegators`, and per-user `/getRank/:user`), so ranking friends by daily steps likely needs a small new endpoint.
- **Impact:** High · **Effort:** Low (feed) / Medium 🧱 (leaderboard) · **Backend:** feed none; leaderboard likely new.

### 3.3 🆕 GPS Route Polish + Shareable Maps
Android already has `RouteRecordingService` + `RouteMapActivity`; **web is only a viewer with no maps** — a clear mobile advantage. Add auto-pause, splits, elevation, and a shareable route snapshot image.
- Note: routes live inside post/workout JSON (no dedicated routes endpoint), so persistence stays local/embedded.
- **Impact:** Medium · **Effort:** Medium · **Backend:** none.

### 3.4 🆕 Multi-Activity Types from Health Connect (cycling, swimming, workouts)
Neither app tracks these natively (web only *displays* `activity_type` tags). Read HC `ExerciseSessionRecord`/distance/calories so non-walkers earn too.
- `verified_posts` already stores `activity_type`; reward eligibility/scoring for non-step activity may need backend rules.
- **Impact:** High · **Effort:** Medium-High · **Backend:** 🧱 partial (scoring rules).

### 3.5 🆕 Water & Sleep Quick-Log
Not on web or backend. Lightweight daily trackers (or read sleep from HC) to add touchpoints and enrich AI insight context.
- **Impact:** Low-Medium · **Effort:** Medium · **Backend:** 🧱 if persisted server-side (or local-only first).

### 3.6 🆕 Post Scheduling & Draft Manager
Not on web (no future-publish). Save drafts + schedule posting to hit the daily cadence. Extends `PostSteemitActivity`.
- **Impact:** Medium · **Effort:** Medium · **Backend:** 🧱 for server-side scheduling (local drafts first, no backend).

### 3.7 🆕 Home-screen / Lock-screen Widget
Client-side, mobile-only. Glanceable steps + goal ring + streak from `StepsDBHelper`; pushed by `ActivityMonitorService`. Strong daily-retention lever.
- **Impact:** High · **Effort:** Medium · **Backend:** none.

### 3.8 🆕 Wear OS Companion
Live step count, goal ring, start/stop GPS route on the watch. Mobile-only.
- **Impact:** Medium · **Effort:** High · **Backend:** none.

### 3.9 🆕 Adaptive Reminders
`ReminderNotificationService` fires at a fixed time; make it context-aware ("1,200 steps short with 2h of daylight left") using local data.
- **Impact:** Medium · **Effort:** Low-Medium · **Backend:** none.

### 3.10 🆕 Configurable Daily Step Goal (`tasklist.md` P3-4, pending)
Goal is hardcoded 10K. Let users set it; store in SharedPreferences (no goals endpoint exists server-side, so local is correct). Drives the arc ring + nudges.
- **Impact:** High · **Effort:** Low · **Backend:** none.

### 3.11 🆕 Health Metrics Chips (`tasklist.md` P3-2, pending)
HC-mode chips: Calories · Active Minutes · Resting HR below the step ring, via `HealthConnectManager`.
- **Impact:** Medium · **Effort:** Medium · **Backend:** none.

---

## 4. Suggested Prioritization

**Cycle 1 — ship native ports of live web features (highest ROI, low backend risk):**
1. 🔨 Signup wizard (in progress)
2. **Native In-App Profile (2.1)** — foundational; zero new backend
3. **Friends system (2.2)** — reuse the existing friend-request API
4. **Badges (2.3)** + **Body-metrics charts (2.7)** — quick native surfaces on ready endpoints
5. **Configurable step goal (3.10)** + **Home-screen widget (3.7)** — cheap retention wins

**Cycle 2 — parity + app-lead differentiators:**
6. **AFIT Rewards Store (2.4)** + **Referrals (2.5)** — monetization/growth, endpoints ready
7. **Notification preferences (2.6)**, **Dark mode (2.8)**
8. 🔨 AI post composer + **Post scheduling/drafts (3.6)** — app leads over web
9. **Friends-only feed (3.2)**, **GPS route polish (3.3)**, **HC metric chips (3.11)**

**Cycle 3 — bigger bets (need backend build):**
10. **Challenges/Duels/Teams (3.1)** 🧱 — highest engagement upside, most backend work
11. **Multi-activity types (3.4)** 🧱, **Water/Sleep (3.5)**, **Wear OS (3.8)**

---

## 5. Backend Dependency Summary

| Ready to build now (endpoints live) | Needs new/changed backend (🧱) |
| :--- | :--- |
| Profile (`/user/:user`, `/getRank/:user`, `/trackedActivity`, `/trackedMeasurements`, `/userBadges`) | Challenges / duels / teams (no API; internal collections only) |
| Friends (`/userFriends`, `/addFriend`, `/acceptFriend`, `/dropFriendship`, `/userFriendRequests`) | Friends **activity** leaderboard (no activity-leaderboard endpoint) |
| Badges (`/userBadges`, `/allUserBadges`, `/claimBadge`) | Non-step activity reward scoring (multi-activity types) |
| Rewards store (`/products`, `/purchaseRealProduct`, gadget buy/activate family, `/buyAFITHive`) | Server-side post scheduling; server-side water/sleep persistence |
| Referrals (`/referrals`, `/signups`, `/activeRefReward`, free-signup links) | — |
| Notifications + prefs (`/notificationTypes`, `/activeNotifications`, `/markRead`, `/userSettings`, `/registerUserNotification`) | — |
| Estimated/pending rewards (`/getEstimatedReward`, `/pendingRewards`, `/getPostReward`) — used by `tasklist.md` P1-5 | — |
| Wallet/tokens (`/user/:user?fullBalance`, `/afitxData`, swaps, prices, BSC bridge) | — |
| Workouts CRUD (`/saveworkout`, `/workouts`, `/workouts/:id`) — already used by `WorkoutWizardActivity` | — |
| Client-only: step goal, widget, Wear OS, adaptive reminders, GPS route polish, dark mode, AI composer (Gemini direct), local drafts | — |

> Note: the **activity-posting** endpoint (`p0stact1f1t` / `p0stact1f1t_Js0n`) lives on a
> separate service (`actifit-pst-cr3at0r.herokuapp.com`), not `actifitbot`. `actifitbot`
> (`api2.actifit.io`) serves the read side (`verified_posts`) and everything above.
</content>
