package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.android.volley.DefaultRetryPolicy;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;

import org.json.JSONArray;

import java.util.ArrayList;

/**
 * Friends screen: a segmented toggle (Friends | Requests | Suggested) over a single ListView.
 * Reads are public (`userFriends`, `userFriendRequests`; Suggested is activity-ordered from
 * `recentVerifiedPosts` with a Hive #actifit fallback, enriched per row); writes via
 * {@link FriendsApi}.
 */
public class FriendsActivity extends BaseActivity {

    /** Optional int extra: which tab to open on launch (used by notification deep-links). */
    public static final String EXTRA_INITIAL_TAB = "initial_tab";
    public static final int TAB_FRIENDS = 0;
    public static final int TAB_REQUESTS = 1;
    public static final int TAB_SUGGESTED = 2;

    private ListView listView;
    private TextView tabFriends, tabRequests, tabSuggested, emptyState;
    private View progress;
    private View loadMoreFooter;

    private final ArrayList<FriendModel> friendsData = new ArrayList<>();
    private final ArrayList<FriendModel> requestsData = new ArrayList<>();
    private final ArrayList<FriendModel> suggestedData = new ArrayList<>();
    private final ArrayList<FriendModel> current = new ArrayList<>();
    private FriendEntryAdapter adapter;

    private int currentTab = TAB_FRIENDS;
    private boolean dataLoaded = false;
    private boolean suggestedLoaded = false;
    private int pendingReceivedCount = 0;
    private String me;

    private RequestQueue queue;
    // Bumped on every loadData(); responses from a superseded load are ignored so an
    // onResume-triggered reload can't interleave with an in-flight one and duplicate rows.
    private int loadGeneration = 0;
    // Separate generation for the lazily-loaded suggestions request.
    private int suggestGeneration = 0;
    // Per-source loading flags so the shared spinner reflects whichever tab is showing.
    private boolean friendsLoading = false;
    private boolean suggestLoading = false;

