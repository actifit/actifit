package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Collections;

/** Gadget operations (must stay what the web signs) and the confirm-reply rules. */
public class GadgetTest {

    private static String opsJson(HiveTx.Op op) {
        return new HiveTx.Transaction(1, 1, 0, Collections.singletonList(op)).operationsJson();
    }

    @Test
    public void buyMatchesTheWebOperation() {
        assertEquals("[[\"custom_json\",{\"required_auths\":[],\"required_posting_auths\":[\"alice\"],\"id\":\"actifit\","
                        + "\"json\":\"{\\\"transaction\\\": \\\"buy-gadget\\\", \\\"gadget\\\": \\\"g1\\\"}\"}]]",
                opsJson(GadgetOps.buy("alice", "g1")));
    }

    @Test
    public void activateCarriesTheBeneficiaryOnlyWhenGiven() {
        assertEquals("{\"transaction\": \"activate-gadget\" , \"gadget\": \"g1\"}",
                ((HiveTx.CustomJson) GadgetOps.activate("alice", "g1", null)).json);
        assertEquals("{\"transaction\": \"activate-gadget\" , \"gadget\": \"g1\", \"benefic\": \"bob\"}",
                ((HiveTx.CustomJson) GadgetOps.activate("alice", "g1", "bob")).json);
    }

    @Test
    public void deactivateMatchesTheWebOperation() {
        assertEquals("{\"transaction\": \"deactivate-gadget\" , \"gadget\": \"g1\"}",
                ((HiveTx.CustomJson) GadgetOps.deactivate("alice", "g1")).json);
    }

    @Test
    public void hiveBuyIsATransferToTheMarketWithTheGadgetMemo() {
        assertEquals("[[\"transfer\",{\"from\":\"alice\",\"to\":\"actifit.market\",\"amount\":\"12.346 HIVE\","
                        + "\"memo\":\"buy-gadget:g1\"}]]",
                opsJson(GadgetOps.buyWithHive("alice", "actifit.market", "g1", HiveTx.formatAmount(12.3456, "HIVE"))));
    }

    @Test
    public void successfulRepliesAreOk() {
        assertEquals(GadgetReply.Kind.OK, GadgetReply.classify(false, "", "success", null, ""));
        assertEquals(GadgetReply.Kind.OK, GadgetReply.classify(false, "", "", true, ""));
    }

    @Test
    public void statusErrorIsAFailureEvenWithoutAnErrorField() {
        // what the routes answer when on-chain verification fails
        assertEquals(GadgetReply.Kind.FAILED, GadgetReply.classify(false, "", "error", null, ""));
    }

    @Test
    public void successFalseWithoutDetailIsAnExpiredSession() {
        // checkHdrs answers HTTP 200 {success:false}; it used to be read as success
        assertEquals(GadgetReply.Kind.SESSION, GadgetReply.classify(false, "", "", false, ""));
    }

    @Test
    public void keyRotationGatesWin() {
        assertEquals(GadgetReply.Kind.GATE, GadgetReply.classify(false, "", "", false, "KEY_ROTATION_HOLD"));
        assertEquals(GadgetReply.Kind.GATE, GadgetReply.classify(true, "x", "", null, "KEY_ROTATION_RECOMMENDED"));
    }

    @Test
    public void knownRefusalsAreNamed() {
        assertEquals(GadgetReply.Kind.INSUFFICIENT_AFIT, GadgetReply.classify(true,
                "User does not have enough AFIT funds to buy this gadget", "", null, ""));
        assertEquals(GadgetReply.Kind.BUSY, GadgetReply.classify(true,
                "Another AFIT transaction for this account is being processed. Please try again in a moment.", "", null, ""));
        assertEquals(GadgetReply.Kind.UNAVAILABLE, GadgetReply.classify(true, "Product not found", "", null, ""));
        assertEquals(GadgetReply.Kind.FAILED, GadgetReply.classify(true, "Error performing buy action", "", null, ""));
        assertEquals(GadgetReply.Kind.FAILED, GadgetReply.classify(true, "trx already exists", "", null, ""));
    }
}
