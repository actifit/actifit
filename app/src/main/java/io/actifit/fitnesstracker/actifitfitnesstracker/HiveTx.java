package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.bitcoinj.core.Base58;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.Sha256Hash;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.crypto.params.ECPrivateKeyParameters;
import org.bouncycastle.crypto.params.ParametersWithRandom;
import org.bouncycastle.crypto.signers.ECDSASigner;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.List;

/**
 * Builds, serializes and signs Hive transactions on the device.
 *
 * Since the 2026-10-22 security lockdown the backend no longer signs for the app
 * (/performTrx is gone), so every on-chain write is signed here with the user's own key
 * and broadcast straight to a Hive node ({@link HiveBroadcaster}); the backend only
 * confirms the transaction from the chain afterwards.
 *
 * Pure Java (no Android, no org.json) so the JVM unit tests can check it against real
 * on-chain transactions. The binary format follows hived's serializer: little-endian
 * integers, varint-prefixed strings and arrays, and assets with the LEGACY symbols
 * ("STEEM" for HIVE, "SBD" for HBD) — verified against condenser_api.get_transaction_hex.
 */
public final class HiveTx {

    private HiveTx() {}

    /** Hive mainnet chain id. */
    public static final String CHAIN_ID = "beeab0de00000000000000000000000000000000000000000000000000000000";

    // ---------------------------------------------------------------- operations

    /** One operation: binary form for signing, condenser JSON form for broadcast/confirm. */
    public interface Op {
        void write(ByteArrayOutputStream out);

        /** e.g. ["custom_json",{...}] — the exact shape the chain returns for the op. */
        String toJson();
    }

    public static final class CustomJson implements Op {
        public final List<String> requiredAuths;
        public final List<String> requiredPostingAuths;
        public final String id;
        public final String json;

        public CustomJson(List<String> requiredAuths, List<String> requiredPostingAuths, String id, String json) {
            this.requiredAuths = requiredAuths;
            this.requiredPostingAuths = requiredPostingAuths;
            this.id = id;
            this.json = json;
        }

        /** The common case: a posting-auth custom_json signed by one account. */
        public static CustomJson posting(String account, String id, String json) {
            return new CustomJson(java.util.Collections.<String>emptyList(),
                    java.util.Collections.singletonList(account), id, json);
        }

        @Override
        public void write(ByteArrayOutputStream out) {
            writeVarint(out, 18);
            writeStringArray(out, requiredAuths);
            writeStringArray(out, requiredPostingAuths);
            writeString(out, id);
            writeString(out, json);
        }

        @Override
        public String toJson() {
            return "[\"custom_json\",{\"required_auths\":" + jsonArray(requiredAuths)
                    + ",\"required_posting_auths\":" + jsonArray(requiredPostingAuths)
                    + ",\"id\":" + jsonString(id) + ",\"json\":" + jsonString(json) + "}]";
        }
    }

    public static final class Transfer implements Op {
        public final String from;
        public final String to;
        /** e.g. "12.345 HIVE" or "0.001 HBD" — always 3 decimals, as the chain reports it. */
        public final String amount;
        public final String memo;

        public Transfer(String from, String to, String amount, String memo) {
            this.from = from;
            this.to = to;
            this.amount = amount;
            this.memo = memo == null ? "" : memo;
        }

        @Override
        public void write(ByteArrayOutputStream out) {
            writeVarint(out, 2);
            writeString(out, from);
            writeString(out, to);
            writeAsset(out, amount);
            writeString(out, memo);
        }

        @Override
        public String toJson() {
            return "[\"transfer\",{\"from\":" + jsonString(from) + ",\"to\":" + jsonString(to)
                    + ",\"amount\":" + jsonString(amount) + ",\"memo\":" + jsonString(memo) + "}]";
        }
    }

    public static final class Vote implements Op {
        public final String voter;
        public final String author;
        public final String permlink;
        /** -10000..10000 (basis points). */
        public final int weight;

        public Vote(String voter, String author, String permlink, int weight) {
            if (weight < -10000 || weight > 10000) throw new IllegalArgumentException("weight");
            this.voter = voter;
            this.author = author;
            this.permlink = permlink;
            this.weight = weight;
        }

        @Override
        public void write(ByteArrayOutputStream out) {
            writeVarint(out, 0);
            writeString(out, voter);
            writeString(out, author);
            writeString(out, permlink);
            writeUint16(out, weight & 0xFFFF); // int16, two's complement
        }

