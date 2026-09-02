package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.browser.customtabs.CustomTabsIntent;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.airbnb.lottie.LottieAnimationView;
import com.airbnb.lottie.LottieDrawable;
import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import de.hdodenhof.circleimageview.CircleImageView;

/**
 * The "Living Fitness Identity" — a native profile screen replacing the web CustomTab.
 *
 * Centrepiece is the {@link AuraView} companion: an energy aura whose arc = today's goal
 * progress and whose tier (colour/glow/pulse) reflects the user's vitality — driven by the
 * streak for the logged-in user, and by rank for other users.
 *
 * Launch with an optional string extra {@link #EXTRA_USERNAME}; when absent (or equal to the
 * logged-in user) it renders the viewer's own profile from local step data.
 */
public class ProfileActivity extends BaseActivity {

    public static final String EXTRA_USERNAME = "username";

    private static final int DAILY_GOAL = 10000;
    private static final int ACTIVE_THRESHOLD = 5000;
    // activity-ring goals + derivation factors (distance/calories derived from steps for now)
    private static final float DIST_GOAL_KM = 8f;
    private static final float CAL_GOAL = 500f;
    private static final float STRIDE_M = 0.762f;      // avg stride, steps → distance
    private static final float KCAL_PER_STEP = 0.04f;  // rough steps → calories

    private AuraView auraView;
    private LottieAnimationView profileAnimal;
    private CircleImageView avatar;
    private TextView tierLabel, usernameTv, subtitleTv, metricsLegend;
    private TextView tile1Value, tile1Label, tile2Value, tile2Label, tile3Value, tile3Label;
    private LinearLayout recentContainer;
    private TextView recentEmpty;
    private Button shareButton;

    private RequestQueue queue;
    private String username;
    private boolean isSelf;
    private SharedPreferences prefs;

    private TextView companionHint;
    private int companionIndex = 0;

    // aura state assembled from possibly-async sources
    private float auraStepsFrac = 0f;
    private float auraDistFrac = 0f;
    private float auraCalFrac = 0f;
    private int auraLevel = 0;
    private boolean auraWilting = false;

    // kept for the share card
    private int shareSteps = 0;
    private String shareRank = "0";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        auraView = findViewById(R.id.aura_view);
        profileAnimal = findViewById(R.id.profile_animal);
        avatar = findViewById(R.id.profile_avatar);
        // the animated animal is the hero in the ring centre, so the canvas emoji is off here;
        // the avatar becomes a small identity badge nudged to the lower-right of the rings
        auraView.setShowAnimal(false);
        float badgeOffset = 74f * getResources().getDisplayMetrics().density;
        avatar.setTranslationX(badgeOffset);
        avatar.setTranslationY(badgeOffset);
        tierLabel = findViewById(R.id.tier_label);
        usernameTv = findViewById(R.id.profile_username);
        subtitleTv = findViewById(R.id.profile_subtitle);
        metricsLegend = findViewById(R.id.metrics_legend);
        tile1Value = findViewById(R.id.tile1_value);
        tile1Label = findViewById(R.id.tile1_label);
        tile2Value = findViewById(R.id.tile2_value);
        tile2Label = findViewById(R.id.tile2_label);
        tile3Value = findViewById(R.id.tile3_value);
        tile3Label = findViewById(R.id.tile3_label);
        recentContainer = findViewById(R.id.recent_activity_container);
        recentEmpty = findViewById(R.id.recent_activity_empty);
        shareButton = findViewById(R.id.btn_share_card);
        companionHint = findViewById(R.id.companion_hint);

        findViewById(R.id.back_button).setOnClickListener(v -> finish());

        prefs = getSharedPreferences("actifitSets", MODE_PRIVATE);
        String loggedInUser = prefs.getString("actifitUser", "");

        username = getIntent().getStringExtra(EXTRA_USERNAME);
        if (username == null || username.trim().isEmpty()) {
            username = loggedInUser;
        }
        username = username.trim().toLowerCase().replace("@", "");
        isSelf = !username.isEmpty() && username.equalsIgnoreCase(loggedInUser);

        // the logged-in user picks and keeps their companion; other users get a stable
        // element derived from their name so their identity is consistent across sessions
        companionIndex = CompanionUtil.resolveCompanion(prefs, username, isSelf);
        auraView.setCompanion(companionIndex);

