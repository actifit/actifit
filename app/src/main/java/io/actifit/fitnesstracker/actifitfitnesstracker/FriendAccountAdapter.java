package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Filter;
import android.widget.Filterable;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Autocomplete adapter for the gadget "reward a friend" field.
 *
 * Suggestions are sourced friends-first, then any Hive account as a fallback:
 *   1. the current user's Actifit friends (via the {@code userFriends/<user>} endpoint), and
 *   2. Hive accounts by name prefix (via {@code condenser_api.lookup_accounts}).
 *
 * Both lookups run inside {@link Filter#performFiltering(CharSequence)}, which AutoCompleteTextView
 * already executes on a background thread — so the synchronous HTTP here never touches the UI thread.
 * The friends list is fetched once and cached; any network failure degrades gracefully to whatever
 * source did respond (so the field always keeps working as a plain input).
 */
public class FriendAccountAdapter extends ArrayAdapter<String> implements Filterable {

    private static final String TAG = "FriendAccountAdapter";
    private static final int MAX_SUGGESTIONS = 10;
    private static final int TIMEOUT_MS = 4000;

    private final String apiBase;      // Utils.apiUrl(ctx)
    private final String hiveNode;     // e.g. https://hiveapi.actifit.io
    private final String currentUser;

    private final List<String> suggestions = new ArrayList<>();
    private volatile List<String> friends = null; // lazily loaded + cached

    public FriendAccountAdapter(@NonNull Context ctx, String apiBase, String hiveNode, String currentUser) {
        super(ctx, android.R.layout.simple_dropdown_item_1line);
        this.apiBase = apiBase;
        this.hiveNode = hiveNode;
        this.currentUser = currentUser == null ? "" : currentUser.toLowerCase(Locale.ROOT);
    }

    @Override
    public int getCount() {
        return suggestions.size();
    }

    @Override
    public String getItem(int position) {
        return suggestions.get(position);
    }

    @NonNull
    @Override
    public Filter getFilter() {
        return new Filter() {
            @Override
            protected FilterResults performFiltering(CharSequence constraint) {
                FilterResults results = new FilterResults();
                List<String> out = new ArrayList<>();
                String prefix = constraint == null ? "" : constraint.toString().trim().toLowerCase(Locale.ROOT);
                while (prefix.startsWith("@")) {
                    prefix = prefix.substring(1);
                }
                // Keep only valid Hive-username characters — also prevents JSON injection into the RPC body.
                prefix = prefix.replaceAll("[^a-z0-9.\\-]", "");
                if (!prefix.isEmpty()) {
                    // 1) friends matching the prefix (most relevant for "reward a friend")
                    for (String f : loadFriends()) {
                        if (f.startsWith(prefix) && !out.contains(f)) {
                            out.add(f);
                            if (out.size() >= MAX_SUGGESTIONS) break;
                        }
                    }
                    // 2) any Hive account, by prefix, to fill the rest
                    if (out.size() < MAX_SUGGESTIONS) {
                        for (String a : lookupHiveAccounts(prefix, MAX_SUGGESTIONS)) {
                            if (a.startsWith(prefix) && !out.contains(a)) {
                                out.add(a);
                                if (out.size() >= MAX_SUGGESTIONS) break;
                            }
                        }
                    }
                }
                results.values = out;
                results.count = out.size();
                return results;
            }

            @Override
            @SuppressWarnings("unchecked")
            protected void publishResults(CharSequence constraint, FilterResults r) {
                suggestions.clear();
                if (r != null && r.values instanceof List) {
                    suggestions.addAll((List<String>) r.values);
                }
                if (r != null && r.count > 0) {
                    notifyDataSetChanged();
                } else {
                    notifyDataSetInvalidated();
                }
            }
        };
    }

    private synchronized List<String> loadFriends() {
        if (friends != null) {
            return friends;
        }
        List<String> list = new ArrayList<>();
        if (!currentUser.isEmpty() && apiBase != null && !apiBase.isEmpty()) {
            try {
                String base = apiBase.endsWith("/") ? apiBase : apiBase + "/";
                String url = base + "userFriends/" + URLEncoder.encode(currentUser, "UTF-8");
                String body = httpGet(url);
                if (body != null) {
                    JSONArray arr = new JSONArray(body);
                    for (int i = 0; i < arr.length(); i++) {
                        JSONObject o = arr.optJSONObject(i);
                        String name = o != null ? o.optString("friend", "") : "";
                        if (!name.isEmpty()) {
                            list.add(name.toLowerCase(Locale.ROOT));
                        }
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "friends load failed: " + e.getMessage());
            }
        }
        friends = list; // cache even on failure (empty) so we don't retry every keystroke
        return list;
    }

    private List<String> lookupHiveAccounts(String prefix, int limit) {
        List<String> list = new ArrayList<>();
        if (hiveNode == null || hiveNode.isEmpty()) {
            return list;
        }
        try {
            String payload = "{\"jsonrpc\":\"2.0\",\"method\":\"condenser_api.lookup_accounts\",\"params\":[\""
                    + prefix + "\"," + limit + "],\"id\":1}";
            String resp = httpPost(hiveNode, payload);
            if (resp != null) {
                JSONArray arr = new JSONObject(resp).optJSONArray("result");
                if (arr != null) {
                    for (int i = 0; i < arr.length(); i++) {
                        String n = arr.optString(i, "");
                        if (!n.isEmpty()) {
                            list.add(n);
                        }
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "hive lookup failed: " + e.getMessage());
        }
        return list;
    }

    private static String httpGet(String urlStr) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            if (conn.getResponseCode() / 100 != 2) {
                return null;
            }
            return readBody(conn);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String httpPost(String urlStr, String jsonBody) {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestMethod("POST");
            conn.setConnectTimeout(TIMEOUT_MS);
            conn.setReadTimeout(TIMEOUT_MS);
            conn.setDoOutput(true);
            conn.setRequestProperty("Content-Type", "application/json");
            byte[] out = jsonBody.getBytes(StandardCharsets.UTF_8);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(out);
            }
            if (conn.getResponseCode() / 100 != 2) {
                return null;
            }
            return readBody(conn);
        } catch (Exception e) {
            return null;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private static String readBody(HttpURLConnection conn) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        }
        return sb.toString();
    }
}
