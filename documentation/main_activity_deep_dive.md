# Actifit Main Activity Deep Dive

The `MainActivity` (located at `app/src/main/java/io/actifit/fitnesstracker/actifitfitnesstracker/MainActivity.java`) is the central hub of the Actifit Android application. It serves as the primary dashboard where users monitor their physical activity, manage their accounts, and interact with the Actifit ecosystem.

## 1. User Identity & Profile Management
The header area provides immediate feedback on the user's status and quick access to account-related actions.

*   **User Profile Display:** Displays the user's profile picture (fetched from Hive), username, and current Actifit User Rank.
*   **Deep Linking:**
    *   Clicking the **Profile Picture** or **Username** opens the user's full activity blog on `actifit.io`.
    *   Clicking the **User Rank** displays a detailed explanation of how the rank is calculated and provides a link to the web-based rank breakdown.
*   **Authentication Flow:**
    *   **Login/Signup:** Visible when a user is not authenticated. Redirects to specialized login or signup screens.
    *   **Logout:** Accessible via the top icons/menu once logged in.
*   **Resource Credits (RC):** For Hive users, the current RC percentage is displayed. Clicking it shows an information dialog about how RC affects blockchain interactions.
*   **Notifications Center:** A bell icon shows the count of unread notifications and links to the `NotificationsActivity`.

## 2. Activity Tracking Scenarios
Actifit supports three distinct tracking methods, which can be toggled by the user:

*   **Phone Sensors (Default):**
    *   Uses internal step sensors or accelerometers via a foreground service (`ActivityMonitorService`).
    *   Updates the UI in real-time as steps are taken.
    *   **Battery Optimization Check:** The app monitors if the OS is restricting its background activity and prompts the user to disable battery optimization for Actifit to ensure accurate tracking.
*   **Fitbit Integration:**
    *   Allows users to sync steps from their Fitbit account.
    *   Includes a dedicated "Sync" button to fetch the latest data from Fitbit's API.
*   **Health Connect Integration:**
    *   Syncs data from Android's Health Connect platform.
    *   Includes permission management and a sync button to manually refresh data.

## 3. Data Visualization & Charts
The main screen uses multiple chart types to represent activity data:

*   **Daily Progress (Pie Chart):** Located at the center, it shows current steps relative to milestones (5,000, 7,000, and 10,000 steps).
*   **Hourly Activity (Bar Chart):** Shows step distribution across 15-minute intervals for the current 24-hour period.
*   **Activity History (Bar Chart):** Shows daily step totals over time.
*   **Chart Interaction:** A "Chart Switcher" allows users to toggle the visible bar chart between "Hourly" (today) and "Daily" (historical history).

## 4. Rewards & Gamification
Actifit rewards users for physical activity through several mechanisms:

*   **Daily Reward Tiers:**
    *   Users can claim rewards at four milestones: **0 steps (Free)**, **5,000 steps**, **7,000 steps**, and **10,000 steps**.
    *   Claiming a reward triggers an **AdMob Rewarded Ad**. Upon completion, a random AFIT token payout is granted.
    *   The UI indicates "Claimed" status and shows the amount earned.
*   **Earning Status Panel:** A horizontal strip of icons (AFIT, HIVE, STEEM, BLURT, SPORTS) indicates whether the user is eligible for rewards on those chains. Grayed-out icons signify missing accounts or low balances.
*   **Pending Rewards Notification:** A periodic popup informs users of available rewards waiting for them on various blockchains, with a direct link to their activity feed to claim/view them.
*   **Voting Cycle Status:** A "Rewards Processing" indicator shows the status of the current global reward distribution cycle.

## 5. Ecosystem & Social Interaction
The screen provides shortcuts to all major platform features:

*   **Post to Steem/Hive:** A prominent button to create and publish the daily activity report.
*   **Waves (Microblogging):** Quick access to the Actifit Waves microblogging tool.
*   **Leaderboard:** Links to the `LeaderboardActivity` to see top performers.
*   **Social/Market/Social:** Access to the broader Actifit community feed, the market for gadgets, and social tools.
*   **Workout Wizard:** A dedicated section (highlighted with a trophy icon) that launches the multi-step `WorkoutWizardActivity` for logging specific exercises.
*   **News Slider:** A ViewPager at the top that automatically cycles through announcements, contest info, and platform updates.
*   **Surveys:** Occasional popups for community surveys that may reward users for participation.

## 6. Financial & Growth Tools
*   **Wallet:** Displays current balances and links to a detailed `WalletActivity`.
*   **Buy AFIT:** A popup menu linking to various external exchanges where AFIT is traded.
*   **Referral Program:** A dedicated button to generate a referral link and share it via social apps.
*   **Free Signup Links:** Eligible users can claim and share promotional links that allow their friends to join the platform for free.

## 7. Configuration & Utility
*   **Settings:** Access to sensor selection, aggressive background tracking toggles, theme settings, and language selection.
*   **Daily Tips:** A random fitness or app usage tip shown in a popup.
*   **Dark Mode:** Automatic or manual theme switching.
*   **Newbie Help:** Info for new users on how to get started and verified, with a link to the Actifit Discord.

## 8. Security & Integrity Checks
The `MainActivity` performs several "ground preparation" tasks to ensure platform integrity:
*   **App Signature Validation:** Detects if the APK has been tampered with or resigned.
*   **Emulator Detection:** Prevents automated botting by checking for common emulator indicators.
*   **Root Detection:** Checks if the device is rooted.
*   **SIM Card Check:** Verifies that the app is running on a legitimate mobile device.
