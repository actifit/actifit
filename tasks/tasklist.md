# Dashboard Enhancement Tasklist

Product and UX improvements for the Actifit dashboard (MainActivity / activity_main.xml).
Organized by priority. All tasks are UI/UX enhancements unless otherwise noted.

---

## Priority 1 — High Impact, Lower Effort

- [x] **P1-1: Replace pie chart with step-progress arc ring**
  Replace the current pie chart with a goal arc ring (0–360°, fills clockwise) showing
  today's steps as a percentage of the daily goal. Center the raw step count inside the
  ring. Mark the three milestone thresholds (5K, 7K, 10K) as tick marks on the ring arc.
  _Files: activity_main.xml, MainActivity.java, ChartManager.java_

- [x] **P1-2: Headline "Today's Goal" progress label**
  Directly below (or inside) the step ring, show `6,234 / 10,000 steps` and
  `You're 62% to your goal`. Make the step count the undeniable headline number
  on open rather than buried inside a pie slice.
  _Files: activity_main.xml, MainActivity.java_

- [x] **P1-3: Contextual action banner / smart nudge card**
  Add a dismissible card just below the hero that surfaces the single most impactful
  action at any moment:
  - < 5K steps → "Keep going! You're X steps from your first reward"
  - ≥ 5K unclaimed → "You've hit 5K — claim your reward now!"
  - All claimed + goal met → "Great day! Share your achievement"
  - Not posted today → "You've earned it — post your report to the blockchain"
  Replaces the static "Rewards Cycle In Progress" text panel.
  _Files: activity_main.xml, MainActivity.java_

- [x] **P1-4: Condense footer navigation from 8+ items to 5**
  Bottom nav should have exactly 5 items: Home · Social · Market · Leaderboard · More.
  Move Videos, Socials, Help, and Chat into a bottom sheet triggered by "More".
  _Files: activity_main.xml (footer), MainActivity.java (footer click handlers)_

- [x] **P1-5: Earnings clarity card — estimated AFIT reward**
  Replace the four opaque token logos in the earnings panel with a single card showing
  `Estimated Reward: ~142 AFIT` sourced from a new server-side scoring endpoint.
  Tap to expand for the per-token blockchain breakdown (HIVE/BLURT, already via
  `/pendingRewards`).

  **How the estimate is calculated (server-side, Option C):**
  A new endpoint `GET /getEstimatedReward?user={username}` is added to the API server
  (`c:\mo\coding\actifitbot\app.js`). It fetches the user's latest actifit post
  (from the `verified_posts` collection or Hive blockchain), then applies the same
  scoring formula used by the curation bot (`c:\mo\coding\actifitvoter\curation-bot.js`):

  ```
  estimated_afit = activity_score(step_count)
                 + content_score(body_length)
                 + media_score(image_count)
                 + upvote_score(net_votes)
                 + comment_score(comment_count)
                 + moderator_score
                 + user_rank_score(rank * rank_factor / 100)
                 + applicable boosts
  ```

  Scoring rules and factors (activity_factor, rank_factor, etc.) must be shared or
  replicated from actifitvoter config into actifitbot so the endpoint can run the
  formula without cross-repo dependency at runtime.

  Returns: `{ estimated_afit, post_url, post_date, already_rewarded: bool }`
  — `already_rewarded: true` means the curation bot already ran for this post,
  in which case the app shows "Last reward: X AFIT" instead of "Estimated".

  **Work split:**
  - Backend (`actifitbot/app.js`): new `/getEstimatedReward` endpoint + scoring logic
  - Android (`ApiManager.java`, `activity_main.xml`, `MainActivity.java`): call endpoint,
    display result in earnings panel replacing the token logo row

  _Android files: activity_main.xml (earnings panel), ApiManager.java, MainActivity.java_
  _Backend repo: c:\mo\coding\actifitbot\app.js_

---

## Priority 2 — High Impact, Moderate Effort

- [x] **P2-1: 7-day streak / consistency tracker strip**
  Add a horizontal row of 7 circles (Mon–Sun) filled for days with ≥ 5K steps, empty
  otherwise. Show current streak count prominently above the circles.
  Data already exists in StepsDBHelper — query last 7 days and render.
  _Files: activity_main.xml, MainActivity.java, StepsDBHelper.java_

