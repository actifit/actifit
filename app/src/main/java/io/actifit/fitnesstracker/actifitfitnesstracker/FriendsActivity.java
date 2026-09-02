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
 * Friends screen: a segmented toggle (Friends | Requests) over a single ListView.
 * Reads are public (`userFriends`, `userFriendRequests`); writes go through {@link FriendsApi}.
 */
public class FriendsActivity extends BaseActivity {

    private ListView listView;
    private TextView tabFriends, tabRequests, emptyState;
    private View progress;

    private final ArrayList<FriendModel> friendsData = new ArrayList<>();
    private final ArrayList<FriendModel> requestsData = new ArrayList<>();
    private final ArrayList<FriendModel> current = new ArrayList<>();
    private FriendEntryAdapter adapter;

    private boolean showingRequests = false;
    private boolean dataLoaded = false;
    private int pendingReceivedCount = 0;
    private String me;

    private RequestQueue queue;
    // Bumped on every loadData(); responses from a superseded load are ignored so an
    // onResume-triggered reload can't interleave with an in-flight one and duplicate rows.
    private int loadGeneration = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_friends);

        listView = findViewById(R.id.friends_list);
        tabFriends = findViewById(R.id.friends_tab_friends);
        tabRequests = findViewById(R.id.friends_tab_requests);
        emptyState = findViewById(R.id.friends_empty_state);
        progress = findViewById(R.id.friends_progress);
        findViewById(R.id.friends_back_button).setOnClickListener(v -> finish());

        me = getSharedPreferences("actifitSets", MODE_PRIVATE).getString("actifitUser", "");

        AutoCompleteTextView searchInput = findViewById(R.id.friends_search_input);
        searchInput.setAdapter(new FriendAccountAdapter(this, Utils.apiUrl(this),
                getString(R.string.hive_default_node), me));
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

        adapter = new FriendEntryAdapter(this, current, this::onAction);
        listView.setAdapter(adapter);

        tabFriends.setOnClickListener(v -> selectTab(false));
        tabRequests.setOnClickListener(v -> selectTab(true));

        selectTab(false);
        loadData();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Refresh after returning from a profile where a request may have been sent/accepted.
        if (dataLoaded) loadData();
    }

    private void selectTab(boolean requests) {
        showingRequests = requests;
        int white = getResources().getColor(R.color.colorWhite);
        int idleText = getResources().getColor(R.color.md_theme_textSecondary);
        // Selected segment = rounded red pill thumb; idle = transparent (shows the track).
        tabFriends.setBackgroundResource(requests ? 0 : R.drawable.segmented_selected);
        tabFriends.setTextColor(requests ? idleText : white);
        tabRequests.setBackgroundResource(requests ? R.drawable.segmented_selected : 0);
        tabRequests.setTextColor(requests ? white : idleText);
        render();
    }

    private void render() {
        current.clear();
        current.addAll(showingRequests ? requestsData : friendsData);
        adapter.notifyDataSetChanged();
        boolean showEmpty = dataLoaded && current.isEmpty();
        emptyState.setVisibility(showEmpty ? View.VISIBLE : View.GONE);
        emptyState.setText(showingRequests ? R.string.friends_empty_requests : R.string.friends_empty_friends);
    }

    private void updateRequestsTabLabel() {
        tabRequests.setText(pendingReceivedCount > 0
                ? getString(R.string.friends_tab_requests) + " (" + pendingReceivedCount + ")"
                : getString(R.string.friends_tab_requests));
    }

    private void loadData() {
        progress.setVisibility(View.VISIBLE);
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
        progress.setVisibility(View.GONE);
        updateRequestsTabLabel();
        render();
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
            Toast.makeText(this,
                    getString(success ? R.string.friends_action_done : R.string.friends_action_failed),
                    Toast.LENGTH_SHORT).show();
            loadData();
        };
        switch (m.type) {
            case RECEIVED: FriendsApi.acceptFriend(this, this, me, m.username, cb); break;
            case SENT: FriendsApi.cancelRequest(this, this, me, m.username, cb); break;
            case FRIEND: FriendsApi.unfriend(this, this, me, m.username, cb); break;
            default: break;
        }
    }
}