        @Override
        public String toJson() {
            return "[\"vote\",{\"voter\":" + jsonString(voter) + ",\"author\":" + jsonString(author)
                    + ",\"permlink\":" + jsonString(permlink) + ",\"weight\":" + weight + "}]";
        }
    }

    public static final class Comment implements Op {
        public final String parentAuthor;
        public final String parentPermlink;
        public final String author;
        public final String permlink;
        public final String title;
        public final String body;
        public final String jsonMetadata;

        public Comment(String parentAuthor, String parentPermlink, String author, String permlink,
                       String title, String body, String jsonMetadata) {
            this.parentAuthor = parentAuthor == null ? "" : parentAuthor;
            this.parentPermlink = parentPermlink == null ? "" : parentPermlink;
            this.author = author;
            this.permlink = permlink;
            this.title = title == null ? "" : title;
            this.body = body == null ? "" : body;
            this.jsonMetadata = jsonMetadata == null ? "" : jsonMetadata;
        }

        @Override
        public void write(ByteArrayOutputStream out) {
            writeVarint(out, 1);
            writeString(out, parentAuthor);
            writeString(out, parentPermlink);
            writeString(out, author);
            writeString(out, permlink);
            writeString(out, title);
            writeString(out, body);
            writeString(out, jsonMetadata);
        }

        @Override
        public String toJson() {
            return "[\"comment\",{\"parent_author\":" + jsonString(parentAuthor) + ",\"parent_permlink\":"
                    + jsonString(parentPermlink) + ",\"author\":" + jsonString(author) + ",\"permlink\":"
                    + jsonString(permlink) + ",\"title\":" + jsonString(title) + ",\"body\":" + jsonString(body)
                    + ",\"json_metadata\":" + jsonString(jsonMetadata) + "}]";
        }
    }

    /** Power up. */
    public static final class TransferToVesting implements Op {
        public final String from;
        public final String to;
        public final String amount; // "1.000 HIVE"

        public TransferToVesting(String from, String to, String amount) {
            this.from = from;
            this.to = to == null || to.isEmpty() ? from : to;
            this.amount = amount;
        }

        @Override
        public void write(ByteArrayOutputStream out) {
            writeVarint(out, 3);
            writeString(out, from);
            writeString(out, to);
            writeAsset(out, amount);
        }

        @Override
        public String toJson() {
            return "[\"transfer_to_vesting\",{\"from\":" + jsonString(from) + ",\"to\":" + jsonString(to)
                    + ",\"amount\":" + jsonString(amount) + "}]";
        }
    }

    /** Power down. */
    public static final class WithdrawVesting implements Op {
        public final String account;
        public final String vestingShares; // "123.456789 VESTS"

        public WithdrawVesting(String account, String vestingShares) {
            this.account = account;
            this.vestingShares = vestingShares;
        }

        @Override
        public void write(ByteArrayOutputStream out) {
            writeVarint(out, 4);
            writeString(out, account);
            writeAsset(out, vestingShares);
        }

        @Override
        public String toJson() {
            return "[\"withdraw_vesting\",{\"account\":" + jsonString(account) + ",\"vesting_shares\":"
                    + jsonString(vestingShares) + "}]";
        }
    }

    /** Claim pending author/curation rewards (posting authority). */
    public static final class ClaimRewardBalance implements Op {
        public final String account;
        public final String rewardHive;   // "0.000 HIVE"
        public final String rewardHbd;    // "0.000 HBD"
        public final String rewardVests;  // "0.000000 VESTS"

        public ClaimRewardBalance(String account, String rewardHive, String rewardHbd, String rewardVests) {
            this.account = account;
            this.rewardHive = rewardHive;
            this.rewardHbd = rewardHbd;
            this.rewardVests = rewardVests;
        }

        @Override
        public void write(ByteArrayOutputStream out) {
            writeVarint(out, 39);
            writeString(out, account);
            writeAsset(out, rewardHive);
            writeAsset(out, rewardHbd);
            writeAsset(out, rewardVests);
        }

        @Override
        public String toJson() {
            return "[\"claim_reward_balance\",{\"account\":" + jsonString(account) + ",\"reward_hive\":"
                    + jsonString(rewardHive) + ",\"reward_hbd\":" + jsonString(rewardHbd) + ",\"reward_vests\":"
                    + jsonString(rewardVests) + "}]";
        }
    }