- [x] **P2-2: Personalized AI insight card (Gemini)**
  Surface one daily Gemini-generated insight as a compact card below the hero:
  e.g. "You're most active 8–10am. A short walk after lunch could push you past 10K."
  Refresh once per day. AiService is already integrated — add a dashboard entrypoint.
  _Files: activity_main.xml, MainActivity.java, AiService.java_

- [x] **P2-3: Improve empty gadgets state to a conversion CTA**
  When no gadgets are active, replace "No Active Gadgets" dead space with:
  "Activate a Gadget — Boost earnings by up to 2×" + "Browse Market" button.
  _Files: activity_main.xml (gadgets section), MainActivity.java_

- [x] **P2-4: Milestone celebration animations**
  When steps cross 5K, 7K, or 10K thresholds during a live session, trigger a brief
  visual celebration (confetti burst or badge-unlock animation) and auto-surface the
  reward claim popup. Currently milestone crossing is silent.
  _Files: MainActivity.java, ActivityMonitorService.java_

- [x] **P2-5: Monthly activity heatmap**
  Add a compact calendar heatmap (GitHub-style contribution graph) below the bar chart
  showing active days this month. Color intensity = steps achieved. Provides a
  "look how consistent I've been" moment.
  _Files: activity_main.xml, MainActivity.java, StepsDBHelper.java_

---

## Priority 3 — Longer Horizon, High Value

- [ ] **P3-1: Personalized / reorderable dashboard cards**
  Allow users to long-press and drag to reorder dashboard sections, or toggle visibility
  via a "Customize" option. Store order in SharedPreferences.
  Power users want charts first; casual users want rewards first.
  _Files: activity_main.xml, MainActivity.java, new DashboardPreferences helper_

- [ ] **P3-2: Health metrics expansion for Health Connect users**
  When Health Connect is active, surface secondary metric chips below the step ring:
  Calories · Active Minutes · Resting Heart Rate (if available via HC APIs).
  _Files: activity_main.xml, MainActivity.java, HealthConnectManager.java_

- [x] **P3-3: Community feed preview strip**
  Add a horizontal scrollable strip of 3 recent community posts (avatar + username +
  step count) just above the footer. Creates social proof and reduces the feeling of
  posting into a void. Pull data already fetched for the Social screen.
  _Files: activity_main.xml, MainActivity.java, new adapter_

- [ ] **P3-4: Configurable daily step goal**
  Let users set their own step goal (not fixed 10K). Show progress against their chosen
  goal on the arc ring. Add a quick-edit pencil icon next to the goal count label.
  Store in SharedPreferences; default 10K.
  _Files: activity_main.xml, MainActivity.java, SettingsActivity.java_

---

## Summary Table

| ID | Title | Priority | Effort | Status |
|----|-------|----------|--------|--------|
| P1-1 | Step-progress arc ring | High | Low | ✅ Done |
| P1-2 | Today's Goal progress label | High | Low | ✅ Done |
| P1-3 | Contextual action banner | High | Low | ✅ Done |
| P1-4 | Condense footer to 5 items | High | Low | ✅ Done |
| P1-5 | Earnings clarity card (estimated AFIT) | High | Medium | ✅ Done |
| P2-1 | 7-day streak tracker | High | Medium | ✅ Done |
| P2-2 | AI insight card (Gemini) | High | Medium | ✅ Done |
| P2-3 | Gadgets empty-state CTA | Medium | Low | ✅ Done |
| P2-4 | Milestone celebration animations | Medium | Medium | ✅ Done |
| P2-5 | Monthly activity heatmap | Medium | Medium | ✅ Done |
| P3-1 | Reorderable dashboard cards | High | High | ⬜ Pending |
| P3-2 | Health metrics chips | Medium | Medium | ⬜ Pending |
| P3-3 | Community feed preview | Medium | High | ✅ Done |
| P3-4 | Configurable step goal | High | Medium | ⬜ Pending |
