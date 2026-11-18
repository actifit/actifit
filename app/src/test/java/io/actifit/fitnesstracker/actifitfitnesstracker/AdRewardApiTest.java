package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

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
}
