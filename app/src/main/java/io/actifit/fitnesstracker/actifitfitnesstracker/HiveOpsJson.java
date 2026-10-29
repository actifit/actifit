package io.actifit.fitnesstracker.actifitfitnesstracker;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Turns the (op_name, params) pairs the app's dialogs already build into signable
 * {@link HiveTx.Op}s. Supports exactly the operations the app broadcasts: vote, comment,
 * transfer, transfer_to_vesting, withdraw_vesting and custom_json.
 */
final class HiveOpsJson {

    private HiveOpsJson() {}

    static HiveTx.Op fromJson(String opName, JSONObject p) {
        switch (opName) {
            case "vote":
                // A missing weight must not silently become 0 (which is an unvote).
                if (!p.has("weight")) throw new IllegalArgumentException("vote without weight");
                return new HiveTx.Vote(s(p, "voter"), s(p, "author"), s(p, "permlink"), p.optInt("weight"));
            case "comment":
                return new HiveTx.Comment(s(p, "parent_author"), s(p, "parent_permlink"), s(p, "author"),
                        s(p, "permlink"), s(p, "title"), s(p, "body"), s(p, "json_metadata"));
            case "transfer":
                return new HiveTx.Transfer(s(p, "from"), s(p, "to"), asset(s(p, "amount")), s(p, "memo"));
            case "transfer_to_vesting":
                return new HiveTx.TransferToVesting(s(p, "from"), s(p, "to"), asset(s(p, "amount")));
            case "withdraw_vesting":
                return new HiveTx.WithdrawVesting(s(p, "account"), asset(s(p, "vesting_shares")));
            case "custom_json":
                return new HiveTx.CustomJson(strings(p.opt("required_auths")), strings(p.opt("required_posting_auths")),
                        s(p, "id"), s(p, "json"));
            default:
                throw new IllegalArgumentException("unsupported operation: " + opName);
        }
    }

    /**
     * Several dialogs format amounts with String.format("%.3f"), which writes "1,000" in
     * comma-decimal locales; the chain only accepts a dot. No grouping is used, so a lone
     * comma is the decimal separator.
     */
    static String asset(String a) {
        String s = a == null ? "" : a.trim();
        int space = s.indexOf(' ');
        if (space < 0) return s;
        String num = s.substring(0, space);
        int comma = num.indexOf(',');
        // Only a single comma followed by exactly 3 or 6 digits (the asset precisions) is a
        // decimal separator; "1,23" or "1,000,000" stay as they are and are then rejected rather than misread.
        int decimals = num.length() - comma - 1;
        boolean decimalComma = num.indexOf('.') < 0 && comma > 0 && comma == num.lastIndexOf(',')
                && (decimals == 3 || (decimals == 6 && s.endsWith(" VESTS")));
        if (decimalComma) num = num.replace(',', '.');
        return num + s.substring(space);
    }

    /** A JSON field as text; absent or JSON null becomes "" (optString would give "null"). */
    private static String s(JSONObject p, String key) {
        if (!p.has(key) || p.isNull(key)) return "";
        Object v = p.opt(key);
        return v == null ? "" : String.valueOf(v);
    }

    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof JSONArray) {
            JSONArray a = (JSONArray) o;
            for (int i = 0; i < a.length(); i++) out.add(a.optString(i));
        } else if (o instanceof String && !((String) o).isEmpty() && !"[]".equals(o)) {
            out.add((String) o);
        }
        return out;
    }
}
