# Actifit Android — Product Vision: From Tracker to Movement

> Companion to [`feature-backlog.md`](feature-backlog.md). That doc maps features to
> existing web + API parity (pragmatic, low-risk). **This doc does the opposite:** it
> reimagines even the surfaces we already have — profile, badges, leaderboard, social,
> rewards — as bold, game-like, identity-driven experiences. The goal isn't parity. It's
> to make Actifit *exciting enough to talk about*, so existing users get hooked and new
> users show up.

> **House rule (non-negotiable):** no wagering / betting / games-of-chance-for-money. Competition
> is skill- and goal-based; challenge rewards come from **sponsor / DHF / Actifit-funded prize
> pools**, never from pooling participants' own AFIT and redistributing it. This constrains the
> Arena (§6) — see its reward model.

---

## The core shift

Actifit today is a **utility**: track steps → post a report → earn AFIT. Utilities get
used when needed and forgotten otherwise. The apps people open every day — Duolingo,
Strava, Pokémon GO, BeReal — aren't utilities, they're **identities and games**. Three
moves get us there:

1. **Identity** — your profile becomes a *living character* that visibly grows with you, not a stat sheet.
2. **Play** — earning AFIT becomes a *game with surprise, streaks, rivalry, and loot*, not a spreadsheet.
3. **Belonging** — movement becomes *social and alive* — you feel other people moving with you.

Every idea below is scored for the two things that actually drive growth:
**🔥 Excite** (does it create a wow / share moment?) and **🪝 Hook** (does it pull you back tomorrow?).

---

## 1. Profile → **"Living Fitness Identity"**

**Today (web + app):** a stat sheet — avatar, rank, balances, tabs. Static. Nobody screenshots it.

**Reimagined:** your profile is a **character that evolves visually with your real activity.**

- **The Aura / Companion.** Pick a companion at signup (a spirit animal, a plant, an orb — your call). It *visibly evolves* with your consistency: thriving on streaks, dimming when you go quiet, leveling up at milestones. This is the Tamagotchi hook — people return to keep their companion alive. 🔥🔥 🪝🪝🪝
- **Trading-card profile.** Render the profile as a collectible **card with a rarity tier** (Bronze → Mythic) derived from rank + streak + lifetime steps + badges. Rare cards *look* rare (holo, animated borders). Instantly shareable as an image. 🔥🔥🔥 🪝
- **Dynamic themes** unlocked by milestones — hitting 1M lifetime steps unlocks a "Marathon Gold" profile skin. Cosmetic progression = long-term goals people chase. 🪝🪝
- **"Year in Motion"** — a Spotify-Wrapped-style animated recap (weekly mini + annual epic): most active day, total distance "you walked from X to Y", top hour, streak record. Built to be shared. 🔥🔥🔥
- **Live pulse header** — an animated heartbeat/step-ring that reflects *today* in real time, so opening your own profile feels alive.

**Why it grows the base:** the card and Wrapped recap are *designed to leave the app* — every share is a free, credible ad from a real person.

---

## 2. Badges → **"Quests & Collectible Saga"**

**Today:** ~4 static claimable badges (iso, rewarded-activity, doubled-up, charity). Functional, forgettable.

**Reimagined:** a **living quest system** with surprise and collection psychology.

- **Weekly & seasonal quests** with a countdown ("Walk 3 sunrises this week", "10K on a weekend"). Rotating goals beat static ones — there's always a reason to come back *this week*. 🪝🪝🪝
- **Mystery / surprise badges.** Some unlock unexpectedly ("Night Owl — 5K after midnight", "Globetrotter — active in a new city"). Unpredictable rewards are the strongest retention mechanic known. 🔥🔥
- **Rarity + a showcase case.** Badges have tiers and animated/holo art; your profile has a **trophy case** you curate. "Gotta collect 'em all." 🪝🪝
- **Evolving badges** — the streak badge visually *ranks up* (7 → 30 → 100 → 365 days) instead of being one flat icon.
- **Collectible / on-chain edition (moonshot).** Limited seasonal badges minted on Hive/BSC — real scarcity, tradeable, a reason for collectors to grind. ⚠️ ambitious.

**Why it excites:** turns a claim screen into a **game with a collection meta** — the thing that keeps people in gacha and card games for years.

---

## 3. Leaderboard → **"Leagues, Rivals & Ghosts"**

**Today:** a global top-N daily list. Demotivating — a new user is #4,000 and never top, so they disengage.

**Reimagined:** make everyone feel *competitive at their own level*.

