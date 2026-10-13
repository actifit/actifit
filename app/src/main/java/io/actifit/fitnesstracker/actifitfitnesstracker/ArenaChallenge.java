package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * One Arena challenge as returned by `arena/challenges` (list) or `arena/challenges/:id`
 * (detail, which also carries participants). Parsed with opt* getters throughout so a
 * missing or new field never breaks a screen.
 */
public class ArenaChallenge {

    public String id = "";
    public String title = "";
    public String type = "";
    public String state = "";
    public String art;
    public String recurrence = "";
    public String tagline = "";
    public String howItWorks = "";
    public String prizeSummary = "";
    public String metric = "";
    public long threshold = 0;
    public long startMs = -1;
    public long endMs = -1;
    public final List<String> offeredBadges = new ArrayList<>();

    // Filled from the detail call only; -1 = not loaded yet.
    public int participantCount = -1;
    public boolean serverJoined = false;
    public final List<Participant> participants = new ArrayList<>();

    /** A participant row from the detail response. */
    public static class Participant {
        public String entity = "";
        public String state = "";
        public int resultRank = -1;
        public double rewardAfit = 0;
        public final List<String> earnedBadges = new ArrayList<>();

        public boolean isActive() {
            return !"left".equals(state);
        }
    }

    /** One row of the materialized standings board. */
    public static class StandingRow {
        public String entity = "";
        public int rank = 0;
        public long score = 0;
    }

    public static ArenaChallenge fromJson(JSONObject o) {
        ArenaChallenge c = new ArenaChallenge();
        if (o == null) return c;
        c.id = str(o, "id");
        c.type = str(o, "type");
        c.state = str(o, "state");
        c.title = str(o, "title");
        if (c.title.isEmpty()) c.title = c.id;
        c.art = o.isNull("art") ? null : o.optString("art", null);
        c.recurrence = str(o, "recurrence");
        c.tagline = str(o, "tagline");
        c.howItWorks = str(o, "how_it_works");
        // Older docs may carry a single `description` instead of the split copy.
        String description = str(o, "description");
        if (c.tagline.isEmpty()) c.tagline = description;
        if (c.howItWorks.isEmpty()) c.howItWorks = description;
        c.prizeSummary = str(o, "prize_summary");

        JSONObject window = o.optJSONObject("window");
        if (window != null) {
            c.startMs = ArenaLogic.parseIsoMs(str(window, "start"));
            c.endMs = ArenaLogic.parseIsoMs(str(window, "end"));
        }
        JSONObject scoring = o.optJSONObject("scoring");
        if (scoring != null) {
            c.metric = str(scoring, "metric");
            c.threshold = scoring.optLong("threshold", 0);
        }
        JSONObject rewards = o.optJSONObject("rewards");
        if (rewards != null) {
            String summary = str(rewards, "summary");
            if (!summary.isEmpty()) c.prizeSummary = summary;
            addStrings(rewards.optJSONArray("badges"), c.offeredBadges);
        }
        return c;
    }

    /** Parses the `{ challenge, participants }` detail envelope; null if it has no challenge. */
    public static ArenaChallenge fromDetail(JSONObject envelope, String viewer) {
        if (envelope == null) return null;
        JSONObject ch = envelope.optJSONObject("challenge");
        if (ch == null) return null;
        ArenaChallenge c = fromJson(ch);
        JSONArray parts = envelope.optJSONArray("participants");
        int active = 0;
        if (parts != null) {
            for (int i = 0; i < parts.length(); i++) {
                JSONObject po = parts.optJSONObject(i);
                if (po == null) continue;
                Participant p = new Participant();
                p.entity = str(po, "entity");
                p.state = str(po, "state");
                JSONObject result = po.optJSONObject("result");
                if (result != null) {
                    p.resultRank = result.optInt("rank", -1);
                    JSONObject reward = result.optJSONObject("reward");
                    if (reward != null) {
                        p.rewardAfit = reward.optDouble("afit", 0);
                        addStrings(reward.optJSONArray("badges"), p.earnedBadges);
                    }
                }
                c.participants.add(p);
                if (p.isActive()) {
                    active++;
                    if (viewer != null && !viewer.isEmpty() && viewer.equals(p.entity)) c.serverJoined = true;
                }
            }
        }
        c.participantCount = active;
        return c;
    }

    public static List<StandingRow> standingsFromJson(JSONObject doc) {
        List<StandingRow> rows = new ArrayList<>();
        JSONArray arr = doc == null ? null : doc.optJSONArray("rows");
        if (arr == null) return rows;
        for (int i = 0; i < arr.length(); i++) {
            JSONObject ro = arr.optJSONObject(i);
            if (ro == null) continue;
            StandingRow r = new StandingRow();
            r.entity = str(ro, "entity");
            r.rank = ro.optInt("rank", i + 1);
            r.score = ro.optLong("score", 0);
            if (!r.entity.isEmpty()) rows.add(r);
        }
        return rows;
    }

    public Participant participant(String entity) {
        if (entity == null) return null;
        for (Participant p : participants) if (entity.equals(p.entity)) return p;
        return null;
    }

    private static String str(JSONObject o, String key) {
        return o.isNull(key) ? "" : o.optString(key, "").trim();
    }

    private static void addStrings(JSONArray arr, List<String> out) {
        if (arr == null) return;
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.isNull(i) ? "" : arr.optString(i, "").trim();
            if (!s.isEmpty()) out.add(s);
        }
    }
}
