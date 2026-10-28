package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.bitcoinj.core.Base58;
import org.bitcoinj.core.Sha256Hash;
import org.junit.Test;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;

/**
 * The expected bytes come from Hive itself: condenser_api.get_transaction_hex on
 * api.hive.blog for the same transactions (trailing signature-count byte removed), and a
 * real on-chain @actifit transaction (5216f7ab…) with its signature and posting key.
 */
public class HiveTxTest {

    private static final long EXPIRATION = HiveTx.parseIsoSeconds("2026-10-28T12:00:00");
    private static final String GADGET_JSON = "{\"transaction\": \"buy-gadget\", \"gadget\": \"6012ab34cd56ef7890123456\"}";

    private static HiveTx.Transaction tx(HiveTx.Op... ops) {
        return new HiveTx.Transaction(12345, 2847562398L, EXPIRATION, Arrays.asList(ops));
    }

    @Test
    public void customJsonSerializesLikeHived() {
        HiveTx.Transaction t = tx(HiveTx.CustomJson.posting("mcfarhat", "actifit", GADGET_JSON));
        assertEquals("39309e5abaa9c0e3e16a01120001086d636661726861740761637469666974437b227472616e73616374696f6e"
                        + "223a20226275792d676164676574222c2022676164676574223a2022363031326162333463643536656637"
                        + "383930313233343536227d00",
                HiveTx.toHex(t.serialize()));
    }

    @Test
    public void hiveTransferUsesTheLegacySteemSymbol() {
        HiveTx.Transaction t = tx(new HiveTx.Transfer("mcfarhat", "actifit.pay", "12.345 HIVE",
                "buy-gadget:6012ab34cd56ef7890123456"));
        String hived = "39309e5abaa9c0e3e16a0102086d636661726861740b616374696669742e706179393000000000000003535445"
                + "454d0000236275792d6761646765743a3630313261623334636435366566373839303132333435360000";
        assertEquals(hived.substring(0, hived.length() - 2), HiveTx.toHex(t.serialize()));
    }

    @Test
    public void hbdTransferUsesTheLegacySbdSymbol() {
        HiveTx.Transaction t = tx(new HiveTx.Transfer("mcfarhat", "actifit.pay", "0.001 HBD", ""));
        String hived = "39309e5abaa9c0e3e16a0102086d636661726861740b616374696669742e70617901000000000000000353424400000000000000";
        assertEquals(hived.substring(0, hived.length() - 2), HiveTx.toHex(t.serialize()));
    }

    @Test
    public void severalOperationsSerializeInOrder() {
        HiveTx.Transaction t = tx(HiveTx.CustomJson.posting("mcfarhat", "actifit", GADGET_JSON),
                new HiveTx.Transfer("mcfarhat", "actifit.pay", "12.345 HIVE", "buy-gadget:6012ab34cd56ef7890123456"));
        String hived = "39309e5abaa9c0e3e16a02120001086d636661726861740761637469666974437b227472616e73616374696f6e223a"
                + "20226275792d676164676574222c2022676164676574223a2022363031326162333463643536656637383930313233"
                + "343536227d02086d636661726861740b616374696669742e706179393000000000000003535445454d0000236275"
                + "792d6761646765743a3630313261623334636435366566373839303132333435360000";
        assertEquals(hived.substring(0, hived.length() - 2), HiveTx.toHex(t.serialize()));
    }

    private static void assertHived(String hivedHex, HiveTx.Op op) {
        assertEquals(hivedHex.substring(0, hivedHex.length() - 2), HiveTx.toHex(tx(op).serialize()));
    }

    @Test
    public void voteWritesWeightAsSignedInt16() {
        assertHived("39309e5abaa9c0e3e16a0100086d63666172686174076163746966697409736f6d652d706f73743cf60000",
                new HiveTx.Vote("mcfarhat", "actifit", "some-post", -2500));
    }

