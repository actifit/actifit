package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The bot's /ai/* replies, as AiService reads them. */
public class AiServiceTest {

    private static final String FALLBACK = "could not answer";

    @Test
    public void successCarriesTheText() {
        AiService.Reply r = AiService.parseReply(200, "{\"status\":\"success\",\"text\":\"  Hello  \"}", FALLBACK);
        assertEquals("Hello", r.text);
        assertNull(r.error);
        assertFalse(r.auth);
    }

    @Test
    public void botMessageIsShownOnFailure() {
        AiService.Reply r = AiService.parseReply(429,
                "{\"error\":\"quota\",\"message\":\"You reached today's limit\"}", FALLBACK);
        assertNull(r.text);
        assertEquals("You reached today's limit", r.error);
    }

    @Test
    public void everySessionRefusalAsksToRenewTheSession() {
        assertTrue(AiService.parseReply(200, "{\"success\":false,\"message\":\"Token is not valid\"}", FALLBACK).auth);
        assertTrue(AiService.parseReply(200, "{\"error\":\"Authentication failed. Key not found\"}", FALLBACK).auth);
        assertTrue(AiService.parseReply(200, "{\"error\":\"Invalid token payload\"}", FALLBACK).auth);
    }

    @Test
    public void unreadableOrEmptyRepliesAreFailures() {
        assertEquals(FALLBACK, AiService.parseReply(429, "Too many requests, please try again later.", FALLBACK).error);
        assertEquals(FALLBACK, AiService.parseReply(200, "{\"status\":\"success\",\"text\":\"   \"}", FALLBACK).error);
        assertEquals(FALLBACK, AiService.parseReply(502, "<html>bad gateway</html>", FALLBACK).error);
        // a success body on an error status is not trusted
        assertEquals(FALLBACK, AiService.parseReply(500, "{\"status\":\"success\",\"text\":\"x\"}", FALLBACK).error);
    }

    private static String text(int n) {
        return new String(new char[n]).replace('\0', 'x');
    }

    private static List<ChatMessage> alternating(int turns, String body) {
        List<ChatMessage> h = new ArrayList<>();
        for (int i = 0; i < turns; i++) {
            h.add(new ChatMessage(i % 2 == 0 ? ChatMessage.ROLE_USER : ChatMessage.ROLE_AI, body == null ? "t" + i : body));
        }
        return h;
    }

    @Test
    public void shortChatIsSentWhole() {
        List<ChatMessage> h = alternating(3, null);
        assertEquals(h, AiService.chatWindow(h));
    }

    @Test
    public void longChatKeepsTheLatestTurnsWithinTheBotLimits() {
        List<ChatMessage> h = alternating(40, text(3500));
        h.add(new ChatMessage(ChatMessage.ROLE_USER, "latest"));
        List<ChatMessage> w = AiService.chatWindow(h);
        int total = 0;
        for (ChatMessage m : w) total += m.getText().length();
        assertTrue(w.size() <= 30);
        assertTrue(total <= 20000);
        assertTrue(w.get(0).isUser());
        assertEquals("latest", w.get(w.size() - 1).getText());
    }

    @Test
    public void oneHugeTurnIsCappedNotDropped() {
        List<ChatMessage> h = new ArrayList<>();
        h.add(new ChatMessage(ChatMessage.ROLE_USER, text(9000)));
        List<ChatMessage> w = AiService.chatWindow(h);
        assertEquals(1, w.size());
        assertEquals(4000, w.get(0).getText().length());
    }

    @Test
    public void tooManyShortTurnsKeepsTheLast30() {
        List<ChatMessage> w = AiService.chatWindow(alternating(61, null));
        assertTrue(w.size() <= 30);
        assertTrue(w.get(0).isUser());
        assertEquals("t60", w.get(w.size() - 1).getText());
    }

    @Test
    public void workoutJsonIsUnwrappedFromMarkdownFences() {
        assertEquals("{\"a\":1}", AiService.extractJsonFromContentString("```json\n{\"a\":1}\n```"));
        assertEquals("{\"a\":1}", AiService.extractJsonFromContentString(" {\"a\":1} "));
    }

    @Test
    public void freeTextIsCappedToWhatTheBotAccepts() {
        assertEquals(500, AiService.capped(new String(new char[900]).replace('\0', 'x'), 500).length());
        assertEquals("knee", AiService.capped("knee", 500));
        assertEquals("", AiService.capped(null, 500));
    }
}
