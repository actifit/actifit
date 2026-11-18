package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

public class AdRewardApiTest {

    private static final String ID = "0123456789abcdef0123456789abcdef";

    @Test
    public void successCarriesIdAndAmount() {
        AdRewardApi.Reply r = AdRewardApi.parse("{\"status\":\"success\",\"id\":\"" + ID + "\",\"amount\":2.345}");
        assertNull(r.error);
        assertEquals(ID, r.id);
        assertEquals(2.345, r.amount, 1e-9);
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
    public void refusedTokenIsAuth() {
        assertEquals(AdRewardApi.Error.AUTH, AdRewardApi.parse("{\"success\":false,\"error\":\"token\"}").error);
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
}
