package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.params.MainNetParams;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Hive account keys derived from the master password on the device, the same way as dhive's
 * PrivateKey.fromLogin: private key = sha256(username + role + password). Only the public keys
 * ever leave the phone.
 */
final class SignupKeys {
    static final String[] ROLES = {"owner", "active", "posting", "memo"};

    private SignupKeys() {}

    static byte[] privateKey(String username, String role, String masterPassword) {
        return Sha256Hash.hash((username + role + masterPassword).getBytes(StandardCharsets.UTF_8));
    }

    /** The posting private key (WIF) the user logs in to Actifit with. */
    static String postingWif(String username, String masterPassword) {
        return ECKey.fromPrivate(privateKey(username, "posting", masterPassword), false)
                .getPrivateKeyAsWiF(MainNetParams.get());
    }

    /** The four "STM…" public keys, keyed by role, in ROLES order. */
    static Map<String, String> publicKeys(String username, String masterPassword) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String role : ROLES) {
            ECKey key = ECKey.fromPrivate(privateKey(username, role, masterPassword), true);
            out.put(role, HiveTx.publicKeyString(key.getPubKey()));
        }
        return out;
    }
}
