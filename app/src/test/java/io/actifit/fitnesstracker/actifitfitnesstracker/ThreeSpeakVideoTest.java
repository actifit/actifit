package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class ThreeSpeakVideoTest {

    private static final String EMBED = "https://play.3speak.tv/embed?v=alice/yn77aj9g";

    @Test
    public void snippetPutsTheEmbedUrlOnItsOwnLineSoFrontendsShowAPlayer() {
        String s = ThreeSpeakVideo.reportSnippet("alice", "yn77aj9g", EMBED, null);
        assertTrue(s.contains("\n" + EMBED + "\n"));
        assertTrue(s.contains("▶ [Watch on 3speak.tv](https://3speak.tv/watch?v=alice/yn77aj9g)"));
        assertFalse(s.contains("!["));
        assertTrue(ThreeSpeakVideo.isInContent("my report" + s, EMBED));
    }

    @Test
    public void withAFrameTheWatchLinkIsTheFrameSoItBecomesTheCover() {
        String thumb = "https://usermedia.actifit.io/abc.jpg";
        String s = ThreeSpeakVideo.reportSnippet("alice", "yn77aj9g", EMBED, thumb);
        assertTrue(s.contains("\n" + EMBED + "\n"));
        assertTrue(s.contains("[![Watch on 3speak.tv](" + thumb + ")](https://3speak.tv/watch?v=alice/yn77aj9g)"));
        // the post service collects images with /!\[(.*?)\]\((.*?)\)/ and the first is the cover
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("!\\[(.*?)\\]\\((.*?)\\)").matcher(s);
        assertTrue(m.find());
        assertEquals(thumb, m.group(2));
        assertEquals(s, ThreeSpeakVideo.reportSnippet("alice", "yn77aj9g", EMBED, thumb));
        assertTrue(ThreeSpeakVideo.reportSnippet("alice", "p", EMBED, "").contains("▶ [Watch on 3speak.tv]"));
    }

    @Test
    public void removedOrLegacyVideosAreNotInContent() {
        assertFalse(ThreeSpeakVideo.isInContent("report without the video", EMBED));
        assertFalse(ThreeSpeakVideo.isInContent("anything", null));   // pre-embed saved video
        assertFalse(ThreeSpeakVideo.isInContent("anything", ""));
        assertFalse(ThreeSpeakVideo.isInContent(null, EMBED));
    }

    @Test
    public void metadataCarriesAuthorAndPermlinkForPeakdAndEcency() throws Exception {
        JSONObject v = ThreeSpeakVideo.metadata("alice", "yn77aj9g", EMBED, "Morning run", 42.5, "https://img/t.jpg");
        assertEquals("3speak", v.getString("platform"));
        assertEquals(EMBED, v.getString("url"));
        assertFalse(v.getBoolean("reusable"));
        assertEquals("https://img/t.jpg", v.getString("thumbnail"));
        JSONObject info = v.getJSONObject("info");
        assertEquals("alice", info.getString("author"));
        assertEquals("yn77aj9g", info.getString("permlink"));
        assertEquals("Morning run", info.getString("title"));
        assertEquals(42.5, info.getDouble("duration"), 0.001);
        assertEquals("thumbnail", info.getJSONArray("sourceMap").getJSONObject(0).getString("type"));
        // the post service splices this text into json_metadata as-is: must be one JSON object
        assertEquals("alice", new JSONObject(v.toString()).getJSONObject("info").getString("author"));
    }

    @Test
    public void metadataWithoutThumbnailOrDurationLeavesThemOut() throws Exception {
        JSONObject v = ThreeSpeakVideo.metadata("alice", "p", EMBED, null, 0, null);
        assertFalse(v.has("thumbnail"));
        assertFalse(v.getJSONObject("info").has("duration"));
        assertEquals(0, v.getJSONObject("info").getJSONArray("sourceMap").length());
        assertEquals("", v.getJSONObject("info").getString("title"));
    }

    @Test
    public void beneficiariesMatch3SpeakAndStayWithinLimits() throws Exception {
        JSONArray b = ThreeSpeakVideo.beneficiaries();
        int total = 0;
        for (int i = 0; i < b.length(); i++) total += b.getJSONObject(i).getInt("weight");
        assertEquals(1100, total);
        // Hive requires beneficiaries sorted by account name
        assertTrue(b.getJSONObject(0).getString("account").compareTo(b.getJSONObject(1).getString("account")) < 0);
    }

    @Test
    public void onlyVideos3SpeakHasCanBeAttached() {
        assertTrue(ThreeSpeakVideo.canAttach("processing", false));
        assertTrue(ThreeSpeakVideo.canAttach("published", false));
        // "uploading" only right after this phone finished sending it
        assertTrue(ThreeSpeakVideo.canAttach("uploading", true));
        assertFalse(ThreeSpeakVideo.canAttach("uploading", false));
        assertFalse(ThreeSpeakVideo.canAttach("", true));                 // 3Speak unreachable
        assertFalse(ThreeSpeakVideo.canAttach(ThreeSpeakVideo.STATUS_MISSING, true));
        assertFalse(ThreeSpeakVideo.canAttach(ThreeSpeakVideo.STATUS_POSTED, false));
        assertFalse(ThreeSpeakVideo.canAttach("failed", true));
        assertFalse(ThreeSpeakVideo.canAttach("deleted", false));
    }

    @Test
    public void getContentReplyTellsWhetherThePostExists() throws Exception {
        assertTrue(ThreeSpeakVideo.isExistingPost(new JSONObject()
                .put("result", new JSONObject().put("author", "alice").put("permlink", "p"))));
        // condenser returns an empty shell for a missing post
        assertFalse(ThreeSpeakVideo.isExistingPost(new JSONObject()
                .put("result", new JSONObject().put("author", "").put("permlink", ""))));
        assertFalse(ThreeSpeakVideo.isExistingPost(new JSONObject()
                .put("error", new JSONObject().put("code", -32000))));
        assertFalse(ThreeSpeakVideo.isExistingPost(null));
    }

    @Test
    public void fromStatusReadsThe3SpeakStatusReply() throws Exception {
        JSONObject st = new JSONObject().put("status", "processing").put("encodingProgress", 37.5)
                .put("duration", 61.2).put("thumbnail_url", "https://img/t.jpg").put("originalFilename", "run.mp4");
        UploadedVideoModel v = ThreeSpeakVideo.fromStatus("alice", "p1", "", "2026-11-19T12:00:00.000Z", st);
        assertEquals("p1", v.permlink);
        assertEquals("https://play.3speak.tv/embed?v=alice/p1", v.embed_url);
        assertEquals("processing", v.status);
        assertEquals(37.5, v.encodingProgress, 0.001);
        assertEquals(61.2, v.duration, 0.001);
        assertEquals("https://img/t.jpg", v.thumbnail_url);
        assertEquals("run.mp4", v.title);
    }

    @Test
    public void fromStatusWithoutAReplyStillListsTheUpload() throws Exception {
        UploadedVideoModel v = ThreeSpeakVideo.fromStatus("alice", "p2", EMBED, "", null);
        assertEquals(EMBED, v.embed_url);
        assertEquals("", v.status);
        UploadedVideoModel gone = ThreeSpeakVideo.fromStatus("alice", "p4", EMBED, "",
                new JSONObject().put("error", "Video not found"));
        assertEquals(ThreeSpeakVideo.STATUS_MISSING, gone.status);
        UploadedVideoModel n = ThreeSpeakVideo.fromStatus("alice", "p3", null, "",
                new JSONObject().put("thumbnail_url", JSONObject.NULL).put("status", "uploading"));
        assertNull(n.thumbnail_url);
        assertEquals(0, n.duration, 0);
    }
}