        usernameTv.setText("@" + username);
        loadAvatar();

        queue = Volley.newRequestQueue(this);

        if (isSelf) {
            bindSelfStats();
            shareButton.setVisibility(View.VISIBLE);
            shareButton.setOnClickListener(v -> shareCard());
            companionHint.setVisibility(View.VISIBLE);
            View.OnClickListener pick = v -> showCompanionPicker();
            auraView.setOnClickListener(pick);
            companionHint.setOnClickListener(pick);
        } else {
            setupFriendButton();
        }

        updateAura();
        fetchRank();
        fetchRecentActivity();
    }

    private void showCompanionPicker() {
        int count = AuraView.companionCount();
        CharSequence[] items = new CharSequence[count];
        for (int i = 0; i < count; i++) {
            items[i] = AuraView.companionEmoji(i) + "  " + AuraView.companionName(i);
        }
        new androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(R.string.profile_companion_picker_title)
                .setSingleChoiceItems(items, companionIndex, (dialog, which) -> {
                    companionIndex = which;
                    prefs.edit().putInt(CompanionUtil.PREF_COMPANION, which).apply();
                    auraView.setCompanion(which);
                    updateAura();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel_button, null)
                .show();
    }

    private void loadAvatar() {
        String url = getString(R.string.hive_image_host_url).replace("USERNAME", username);
        Glide.with(this)
                .load(url)
                .placeholder(R.drawable.default_pic)
                .error(R.drawable.default_pic)
                .into(avatar);
    }

    private void updateAura() {
        if (auraView != null) {
            auraView.setActivityRings(auraStepsFrac, auraDistFrac, auraCalFrac, auraLevel, auraWilting);
        }
        updateCompanionAnimal();
        tierLabel.setText(getString(R.string.profile_tier_element,
                AuraView.companionEmoji(companionIndex),
                AuraView.companionName(companionIndex),
                AuraView.tierName(auraLevel)));
        tierLabel.setTextColor(AuraView.companionColor(companionIndex));
    }

    // real Health Connect distance (km) for today, or -1 to derive. Only for the logged-in
    // user while in HC mode, with fresh (today's) metrics cached by TrackingManager.
    private float realHcDistanceKm() {
        if (!isSelf || !hcMetricsFresh()) return -1f;
        float dM = prefs.getFloat("hcTodayDistanceM", -1f);
        return dM >= 0 ? dM / 1000f : -1f;
    }

    private float realHcKcal() {
        if (!isSelf || !hcMetricsFresh()) return -1f;
        return prefs.getFloat("hcTodayKcal", -1f);
    }

    private boolean hcMetricsFresh() {
        boolean hcMode = getString(R.string.health_connect_tracking_ntt)
                .equals(prefs.getString("dataTrackingSystem", ""));
        String today = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH).format(new Date());
        return hcMode && today.equals(prefs.getString("hcMetricsDate", ""));
    }

    // compute the three activity-ring fractions + legend. Real HC distance/calories are used
    // when provided (>= 0); otherwise they're derived from steps.
    private void computeMetrics(int steps, float realDistanceKm, float realKcal) {
        float distKm = (realDistanceKm >= 0) ? realDistanceKm : steps * STRIDE_M / 1000f;
        float cal = (realKcal >= 0) ? realKcal : steps * KCAL_PER_STEP;
        auraStepsFrac = steps / (float) Utils.getDailyStepGoal(this);
        auraDistFrac = distKm / DIST_GOAL_KM;
        auraCalFrac = cal / CAL_GOAL;
        // honor the user's measurement system (metric km / US miles) for the legend distance
        boolean metric = Utils.isMetricSystem(this);
        float distDisplay = metric ? distKm : distKm / 1.609344f;
        String distToken = String.format(Locale.getDefault(), "%.1f %s", distDisplay, metric ? "km" : "mi");
        metricsLegend.setText(getString(R.string.profile_metrics_legend,
                compact(steps),
                distToken,
                String.valueOf(Math.round(cal))));
    }

    // drive the animated hero animal: swap the Lottie on companion change, grow it with tier,
    // and slow it to a tired crawl when wilting
    private void updateCompanionAnimal() {
        if (profileAnimal == null) return;
        String asset = AuraView.companionLottieAsset(companionIndex);
        if (!asset.equals(profileAnimal.getTag())) {
            profileAnimal.setTag(asset);
            profileAnimal.setAnimation(asset);
            profileAnimal.setRepeatCount(LottieDrawable.INFINITE);
            profileAnimal.playAnimation();
        }
        float scale = 0.75f + 0.07f * auraLevel; // grows Couch → Champion
        profileAnimal.setScaleX(scale);
        profileAnimal.setScaleY(scale);
        profileAnimal.setSpeed(auraWilting ? 0.3f : 1f);
        profileAnimal.setAlpha(auraWilting ? 0.55f : 1f);
    }

    // compact large numbers so tiles never overflow: 15,322 · 392K · 1.2M
    private String compact(long n) {
        if (n < 100000) return NumberFormat.getInstance().format(n);
        if (n < 1000000) return Math.round(n / 1000.0) + "K";
        return String.format(Locale.getDefault(), "%.1fM", n / 1000000.0);
    }

    // ── Self (logged-in) profile from local step data ────────────────────────────

    private void bindSelfStats() {
        StepsDBHelper db = new StepsDBHelper(this);
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);

        int todaySteps = Math.max(0, db.fetchStepCountByDate(sdf.format(new Date())));
        int streak = computeStreak(db, sdf);

        Map<String, Integer> byDate = collectAllDailySteps(db);
        long lifetime = 0;
        int best = 0;
        for (int steps : byDate.values()) {
            lifetime += steps;
            if (steps > best) best = steps;
        }

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        auraWilting = CompanionUtil.isWilting(streak, todaySteps, hour);

        shareSteps = todaySteps;
        computeMetrics(todaySteps, realHcDistanceKm(), realHcKcal());
        auraLevel = CompanionUtil.levelFromStreak(streak);
        updateAura();

        // when the streak is at risk, turn the companion hint into a loss-aversion nudge
        if (auraWilting) {
            int remaining = Math.max(0, CompanionUtil.ACTIVE_THRESHOLD - todaySteps);
            companionHint.setText(getString(R.string.profile_streak_at_risk, compact(remaining), streak));
            companionHint.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.md_theme_warning));
        }

        tile1Value.setText(compact(todaySteps));
        tile1Label.setText(R.string.profile_stat_today);
        tile2Value.setText(String.valueOf(streak));
        tile2Label.setText(R.string.profile_stat_streak);
        tile3Value.setText(compact(lifetime));
        tile3Label.setText(R.string.profile_stat_lifetime);
    }

    // replicates MainActivity.updateStreakStrip streak logic (>= 5000 steps, today in progress)
    private int computeStreak(StepsDBHelper db, SimpleDateFormat sdf) {
        int todaySteps = db.fetchStepCountByDate(sdf.format(new Date()));
        int startDaysBack = (todaySteps >= ACTIVE_THRESHOLD) ? 0 : 1;
        int streak = 0;
        for (int daysBack = startDaysBack; daysBack <= 366; daysBack++) {
            Calendar cal = Calendar.getInstance();
            cal.add(Calendar.DATE, -daysBack);
            int steps = db.fetchStepCountByDate(sdf.format(cal.getTime()));
            if (steps >= ACTIVE_THRESHOLD) {
                streak++;
            } else {
                break;
            }
        }
        return streak;
    }

    // merge the mode-specific summary tables, keeping the max per date to avoid double counting
    private Map<String, Integer> collectAllDailySteps(StepsDBHelper db) {
        Map<String, Integer> byDate = new HashMap<>();
        mergeEntries(byDate, db.readStepsEntries());
        mergeEntries(byDate, db.readHCStepsEntries());
        mergeEntries(byDate, db.readFitbitStepsEntries());
        return byDate;
    }

    private void mergeEntries(Map<String, Integer> byDate, ArrayList<DateStepsModel> entries) {
        if (entries == null) return;
        for (DateStepsModel m : entries) {
            if (m == null || m.mDate == null) continue;
            Integer existing = byDate.get(m.mDate);
            if (existing == null || m.mStepCount > existing) {
                byDate.put(m.mDate, m.mStepCount);
            }
        }
    }

    // ── Rank (both self and other users) ─────────────────────────────────────────

    private void fetchRank() {
        String url = Utils.apiUrl(this) + getString(R.string.user_rank_api_url) + username;
        JsonObjectRequest request = new JsonObjectRequest(Request.Method.GET, url, null,
                response -> {
                    String rank = response.optString("user_rank", "0");
                    shareRank = rank;
                    subtitleTv.setText(getString(R.string.profile_subtitle_rank, rank));
                    if (!isSelf) {
                        // other users: the aura tier comes from rank
                        auraLevel = CompanionUtil.levelFromRank(rank);
                        updateAura();
                        tile2Value.setText(rank);
                        tile2Label.setText(R.string.profile_stat_rank);
                    }
                },
                error -> { /* leave subtitle blank on failure */ });
        queue.add(request);
    }

    // ── Recent activity list (both) + other-user aura fill ───────────────────────

    private void fetchRecentActivity() {
        String url = Utils.apiUrl(this) + getString(R.string.tracked_activity_api_url) + username;
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null,
                this::bindRecentActivity,
                error -> showNoActivity());
        queue.add(request);
    }

    private void bindRecentActivity(JSONArray posts) {
        recentContainer.removeAllViews();
        if (posts == null || posts.length() == 0) {
            showNoActivity();
            if (!isSelf) {
                tile3Value.setText("0");
                tile3Label.setText(R.string.profile_stat_posts);
            }
            return;
        }
        recentEmpty.setVisibility(View.GONE);

        NumberFormat nf = NumberFormat.getInstance();
        int rows = Math.min(posts.length(), 12);
        int latestSteps = -1;
        Map<String, Integer> typeCounts = new HashMap<>();

        for (int i = 0; i < rows; i++) {
            JSONObject post = posts.optJSONObject(i);
            if (post == null) continue;
            JSONObject meta = post.optJSONObject("json_metadata");

            int steps = firstIntFromMeta(meta, "step_count");
            String type = firstStringFromMeta(meta, "activity_type");
            String dateStr = firstStringFromMeta(meta, "activityDate");
            String author = post.optString("author", username);
            String permlink = post.optString("permlink", "");

            if (latestSteps < 0 && steps >= 0) latestSteps = steps;
            if (type != null && !type.isEmpty()) {
                Integer c = typeCounts.get(type);
                typeCounts.put(type, c == null ? 1 : c + 1);
            }

            addActivityRow(author, permlink, type, dateStr, steps, nf);
        }

        // pick the spirit animal from the user's most-reported activity type
        applySportCompanion(dominantKey(typeCounts));

        if (!isSelf) {
            // other users: aura arc fill comes from their latest report
            if (latestSteps >= 0) {
                computeMetrics(latestSteps, -1f, -1f);
                updateAura();
                tile1Value.setText(compact(latestSteps));
                tile1Label.setText(R.string.profile_stat_latest);
            }
            tile3Value.setText(String.valueOf(posts.length()));
            tile3Label.setText(R.string.profile_stat_posts);
        }
    }

    private void addActivityRow(String author, String permlink, String type, String dateStr,
                                int steps, NumberFormat nf) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        int padV = dp(12);
        row.setPadding(0, padV, 0, padV);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackgroundResource(outValueSelectableBackground());

        TextView left = new TextView(this);
        LinearLayout.LayoutParams leftLp =
                new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        left.setLayoutParams(leftLp);
        String typeLabel = (type == null || type.isEmpty()) ? getString(R.string.profile_stat_today) : type;
        left.setText(getString(R.string.profile_activity_row, typeLabel, formatDate(dateStr)));
        left.setTextColor(getColorCompat(R.color.md_theme_onSurface));

        TextView right = new TextView(this);
        right.setText(steps >= 0 ? nf.format(steps) : "—");
        right.setTextColor(getColorCompat(R.color.md_theme_primary));
        right.getPaint().setFakeBoldText(true);

        row.addView(left);
        row.addView(right);

        if (!permlink.isEmpty()) {
            String postUrl = MainActivity.ACTIFIT_CORE_URL + "/@" + author + "/" + permlink;
            row.setOnClickListener(v -> openUrl(postUrl));
        }

        recentContainer.addView(row);

        View sep = new View(this);
        sep.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        sep.setBackgroundColor(getColorCompat(R.color.md_theme_separator));
        recentContainer.addView(sep);
    }

    private String dominantKey(Map<String, Integer> counts) {
        String best = null;
        int bestVal = -1;
        for (Map.Entry<String, Integer> e : counts.entrySet()) {
            if (e.getValue() > bestVal) {
                bestVal = e.getValue();
                best = e.getKey();
            }
        }
        return best;
    }

    // set the companion from the user's dominant sport; the logged-in user's explicit pick wins
    private void applySportCompanion(String dominantActivity) {
        if (dominantActivity == null) return;
        int sportIdx = CompanionUtil.animalForActivity(dominantActivity);
        if (isSelf) {
            // cache for the dashboard header; only change the shown animal if not explicitly picked
            prefs.edit().putInt(CompanionUtil.PREF_COMPANION_AUTO, sportIdx).apply();
            if (prefs.contains(CompanionUtil.PREF_COMPANION)) return;
        }
        companionIndex = sportIdx;
        auraView.setCompanion(companionIndex);
        updateAura();
    }

    private void showNoActivity() {
        if (recentContainer.getChildCount() == 0) {
            recentEmpty.setVisibility(View.VISIBLE);
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────────────

    private void shareCard() {
        // Only reachable on the self profile (share button is gated by isSelf), so local
        // weekly steps + the logged-in AFIT balance are correct — pass them for parity with
        // the dashboard share (enables the Today / This Week toggle here too).
        Intent intent = new Intent(this, ShareAchievementActivity.class);
        intent.putExtra("steps", String.valueOf(shareSteps));
        intent.putExtra("weekly_steps", String.valueOf(new StepsDBHelper(this).fetchWeeklyStepCount()));
        intent.putExtra("rank", shareRank);
        intent.putExtra("username", username);
        if (MainActivity.userFullBalance != null) {
            intent.putExtra("afit", String.format(Locale.getDefault(), "%.2f", MainActivity.userFullBalance));
        }
        startActivity(intent);
    }

    // ── Friend action button (other users only) ──────────────────────────────────

    private void setupFriendButton() {
        final Button friendBtn = findViewById(R.id.btn_friend_action);
        final String me = prefs.getString("actifitUser", "");
        if (me.isEmpty() || username.equalsIgnoreCase(me)) return;
        friendBtn.setVisibility(View.VISIBLE);
        friendBtn.setEnabled(false);
        friendBtn.setText(R.string.friends_action_loading);
        refreshFriendState(friendBtn, me);
    }

    private void refreshFriendState(final Button friendBtn, final String me) {
        friendBtn.setEnabled(false);
        final boolean[] done = {false, false};
        final boolean[] flags = {false, false, false}; // isFriend, sent, received
        final Runnable apply = () -> {
            if (done[0] && done[1]) applyFriendState(friendBtn, me, flags[0], flags[1], flags[2]);
        };
        queue.add(new com.android.volley.toolbox.JsonArrayRequest(com.android.volley.Request.Method.GET,
                Utils.apiUrl(this) + "userFriends/" + me, null,
                arr -> {
                    for (int i = 0; i < arr.length(); i++) {
                        try {
                            if (username.equalsIgnoreCase(arr.getJSONObject(i).optString("friend"))) { flags[0] = true; break; }
                        } catch (Exception ignored) {}
                    }
                    done[0] = true; apply.run();
                }, err -> { done[0] = true; apply.run(); }));
        queue.add(new com.android.volley.toolbox.JsonObjectRequest(com.android.volley.Request.Method.GET,
                Utils.apiUrl(this) + "userFriendRequests/" + me, null,
                obj -> {
                    org.json.JSONArray sent = obj.optJSONArray("sent_pending");
                    if (sent != null) for (int i = 0; i < sent.length(); i++) {
                        try { if (username.equalsIgnoreCase(sent.getJSONObject(i).optString("target"))) { flags[1] = true; break; } } catch (Exception ignored) {}
                    }
                    org.json.JSONArray rec = obj.optJSONArray("received_pending");
                    if (rec != null) for (int i = 0; i < rec.length(); i++) {
                        try { if (username.equalsIgnoreCase(rec.getJSONObject(i).optString("initiator"))) { flags[2] = true; break; } } catch (Exception ignored) {}
                    }
                    done[1] = true; apply.run();
                }, err -> { done[1] = true; apply.run(); }));
    }

    private void applyFriendState(final Button friendBtn, final String me,
                                  boolean isFriend, boolean sent, boolean received) {
        friendBtn.setEnabled(true);
        final FriendsApi.Callback cb = (success, message) -> {
            android.widget.Toast.makeText(this,
                    getString(success ? R.string.friends_action_done : R.string.friends_action_failed),
                    android.widget.Toast.LENGTH_SHORT).show();
            refreshFriendState(friendBtn, me);
        };
        // Mirror the list rows' per-state colour: green to accept, muted grey for
        // pending/already-friends, primary red only for the "Add Friend" call to action.
        if (isFriend) {
            friendBtn.setText(R.string.profile_friend_friends);
            tintFriendButton(friendBtn, R.color.md_theme_textSecondary);
            friendBtn.setOnClickListener(v -> new androidx.appcompat.app.AlertDialog.Builder(this)
                    .setMessage(getString(R.string.friends_confirm_unfriend, username))
                    .setPositiveButton(R.string.friends_action_unfriend, (d, w) -> { friendBtn.setEnabled(false); FriendsApi.unfriend(this, this, me, username, cb); })
                    .setNegativeButton(R.string.close_button, null).show());
        } else if (received) {
            friendBtn.setText(R.string.profile_friend_accept);
            tintFriendButton(friendBtn, R.color.actifitDarkGreen);
            friendBtn.setOnClickListener(v -> { friendBtn.setEnabled(false); FriendsApi.acceptFriend(this, this, me, username, cb); });
        } else if (sent) {
            friendBtn.setText(R.string.profile_friend_pending);
            tintFriendButton(friendBtn, R.color.md_theme_textSecondary);
            friendBtn.setOnClickListener(v -> { friendBtn.setEnabled(false); FriendsApi.cancelRequest(this, this, me, username, cb); });
        } else {
            friendBtn.setText(R.string.profile_friend_add);
            tintFriendButton(friendBtn, R.color.md_theme_primary);
            friendBtn.setOnClickListener(v -> { friendBtn.setEnabled(false); FriendsApi.addFriend(this, this, me, username, cb); });
        }
    }

    private void tintFriendButton(Button friendBtn, int colorRes) {
        friendBtn.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(getResources().getColor(colorRes)));
    }

    private void openUrl(String url) {
        try {
            CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
            customTabsIntent.launchUrl(this, Uri.parse(url));
        } catch (Exception e) {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        }
    }

    private int firstIntFromMeta(JSONObject meta, String key) {
        String s = firstStringFromMeta(meta, key);
        if (s == null) return -1;
        try {
            return (int) Float.parseFloat(s);
        } catch (Exception e) {
            return -1;
        }
    }

    private String firstStringFromMeta(JSONObject meta, String key) {
        if (meta == null) return null;
        try {
            Object val = meta.opt(key);
            if (val instanceof JSONArray) {
                JSONArray arr = (JSONArray) val;
                return arr.length() > 0 ? arr.optString(0) : null;
            }
            if (val != null) {
                return val.toString();
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    private String formatDate(String yyyymmdd) {
        if (yyyymmdd == null || yyyymmdd.length() != 8) return yyyymmdd == null ? "" : yyyymmdd;
        try {
            SimpleDateFormat in = new SimpleDateFormat("yyyyMMdd", Locale.ENGLISH);
            SimpleDateFormat out = new SimpleDateFormat("MMM d, yyyy", Locale.getDefault());
            return out.format(in.parse(yyyymmdd));
        } catch (Exception e) {
            return yyyymmdd;
        }
    }

    private int getColorCompat(int resId) {
        return androidx.core.content.ContextCompat.getColor(this, resId);
    }

    private int outValueSelectableBackground() {
        android.util.TypedValue outValue = new android.util.TypedValue();
        getTheme().resolveAttribute(android.R.attr.selectableItemBackground, outValue, true);
        return outValue.resourceId;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
