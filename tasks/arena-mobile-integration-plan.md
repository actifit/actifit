# The Arena on mobile — integration plan (Android first, iOS to follow)

**Status (2026-10-17):** phases 1–3 are merged to Android `develop` (PRs #103, #104, #106) and not yet
released; the backend for phase 3 is live (actifit-bot #131/#132, deployed 2026-10-15). Phase 4 (create
wizard) is deferred. iOS: phases 1–2 and Friends done in the iOS session, phase 3 handed over.
**Tracks:** Trello "Mobile 1 - Android" card #56 (The Arena), roadmap item 9 in `feature-roadmap.md`.
**Readers:** whoever builds the Android screens, and the iOS session that ports them. Sections 1–4
are platform-neutral; section 5 is Android-specific; section 6 is the iOS handover.

---

## 1. What the Arena is

The Arena is Actifit's challenge system: time-boxed fitness contests that anyone can join for free,
scored from the activity reports users already post, and paid out in off-chain AFIT and/or named
badges. It went live on the web in September 2026 (`actifit.io/arena`). The mobile apps have no
Arena surface at all today.

It is one backend (`actifit-bot`) with thin clients. The web client is three pages:

| Web page | What it does |
|---|---|
| `/arena` | Discovery: cards for every public challenge |
| `/arena/:id` | Detail: hero art, facts, how it works, prizes, standings table, Join/Leave |
| `/arena/create` | Three-step wizard to create a user challenge (badge reward, or a self-funded AFIT prize) |

Plus a profile tab listing the badges a user has earned.

Three properties shape every client:

1. **Chain-first writes.** Join, leave and create are `custom_json` operations on Hive with
   `id: "actifit_arena"`, signed with the user's posting authority. The backend tails the chain and
   indexes them. There is no "join" HTTP endpoint that changes state.
2. **Scores come from verified reports, not from the device.** The backend recomputes every
   participant's score from the `verified_posts` feed every 15 minutes. A user's steps count only
   once they have posted their daily report and it has been verified. The client never submits a score.
3. **No wagering, ever.** Entry is free; there is no fee, stake or pot funded by participants. This
   is a hard product rule. Client copy must never use "bet", "wager", "stake", "odds", "jackpot" or
   "entry fee". Reuse the web strings (section 4.6) instead of writing new prize copy.

## 2. Live state (checked against production on 2026-10-13)

- All three API bases the Android app rotates between serve the Arena routes identically:
  `api.actifit.io`, `api2.actifit.io`, `actifitbot.herokuapp.com`.
- 35 public challenges exist: 6 open, 29 settled. All are official (`created_by: actifit`); no
  user-created challenge exists yet.
- The six open ones are the recurring official set: Daily Focus Goal, Weekly Step League, Weekly
  Global Top-N, Weekend Warrior, Season Ladder (two weeks), Monthly Live-Ops Event.
- Recurring challenges roll forward under a new id (`def_daily_focus@2026-10-13`) that points to the
  original through `parent_id`. Existing participants are carried into the new instance, so a user
  joins a recurring challenge once.
- Participation is small: 4 to 6 participants per challenge. Mobile is where the posting users are,
  so the app is the main lever for participation.
- Settled official leagues do pay: the first Weekly Step League paid 100 / 60 / 40 AFIT to ranks 1–3.
- Responses took 4–8 s from the dev machine, but an unrelated endpoint (`userFriends`) took 3.7 s on
  the same connection, so this is not evidence of an Arena-specific problem. Design for a slow
  network anyway: skeletons, cached last result, no blocking spinners.

## 3. Backend contract

Base URL: the app's normal API base. All reads are public, unauthenticated and rate-limited.

### 3.1 Reads

| Call | Returns | Notes |
|---|---|---|
| `GET arena/challenges?state=&type=&origin_tier=&entity=` | Array of challenge docs | Public challenges only. Each filter takes one value. `entity=<username>` limits to challenges that user is in. No paging, no sort. |
| `GET arena/challenges/:id` | `{ challenge, participants[] }` | 404 if unknown. URL-encode the id (rolled ids contain `@`). |
| `GET arena/standings?id=:id` | One standings doc, or an empty body if no board exists yet | An empty body is a normal state, not an error. |
| `GET arena/badges/:user?limit=` | `{ user, count, badges[] }` | `count` is the total; `badges` is a newest-first page. Badges from non-public challenges are excluded. |
| `GET arena/events/:user?limit=` | Array of events, newest first | Only `results_settled` is emitted today (section 7.2). |
| `GET arena/pools/:id` | Pool status for a funded challenge | Only needed for creator-funded challenges. |
| `POST arena/ops/validate` body `{ op, username }` | `{ ok, errors[], op }` | Advisory pre-check of a proposed op. Never changes state. |

Do not use `arena/merits/:user`. Merits were retired in favour of AFIT; the route still exists but
nothing writes to it. The user's AFIT balance comes from the existing user-tokens endpoint.

**Challenge doc — fields a client renders**

```
id, parent_id, title, type, state, origin_tier, visibility, created_by
window { start, end, tz }            ISO timestamps, UTC
entry { mode: "free" | "activity_gated", gate { min_activity } }
scoring { metric, rule, threshold? }
rewards { badges[]? , afit? , summary? }   null on official challenges
badge_rule: "winner" | "top3" | "all"      who earns the badge
tagline, how_it_works, prize_summary, recurrence, art
```

- `state`: `open` and `active` are joinable; `settled` is finished. Treat any other value as not joinable.
- `type` seen or creatable: `league_fixture`, `daily_focus`, `liveops`, `duel`, `brawl`.
- `scoring.metric`: `activity_count`, `goal_hit`, `steps`, `distance`.
- `scoring.threshold` must be read from the doc, never hard-coded. The first Daily Focus instance
  carried 10000; today's carries 5000.

**Participant** (in the detail response)

```
entity, entity_kind, state ("enrolled" | "left"), joined_at
score { verified, best_day, days, metric, as_of }
result { rank, score_verified, reward { afit, badges[] } }   null until settled
```

**Standings doc**

```
id, window, computed_at
rows[] { entity, rank, score, points, movement }
```

Scores for `activity_count` are large step-like totals (the current monthly leader is at 866,219).
Show the number as given, with thousands separators, under the label "total activity".

### 3.2 Writes

Every write is one Hive `custom_json`:

```
required_auths: []
required_posting_auths: [ <username> ]
id: "actifit_arena"
json: <stringified op body>
```

| Action | Op body |
|---|---|
| Join | `{ "op": "join", "v": 1, "challenge_id": "<id>" }` |
| Leave | `{ "op": "leave", "v": 1, "challenge_id": "<id>" }` |
| Create | `{ "op": "challenge_create", "v": 1, "id": "ch_<user>_<base36 time><4 random>", ... }` (section 5, phase 4) |

After the broadcast succeeds the client is finished. There is no second "index" call (unlike the
friends flow). The backend tailer polls the chain every 3 seconds and applies the op.

Two consequences every client must handle:

- **Pending state.** Between broadcast and indexing the server still reports the old membership.
  The client must show the new state optimistically and persist that locally, so it survives leaving
  and re-entering the screen.
- **Silent rejection.** If the backend rejects the op at ingest (entry gate not met, challenge
  already closed), nothing comes back. The pending state simply never confirms. Expire a pending
  join that the server has not confirmed after a few minutes (Android uses five) and tell the user.
  `arena/ops/validate` does not help here: it checks only the op's shape and the creator's tier,
  not the entry gate or whether the challenge is still open. It is useful for the create wizard.

### 3.3 Art

`art` is a key (`step-league`, `daily-focus`, `season-ladder`, `global-top`, `weekend-warrior`,
`monthly-event`). The image is `https://actifit.io/img/arena/<art>.webp` (about 23 KB each).
If `art` is missing, fall back by type: `league_fixture` → `step-league`, `daily_focus` →
`daily-focus`, `liveops` → `monthly-event`, anything else → `step-league`.

### 3.4 Copy fallback

Official challenges carry their own `tagline`, `how_it_works` and `prize_summary`. A user-created
challenge may omit them. The web keeps a small per-type fallback table in `utils/arenaCatalog.js`
(actifit-landingpage); port that table as-is rather than inventing wording. Resolution order:
field on the challenge → entry for `parent_id` → entry for `type` → generic default.

## 4. Behaviour both mobile apps should share

### 4.1 Screens

1. **Arena home.** Three groups: *Your challenges* (joined and still running), *Open now*, *Results*
   (settled). Each card: art, title, recurrence chip, time left, participant count if known, and a
   "Joined" mark.
2. **Challenge detail.** Hero art with type / recurrence / state chips, title, tagline; facts
   (length, starts, ends, scored by); how it works; prizes and offered badges; standings; a sticky
   Join / Leave button.
3. **Results view** (a settled challenge): final table, each finisher's earned badges, and the
   viewer's own outcome ("You finished 2nd · 60 AFIT").
4. **Profile badges.** A section on the profile listing earned badges, with an "unofficial /
   Community" marker on any badge whose `origin_tier` is not `official`. A badge name is free text
   chosen by a challenge creator, so the marker is what stops it passing as an official award.

### 4.2 What mobile should add beyond the web

The web is a browsing surface. The phone is where the steps are, so the app can close the loop:

- **Your row.** Pin the viewer's own standings row above the table with the gap to the next rank
  ("2,100 behind #2").
- **Today's contribution.** On the detail screen, show today's device step count next to the
  verified score, with the line "Post today's report to make these count". This is the single most
  important thing to explain, because unposted steps score nothing.
- **Post-report hook.** After a report is posted, show "This counts toward N challenges" with a
  link to the Arena; if the user is in none, offer the open ones.
- **Daily Focus progress.** For `goal_hit` challenges, a progress ring of today's steps against
  `scoring.threshold`.
- **Countdown.** "Ends in 5h 12m" on cards and detail, switching to an urgent style in the last day.

### 4.3 Join rules

- Logged out → send to login, then return to the same challenge.
- Joinable only when `state` is `open` or `active`.
- Joined means: a participant row for the user whose `state` is not `left`, or a local pending join.
- Leave is offered only while the challenge is joinable. After it closes, show "Joined" with no action.
- Disable the button while a broadcast is in flight; never send the same op twice.

### 4.4 Freshness

Standings change every 15 minutes at most. Show "Updated N min ago" from `computed_at`, refresh on
pull, and do not poll in the background.

### 4.5 List growth

The unfiltered list grows by roughly ten docs a week (seven daily instances plus the weekly ones)
and has no paging. Always pass `state=open` for the main list (6 docs today). The *Results* group
needs `state=settled`, which is the part that grows; see the backend ask in section 7.1.

### 4.6 Strings and translations

The web already has about 100 `Arena_*` keys in `actifit-landingpage/lang/`, translated into 13 locales.
Take the wording and the translations from there. Locales Android has that the web also has: ar,
de, es, hi, it, pt, tr, uk, zh. Android-only locales that need new translation: ko, nl, yo (and
pt-BR can start from pt).

## 5. Android implementation

Follows the conventions already used for the Friends feature: Java, the single flat package,
Volley, `ListView` + `ArrayAdapter`, Glide, `BaseActivity` for the footer.

### 5.1 How Android signs

Use the same server-side signing the Friends feature uses: `GET performTrx` with the login access
token in the `x-acti-token` header (`FriendsApi.doFriendAction`, step 1). The endpoint accepts any
operation and does not restrict the `custom_json` id, so an `actifit_arena` op goes through
unchanged. On-device signing stays out of scope; it is still experimental in this app.

Success is a response containing `success` with `trx.tx.ref_block_num` set.

### 5.2 New classes

| Class | Role |
|---|---|
| `ArenaApi` | Reads (list, detail, standings, badges, events, validate) and `join` / `leave` / `create` through `performTrx`. Delivers callbacks on the UI thread and drops them if the activity is finishing, as `FriendsApi.finish` does. |
| `ArenaChallenge`, `ArenaParticipant`, `ArenaStandingRow`, `ArenaBadge` | Models parsed from JSON with `opt*` getters, so an unknown or missing field never crashes a screen. |
| `ArenaLogic` | Pure functions with no Android imports: catalog fallback, art key, duration and countdown text, joined-state resolution, pending-join expiry, gap-to-next-rank. This is the unit-tested part. |
| `ArenaPendingStore` | SharedPreferences record of optimistic joins and leaves, keyed by user and challenge id with a timestamp. |
| `ArenaActivity` | Arena home (extends `BaseActivity`). |
| `ArenaChallengeActivity` | Detail and results. Takes `EXTRA_CHALLENGE_ID`. |
| `ArenaCardAdapter`, `ArenaStandingsAdapter` | List adapters. |
| `ArenaCreateActivity` | Phase 4 only. |

### 5.3 Entry points

- **Footer (decided 2026-10-13):** the Arena takes the footer slot the Daily Leaderboard had
  (`btn_view_arena`, trophy icon). The leaderboard moves to a `more_item_leaderboard` row in the
  More menu, and `LeaderboardActivity` now highlights the More button.
- **Home dashboard card** on `MainActivity`: the user's most urgent joined challenge with rank and
  time left, or a "Join the Arena" prompt if they are in none. This is what will actually drive
  participation; a footer button alone will not.
- **Notifications and links:** phase 3.

### 5.4 Phases

Each phase is one PR to `develop`, reviewed before merge, tested on a device.

**Phase 1 — Browse and join.** ✅ Merged (#103). The web's `/arena` and `/arena/:id` at parity.
- `ArenaApi` reads, models, `ArenaLogic`, `ArenaPendingStore`.
- `ArenaActivity` with the three groups; `ArenaChallengeActivity` with hero, facts, copy, prizes,
  offered badges, standings, Join / Leave.
- Broadcast → optimistic state → confirm on next load → expire if unconfirmed.
- Footer entry. Remote art through Glide with a placeholder.
- JUnit tests for `ArenaLogic`.
- Done when: a logged-in user can join a live challenge from the phone, sees "Joined" immediately
  and still sees it after restarting the app, and the participant row appears on the web.

**Phase 2 — Make it a phone feature.** ✅ Merged (#104). Section 4.2.
- Home dashboard card; pinned "your row" with the gap; today's-steps line; Daily Focus ring;
  countdowns; post-report hook in `PostSteemitActivity`'s success path.
- Results view for settled challenges with the viewer's own outcome.
- Earned-badges section on `ProfileActivity` (own and other users), with the Community marker.
- Done when: a user who posts a report is told it counted, and can see where they stand without
  opening the detail screen.

**Phase 3 — Notifications and deep links.** ✅ Notifications merged (#106); App Links not done. Backend
work in section 7.2 is live. As built: tapping an Arena push or inbox entry opens the native challenge
screen for `https://actifit.io/arena/<id>` (`ArenaLogic.challengeIdFromUrl`); App Links for links opened
from other apps still need an `assetlinks.json` on actifit.io.
- Before the backend work lands: on app open, read `arena/events/:user` and surface unseen
  `results_settled` events as an in-app result card.
- After: handle Arena push types in `ActifitFirebaseMessagingService` and route them to
  `ArenaChallengeActivity`; add the same routing in `NotificationEntryAdapter` next to the existing
  friendship deep links.
- Android App Links for `actifit.io/arena` and `actifit.io/arena/*`, so a shared challenge link
  opens the native screen.
- A "Share challenge" action that shares the web URL.

**Phase 4 — Create a challenge.** ⏸ Deferred by the owner. The web wizard, natively.
- Three steps: details (title, type, metric, rule, window, entry) → reward → review.
- Start with the badge-only reward (`origin_tier: "friendly"`, `rewards.badges`, `badge_rule`),
  which any user may create.
- The self-funded AFIT prize (`origin_tier: "community"`, `rewards.afit`) is offered only when the
  user's AFIT balance is at least 20,000; the prize minimum is 50 AFIT and a 5% fee is burned on
  top. These limits are advisory in the client and enforced by the backend at ingest, so the screen
  must state the total that will be deducted before the user signs.
- Rules carried over from the web wizard: `head_to_head` only for `duel`; a same-day window is
  valid; the id never starts with `def_`.
- Until this ships, a "Create a challenge" row can open `actifit.io/arena/create` in a Custom Tab.

### 5.5 Rough size

Phase 1 is comparable to Friends phase 1 (two screens, one API class, adapters, layouts, strings).
Phase 2 is smaller in code but touches `MainActivity`, `PostSteemitActivity` and `ProfileActivity`.
Phase 3's app side is small; it waits on the backend. Phase 4 is the largest single screen.

## 6. iOS handover

Not inspected for this plan: the iOS codebase (`actifit-ios-repo`, `actifit-ios-priv`). Everything
in sections 1–4 applies unchanged. The iOS session needs to settle three things itself:

1. **Signing path.** Confirm how the iOS app broadcasts a posting-authority `custom_json` today (for
   example for friends or votes). If it uses the same `performTrx` endpoint with the access token,
   section 5.1 carries over directly.
2. **Entry point.** Where the Arena sits in the iOS navigation; the home card matters more than the
   menu position.
3. **Push.** APNs routing for the Arena types once the backend sends them.

Port `ArenaLogic` first. It has no platform dependencies and its JUnit cases double as the
specification for the Swift version.

## 7. Backend work this plan needs

None of it blocks phases 1 and 2.

### 7.1 List paging (small; wanted by phase 2)

`GET arena/challenges` returns every matching doc with no limit or order. Add `limit` and a
newest-first sort (by `window.end`), and allow `state=open,active` in one call. Without it the
*Results* group downloads an ever-growing array.

### 7.2 Push notifications (needed by phase 3) — ✅ DONE (actifit-bot #131/#132, live 2026-10-15)

As built: `arena_results` (rewarded finishers, once, at settlement) and `arena_closing` (once per user and
challenge: 2+ day challenges, last 12h, not posted today), both via `utils.sendNotification` under the
`arena` category; `/notificationTypes/` lists it so settings show an Arena switch. Daily and squad
challenges are excluded, and only official public challenges are named in the text. `challenge_opening`
was not built. The original ask follows.

Today the engine writes only `results_settled` to the `arena_events` collection, and nothing sends
a push or writes to the notifications inbox the apps already show. Needed:

- Send FCM (and the inbox entry) on `results_settled`, carrying the challenge id.
- Emit and send `window_closing` (a joined challenge ends within N hours and the user has not
  posted today) and `challenge_opening` for new official challenges.

### 7.3 Nice to have

- A participant count on the list response, so cards do not need one detail call each.
- The viewer's own membership on the list (`entity=` already gives this in a second call).

## 8. Risks

| Risk | Mitigation |
|---|---|
| Users expect live step counts to move their rank | The "post today's report" line and the "Updated N min ago" stamp, from phase 1 |
| A join is rejected silently at ingest | Expire unconfirmed pending joins after five minutes with a message |
| Unknown challenge types or fields appear later (squads, seasons) | Tolerant parsing; unknown type renders with the generic fallback; unknown state is not joinable |
| Prize wording drifts toward gambling language | Strings come from the web set only; add a unit test that scans Arena strings for the forbidden words, as the web repo does |
| Slow API on mobile networks | Cache the last list and detail response; skeleton rows; pull to refresh |

## 9. Decisions (owner, 2026-10-13)

1. **Entry point.** The Arena replaces the Leaderboard button in the footer; the leaderboard moves
   under More. A home dashboard card is also wanted (phase 2).
2. **Scope of the first release.** Phases 1 and 2 ship together as one release.
3. **Create wizard.** Deferred to a later phase.
4. **Backend items in section 7.** 7.2 done (2026-10-15). 7.1 (list paging) and 7.3 still open; the app caps Results at 30.
