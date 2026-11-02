package io.actifit.fitnesstracker.actifitfitnesstracker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;

public class SignupKeysTest {
    // Expected values from dhive: PrivateKey.fromLogin(user, password, role).createPublic()
    private static final String USER = "testvector";
    private static final String PASSWORD = "P5KtestVectorOnlyNotARealPassword+/=";

    @Test
    public void publicKeysMatchDhive() {
        Map<String, String> keys = SignupKeys.publicKeys(USER, PASSWORD);
        assertEquals(Arrays.asList("owner", "active", "posting", "memo"), Arrays.asList(keys.keySet().toArray()));
        assertEquals("STM5Wo3ecBs2SC6pH7C1tDVaagnBSW1Gtzn8uSjgQucnB7S2r2YTU", keys.get("owner"));
        assertEquals("STM8WyNEvJg27Fg7CQQnJJtSSK66tWmw2ngB6X7nfi9qgkpG5Rpvb", keys.get("active"));
        assertEquals("STM6eDi6UnLKgmpMitzbh9g74smWSbAT18GCmtXbZti85jQgxBMaz", keys.get("posting"));
        assertEquals("STM8Cu2hziA4Nfa3LeRPxg77p4MRRoNyqJCixvQQQDigpK6sfPY7h", keys.get("memo"));
    }

    @Test
    public void keysDependOnUsernameAndPassword() {
        Map<String, String> a = SignupKeys.publicKeys(USER, PASSWORD);
        assertFalse(a.equals(SignupKeys.publicKeys(USER + "x", PASSWORD)));
        assertFalse(a.equals(SignupKeys.publicKeys(USER, PASSWORD + "x")));
        assertEquals(4, new HashSet<>(a.values()).size());
    }

    @Test
    public void postingKeyMatchesTheLoginKeyShownToTheUser() {
        // the posting WIF shown on the last screen must belong to the posting public key sent
        String wif = SignupKeys.postingWif(USER, PASSWORD);
        assertTrue(wif.startsWith("5"));
        assertEquals(SignupKeys.publicKeys(USER, PASSWORD).get("posting"), HiveTx.publicKeyOf(wif));
    }

    @Test
    public void serverMemoFormat() {
        assertTrue(SignupOutcome.isServerMemo("signup:0123456789abcdef01234567"));
        assertFalse(SignupOutcome.isServerMemo("signup:ABCDEFGHJK"));
        assertFalse(SignupOutcome.isServerMemo(""));
        assertFalse(SignupOutcome.isServerMemo(null));
    }
}
