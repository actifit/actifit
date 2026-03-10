# Actifit: Features, User Rank, and Workouts Documentation

Actifit is a decentralized "Move-to-Earn" ecosystem that incentivizes healthy lifestyles by rewarding users with cryptocurrency for their physical activity. This document provides a comprehensive overview of Actifit's core functionality, the User Rank system, the Workout Wizard, and its economic model.

## 1. Core Functionality

### Activity Tracking
Actifit tracks user activity (primarily steps) through multiple integrated sources:
*   **Phone Sensors:** Uses the device's built-in accelerometer and pedometer sensors via a background service (`ActivityMonitorService`).
*   **Health Connect (Android):** A central integration point for Android devices to sync data from other fitness apps.
*   **Fitbit:** Direct synchronization with Fitbit accounts.

### The Daily Report Card (Post Wizard)
To earn rewards, users must submit a daily activity report.
*   **Minimum Requirements:** Typically 5,000 activity counts (steps) and a text description of at least 30 words.
*   **Post Content:** Users can add photos ("Acti Pics"), specify activity types (Walking, Running, Cycling, etc.), and include custom tags.
*   **Multi-Chain Publishing:** Reports are published as posts on the Hive and Blurt blockchains.

### Rewards System
Actifit employs a dual-reward mechanism:
1.  **AFIT Tokens:** Actifit's native utility token, rewarded proportionally to the activity count and user rank.
2.  **Blockchain Upvotes:** Successful reports receive upvotes from the `@actifit` account on Hive/Blurt, providing liquid cryptocurrency (HIVE/HBD or BLURT).
3.  **Partner Tokens:** Occasionally, posts earn additional tokens like SPORTS, LEO, or others depending on community partnerships.

---

## 2. User Rank System

The **User Rank** is a gamified metric (0–100) that acts as a multiplier for a user's daily rewards and determines the value of the upvote they receive.

### Basis of Calculation
The rank is a weighted average of five primary components:

| Component | Weight | Description |
| :--- | :--- | :--- |
| **Delegated HP** | High | Hive Power (HP) delegated to `@actifit`. This "fuels" the pool of rewards. |
| **AFIT Holdings** | Medium-High | Total AFIT tokens held in the user's wallet (in-app or on-chain). Incentivizes holding over selling. |
| **AFITX Ownership** | Very High | Ownership of AFITX (Governance Token). Even small amounts provide significant rank boosts. |
| **Recent Activity** | Medium | A "rolling" metric based on activity over the last 10–30 days. Encourages daily consistency. |
| **Post Count** | Low-Medium | Total number of rewarded activity reports published over the lifetime of the account. |

### Rank Decay and Maintenance
*   **Consistency is Key:** If a user stops posting daily, their "Recent Activity" component will gradually decrease, leading to **Rank Decay**.
*   **Inactivity:** Long-term inactivity can significantly lower a user's rank, requiring consistent posting to rebuild.

---

## 3. Workout Wizard and AI Generation

The **Workout Wizard** is a sophisticated feature that allows users to generate and manage personalized fitness plans.

### AI-Powered Generation
Users can generate custom workout plans using Actifit's integrated AI (Gemini). The AI considers several factors to create a tailored plan:
*   **Fitness Goals:** Weight loss, muscle gain, endurance, etc.
*   **Experience Level:** Beginner, Intermediate, or Advanced.
*   **Time Commitment:** Weekly time and daily frequency.
*   **Equipment Availability:** Full gym, home equipment, or no equipment.
*   **Physical Limitations:** Specific injuries or areas to avoid.

### Workout Management
*   **Saved Workouts:** Users can save their generated or custom-created plans for easy access.
*   **Editing and Deletion:** Full control over managing the library of fitness routines.
*   **Exercise Database:** Plans are composed of detailed exercises including instructions, target muscle groups, and instructional images.

### Economic Integration
*   **AFIT Fee:** Generating a new AI-powered workout plan requires a small fee in AFIT tokens, which is broadcasted as a transaction to the Hive blockchain.

---

## 4. Actifit Market and Gadgets

Users can spend their earned AFIT tokens in the **Actifit Market** to purchase virtual "Gadgets" that provide various boosts.

### Gadget Categories:
*   **Earnings Boosters:** Items like "Running Shoes" or "Water Bottles" that increase the amount of AFIT earned per post for a fixed duration.
*   **Rank Enhancers:** Items like "Rank Bag" or "Friend Ranker" that provide a temporary or permanent boost to the User Rank.
*   **Consumables:** Single-use items like "Protein Shakes" that might offer immediate reward spikes.

---

## 5. Social and Governance Elements

*   **Social Feed:** Direct in-app access to fitness reports from the global community, allowing for upvoting, commenting, and following.
*   **Delegation Rewards:** Users who delegate HP to Actifit but don't necessarily post every day still earn daily AFIT tokens and a share of the beneficiary rewards from all Actifit posts.
*   **Moderation:** A team of moderators reviews posts to ensure quality and prevent "gaming" of the system (e.g., fraudulent step counts).

---

## 6. Recent Technical Enhancements

Based on recent development cycles, the Actifit Android app has integrated:
*   **AI Insights (Gemini):** Personalized fitness analysis, content translation, and workout generation.
*   **Advanced UI:** Horizontal image carousels for reports, enhanced navigation indicators, and branding-consistent placeholders.
*   **Resilience:** Recursive Hive RPC fallback mechanisms to ensure high availability during node outages.
*   **Android 14+ Support:** Rigorous foreground service management and Health Connect permission handling.