- **Leagues / divisions** (Bronze → Silver → Gold → Diamond, Duolingo-style). Each week the top of your league gets **promoted**, the bottom **relegated**. Everyone has a live race they can actually win. 🪝🪝🪝
- **"People near you"** — a rank slice around *you*, not just the untouchable top. You're always 200 steps from passing someone.
- **Rivals** — the app nominates a nemesis at your level each week; beat them for bonus AFIT. Personal stakes > abstract rankings. 🔥
- **Ghost races** — race your own best week, or a friend's ghost, with a live progress bar.
- **City / country boards** — local pride ("Beirut #3 today"). Geographic identity is deeply shareable and recruits whole communities. 🔥🔥

**Why it grows the base:** leagues manufacture a weekly cliffhanger; city boards turn users into local evangelists.

---

## 4. Social → **"Movement, Live"**

**Today:** a native Hive feed of reports with upvotes/comments. Passive — you read into a void.

**Reimagined:** make it feel like other humans are *moving right now, with you*.

- **Live activity pulse** — "1,204 actifiters are moving right now" with an animated globe/map of anonymized live motion. Social proof that the place is alive the moment you open it. 🔥🔥
- **Cheers & reactions** — lightweight claps/fire/💪 beyond upvotes; a cheer can carry a **micro-AFIT tip** (the `tipAccount` rails already exist). Giving *and* getting cheers is dopamine both ways. 🪝🪝
- **Kudos & shout-outs** — "You beat your rival!", "Sara cheered your 15K." Real-time social feedback loops.
- **Activity Stories** — ephemeral 24h story of your walk/route/PR (BeReal/IG energy) that vanishes, lowering the pressure to post a "perfect" report. 🔥🔥
- **Walking buddies / co-walks** — opt-in matching to a buddy for a synchronized session; you both see each other's live progress bar during the walk. Accountability = retention. 🪝🪝

**Why it excites:** shifts social from "publish and wait" to "we're in this together, now."

---

## 5. Rewards → **"The Dopamine Economy"**

**Today:** earn AFIT per post via an opaque formula. The reward is delayed and invisible.

**Reimagined:** keep the fair long-term economy, but wrap it in **immediate, surprising, playful** feedback.

- **Daily spin / loot drop** — a free daily pull (small AFIT, boosters, badge shards, streak-freeze). A tiny variable reward for *showing up*, independent of posting. 🪝🪝🪝
- **Streak freeze / repair** — Duolingo's killer feature. One "freeze" saves a missed day so a 90-day streak doesn't die to one sick day. Directly attacks the #1 churn moment. 🪝🪝🪝
- **Surprise AFIT rain** — occasional unannounced bonus drops ("Power Hour: 2× on steps for the next 60 min!"). Unpredictable timing → people open the app "just in case." 🔥🔥
- **Combos & multipliers** — consecutive-day and milestone combos build a visible multiplier you don't want to break.
- **Treasure Walk (moonshot).** Geo-placed AFIT/badge drops on a map you collect by *physically walking there* — Pokémon GO for fitness. Turns earning into an adventure and gets people outside. 🔥🔥🔥 ⚠️ big build, huge wow.

**Why it grows the base:** the spin + streak-freeze give a reason to open the app on days you *didn't* work out — closing the retention gap utilities always have.

---

## 6. Challenges / Duels → **"The Arena"** (the marquee bet)

**Today:** nonexistent (no web feature, no API — fully greenfield).

**Reimagined:** the flagship social-competitive mode.

- **Live 1v1 duels** — challenge a friend/rival to a 24h step goal, with a real-time dual progress bar and trash-talk from your AI coach. Winner takes a **sponsor / Actifit-funded** AFIT prize + bragging rights — **no user-staked wager** (house rule). 🔥🔥🔥 🪝🪝
- **Team raids / boss battles** — a squad pools steps to "defeat a boss" (a collective goal); everyone shares the loot. Collaborative goals pull in *friend groups*, not just individuals. 🔥🔥
- **City vs City** — recurring geographic tournaments. Whole communities mobilize; local press-worthy. 🔥🔥🔥
- **Tournaments & brackets** — seasonal knockout events with a leaderboard and prize pool.

**Why it's the marquee:** competition + prizes + social is the single biggest untapped engagement lever, and *no competitor in the move-to-earn space does it well*. This is where Actifit can lead, not follow. **Reward model:** free/low-friction entry; win on **effort** (skill, not chance); AFIT from **sponsor / DHF / Actifit-funded prize pools**, never user-staked escrow (house rule). (Needs a new backend for the challenge lifecycle + prize-pool payout — scope early. Build it once; web consumes the same engine.)

