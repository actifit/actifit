package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

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
