# Actifit Posting Screen Deep Dive

The `PostSteemitActivity` (located at `app/src/main/java/io/actifit/fitnesstracker/actifitfitnesstracker/PostSteemitActivity.java`) is the primary interface for users to publish their daily activity reports to the Hive and Steem blockchains. It is designed to be a comprehensive form that captures activity data, multimedia, and user-generated content.

## 1. Core Post Information
*   **Post Title:** Automatically pre-filled with a default title and the current date (e.g., "My Daily Actifit Report: March 10 2026"). Users can manually edit this title.
*   **Report Date Selection:** Users can choose to post a report for "Today" or "Yesterday". The "Yesterday" option is automatically disabled if a report for that date has already been published, ensuring the "one post per day" rule.
*   **Step Count Tracking:**
    *   **Auto-Fetch:** Automatically fetches the step count from the local database for the selected date.
    *   **Manual Sync:** Users can manually sync data from **Fitbit** or **Health Connect** if they use those platforms for tracking.
    *   **Validation:** The app enforces a minimum step count (typically 5,000 steps) for reward eligibility, though users can choose to post with fewer steps after confirming a warning dialog.

## 2. Activity & Health Metrics
*   **Activity Type Selector:** A multi-selection spinner where users must select at least one type of activity performed (e.g., Walking, Running, Gym, House Chores).
*   **Body Measurements (Optional):** Users can record their physical metrics, which are appended to the post's metadata:
    *   Height, Weight, Body Fat percentage.
    *   Waist, Chest, and Thigh measurements.
    *   **Unit Support:** Automatically switches between Metric (cm/kg) and Imperial (in/lb) units based on user settings.

## 3. Content Creation & Multimedia
*   **Markdown Editor:** A dedicated `EditText` for writing the report body. 
    *   **Draft Persistence:** The editor automatically saves the current text to `SharedPreferences` to prevent data loss in case the app closes.
    *   **Character Count:** Real-time tracking of the character count. A minimum of 100 characters is required to post.
    *   **Expanded Mode:** A dedicated "Expand" button (\uf065) toggles a full-screen editor mode, hiding other form fields and making the button bar "sticky" at the top for a focused writing experience.
*   **Rich Text Preview:** A real-time Markdown preview (rendered using the `Markwon` library) appears below the editor, showing how the post will look on the blockchain.
*   **Image Upload:** Users can select images from their gallery. The app scales the images and uploads them (via `Utils.uploadFile`) before inserting the Markdown link into the editor.
*   **Video Integration:** 
    *   **3Speak Support:** Users can select videos previously uploaded to 3Speak or initiate a new upload.
    *   **Metadata Integration:** 3Speak video metadata (thumbnail, permlink, duration) is automatically included in the post's `json_metadata` and beneficiaries.

## 4. Post Submission & Blockchain Logic
*   **Submit Button:** A floating "Post" button initiates the submission process. It features a continuous pulse animation once reward milestones are met.
*   **Data Packaging:** The app compiles a complex JSON object containing:
    *   **Author & Posting Key:** Security credentials (stored locally).
    *   **Detailed Activity Map:** A pipe-delimited string of 15-minute step intervals to verify activity authenticity.
    *   **App Metadata:** App version, device type (Android), and tracking source (Sensors, Fitbit, or Health Connect).
    *   **Timezone & Security Params:** GMT offset and unique user identifiers.
    *   **Charity Integration:** If a user has selected a charity in settings, the post automatically includes the charity as a beneficiary.
*   **API Communication:** The post is sent to the Actifit API (`api_url_new`), which handles the actual blockchain broadcasting.
*   **Post-Submission Actions:**
    *   Upon success, the local draft is cleared, and the "last post date" is updated.
    *   Users are presented with options to **View Post** (via Custom Tabs) or **Share Post** (to other social apps).

## 5. User Guidance & Validation
*   **Visual Points System:** The form uses numbered circles (❶ to ❽) to guide the user through the required steps. These circles change color (Red to Green) as requirements are met.
*   **Error Handling:** Extensive validation for:
    *   Missing username/posting key.
    *   Duplicate posts for the same day.
    *   Insufficient character count or step count.
    *   Missing activity types.
*   **Informational Popups:** Help icons provide context on specific requirements, such as the minimum content length.

## 6. Technical Implementation Details
*   **Background Processing:** Post submission and image processing are handled via `AsyncTask` and `ExecutorService` to keep the UI responsive.
*   **Permissions:** Dynamically requests storage permissions for image and video access.
*   **Custom View Logic:** Uses `TransitionManager` and `ConstraintSet` for smooth UI transitions when expanding the editor or toggling date options.