---

## 7. AI → **"Coach with a Personality"**

**Today:** `AiService` (Gemma) does workout plans, one dashboard insight, and translation. Competent but faceless.

**Reimagined:** a **named coach persona** you choose at signup — with a voice, attitude, and memory.

- Proactive, personal check-ins ("You're 2K behind your Tuesday pace — quick walk?").
- **Voice** morning briefing & evening retro (audio, hands-free).
- In-duel commentary and trash talk; celebrates your PRs like a hype-man.
- Adaptive coaching that remembers your patterns and goals across weeks.
- Multiple coach personalities (drill sergeant / zen guide / cheerleader) = a fun identity choice and a reason to screenshot. 🔥 🪝🪝

**Why it excites:** a personality is memorable and shareable in a way a "tip card" never is — and it makes the app feel like it *knows you*.

---

## 8. Onboarding → **"Win in 60 seconds"**

**Today:** create-account hyperlink → friction, no payoff.

**Reimagined:** a **playable, rewarding** first minute.

- Pick your **coach + companion** (a fun identity choice, not a form).
- Grant step access → **instantly see today's steps counted** and get a **first AFIT drop + first badge** before you've done anything. Immediate payoff = the strongest activation lever.
- A 3-tap "first quest" ("take 500 steps now") that pays out on the spot.
- Optional: pull yesterday's steps from Health Connect so day one already looks full.

**Why it grows the base:** most move-to-earn apps make you work for days before the first reward. Paying off in *60 seconds* dramatically lifts activation and word-of-mouth.

---

## Signature bets (if we only do a few)

The boldest, most differentiating, highest-excitement swings — pick 3 to make Actifit *famous for something*:

| Bet | Excite | Hook | Effort | Why it matters |
| :-- | :-- | :-- | :-- | :-- |
| **Living Companion profile** (§1) | 🔥🔥 | 🪝🪝🪝 | Med-High | Daily-return identity hook; nothing like it in move-to-earn |
| **Leagues + Rivals** (§3) | 🔥🔥 | 🪝🪝🪝 | Medium | Proven (Duolingo) weekly-cliffhanger retention |
| **The Arena: duels & city battles** (§6) | 🔥🔥🔥 | 🪝🪝 | High 🧱 | The marquee differentiator; community-scale recruiting |
| **Daily spin + streak freeze** (§5) | 🔥 | 🪝🪝🪝 | Low-Med | Cheapest, highest-ROI retention mechanic here |
| **Year/Week in Motion recap** (§1) | 🔥🔥🔥 | 🪝 | Medium | Built-to-share; free viral acquisition |
| **Treasure Walk (geo-AFIT)** (§5) | 🔥🔥🔥 | 🪝🪝 | High 🧱 | Moonshot wow; gets people outside & talking |

---

## Cross-cutting engagement toolbox

Reusable mechanics to sprinkle across every surface:
- **Variable rewards** (spin, mystery badges, AFIT rain) — surprise > predictable.
- **Streaks with mercy** (freeze/repair) — protect the sunk-cost people fear losing.
- **Visible progress everywhere** (rings, multipliers, evolving art) — never a dead screen.
- **Manufactured rivalry** (rivals, leagues, ghosts) — someone to beat at *your* level.
- **Share-designed moments** (cards, recaps, PR stories) — every wow is a share is an install.
- **Juice** — confetti, haptics, sound, animated milestone crossings. The *feel* is the feature.

---

## How to pilot without boiling the ocean

Sequence the excitement so each phase ships a *visible* wow:

1. **Feel & surprise first (cheap, fast):** milestone juice, daily spin, streak freeze, shareable card. Low effort, immediate retention lift — proves the thesis.
2. **Identity next:** Living Companion + evolving badges/quests + Week-in-Motion recap. The daily-return hook + the viral share engine.
3. **Rivalry:** Leagues + Rivals + friends-only boards. Weekly cliffhanger.
4. **The Arena (marquee):** duels → team raids → city battles. The differentiator; start its backend during phase 2.
5. **Moonshots:** Treasure Walk, on-chain collectible badges, Wear OS coach.

> Guardrail: keep the underlying AFIT economy *fair and unchanged* — this is a
> presentation, feedback, and social layer on top of honest rewards, not a change to how
> much people earn. Excitement should come from *feel and play*, never from inflating payouts.
