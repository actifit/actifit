# Actifit Screen Redesign Mockups

> **Status:** Approved for implementation
> **Branch:** `feature/screen-redesign`
> **Design System:** Material 3, Actifit Red (#FF112D), Dark Mode Support

---

## Footer Navigation — Option A (Bottom Sheet "More")

### Primary Nav (4 items)
```
┌─────────────────────────────────┐
│  🏠      🛒     📊      ⋮      │
│ Home  Market  History  More    │
└─────────────────────────────────┘
```

### "More" Bottom Sheet
```
┌─────────────────────────────────┐
│  More                          ×│
├─────────────────────────────────┤
│  🎬 Video                      │
│  💬 Socials                    │
│  📺 TV                         │
│  ✉️ Chat (2)                   │
│  ❓ Help                       │
│  ⚙️ Settings                   │
│  🏆 Leaderboard                │
└─────────────────────────────────┘
```

**Rationale:** Keeps primary nav clean, follows Material 3 patterns, easy to extend later.

---

## Gamified Rewards — Enhanced Concept

### Current System
- 4 reward tiers: Free (no step requirement), 5K, 7K, 10K steps
- Claimed via dialog popup from dashboard
- Each tier has: title, description, claim button, status text
- Rewards reset daily

### Enhanced Dashboard Integration
```
┌──────────────────────────────────────────┐
│  🎁 Daily Rewards                        │
│                                          │
│  ┌──────┐ ┌──────┐ ┌──────┐ ┌──────┐   │
│  │ FREE │ │ 5K   │ │ 7K   │ │ 10K  │   │
│  │  🎁  │ │ 🥉   │ │ 🥈   │ │ 🥇   │   │
│  │  ✓   │ │  ✓   │ │  !   │ │  🔒  │   │
│  │ 2.1  │ │ 5.3  │ │ —    │ │ 15.0 │   │
│  └──────┘ └──────┘ └──────┘ └──────┘   │
│                                          │
│  Steps today: 7,234 / 10,000             │
│  ════════════════░░░░░░░░░░  72%        │
└──────────────────────────────────────────┘
```

### Rewards Card States
| Tier | Locked (steps not met) | Available (steps met, not claimed) | Claimed |
|------|----------------------|-----------------------------------|---------|
| FREE | N/A (always available) | Red pulse animation + "!" | Greyed out + checkmark |
| 5K   | Greyed out + 🔒 | Red pulse + AFIT amount shown | Greyed out + ✓ |
| 7K   | Greyed out + 🔒 | Red pulse + AFIT amount shown | Greyed out + ✓ |
| 10K  | Greyed out + 🔒 | Red pulse + AFIT amount shown | Greyed out + ✓ |

### Progress Bar Logic
- Bar fills proportionally to current steps vs 10K goal
- Milestone markers at 0, 5K, 7K, 10K positions on the bar
- Tap any tier card → opens detailed claim dialog (existing `reward_popup_v2.xml` logic)
- Tap the progress bar → expands to show hourly breakdown

### Design Notes
- Cards are compact on dashboard (icon + status + reward value)
- Full details in dialog (description, claim button, status text)
- Color coding: grey = locked, red pulse = available, muted green = claimed
- Progress bar uses brand red for fill, light grey for track
- Milestone markers are small dots on the bar with labels

---

## Mockup 1: Login Screen

```
┌──────────────────────────────────────────┐
│                                          │
│          [Animated Actifit Logo]         │
│                                          │
│              ACTIFIT                     │
│        Move. Earn. Thrive.               │
│                                          │
│   ┌──────────────────────────────────┐   │
│   │                                  │   │
│   │  👤  Username                    │   │
│   │                                  │   │
│   │  🔑  Private Posting Key    [📷] │   │
│   │                                  │   │
│   │  ──────────────────────────────  │   │
│   │                                  │   │
│   │      [   LOGIN TO ACTIFIT   ]    │   │
│   │                                  │   │
│   │            or                    │   │
│   │                                  │   │
│   │         Continue as Guest        │   │
│   │                                  │   │
│   └──────────────────────────────────┘   │
│                                          │
│   Don't have an account? Create one →    │
│   How to find your posting key? →        │
│                                          │
└──────────────────────────────────────────┘
```

### Design Notes
- **Background:** Subtle dark-to-light gradient with red accent wave at bottom
- **Card:** Rounded corners (24dp), white bg, soft shadow (elevation 4dp)
- **Inputs:** Rounded backgrounds (12dp) with leading icons, trailing QR button on key field
- **Primary button:** Full-width, rounded (12dp), red fill (#FF112D), white text, bold
- **Guest/Skip:** Text link, understated, centered
- **Helper links:** Bottom, smaller gray text, right-aligned arrows
- **Animation:** Fade-in + slide-up on load

### View ID Mapping
| Old ID | New ID |
|--------|--------|
| `login_hero` | `login_logo_container` |
| `steemit_username_lbl` | *(removed, use input hint)* |
| `username_login` | `et_username` |
| `steemit_posting_key_lbl` | *(removed, use input hint)* |
| `posting_key_login` | `et_posting_key` |
| `qrCodeButton` | `btn_qr_scan` (trailing icon) |
| `loginButton` | `btn_login` |
| `skipButton` | `btn_guest_login` |
| `username_create_account_link` | `tv_create_account` |
| `posting_key_link` | `tv_find_posting_key` |

---

## Mockup 2: Dashboard

```
┌──────────────────────────────────────────┐
│ 👋 Hey, Username!           🔔(3)  💰   │  ← clean header
├──────────────────────────────────────────┤
│                                          │
│  ┌────────────────────────────────────┐  │
│  │        Today's Activity            │  │  ← hero card
│  │                                    │  │
│  │          ╭───────╮                 │  │
│  │         ╱   72%   ╲                │  │
│  │        │  7,234   │                │  │
│  │        │  steps   │                │  │
│  │         ╲  /10k  ╱                 │  │
│  │          ╰───────╯                 │  │
│  │                                    │  │
│  │  📅 Sat, Apr 25   🔋 Battery OK   │  │
│  │                                    │  │
│  │  [🔄 Sync]  [⚙️ Settings]  [📤 Share]│  │
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────┐ ┌────────────┐          │  ← quick actions
│  │ 📝 Post    │ │ 🏋️ Workout │          │
│  │  Activity  │ │  Log       │          │
│  └────────────┘ └────────────┘          │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │  🎁 Daily Rewards                  │  │  ← rewards card
│  │                                    │  │
│  │  [🎁] [🥉] [🥈] [🥇]             │  │
│  │  ✓    ✓    !    🔒                │  │
│  │  2.1  5.3   —   15.0              │  │
│  │                                    │  │
│  │  ════════●═════●═══░░░░  7,234    │  │
│  │  0      5K    7K    10K           │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │  💎 Earnings                       │  │  ← earnings card
│  │                                    │  │
│  │  ⬡ AFIT    ◆ HIVE    ● BLURT     │  │
│  │  1,234     0.456     12.3         │  │
│  │                                    │  │
│  │  🏅 Pending: 45.2 AFIT            │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │  📊 Activity Breakdown             │  │  ← chart card
│  │                                    │  │
│  │  [Daily] [Hourly]  ← segmented    │  │
│  │  ╭──╮ ╭──╮ ╭──╮ ╭──╮ ╭──╮        │  │
│  │  │  │ │  │ │  │ │  │ │  │        │  │
│  │  ╰──╯ ╰──╯ ╰──╯ ╰──╯ ╰──╯        │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │  🏆 Workouts Highlight             │  │
│  │     Manage your workout routine →  │  │
│  └────────────────────────────────────┘  │
│                                          │
├──────────────────────────────────────────┤
│  🏠      🛒      📊      ⋮              │  ← footer nav
│ Home   Market  History  More            │
└──────────────────────────────────────────┘
```

### Design Notes
- **Header:** Greeting + profile pic left, notification bell with badge + wallet icon right
- **Hero card:** Pie chart centered with step count overlay, date below, action row (Sync, Settings, Share)
- **Sync/Settings/Share:** Replaces current scattered overlay buttons on pie chart — unified action row below chart
- **Quick actions:** Two pill buttons side by side (Post Activity, Workout Log)
- **Rewards card:** 4 tier cards (Free, 5K, 7K, 10K) with status indicators + progress bar with milestone markers
- **Earnings card:** Horizontal row of crypto icons + balances, collapsible
- **Chart card:** Segmented control for Daily/Hourly toggle
- **Workout highlight:** Gradient card (red tint) with CTA arrow
- **Footer:** 4 primary + More (opens bottom sheet)

### Sync/Data Integration Approach
The current implementation has **three separate pie chart containers** (`third_party_active` for Fitbit, `health_connect_active` for HC, default `step_pie_chart`). The redesign unifies these into **one card** with:
1. **Single pie chart** that swaps data source dynamically
2. **Source indicator badge** (Fitbit/HC/Actifit icon top-left of chart)
3. **Action row below chart:**
   - `🔄 Sync` — triggers data refresh from active source
   - `⚙️ Settings` — opens tracking settings (same as current `switchSettings`)
   - `📤 Share` — share achievement (same as current `btn_share_achievement`)
4. **Source switcher:** Long-press or tap the source badge to cycle between available sources (Fitbit → Health Connect → Actifit native)

### View ID Mapping (Dashboard)
| Old ID | New ID | Notes |
|--------|--------|-------|
| `user_profile_pic` | `iv_profile_pic` | |
| `welcome_user` | `tv_greeting` | |
| `btn_view_notifications` | `btn_notifications` | |
| `notif_count` | `tv_notif_badge` | |
| `btn_view_wallet` | `btn_wallet` | |
| `current_date` | `tv_date` | |
| `step_pie_chart` | `pie_chart_activity` | Unified chart |
| `step_pie_chart_fitbit` | *(removed)* | Merged |
| `step_pie_chart_health_connect` | *(removed)* | Merged |
| `fitbit_logo` | `iv_source_badge` | Dynamic source icon |
| `sync` / `sync_health_connect` | `btn_sync` | Unified |
| `switchSettings` / etc | `btn_tracking_settings` | Unified |
| `btn_share_achievement` / etc | `btn_share_achievement` | Unified |
| `battery_notice` | `tv_battery_notice` | |
| `btn_post_steemit` | `btn_post_activity` | Quick action |
| `btn_waves` | `btn_workout_log` | Quick action |
| `news_pager` | `vp_news_carousel` | |
| `news_tablayout` | `tl_news_tabs` | |
| `main_today_activity_chart` | `bar_chart_daily` | |
| `main_history_activity_chart` | `bar_chart_history` | |
| `daily_chart_btn` | `btn_daily` | Segmented control |
| `hourly_chart_btn` | `btn_hourly` | Segmented control |
| `btn_start_workout_section` | `card_workout_highlight` | |
| `daily_reward` | `btn_daily_reward` | → rewards card |
| `refer_friend_button` | `btn_refer_friend` | → earnings card area |
| `btn_buy_afit` | `btn_buy_afit` | → earnings card area |
| `footer_menu_container` | `bottom_nav` | |
| *(new)* | `sheet_more_menu` | Bottom sheet |
| *(new)* | `card_rewards` | Rewards card |
| *(new)* | `reward_tier_free` | Free tier card |
| *(new)* | `reward_tier_5k` | 5K tier card |
| *(new)* | `reward_tier_7k` | 7K tier card |
| *(new)* | `reward_tier_10k` | 10K tier card |
| *(new)* | `rewards_progress_bar` | Progress bar |
| *(new)* | `tv_steps_progress` | "7,234 / 10,000" |

---

## Mockup 3: Post Creation

```
┌──────────────────────────────────────────┐
│ ← Back     Create Report         [👁️]   │  ← top bar with preview
├──────────────────────────────────────────┤
│                                          │
│  ●────●────○────○────○────○             │  ← step progress
│  1    2    3    4    5    6             │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │  📝 Report Title                   │  │
│  │  ────────────────────────────────  │  │
│  │  My Morning Run                    │  │
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │  📅 Date          [Today▼]         │  │
│  │                                    │  │
│  │  Steps: 7,234    [🔄 Sync]         │  │
│  │                                    │  │
│  │  Activities: [Running] [Walking] + │  │  ← chip-style
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │  📏 Measurements            [▼]    │  │  ← collapsible
│  └────────────────────────────────────┘  │
│                                          │
│  ┌────────────────────────────────────┐  │
│  │  ✍️ Content                        │  │
│  │                                    │  │
│  │  Tags: #running #fitness +         │  │
│  │                                    │  │
│  │  ┌──────────────────────────────┐  │  │
│  │  │ Had a great morning run...   │  │  │
│  │  │                              │  │  │
│  │  │                              │  │  │
│  │  └──────────────────────────────┘  │  │
│  │  [📷] [🎬] [⛶]        234/100    │  │
│  │                                    │  │
│  │  ── Preview ──                     │  │
│  │  Rendered markdown appears here    │  │
│  └────────────────────────────────────┘  │
│                                          │
│                                    [📤]  │  ← FAB Post button
└──────────────────────────────────────────┘
```

### Design Notes
- **Top app bar:** Back button, title, preview toggle
- **Stepper:** Horizontal dots with connecting lines, replaces numbered Unicode characters
- **Cards:** Each section is a card with header icon + title
- **Measurements:** Collapsed by default, expands on tap
- **Tags:** Chip-style input (tap + to add)
- **Editor:** Toolbar buttons for image, video, expand
- **Character counter:** Subtle badge (turns red if under minimum)
- **Post button:** Floating action button at bottom-right
- **Footer nav:** Removed from this screen (focused task)

### View ID Mapping (Post Creation)
| Old ID | New ID | Notes |
|--------|--------|-------|
| `steemit_post_title` | `et_post_title` | |
| `titleCount` | *(removed)* | Stepper replaces |
| `report_date_option_group` | `sg_date_toggle` | Segmented group |
| `report_today_option` | `rb_today` | |
| `report_yesterday_option` | `rb_yesterday` | |
| `steemit_step_count` | `tv_step_count` | Read-only |
| `sync_data` | `btn_sync_data` | |
| `steemit_activity_type` | `chip_group_activities` | Chip group |
| `measurements_height` | `et_height` | |
| `measurements_weight` | `et_weight` | |
| `measurements_bodyfat` | `et_body_fat` | |
| `measurements_waistsize` | `et_waist` | |
| `measurements_thighs` | `et_thighs` | |
| `measurements_chest` | `et_chest` | |
| `steemit_post_tags` | `et_tags` | |
| `steemit_post_text` | `et_post_content` | |
| `btn_choose_file` | `btn_add_image` | |
| `btn_video_post` | `btn_add_video` | |
| `btn_expand_editor` | `btn_expand_editor` | |
| `md_view` | `tv_markdown_preview` | |
| `post_to_steem_btn` | `fab_post` | FAB |
| `bottom_menu_container` | *(removed)* | Focused screen |

---

## Key Design Tokens

```xml
<!-- colors.xml -->
colorPrimary         = #FF112D  <!-- Actifit Red -->
colorPrimaryVariant   = #CC0E24  <!-- Darker red for dark mode status bar -->
colorSecondary       = #00C853  <!-- Fitness green accent -->
colorSurface         = #FFFFFF  <!-- Light mode card bg -->
colorSurfaceDark     = #1E1E1E  <!-- Dark mode card bg -->
colorBackground      = #F5F5F5  <!-- Light mode screen bg -->
colorBackgroundDark  = #121212  <!-- Dark mode screen bg -->
colorOnPrimary       = #FFFFFF  <!-- Text on red -->
colorOnSurface       = #212121  <!-- Primary text on cards -->
colorOnBackground    = #212121  <!-- Primary text on screen -->
colorTextSecondary   = #757575  <!-- Secondary text -->
colorTextHint        = #A0A0A0  <!-- Hint text -->
colorError           = #FF112D  <!-- Same as primary (brand consistency) -->
colorSuccess         = #00C853  <!-- Green for positive states -->
colorWarning         = #FF9800  <!-- Orange for warnings -->
colorSeparator       = #E0E0E0  <!-- Dividers -->
```

```xml
<!-- dimens.xml -->
card_corner_radius   = 16dp
button_corner_radius = 12dp
pill_corner_radius   = 24dp
elevation_card       = 2dp
elevation_fab        = 6dp
spacing_grid         = 8dp
input_height         = 56dp
header_height        = 64dp
footer_height        = 56dp
```

```
<!-- Typography -->
text_body            = 14sp
text_subtitle        = 16sp
text_title           = 20sp
text_headline        = 24sp
text_display         = 32sp
```

---

## Implementation Order

1. **Phase 1: Foundation**
   - Update `colors.xml` with semantic color tokens
   - Add `dimens.xml` for spacing/corner values
   - Update `styles.xml` to Material 3 (`Theme.Material3.DayNight.NoActionBar`)
   - Create shared drawables: `card_background.xml`, `input_background.xml`, `button_primary.xml`

2. **Phase 2: Login Screen**
   - New `login_page.xml`
   - Update `LoginActivity.java` for new IDs
   - Test build + verify login flow

3. **Phase 3: Dashboard**
   - New `activity_main.xml`
   - Update `MainActivity.java` for new IDs
   - Implement unified pie chart card with source badge + action row
   - Implement rewards card with tier indicators + progress bar
   - Implement footer nav + More bottom sheet
   - Test build + verify all flows

4. **Phase 4: Post Creation**
   - New `activity_post_steemit.xml`
   - Update `PostSteemitActivity.java` for new IDs
   - Implement stepper, chip groups, collapsible measurements
   - Test build + verify posting flow

5. **Phase 5: Polish**
   - Dark mode testing
   - Animation refinements
   - Accessibility checks
   - Final QA

---

## Open Questions (Resolved)
- ✅ Footer overflow: Option A (Bottom Sheet "More")
- ✅ Sync/data integration: Unified chart card with source badge + action row
- ✅ Post screen footer: Removed (focused task screen)
- ✅ Dark mode: Full support via Material 3 DayNight theme
- ✅ Brand color: Keep Actifit Red (#FF112D) as primary
- ✅ Rewards: Enhanced card on dashboard with 4 tiers + progress bar + milestone markers
