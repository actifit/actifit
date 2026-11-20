package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * 3Speak videos through the embed service (embed2.3speak.tv, 3Speak's primary upload host). Actifit's server hands out a
 * short-lived upload token (POST /video/uploadToken); the file goes straight to 3Speak by
 * TUS; the report links the video by its embed URL, which plays right away while 3Speak
 * encodes it. Our server then links the video to the report post on 3Speak. No Android
 * types here so it can be unit tested.
 */
public final class ThreeSpeakVideo {

    public static final String EMBED_BASE = "https://embed2.3speak.tv";
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
     * into a player), then a link for apps that don't. With a frame, the link is the frame:
     * the post service makes the report's first image its cover, which 3Speak also takes as
     * the video's thumbnail.
     */
    public static String reportSnippet(String owner, String permlink, String embedUrl, String thumbnailUrl) {
        String watch = watchUrl(owner, permlink);
        String link = (thumbnailUrl == null || thumbnailUrl.isEmpty())
                ? "▶ [Watch on 3speak.tv](" + watch + ")"
                : "[![Watch on 3speak.tv](" + thumbnailUrl + ")](" + watch + ")";
        return "\n" + embedUrl + "\n\n" + link + "\n";
    }

    /** Is the video still in the report text (the user may have deleted the snippet)? */
    public static boolean isInContent(String content, String embedUrl) {
        return content != null && embedUrl != null && !embedUrl.isEmpty() && content.contains(embedUrl);
    }

    /** Our status for an upload 3Speak has no record of (token issued, file never arrived). */
    public static final String STATUS_MISSING = "missing";
    /** Our status for a video that already has a Hive post (used in an earlier report). */
    public static final String STATUS_POSTED = "posted";

    /**
     * Can the video be added to a report? Only once 3Speak has the file (processing or
     * published), or right after this phone finished uploading it ("uploading" can lag).
     */
    public static boolean canAttach(String status, boolean uploadedHere) {
        if ("processing".equals(status) || "published".equals(status)) return true;
        return uploadedHere && "uploading".equals(status);
    }

    /** Does a condenser_api.get_content result describe an existing post? */
    public static boolean isExistingPost(JSONObject rpcReply) {
        JSONObject r = rpcReply == null ? null : rpcReply.optJSONObject("result");
        return r != null && !r.optString("author", "").isEmpty();
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
        video.put("reusable", false);
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
        if (status != null && status.has("status")) {
            v.status = status.optString("status", "");
            v.encodingProgress = status.optDouble("encodingProgress", 0);
            v.duration = status.optDouble("duration", 0);
            String thumb = status.optString("thumbnail_url", "");
            v.thumbnail_url = (thumb.isEmpty() || "null".equals(thumb)) ? null : thumb;
            String title = status.optString("originalFilename", status.optString("filename", ""));
            v.title = "null".equals(title) ? "" : title;
        } else if (status != null) {
            // 3Speak answered without a status: {"error":"Video not found"}
            v.status = STATUS_MISSING;
        } else {
            v.status = ""; // 3Speak unreachable: unknown
        }
        if (Double.isNaN(v.encodingProgress)) v.encodingProgress = 0;
        if (Double.isNaN(v.duration)) v.duration = 0;
        return v;
    }
}