    @Test
    public void commentSerializesUtf8Bodies() {
        assertHived("39309e5abaa9c0e3e16a0101076163746966697409736f6d652d706f7374086d636661726861740e72652d736f6d652d"
                        + "706f73742d31001d477265617420776f726b20e28094206b65657020676f696e672120c3a9187b22617070223a2261"
                        + "6374696669742f302e31342e32227d0000",
                new HiveTx.Comment("actifit", "some-post", "mcfarhat", "re-some-post-1", "",
                        "Great work — keep going! é", "{\"app\":\"actifit/0.14.2\"}"));
    }

    @Test
    public void powerUpAndPowerDown() {
        assertHived("39309e5abaa9c0e3e16a0103086d63666172686174086d63666172686174e80300000000000003535445454d00000000",
                new HiveTx.TransferToVesting("mcfarhat", "mcfarhat", "1.000 HIVE"));
        assertHived("39309e5abaa9c0e3e16a0104086d6366617268617415cd5b070000000006564553545300000000",
                new HiveTx.WithdrawVesting("mcfarhat", "123.456789 VESTS"));
    }

    @Test
    public void claimRewardBalance() {
        assertHived("39309e5abaa9c0e3e16a0127086d63666172686174000000000000000003535445454d00007b0000000000000003534244"
                        + "000000003501b9020000000006564553545300000000",
                new HiveTx.ClaimRewardBalance("mcfarhat", "0.000 HIVE", "0.123 HBD", "45.678901 VESTS"));
    }

