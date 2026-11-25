# Actifit badge catalog (proposal, 2026-11-23)

The next set of claimable badges, turned from the July vision ideas (quests, mystery badges, evolving streaks, rarity tiers) into concrete badges. **Every badge here can be checked by actifit-bot from data it already stores**, so it can be claimed through the existing `/claimBadge` flow. Badges are cosmetic: they never pay AFIT and never change rewards.

## How to read this

**Tiers** (frame colour on the art): 🟤 Bronze → ⚪ Silver → 🟡 Gold → 🔷 Platinum → 💎 Diamond. A family with several tiers is one **evolving badge** that ranks up, so the profile shows the highest tier earned plus progress to the next.

**Data trust:**
- ✅ **Server data**: from the reward ledger or the bot's own records.
- 🟡 **Report data**: from the daily report (steps, activity types). That's the same data rewards already use.
- 🟠 **Self-reported**: from report fields marked "informational only" (workouts, GPS check, 15-minute step slots). Fine for a cosmetic badge, never for AFIT.

**🔮 Mystery**: hidden on the badge wall until earned (shown as "?"), for the surprise effect.

## 1. Consistency

| Badge | Tiers | Rule | Data |
|---|---|---|---|
| **On Fire** (evolving streak) | 🟤 7 · ⚪ 30 · 🟡 100 · 🔷 200 · 💎 365 | That many consecutive days with a report (by the report's activity date) | 🟡 `verified_posts` (author+date index), computed at claim time |
| **Comeback Kid** 🔮 | 🟤 | A report after 30+ days without one, followed by 7 days in a row | 🟡 `verified_posts` |
| **Weekend Warrior** | 🟤 4 · ⚪ 12 · 🟡 26 weekends | Reports on both Saturday and Sunday of the same weekend | 🟡 `verified_posts` (activity date) |
| *Rewarded Activity 1–10* | *(exists)* | *More than 9 … 1079 rewarded reports* | *✅ ledger* |

## 2. Distance and volume

| Badge | Tiers | Rule | Data |
|---|---|---|---|
| **Million Stepper** (evolving) | 🟤 1M · ⚪ 5M · 🟡 10M · 🔷 25M · 💎 50M | Lifetime steps across all reports | 🟡 sum of `step_count` |
| **Big Day** | 🟤 20K · ⚪ 30K · 🟡 50K | One report with at least that many steps | 🟡 `step_count` |
| **Trailblazer** (evolving) | 🟤 10 · ⚪ 50 · 🟡 200 | GPS-verified routes in reports | 🟠 `gps_verified` (new in v0.15.3) |

## 3. Time of day 🔮

| Badge | Tier | Rule | Data |
|---|---|---|---|
| **Early Bird** 🔮 | ⚪ | 2,000+ steps before 07:00 local, on 10 different days | 🟠 15-min slots + time zone (no slots from Fitbit) |
| **Night Owl** 🔮 | ⚪ | 2,000+ steps after 21:00 local, on 10 different days | 🟠 same |
| **Sunrise Seeker** 🔮 | 🟡 | Early Bird days in 3 consecutive weeks | 🟠 same |

## 4. Variety and new features

| Badge | Tiers | Rule | Data |
|---|---|---|---|
| **Explorer** (evolving) | 🟤 5 · ⚪ 10 · 🟡 20 | Different activity types used across reports | 🟡 `activity_type` |
| **All-Rounder** | ⚪ | 4 different activity types within one week | 🟡 `activity_type` |
| **Workout Logger** (evolving) | 🟤 10 · ⚪ 50 · 🟡 150 | Reports with Health Connect workouts | 🟠 `workouts` (new in v0.16) |
| **Director** (evolving) | 🟤 1 · ⚪ 10 · 🟡 50 | Reports with a 3Speak video | ✅ `json_metadata.video` |
| **Body Tracker** (evolving) | 🟤 5 · ⚪ 25 · 🟡 100 | Reports with body measurements logged (only *that* they were logged, never the values) | 🟡 measurement fields |

## 5. Community

| Badge | Tiers | Rule | Data |
|---|---|---|---|
| **Squad** | 🟤 5 · ⚪ 15 · 🟡 50 | Current friends | ✅ `friends` (current count only) |
| **Recruiter** (evolving) | 🟤 1 · ⚪ 5 · 🟡 25 | Referred signups that created an account | ✅ `signup_transactions` |
| **Curator** (evolving) | 🟤 50 · ⚪ 500 · 🟡 2,500 | Actifit reports you voted on | ✅ ledger `Post Vote` |
| **Contender** (evolving) | 🟤 1 · ⚪ 10 · 🟡 50 | Arena challenges joined | ✅ `challenge_participants` |
| **Podium** | ⚪ 1 · 🟡 5 · 💎 20 | Top 3 finishes in settled **public** Arena challenges with **5+ finishers** | ✅ Arena results |
| **Champion** | 🟡 1 · 💎 10 | First place in a settled **public** Arena challenge with **5+ finishers** | ✅ Arena results |

## 6. Supporters

| Badge | Tiers | Rule | Data |
|---|---|---|---|
| **Big Heart** (evolving) | 🟤 1 · ⚪ 10 · 🟡 50 | Charity reports | 🟡 report `charity` field |
| **Backer** (evolving) | 🟤 30 · ⚪ 180 · 🟡 365 days | Days delegating to Actifit | ✅ ledger `Delegation` rows |
| **Gadgeteer** (evolving) | 🟤 5 · ⚪ 25 · 🟡 100 | Gadgets used up in reports | ✅ `user_gadgets` (consumed) |

## 7. Limited editions (seasonal quests)

One limited badge per season, claimable only during its window and never again, so collectors have a reason to come back. Each needs only a date window on an existing rule.

| Badge | Window | Rule |
|---|---|---|
| **Endorphin Rush** | Nov 21 – Dec 31, 2026 | Reports on 20 different days, counted by when the server received them (no backdating) |
| **New Year, New Stride** | Jan 1 – Jan 31, 2027 | 25 reports in January |
| **Spring Bloom / Summer Solstice / …** | One per season | 60 reports in the season |

## Recommended first wave (8 badges)

Chosen for the most impact with the least new server work: all use indexed data, and none need the self-reported fields.

1. **On Fire** (streak): the classic retention hook.
2. **Million Stepper**
3. **Big Day**
4. **Director**: rewards the new video reports.
5. **Explorer**
6. **Contender / Podium / Champion**: the Arena data is already there.
7. **Big Heart**: an evolving version of Charity Donor.
8. **Endorphin Rush**: limited edition to launch the new set. It's the tease from the announcement, and only claimable until Dec 31.

Second wave: the mystery badges (Early Bird, Night Owl, Sunrise Seeker, Comeback Kid), Workout Logger, Trailblazer, Weekend Warrior, Recruiter, Curator, Backer, Gadgeteer.

## Server status (actifit-bot)

The first wave is built in [actifit-bot #187](https://github.com/actifit/actifit-bot/pull/187):

- **`badges.js`:** the catalog and rules. Stats come from one scan of the user's own reports (`verified_posts {author, date}` index) plus their Arena entries, cached for 10 minutes. No ledger scans and no daily job.
- **`GET /badgeCatalog`:** names, tiers, targets, art paths. **`GET /badgeProgress/:user`:** progress and the next claimable level per family.
- **`/claimBadge`:**
  - New badges are checked on the server, and tiers must go in order.
  - A badge already held is never stored twice (fixes the old duplicate gap), backed by a unique `{user, badge}` index.
- **Anti-farming:** one report per day counts, steps per day are capped at 100k, Endorphin Rush counts by server receive time, and Podium/Champion count only public challenges with 5+ finishers.
- **Still open:** claim order for the old Rewarded Activity levels (check how the website claims them first), the badge art, and the app and website screens.

## Rules of the house

- **No gambling:** no badge depends on luck, spins or paid entries. Lucky Doubled Up stays as it is, but no new luck-based badges.
- **Cosmetic only:** badges never pay AFIT and never change rewards.
- **Privacy:** no location badges (we don't store location, and won't), and no badges based on body measurement values.
- **Manual steps:** badge rules use the report's step count, which already excludes steps typed into Health Connect by hand.

## Art direction

- One consistent family: a round medal with the tier-coloured frame, a single bold icon in the middle, and the Actifit red as the accent.
- Evolving badges keep the same icon and gain a frame and a glow per tier.
- Mystery badges show a "?" silhouette until earned.
- Limited editions get a ribbon with the season name.
- The existing badge art lives at actifit.io `/img/badges/…`, so new art should match its size and style.