    // Suggestions pagination (append more #actifit authors from Hive as the user scrolls).
    private final java.util.HashSet<String> suggestSeen = new java.util.HashSet<>();
    private String hiveCursorAuthor;
    private String hiveCursorPermlink;
    private boolean suggestLoadingMore = false;
    private boolean suggestNoMore = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);

        listView = findViewById(R.id.friends_list);
        tabFriends = findViewById(R.id.friends_tab_friends);
        tabRequests = findViewById(R.id.friends_tab_requests);
        tabSuggested = findViewById(R.id.friends_tab_suggested);
        emptyState = findViewById(R.id.friends_empty_state);
        progress = findViewById(R.id.friends_progress);
        findViewById(R.id.friends_back_button).setOnClickListener(v -> finish());

        me = getSharedPreferences("actifitSets", MODE_PRIVATE).getString("actifitUser", "");

        AutoCompleteTextView searchInput = findViewById(R.id.friends_search_input);
        // Default node first, then the app's RPC fallbacks (de-duped), so suggestions survive a
        // node outage without querying the same URL twice.
        String[] rpcNodes = getResources().getStringArray(R.array.hive_rpc_nodes);
        java.util.LinkedHashSet<String> nodes = new java.util.LinkedHashSet<>();
        nodes.add(getString(R.string.hive_default_node));
        for (String n : rpcNodes) if (n != null && !n.isEmpty()) nodes.add(n);
        searchInput.setAdapter(new FriendAccountAdapter(this, Utils.apiUrl(this),
                nodes.toArray(new String[0]), me));
        // Picking a suggestion jumps straight to that profile.
        searchInput.setOnItemClickListener((parent, view, position, id) -> submitSearch(searchInput));
        findViewById(R.id.friends_search_btn).setOnClickListener(v -> submitSearch(searchInput));
        searchInput.setOnEditorActionListener((tv, actionId, ev) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                submitSearch(searchInput);
                return true;
            }
            return false;
        });

        // "Loading more…" footer for the Suggested tab (added before setAdapter as ListView requires).
        loadMoreFooter = getLayoutInflater().inflate(R.layout.friends_load_more, listView, false);
        listView.addFooterView(loadMoreFooter, null, false);

        adapter = new FriendEntryAdapter(this, current, this::onAction);
        listView.setAdapter(adapter);

        // Infinite scroll for the Suggested tab: fetch the next page as the user nears the bottom.
        listView.setOnScrollListener(new android.widget.AbsListView.OnScrollListener() {
            @Override public void onScrollStateChanged(android.widget.AbsListView view, int state) {}
            @Override public void onScroll(android.widget.AbsListView view, int firstVisible,
                                           int visibleCount, int totalCount) {
                if (currentTab == TAB_SUGGESTED && totalCount > 0
                        && firstVisible + visibleCount >= totalCount - 2) {
                    loadMoreSuggestions();
                }
            }
        });

        tabFriends.setOnClickListener(v -> selectTab(TAB_FRIENDS));
        tabRequests.setOnClickListener(v -> selectTab(TAB_REQUESTS));
        tabSuggested.setOnClickListener(v -> selectTab(TAB_SUGGESTED));

        int initialTab = getIntent().getIntExtra(EXTRA_INITIAL_TAB, TAB_FRIENDS);
        selectTab(initialTab);
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh after returning from a profile where a request may have been sent/accepted.
        // Suggestions are (re)loaded from finishLoad() once the friend/request lists are fresh,
        // so the exclude-set is never built from empty lists.
        if (dataLoaded) loadData();
    }

    private void selectTab(int tab) {
        currentTab = tab;
        int white = getResources().getColor(R.color.colorWhite);
        int idleText = getResources().getColor(R.color.md_theme_textSecondary);
        // Selected segment = rounded red pill thumb; idle = transparent (shows the track).
        styleTab(tabFriends, tab == TAB_FRIENDS, white, idleText);
        styleTab(tabRequests, tab == TAB_REQUESTS, white, idleText);
        styleTab(tabSuggested, tab == TAB_SUGGESTED, white, idleText);
        // Suggestions are fetched the first time the tab is opened, but only once the friend/
        // request lists exist (so they're correctly excluded). While pending, show the spinner.
        if (tab == TAB_SUGGESTED && !suggestedLoaded) {
            suggestLoading = true;
            if (dataLoaded) loadSuggestions();   // else finishLoad() triggers it
        }
        refreshProgress();
        refreshLoadMoreFooter();
        render();
    }

    private void styleTab(TextView tab, boolean selected, int selText, int idleText) {
        tab.setBackgroundResource(selected ? R.drawable.segmented_selected : 0);
        tab.setTextColor(selected ? selText : idleText);
        tab.setSelected(selected);   // exposes the selected state to accessibility services
    }

    /** Spinner reflects whichever source feeds the tab currently on screen. */
    private void refreshProgress() {
        boolean loading = (currentTab == TAB_SUGGESTED) ? suggestLoading : friendsLoading;
        progress.setVisibility(loading ? View.VISIBLE : View.GONE);
    }

    /** The bottom "Loading more…" footer shows only while paging the Suggested tab. */
    private void refreshLoadMoreFooter() {
        if (loadMoreFooter != null) {
            loadMoreFooter.setVisibility(
                    currentTab == TAB_SUGGESTED && suggestLoadingMore ? View.VISIBLE : View.GONE);
        }
    }

    private void render() {
        current.clear();
        switch (currentTab) {
            case TAB_REQUESTS:
                current.addAll(requestsData);
                emptyState.setText(R.string.friends_empty_requests);
                break;
            case TAB_SUGGESTED:
                current.addAll(suggestedData);
                emptyState.setText(R.string.friends_empty_suggested);
                break;
            case TAB_FRIENDS:
            default:
                current.addAll(friendsData);
                emptyState.setText(R.string.friends_empty_friends);
                break;
        }
        adapter.notifyDataSetChanged();
        boolean tabReady = (currentTab == TAB_SUGGESTED) ? suggestedLoaded : dataLoaded;
        boolean showEmpty = tabReady && current.isEmpty();
        emptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
    }

    private void updateRequestsTabLabel() {
        tabRequests.setText(pendingReceivedCount > 0
                ? getString(R.string.friends_tab_requests) + " (" + pendingReceivedCount + ")"
                : getString(R.string.friends_tab_requests));
    }

    private void loadData() {
        friendsLoading = true;
        refreshProgress();
        emptyState.setVisibility(View.GONE);
        friendsData.clear();
        requestsData.clear();
        final boolean[] done = {false, false};
        final int gen = ++loadGeneration;

        if (queue == null) queue = Volley.newRequestQueue(getApplicationContext());

        JsonArrayRequest friendsReq = new JsonArrayRequest(Request.Method.GET,
                Utils.apiUrl(this) + "userFriends/" + me, null,
                arr -> {
                    if (gen != loadGeneration) return;
                    for (int i = 0; i < arr.length(); i++) {
                        try {
                            String u = arr.getJSONObject(i).optString("friend");
                            if (u != null && !u.isEmpty()) {
                                friendsData.add(new FriendModel(u, FriendModel.Type.FRIEND));
                            }
                        } catch (Exception ignored) {}
                    }
                    done[0] = true;
                    finishLoad(done, gen);
                },
                error -> { if (gen != loadGeneration) return; done[0] = true; finishLoad(done, gen); });

        JsonObjectRequest requestsReq = new JsonObjectRequest(Request.Method.GET,
                Utils.apiUrl(this) + "userFriendRequests/" + me, null,
                obj -> {
                    if (gen != loadGeneration) return;
                    ArrayList<FriendModel> received = new ArrayList<>();
                    ArrayList<FriendModel> sent = new ArrayList<>();
                    JSONArray rec = obj.optJSONArray("received_pending");
                    if (rec != null) {
                        for (int i = 0; i < rec.length(); i++) {
                            try {
                                String u = rec.getJSONObject(i).optString("initiator");
                                if (u != null && !u.isEmpty()) received.add(new FriendModel(u, FriendModel.Type.RECEIVED));
                            } catch (Exception ignored) {}
                        }
                    }
                    JSONArray snt = obj.optJSONArray("sent_pending");
                    if (snt != null) {
                        for (int i = 0; i < snt.length(); i++) {
                            try {
                                String u = snt.getJSONObject(i).optString("target");
                                if (u != null && !u.isEmpty()) sent.add(new FriendModel(u, FriendModel.Type.SENT));
                            } catch (Exception ignored) {}
                        }
                    }
                    requestsData.clear();
                    if (!received.isEmpty()) {
                        requestsData.add(FriendModel.header(getString(R.string.friends_received)));
                        requestsData.addAll(received);
                    }
                    if (!sent.isEmpty()) {
                        requestsData.add(FriendModel.header(getString(R.string.friends_sent)));
                        requestsData.addAll(sent);
                    }
                    pendingReceivedCount = received.size();
                    done[1] = true;
                    finishLoad(done, gen);
                },
                error -> { if (gen != loadGeneration) return; done[1] = true; finishLoad(done, gen); });

        friendsReq.setRetryPolicy(new DefaultRetryPolicy(MainActivity.connectTimeout,
                MainActivity.connectMaxRetries, MainActivity.connectSubsequentRetryDelay));
        requestsReq.setRetryPolicy(new DefaultRetryPolicy(MainActivity.connectTimeout,
                MainActivity.connectMaxRetries, MainActivity.connectSubsequentRetryDelay));
        queue.add(friendsReq);
        queue.add(requestsReq);
    }

    private void finishLoad(boolean[] done, int gen) {
        if (gen != loadGeneration) return;      // a newer load has superseded this one
        if (!(done[0] && done[1])) return;
        dataLoaded = true;
        friendsLoading = false;
        updateRequestsTabLabel();
        // The friend/request lists are now fresh: drop any suggestion that became a friend or a
        // pending request, and load suggestions if the tab is open and still needs them.
        pruneSuggestionsAgainstLoaded();
        if (currentTab == TAB_SUGGESTED && !suggestedLoaded) {
            suggestLoading = true;
            loadSuggestions();
        }
        refreshProgress();
        render();
    }

    /** Remove any suggested account that is now a friend or has a pending request either way. */
    private void pruneSuggestionsAgainstLoaded() {
        if (suggestedData.isEmpty()) return;
        java.util.HashSet<String> known = buildExcludeSet();
        for (int i = suggestedData.size() - 1; i >= 0; i--) {
            if (known.contains(suggestedData.get(i).username)) suggestedData.remove(i);
        }
    }

    /** Self + all current friends + everyone with a pending request (sent or received). */
    private java.util.HashSet<String> buildExcludeSet() {
        java.util.HashSet<String> exclude = new java.util.HashSet<>();
        exclude.add(me);
        for (FriendModel f : friendsData) if (f.type != FriendModel.Type.HEADER) exclude.add(f.username);
        for (FriendModel r : requestsData) if (r.type != FriendModel.Type.HEADER) exclude.add(r.username);
        return exclude;
    }

    @Override
    protected void onDestroy() {
        if (queue != null) queue.cancelAll(req -> true);
        super.onDestroy();
    }

    /** Resolve the typed handle and open that user's profile (the canonical add-friend surface). */
    private void submitSearch(EditText input) {
        String handle = input.getText().toString().trim().toLowerCase();
        if (handle.startsWith("@")) handle = handle.substring(1).trim();
        // Hive usernames: 3-16 chars, start with a letter, end alphanumeric, lowercase/digit/dot/dash between
        if (!handle.matches("[a-z][a-z0-9.\\-]{1,14}[a-z0-9]")) {
            Toast.makeText(this, R.string.friends_search_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        if (handle.equals(me)) {
            Toast.makeText(this, R.string.friends_search_self, Toast.LENGTH_SHORT).show();
            return;
        }
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(input.getWindowToken(), 0);
        input.setText("");
        Intent i = new Intent(this, ProfileActivity.class);
        i.putExtra(ProfileActivity.EXTRA_USERNAME, handle);
        startActivity(i);
    }

    private void onAction(FriendModel m) {
        if (m.type == FriendModel.Type.FRIEND) {
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.friends_confirm_unfriend, m.username))
                    .setPositiveButton(R.string.friends_action_unfriend, (d, w) -> doAction(m))
                    .setNegativeButton(R.string.close_button, (d, w) -> render())
                    .setOnCancelListener(d -> render())
                    .show();
        } else {
            doAction(m);
        }
    }

    private void doAction(FriendModel m) {
        FriendsApi.Callback cb = (success, message) -> {
            int msgRes;
            if (!success) msgRes = R.string.friends_action_failed;
            else if (m.type == FriendModel.Type.SUGGESTED) msgRes = R.string.friends_request_sent;
            else msgRes = R.string.friends_action_done;
            Toast.makeText(this, msgRes, Toast.LENGTH_SHORT).show();
            if (success && m.type == FriendModel.Type.SUGGESTED) {
                // Instant feedback: drop the just-added account from the suggestions list.
                removeFromSuggested(m.username);
            }
            // Don't refetch suggestions here (would flash a spinner and lose scroll/paged rows);
            // loadData()'s finishLoad prunes any now-friend/pending suggestion in place instead.
            loadData();
        };
        switch (m.type) {
            case RECEIVED: FriendsApi.acceptFriend(this, this, me, m.username, cb); break;
            case SENT: FriendsApi.cancelRequest(this, this, me, m.username, cb); break;
            case FRIEND: FriendsApi.unfriend(this, this, me, m.username, cb); break;
            case SUGGESTED: FriendsApi.addFriend(this, this, me, m.username, cb); break;
            default: break;
        }
    }

    private void removeFromSuggested(String username) {
        for (int i = suggestedData.size() - 1; i >= 0; i--) {
            if (username.equals(suggestedData.get(i).username)) suggestedData.remove(i);
        }
        if (currentTab == TAB_SUGGESTED) render();
    }

    private static final int SUGGEST_CAP = 15;

    /**
     * Lazily builds friend suggestions ordered by ACTIVITY (recent Actifit reporters are most
     * likely to engage), mirroring the web app. Primary source is the verified-reports endpoint
     * with a short timeout; if it's slow/unavailable it falls back to Hive's recent #actifit
     * authors — so no new backend endpoint is required. Each candidate is then enriched with its
     * report count, AFIT balance and mutual-friend count. Only called once the friend/request
     * lists are loaded, so the exclude-set is complete.
     */
    private void loadSuggestions() {
        final int gen = ++suggestGeneration;
        suggestLoading = true;
        suggestedLoaded = false;   // defensive: this load owns the loaded flag until it commits
        suggestSeen.clear();
        hiveCursorAuthor = null;
        hiveCursorPermlink = null;
        suggestNoMore = false;
        suggestLoadingMore = false;
        if (queue == null) queue = Volley.newRequestQueue(getApplicationContext());
        refreshProgress();

        final java.util.HashSet<String> exclude = buildExcludeSet();
        final java.util.HashSet<String> myFriends = new java.util.HashSet<>();
        for (FriendModel f : friendsData) if (f.type != FriendModel.Type.HEADER) myFriends.add(f.username);

        // Race two activity sources in PARALLEL and use whichever returns candidates first:
        //   (a) recentVerifiedPosts (verified reporters) — best source once its date index ships;
        //   (b) Hive's recent #actifit authors — no backend dependency, answers in ~1s.
        // This avoids waiting on the (currently un-indexed, slow) primary before falling back.
        final boolean[] resolved = {false};
        final int[] pending = {2};

        String url = Utils.apiUrl(this) + "recentVerifiedPosts?exclude=" + enc(me) + "&maxCount=20";
        JsonArrayRequest primary = new JsonArrayRequest(Request.Method.GET, url, null,
                arr -> {
                    ArrayList<String> authors = new ArrayList<>();
                    for (int i = 0; i < arr.length(); i++) {
                        try {
                            String u = arr.getJSONObject(i).optString("_id");
                            if (u != null && !u.isEmpty()) authors.add(u);
                        } catch (Exception ignored) {}
                    }
                    raceAuthors(gen, authors, exclude, myFriends, resolved, pending);
                },
                error -> raceAuthors(gen, null, exclude, myFriends, resolved, pending));
        primary.setRetryPolicy(new DefaultRetryPolicy(5000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(primary);

        fetchHiveSuggestedAuthors(gen, exclude, myFriends, 0, resolved, pending);
    }

    /** URL-encode a path/query value; Hive names are already safe but encode defensively. */
    private static String enc(String s) {
        if (s == null) return "";
        try { return java.net.URLEncoder.encode(s, "UTF-8"); } catch (Exception e) { return s; }
    }

    /**
     * First source to return VIABLE candidates wins. "Viable" = at least one author that survives
     * the exclude-set — so a source returning only already-friends/pending doesn't win with an
     * empty result and abandon the other source; the empty state shows only once both are done.
     */
    private void raceAuthors(int gen, ArrayList<String> authors, java.util.HashSet<String> exclude,
                             java.util.HashSet<String> myFriends, boolean[] resolved, int[] pending) {
        if (gen != suggestGeneration || resolved[0]) return;
        pending[0]--;
        boolean viable = false;
        if (authors != null) {
            for (String a : authors) {
                if (a != null && !a.isEmpty() && !exclude.contains(a)) { viable = true; break; }
            }
        }
        if (viable) {
            resolved[0] = true;
            onSuggestionAuthors(gen, authors, exclude, myFriends);
        } else if (pending[0] == 0) {
            resolved[0] = true;
            onSuggestionAuthors(gen, authors != null ? authors : new ArrayList<>(), exclude, myFriends);
        }
    }

    /** Fallback source: Hive's most recent #actifit posts → their authors (activity-ordered). */
    private void fetchHiveSuggestedAuthors(int gen, java.util.HashSet<String> exclude,
                                           java.util.HashSet<String> myFriends, int nodeIdx,
                                           boolean[] resolved, int[] pending) {
        if (gen != suggestGeneration || resolved[0]) return;
        String[] nodes = hiveNodes();
        if (nodeIdx >= nodes.length) {   // no node answered → count this source as done (empty)
            raceAuthors(gen, null, exclude, myFriends, resolved, pending);
            return;
        }
        org.json.JSONObject body = new org.json.JSONObject();
        try {
            body.put("jsonrpc", "2.0");
            body.put("method", "condenser_api.get_discussions_by_created");
            org.json.JSONArray params = new org.json.JSONArray();
            org.json.JSONObject q = new org.json.JSONObject();
            q.put("tag", "actifit");
            q.put("limit", 20);
            params.put(q);
            body.put("params", params);
            body.put("id", 1);
        } catch (Exception ignored) {}
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, nodes[nodeIdx], body,
                resp -> {
                    org.json.JSONArray result = resp.optJSONArray("result");
                    if (result == null) {   // node answered with an RPC error (no result) → try next
                        fetchHiveSuggestedAuthors(gen, exclude, myFriends, nodeIdx + 1, resolved, pending);
                        return;
                    }
                    ArrayList<String> authors = new ArrayList<>();
                    for (int i = 0; i < result.length(); i++) {
                        org.json.JSONObject p = result.optJSONObject(i);
                        String a = p != null ? p.optString("author") : null;
                        if (a != null && !a.isEmpty() && !authors.contains(a)) authors.add(a);
                    }
                    rememberHiveCursor(gen, result);   // for "load more" pagination
                    raceAuthors(gen, authors, exclude, myFriends, resolved, pending);
                },
                err -> fetchHiveSuggestedAuthors(gen, exclude, myFriends, nodeIdx + 1, resolved, pending));
        req.setRetryPolicy(new DefaultRetryPolicy(6000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(req);
    }

    /** Record the last post of a Hive #actifit page as the cursor for the next page. */
    private void rememberHiveCursor(int gen, org.json.JSONArray result) {
        if (gen != suggestGeneration || result.length() == 0) return;
        org.json.JSONObject last = result.optJSONObject(result.length() - 1);
        if (last != null) {
            hiveCursorAuthor = last.optString("author", null);
            hiveCursorPermlink = last.optString("permlink", null);
        }
    }

    /** Commit the activity-ordered author list (deduped, filtered, capped), then enrich each. */
    private void onSuggestionAuthors(int gen, ArrayList<String> authors,
                                     java.util.HashSet<String> exclude,
                                     java.util.HashSet<String> myFriends) {
        if (gen != suggestGeneration) return;
        suggestedData.clear();
        suggestSeen.clear();
        ArrayList<FriendModel> fresh = new ArrayList<>();
        for (String a : authors) {
            if (suggestedData.size() >= SUGGEST_CAP) break;
            if (a == null || a.isEmpty() || exclude.contains(a) || !suggestSeen.add(a)) continue;
            FriendModel s = new FriendModel(a, FriendModel.Type.SUGGESTED);
            suggestedData.add(s);
            fresh.add(s);
        }
        suggestedLoaded = true;
        suggestLoading = false;
        refreshProgress();
        render();   // list shows immediately; each row fills its stats in as enrichment lands
        for (FriendModel s : fresh) enrichSuggestion(gen, s, myFriends);
    }

    /** Append the next page of Hive #actifit authors when the user scrolls near the bottom. */
    private void loadMoreSuggestions() {
        if (!suggestedLoaded || suggestLoadingMore || suggestNoMore
                || hiveCursorAuthor == null || hiveCursorPermlink == null) return;
        suggestLoadingMore = true;
        refreshLoadMoreFooter();
        final int gen = suggestGeneration;
        final java.util.HashSet<String> exclude = buildExcludeSet();
        final java.util.HashSet<String> myFriends = new java.util.HashSet<>();
        for (FriendModel f : friendsData) if (f.type != FriendModel.Type.HEADER) myFriends.add(f.username);
        fetchMoreHive(gen, exclude, myFriends, 0);
    }

    private void fetchMoreHive(int gen, java.util.HashSet<String> exclude,
                               java.util.HashSet<String> myFriends, int nodeIdx) {
        // Superseded by a newer load — don't touch suggestLoadingMore, it belongs to that load now.
        if (gen != suggestGeneration) return;
        String[] nodes = hiveNodes();
        if (nodeIdx >= nodes.length) { suggestLoadingMore = false; refreshLoadMoreFooter(); return; }   // give up quietly
        org.json.JSONObject body = new org.json.JSONObject();
        try {
            body.put("jsonrpc", "2.0");
            body.put("method", "condenser_api.get_discussions_by_created");
            org.json.JSONArray params = new org.json.JSONArray();
            org.json.JSONObject q = new org.json.JSONObject();
            q.put("tag", "actifit");
            q.put("limit", 20);
            q.put("start_author", hiveCursorAuthor);
            q.put("start_permlink", hiveCursorPermlink);
            params.put(q);
            body.put("params", params);
            body.put("id", 1);
        } catch (Exception ignored) {}
        JsonObjectRequest req = new JsonObjectRequest(Request.Method.POST, nodes[nodeIdx], body,
                resp -> {
                    if (gen != suggestGeneration) return;   // superseded; flag owned by newer load
                    org.json.JSONArray result = resp.optJSONArray("result");
                    if (result == null) {   // RPC error (no result) → try the next node
                        fetchMoreHive(gen, exclude, myFriends, nodeIdx + 1);
                        return;
                    }
                    ArrayList<FriendModel> fresh = new ArrayList<>();
                    for (int i = 0; i < result.length(); i++) {
                        org.json.JSONObject p = result.optJSONObject(i);
                        String a = p != null ? p.optString("author") : null;
                        if (suggestedData.size() >= 100) break;   // hard safety cap
                        if (a == null || a.isEmpty() || exclude.contains(a) || !suggestSeen.add(a)) continue;
                        FriendModel s = new FriendModel(a, FriendModel.Type.SUGGESTED);
                        suggestedData.add(s);
                        fresh.add(s);
                    }
                    rememberHiveCursor(gen, result);
                    // A page that yields no new candidates (or ran out) means we've reached the end.
                    if (fresh.isEmpty() || result.length() < 2) suggestNoMore = true;
                    suggestLoadingMore = false;
                    refreshLoadMoreFooter();
                    if (currentTab == TAB_SUGGESTED) render();
                    for (FriendModel s : fresh) enrichSuggestion(gen, s, myFriends);
                },
                err -> fetchMoreHive(gen, exclude, myFriends, nodeIdx + 1));
        req.setRetryPolicy(new DefaultRetryPolicy(6000, 0, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
        queue.add(req);
    }

    /** Fetch report count + AFIT + mutual-friend count for one suggestion (all fast endpoints). */
    private void enrichSuggestion(int gen, final FriendModel s, final java.util.HashSet<String> myFriends) {
        final int[] remaining = {3};
        final String base = Utils.apiUrl(this);
        JsonObjectRequest reports = new JsonObjectRequest(Request.Method.GET,
                base + "userRewardedPostCount/" + s.username, null,
                resp -> { if (gen == suggestGeneration) s.activityCount = resp.optInt("rewarded_post_count", 0);
                          afterEnrichStep(remaining, gen, s); },
                err -> afterEnrichStep(remaining, gen, s));
        JsonObjectRequest afit = new JsonObjectRequest(Request.Method.GET,
                base + "user/" + s.username, null,
                resp -> { if (gen == suggestGeneration) s.afit = formatAfit(resp.optString("tokens", ""));
                          afterEnrichStep(remaining, gen, s); },
                err -> afterEnrichStep(remaining, gen, s));
        JsonArrayRequest mutual = new JsonArrayRequest(Request.Method.GET,
                base + "userFriends/" + s.username, null,
                arr -> {
                    if (gen == suggestGeneration) {
                        int c = 0;
                        for (int i = 0; i < arr.length(); i++) {
                            try { if (myFriends.contains(arr.getJSONObject(i).optString("friend"))) c++; }
                            catch (Exception ignored) {}
                        }
                        s.mutualCount = c;
                    }
                    afterEnrichStep(remaining, gen, s);
                },
                err -> afterEnrichStep(remaining, gen, s));
        for (Request<?> r : new Request<?>[]{reports, afit, mutual}) {
            r.setRetryPolicy(new DefaultRetryPolicy(8000, 1, DefaultRetryPolicy.DEFAULT_BACKOFF_MULT));
            queue.add(r);
        }
    }

    private void afterEnrichStep(int[] remaining, int gen, FriendModel s) {
        if (--remaining[0] == 0) {
            s.enriched = true;
            if (gen == suggestGeneration && currentTab == TAB_SUGGESTED) scheduleAdapterRefresh();
        }
    }

    // Coalesce the burst of per-candidate enrichment completions into one redraw per frame,
    // instead of up to 15 back-to-back notifyDataSetChanged calls.
    private boolean refreshScheduled = false;
    private void scheduleAdapterRefresh() {
        if (refreshScheduled) return;
        refreshScheduled = true;
        listView.post(() -> { refreshScheduled = false; adapter.notifyDataSetChanged(); });
    }

    private String formatAfit(String tokens) {
        if (tokens == null || tokens.isEmpty()) return null;
        try {
            return FriendEntryAdapter.compact(Math.round(Double.parseDouble(tokens)));
        } catch (Exception e) {
            return null;
        }
    }

    /** Default Hive node first, then the app's RPC fallbacks, de-duped. */
    private String[] hiveNodes() {
        java.util.LinkedHashSet<String> nodes = new java.util.LinkedHashSet<>();
        nodes.add(getString(R.string.hive_default_node));
        for (String n : getResources().getStringArray(R.array.hive_rpc_nodes)) {
            if (n != null && !n.isEmpty()) nodes.add(n);
        }
        return nodes.toArray(new String[0]);
    }
}