    /** "12.3 HIVE" → chain asset string with exactly 3 decimals; rejects anything else. */
    public static String formatAmount(double value, String symbol) {
        if (!("HIVE".equals(symbol) || "HBD".equals(symbol))) throw new IllegalArgumentException("symbol");
        if (!(value > 0) || Double.isInfinite(value)) throw new IllegalArgumentException("amount");
        return java.math.BigDecimal.valueOf(value).setScale(3, java.math.RoundingMode.HALF_UP).toPlainString() + " " + symbol;
    }

    // ---------------------------------------------------------------- transaction

    /** A transaction ready to sign: header fields + operations. */
    public static final class Transaction {
        public final int refBlockNum;          // uint16
        public final long refBlockPrefix;      // uint32
        public final long expirationSec;       // uint32, unix seconds UTC
        public final List<Op> operations;
        public String signatureHex;            // set by sign()

        public Transaction(int refBlockNum, long refBlockPrefix, long expirationSec, List<Op> operations) {
            this.refBlockNum = refBlockNum & 0xFFFF;
            this.refBlockPrefix = refBlockPrefix & 0xFFFFFFFFL;
            this.expirationSec = expirationSec;
            this.operations = operations;
        }

        /** Header fields from the head block id, as hived expects them. */
        public static Transaction forHeadBlock(long headBlockNumber, String headBlockIdHex, long expirationSec,
                                               List<Op> operations) {
            byte[] id = hex(headBlockIdHex);
            long prefix = (id[4] & 0xFFL) | ((id[5] & 0xFFL) << 8) | ((id[6] & 0xFFL) << 16) | ((id[7] & 0xFFL) << 24);
            return new Transaction((int) (headBlockNumber & 0xFFFF), prefix, expirationSec, operations);
        }

        /** The signed bytes: everything but the signatures. */
        public byte[] serialize() {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            writeUint16(out, refBlockNum);
            writeUint32(out, refBlockPrefix);
            writeUint32(out, expirationSec);
            writeVarint(out, operations.size());
            for (Op op : operations) op.write(out);
            writeVarint(out, 0); // extensions
            return out.toByteArray();
        }

        /** Hive's transaction id: the first 20 bytes of sha256(serialized tx), hex. */
        public String txId() {
            return toHex(Arrays.copyOf(sha256(serialize()), 20));
        }

        public byte[] digest() {
            byte[] chain = hex(CHAIN_ID);
            byte[] body = serialize();
            byte[] all = new byte[chain.length + body.length];
            System.arraycopy(chain, 0, all, 0, chain.length);
            System.arraycopy(body, 0, all, chain.length, body.length);
            return sha256(all);
        }

        /** Signs with a WIF private key (posting for custom_json, active for transfers). */
        public void sign(String wif) {
            signatureHex = toHex(signCanonical(digest(), decodeWif(wif)));
        }

        /** The condenser_api.broadcast_transaction payload. */
        public String toJson() {
            StringBuilder ops = new StringBuilder("[");
            for (int i = 0; i < operations.size(); i++) {
                if (i > 0) ops.append(',');
                ops.append(operations.get(i).toJson());
            }
            ops.append(']');
            return "{\"ref_block_num\":" + refBlockNum + ",\"ref_block_prefix\":" + refBlockPrefix
                    + ",\"expiration\":" + jsonString(isoSeconds(expirationSec))
                    + ",\"operations\":" + ops + ",\"extensions\":[],\"signatures\":"
                    + (signatureHex == null ? "[]" : "[" + jsonString(signatureHex) + "]") + "}";
        }

        /** The ops array alone, e.g. for the backend's ?operation= confirm parameter. */
        public String operationsJson() {
            StringBuilder ops = new StringBuilder("[");
            for (int i = 0; i < operations.size(); i++) {
                if (i > 0) ops.append(',');
                ops.append(operations.get(i).toJson());
            }
            return ops.append(']').toString();
        }
    }

