package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 3Speak videos through the embed service (embed.3speak.tv). Actifit's server hands out a
 * short-lived upload token (POST /video/uploadToken); the file goes straight to 3Speak by
 * TUS; the report links the video by its embed URL, which plays right away while 3Speak
 * encodes it. No Android types here so it can be unit tested.
 */
public final class ThreeSpeakVideo {

    public static final String EMBED_BASE = "https://embed.3speak.tv";
    public static final String PLAYER_BASE = "https://play.3speak.tv/embed?v=";

    private ThreeSpeakVideo() {}

    /** Public status endpoint for one upload (no auth). */
    public static String statusUrl(String permlink) {
        return EMBED_BASE + "/video/" + permlink;
    }

    public static String embedUrl(String owner, String permlink) {
        return PLAYER_BASE + owner + "/" + permlink;
    }

    public static String watchUrl(String owner, String permlink) {
        return "https://3speak.tv/watch?v=" + owner + "/" + permlink;
    }

    /**
     * Text added to the report body: the embed URL on its own line (Hive frontends turn it
     * into a player), then a plain link for apps that don't.
     */
    public static String reportSnippet(String owner, String permlink, String embedUrl) {
        return "\n" + embedUrl + "\n\n▶ [Watch on 3speak.tv](" + watchUrl(owner, permlink) + ")\n";
    }

    /** Is the video still in the report text (the user may have deleted the snippet)? */
    public static boolean isInContent(String content, String embedUrl) {
        return content != null && embedUrl != null && !embedUrl.isEmpty() && content.contains(embedUrl);
    }

    /** States in which the video can still be added to a report. */
    public static boolean canAttach(String status) {
        return !"failed".equals(status) && !"deleted".equals(status);
    }

    /**
     * The json_metadata "video" object. PeakD and Ecency build the player from
     * info.author and info.permlink, so both are always set.
     */
    public static JSONObject metadata(String owner, String permlink, String embedUrl, String title,
                                      double durationSec, String thumbnailUrl) throws JSONException {
        JSONObject info = new JSONObject();
        info.put("platform", "3speak");
        info.put("author", owner);
        info.put("permlink", permlink);
        info.put("title", title == null ? "" : title);
        if (durationSec > 0) info.put("duration", durationSec);
        JSONArray sourceMap = new JSONArray();
        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) {
            sourceMap.put(new JSONObject().put("type", "thumbnail").put("url", thumbnailUrl));
        }
        info.put("sourceMap", sourceMap);

        JSONObject video = new JSONObject();
        video.put("platform", "3speak");
        video.put("url", embedUrl);
        if (thumbnailUrl != null && !thumbnailUrl.isEmpty()) video.put("thumbnail", thumbnailUrl);
        video.put("info", info);
        return video;
    }

    /** 3Speak's beneficiaries for a video post, as 3speak.tv sets them for regular accounts. */
    public static JSONArray beneficiaries() {
        JSONArray list = new JSONArray();
        try {
            list.put(new JSONObject().put("account", "encoder.pay").put("weight", 100));
            list.put(new JSONObject().put("account", "threespeakfund").put("weight", 1000));
        } catch (JSONException e) {
            // constant values, cannot fail
        }
        return list;
    }

    /** An upload as the app shows it, from our server's list row plus 3Speak's status. */
    public static UploadedVideoModel fromStatus(String owner, String permlink, String embedUrl,
                                                String created, JSONObject status) {
        UploadedVideoModel v = new UploadedVideoModel(new JSONObject());
        v.permlink = permlink;
        v.embed_url = (embedUrl == null || embedUrl.isEmpty()) ? embedUrl(owner, permlink) : embedUrl;
        v.created = created;
        if (status != null) {
            v.status = status.optString("status", "");
            v.encodingProgress = status.optDouble("encodingProgress", 0);
            v.duration = status.optDouble("duration", 0);
            String thumb = status.optString("thumbnail_url", "");
            v.thumbnail_url = (thumb.isEmpty() || "null".equals(thumb)) ? null : thumb;
            String title = status.optString("originalFilename", status.optString("filename", ""));
            v.title = "null".equals(title) ? "" : title;
        } else {
            v.status = "";
        }
        if (Double.isNaN(v.encodingProgress)) v.encodingProgress = 0;
        if (Double.isNaN(v.duration)) v.duration = 0;
        return v;
    }
}
