package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AdRewardApiTest {

    private static final String ID = "0123456789abcdef0123456789abcdef";

    @Test
    public void successCarriesOnlyTheId() {
        AdRewardApi.Reply r = AdRewardApi.parse("{\"status\":\"success\",\"id\":\"" + ID + "\"}");
        assertNull(r.error);
        assertEquals(ID, r.id);
    }

    @Test
    public void customDataIsOnlyTheOpaqueId() {
        assertEquals("r1_" + ID, AdRewardApi.customData(ID));
    }

    @Test
    public void malformedIdIsAFailureNotAnId() {
        assertEquals(AdRewardApi.Error.FAILED,
                AdRewardApi.parse("{\"status\":\"success\",\"id\":\"alice_3_4_10K\"}").error);
        assertEquals(AdRewardApi.Error.FAILED, AdRewardApi.parse("{\"status\":\"success\"}").error);
    }

    @Test
    public void everySessionRefusalIsAuth() {
        assertEquals(AdRewardApi.Error.AUTH, AdRewardApi.parse("{\"success\":false,\"error\":\"token\"}").error);
        assertEquals(AdRewardApi.Error.AUTH,
                AdRewardApi.parse("{\"error\":\"Authentication failed. Key not found\"}").error);
        assertEquals(AdRewardApi.Error.AUTH, AdRewardApi.parse("{\"error\":\"Invalid token payload\"}").error);
    }

    @Test
    public void alreadyClaimedIsReported() {
        assertEquals(AdRewardApi.Error.ALREADY_CLAIMED,
                AdRewardApi.parse("{\"status\":\"error\",\"error\":\"already_claimed\"}").error);
    }

    @Test
    public void otherErrorsAndNonJsonAreFailures() {
        assertEquals(AdRewardApi.Error.FAILED, AdRewardApi.parse("{\"status\":\"error\",\"error\":\"banned\"}").error);
        assertEquals(AdRewardApi.Error.FAILED, AdRewardApi.parse("<html>502</html>").error);
        assertEquals(AdRewardApi.Error.FAILED, AdRewardApi.parse("").error);
    }

    @Test
    public void http503MeansPausedAnythingElseFailed() {
        assertEquals(AdRewardApi.Error.DISABLED, AdRewardApi.classifyHttp(503));
        assertEquals(AdRewardApi.Error.FAILED, AdRewardApi.classifyHttp(500));
        assertEquals(AdRewardApi.Error.FAILED, AdRewardApi.classifyHttp(404));
    }

    @Test
    public void todayRepliesMapEachPaidTierToItsAmount() {
        java.util.Map<Integer, String> m = AdRewardApi.parseToday(
                "{\"status\":\"success\",\"rewards\":{\"2\":3.141,\"4\":9.5,\"1\":2}}");
        assertEquals("3.141", m.get(2));
        assertEquals("9.5", m.get(4));
        assertEquals("2", m.get(1));
        assertEquals(3, m.size());
    }

    @Test
    public void todayRepliesThatAreErrorsOrJunkGiveNothing() {
        assertTrue(AdRewardApi.parseToday("{\"status\":\"error\"}").isEmpty());
        assertTrue(AdRewardApi.parseToday("{\"success\":false}").isEmpty());
        assertTrue(AdRewardApi.parseToday("<html>502</html>").isEmpty());
        assertTrue(AdRewardApi.parseToday("{\"status\":\"success\",\"rewards\":{}}").isEmpty());
        // unknown tiers, zero/negative and non-numbers are skipped, the rest kept
        java.util.Map<Integer, String> m = AdRewardApi.parseToday(
                "{\"status\":\"success\",\"rewards\":{\"9\":1,\"1\":0,\"2\":-1,\"3\":\"x\",\"4\":1.25}}");
        assertEquals(1, m.size());
        assertEquals("1.25", m.get(4));
    }

    @Test
    public void amountsShowAtMostThreeDecimals() {
        assertEquals("3.142", AdRewardApi.formatAmount(3.14159));
        assertEquals("0.01", AdRewardApi.formatAmount(0.01));
        assertEquals("7", AdRewardApi.formatAmount(7.0));
    }
}