    // ---------------------------------------------------------------- keys & signing

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Decodes and checksums a WIF private key; throws IllegalArgumentException if invalid. */
    public static BigInteger decodeWif(String wif) {
        byte[] raw;
        try {
            raw = Base58.decode(wif == null ? "" : wif.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException("not a private key");
        }
        if (raw.length != 37 || raw[0] != (byte) 0x80) throw new IllegalArgumentException("not a private key");
        byte[] check = Sha256Hash.hashTwice(raw, 0, 33);
        for (int i = 0; i < 4; i++) {
            if (check[i] != raw[33 + i]) throw new IllegalArgumentException("private key checksum mismatch");
        }
        return new BigInteger(1, Arrays.copyOfRange(raw, 1, 33));
    }

    /** "STM…" public key string for a WIF — used to check a stored key belongs to the account. */
    public static String publicKeyOf(String wif) {
        return publicKeyString(ECKey.fromPrivate(decodeWif(wif), true).getPubKey());
    }

    public static String publicKeyString(byte[] compressedPub) {
        RIPEMD160Digest r = new RIPEMD160Digest();
        r.update(compressedPub, 0, compressedPub.length);
        byte[] h = new byte[20];
        r.doFinal(h, 0);
        byte[] withCheck = Arrays.copyOf(compressedPub, compressedPub.length + 4);
        System.arraycopy(h, 0, withCheck, compressedPub.length, 4);
        return "STM" + Base58.encode(withCheck);
    }

    /** Public key that produced a 65-byte compact signature over a digest (for checks/tests). */
    public static String recoverPublicKey(byte[] digest, String signatureHex) {
        byte[] sig = hex(signatureHex);
        int recId = (sig[0] & 0xFF) - 31;
        if (recId < 0 || recId > 3) throw new IllegalArgumentException("not a compressed compact signature");
        BigInteger r = new BigInteger(1, Arrays.copyOfRange(sig, 1, 33));
        BigInteger s = new BigInteger(1, Arrays.copyOfRange(sig, 33, 65));
        ECKey key = ECKey.recoverFromSignature(recId, new ECKey.ECDSASignature(r, s), Sha256Hash.wrap(digest), true);
        if (key == null) throw new IllegalArgumentException("signature does not recover");
        return publicKeyString(key.getPubKey());
    }

    /**
     * 65-byte compact recoverable signature that hived accepts: low-S, and "fc canonical"
     * (neither r nor s may have its top bit set or be zero-padded). A fresh random nonce
     * is drawn until the result is canonical — about half the attempts qualify (low-S
     * already clears s's top bit; r's is a coin flip).
     */
    static byte[] signCanonical(byte[] digest, BigInteger priv) {
        ECKey key = ECKey.fromPrivate(priv, true);
        byte[] pub = key.getPubKey();
        BigInteger n = ECKey.CURVE.getN();
        for (int attempt = 0; attempt < 1000; attempt++) {
            ECDSASigner signer = new ECDSASigner();
            signer.init(true, new ParametersWithRandom(new ECPrivateKeyParameters(priv, ECKey.CURVE), RANDOM));
            BigInteger[] rs = signer.generateSignature(digest);
            BigInteger r = rs[0];
            BigInteger s = rs[1];
            if (s.compareTo(n.shiftRight(1)) > 0) s = n.subtract(s);
            byte[] rb = to32(r);
            byte[] sb = to32(s);
            if (!isCanonical(rb, sb)) continue;
            for (int recId = 0; recId < 4; recId++) {
                ECKey k = ECKey.recoverFromSignature(recId, new ECKey.ECDSASignature(r, s), Sha256Hash.wrap(digest), true);
                if (k != null && Arrays.equals(k.getPubKey(), pub)) {
                    byte[] out = new byte[65];
                    out[0] = (byte) (31 + recId);
                    System.arraycopy(rb, 0, out, 1, 32);
                    System.arraycopy(sb, 0, out, 33, 32);
                    return out;
                }
            }
        }
        throw new IllegalStateException("could not produce a canonical signature");
    }

    static boolean isCanonical(byte[] r, byte[] s) {
        return (r[0] & 0x80) == 0 && !(r[0] == 0 && (r[1] & 0x80) == 0)
                && (s[0] & 0x80) == 0 && !(s[0] == 0 && (s[1] & 0x80) == 0);
    }

    private static byte[] to32(BigInteger v) {
        byte[] b = v.toByteArray();
        if (b.length == 32) return b;
        byte[] out = new byte[32];
        if (b.length > 32) System.arraycopy(b, b.length - 32, out, 0, 32);
        else System.arraycopy(b, 0, out, 32 - b.length, b.length);
        return out;
    }

    // ---------------------------------------------------------------- serialization primitives

    static void writeVarint(ByteArrayOutputStream out, long v) {
        while ((v & ~0x7FL) != 0) {
            out.write((int) ((v & 0x7F) | 0x80));
            v >>>= 7;
        }
        out.write((int) v);
    }

    static void writeUint16(ByteArrayOutputStream out, int v) {
        out.write(v & 0xFF);
        out.write((v >>> 8) & 0xFF);
    }

    static void writeUint32(ByteArrayOutputStream out, long v) {
        for (int i = 0; i < 4; i++) out.write((int) ((v >>> (8 * i)) & 0xFF));
    }

    /**
     * Control characters other than tab/newline/CR don't survive the node's JSON path
     * byte-for-byte (\u0001 arrives as "u0001"), so what the node re-serializes would
     * differ from what we signed. Both the binary and the JSON forms drop them.
     */
    static String clean(String s) {
        if (s == null) return "";
        StringBuilder b = null;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean drop = c < 0x20 && c != '\t' && c != '\n' && c != '\r';
            if (drop && b == null) b = new StringBuilder(s.substring(0, i));
            else if (!drop && b != null) b.append(c);
        }
        return b == null ? s : b.toString();
    }