    @Test
    public void wrongPrecisionIsRejectedBeforeSigning() {
        try {
            tx(new HiveTx.Transfer("a", "b", "1.5 HIVE", "")).serialize();
            fail("HIVE needs 3 decimals");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            tx(new HiveTx.WithdrawVesting("a", "1.000 VESTS")).serialize();
            fail("VESTS needs 6 decimals");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    /** A real transaction @actifit broadcast on 2026-09-23 (block 110164368). */
    private static HiveTx.Transaction realActifitTx() {
        String json = "{\"op\":\"challenge_create\",\"v\":1,\"id\":\"def_weekly_step_league\",\"type\":\"league_fixture\","
                + "\"title\":\"Weekly Step League\",\"window\":{\"start\":\"2026-09-23T13:06:26.509Z\",\"end\":\"2026-09-30T13:06:26.509Z\","
                + "\"tz\":\"UTC\"},\"scoring\":{\"metric\":\"activity_count\",\"rule\":\"max\"},\"recurrence\":\"Weekly\",\"art\":\"step-league\","
                + "\"tagline\":\"Climb the weekly leaderboard by staying active every day.\",\"how_it_works\":\"Every activity you log during "
                + "the week counts toward your score. The most active members rise to the top of the league table.\",\"prize_summary\":"
                + "\"Earn AFIT and a featured spot on the weekly leaderboard.\",\"origin_tier\":\"official\",\"entry\":{\"mode\":\"free\"}}";
        return new HiveTx.Transaction(63887, 3540953098L, HiveTx.parseIsoSeconds("2026-09-23T13:07:27"),
                Collections.<HiveTx.Op>singletonList(HiveTx.CustomJson.posting("actifit", "actifit_arena", json)));
    }

    @Test
    public void reproducesTheIdOfARealOnChainTransaction() {
        assertEquals("5216f7ab940ad2771797a2c969802dcb23f116f2", realActifitTx().txId());
    }

    @Test
    public void realSignatureRecoversActifitsPostingKeyOfTheTime() {
        String onChainSig = "206f71b56c2ba3e9d445b38c1a9c1aad5f8236495eec7b6574236f9069d115c1"
                + "3117abeee1fb4f4d82f9cd9b32981778cba343da56559879a4f90fa32966d04f97";
        // @actifit's posting key from its 2026-02-02 account_update until the 2026-10-22
        // rotation — the key that signed this 2026-09-23 transaction.
        assertEquals("STM6ctE1h71HcH89rJX1uPYhAYVg8h4Dwxf3niT9magiwxGoFSkUK",
                HiveTx.recoverPublicKey(realActifitTx().digest(), onChainSig));
    }

    private static String randomWif() {
        byte[] priv = new byte[32];
        new SecureRandom().nextBytes(priv);
        priv[0] &= 0x7F; // stay well below the curve order
        priv[0] |= 0x01;
        byte[] payload = new byte[33];
        payload[0] = (byte) 0x80;
        System.arraycopy(priv, 0, payload, 1, 32);
        byte[] check = Sha256Hash.hashTwice(payload);
        byte[] wif = Arrays.copyOf(payload, 37);
        System.arraycopy(check, 0, wif, 33, 4);
        return Base58.encode(wif);
    }

    @Test
    public void ourSignaturesAreCanonicalAndRecoverOurKey() {
        for (int i = 0; i < 25; i++) {
            String wif = randomWif();
            HiveTx.Transaction t = tx(HiveTx.CustomJson.posting("mcfarhat", "actifit", GADGET_JSON));
            t.sign(wif);
            byte[] sig = HiveTx.hex(t.signatureHex);
            assertEquals(65, sig.length);
            assertTrue(HiveTx.isCanonical(Arrays.copyOfRange(sig, 1, 33), Arrays.copyOfRange(sig, 33, 65)));
            assertEquals(HiveTx.publicKeyOf(wif), HiveTx.recoverPublicKey(t.digest(), t.signatureHex));
        }
    }

    @Test
    public void badWifIsRejected() {
        String wif = randomWif();
        String broken = wif.substring(0, wif.length() - 1) + (wif.endsWith("A") ? "B" : "A");
        try {
            HiveTx.decodeWif(broken);
            fail("expected checksum failure");
        } catch (IllegalArgumentException expected) {
            // ok
        }
        try {
            HiveTx.decodeWif("STM681mj7t76AU2VPgRioyy4xUn4ncS2Fp4h6CoLb8KNgJpF6EJmA");
            fail("a public key is not a private key");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void refBlockPrefixComesFromBytesFourToEightLittleEndian() {
        HiveTx.Transaction t = HiveTx.Transaction.forHeadBlock(0x12345678L,
                "0123456789abcdef0011223344556677", EXPIRATION, Collections.<HiveTx.Op>emptyList());
        assertEquals(0x5678, t.refBlockNum);
        assertEquals(0xefcdab89L, t.refBlockPrefix);
    }

    @Test
    public void operationsJsonIsWhatTheChainReturns() {
        HiveTx.Transaction t = tx(HiveTx.CustomJson.posting("mcfarhat", "actifit", GADGET_JSON));
        assertEquals("[[\"custom_json\",{\"required_auths\":[],\"required_posting_auths\":[\"mcfarhat\"],\"id\":\"actifit\","
                        + "\"json\":\"{\\\"transaction\\\": \\\"buy-gadget\\\", \\\"gadget\\\": \\\"6012ab34cd56ef7890123456\\\"}\"}]]",
                t.operationsJson());
    }

    @Test
    public void amountsAlwaysCarryThreeDecimals() {
        assertEquals("12.300 HIVE", HiveTx.formatAmount(12.3, "HIVE"));
        assertEquals("0.100 HBD", HiveTx.formatAmount(0.1, "HBD"));
        try {
            HiveTx.formatAmount(0, "HIVE");
            fail("zero amount");
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    @Test
    public void expirationRoundTripsAsUtc() {
        assertEquals("2026-10-28T12:00:00", HiveTx.isoSeconds(EXPIRATION));
        assertEquals(BigInteger.valueOf(EXPIRATION), BigInteger.valueOf(HiveTx.parseIsoSeconds("2026-10-28T12:00:00")));
    }
}