    static void writeString(ByteArrayOutputStream out, String s) {
        byte[] b = clean(s).getBytes(StandardCharsets.UTF_8);
        writeVarint(out, b.length);
        out.write(b, 0, b.length);
    }

    static void writeStringArray(ByteArrayOutputStream out, List<String> items) {
        writeVarint(out, items.size());
        for (String s : items) writeString(out, s);
    }

    /** "12.345 HIVE" → int64 satoshis, precision byte, 7-byte legacy symbol (VESTS: 6 decimals). */
    static void writeAsset(ByteArrayOutputStream out, String asset) {
        String[] parts = asset.trim().split(" ");
        if (parts.length != 2) throw new IllegalArgumentException("asset: " + asset);
        String num = parts[0];
        // ASCII digits only: Arabic/Persian locales format numbers with other digits, which
        // must never reach the chain (BigInteger would quietly accept some of them).
        if (!num.matches("[0-9]+\\.[0-9]+")) throw new IllegalArgumentException("asset: " + asset);
        int dot = num.indexOf('.');
        int precision = num.length() - dot - 1;
        String symbol;
        int expected;
        if ("HIVE".equals(parts[1])) { symbol = "STEEM"; expected = 3; }
        else if ("HBD".equals(parts[1])) { symbol = "SBD"; expected = 3; }
        else if ("VESTS".equals(parts[1])) { symbol = "VESTS"; expected = 6; }
        else throw new IllegalArgumentException("unsupported asset: " + parts[1]);
        if (precision != expected) {
            throw new IllegalArgumentException(parts[1] + " amounts need " + expected + " decimals: " + asset);
        }
        long satoshis = new BigInteger(num.replace(".", "")).longValueExact();
        for (int i = 0; i < 8; i++) out.write((int) ((satoshis >>> (8 * i)) & 0xFF));
        out.write(precision);
        byte[] sym = Arrays.copyOf(symbol.getBytes(StandardCharsets.US_ASCII), 7);
        out.write(sym, 0, 7);
    }

    // ---------------------------------------------------------------- helpers

    static byte[] sha256(byte[] in) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(in);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    public static String isoSeconds(long epochSec) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
        f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
        return f.format(new java.util.Date(epochSec * 1000L));
    }

    public static long parseIsoSeconds(String iso) {
        try {
            java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US);
            f.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            return f.parse(iso).getTime() / 1000L;
        } catch (Exception e) {
            throw new IllegalArgumentException("time: " + iso);
        }
    }

    static String jsonString(String raw) {
        String s = clean(raw);
        StringBuilder b = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': b.append("\\\""); break;
                case '\\': b.append("\\\\"); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                default:
                    if (c < 0x20) b.append(String.format(java.util.Locale.US, "\\u%04x", (int) c));
                    else b.append(c);
            }
        }
        return b.append('"').toString();
    }

    static String jsonArray(List<String> items) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) b.append(',');
            b.append(jsonString(items.get(i)));
        }
        return b.append(']').toString();
    }

    public static String toHex(byte[] b) {
        StringBuilder s = new StringBuilder(b.length * 2);
        for (byte x : b) s.append(String.format(java.util.Locale.US, "%02x", x & 0xFF));
        return s.toString();
    }

    public static byte[] hex(String h) {
        byte[] out = new byte[h.length() / 2];
        for (int i = 0; i < out.length; i++) out[i] = (byte) Integer.parseInt(h.substring(2 * i, 2 * i + 2), 16);
        return out;
    }
}
