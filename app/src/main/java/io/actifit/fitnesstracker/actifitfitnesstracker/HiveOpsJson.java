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
                return new HiveTx.Vote(p.optString("voter"), p.optString("author"), p.optString("permlink"),
                        p.optInt("weight"));
            case "comment":
                return new HiveTx.Comment(p.optString("parent_author"), p.optString("parent_permlink"),
                        p.optString("author"), p.optString("permlink"), p.optString("title"),
                        String.valueOf(p.opt("body") == null ? "" : p.opt("body")), p.optString("json_metadata"));
            case "transfer":
                return new HiveTx.Transfer(p.optString("from"), p.optString("to"), asset(p.optString("amount")),
                        p.optString("memo"));
            case "transfer_to_vesting":
                return new HiveTx.TransferToVesting(p.optString("from"), p.optString("to"), asset(p.optString("amount")));
            case "withdraw_vesting":
                return new HiveTx.WithdrawVesting(p.optString("account"), asset(p.optString("vesting_shares")));
            case "custom_json":
                return new HiveTx.CustomJson(strings(p.opt("required_auths")), strings(p.opt("required_posting_auths")),
                        p.optString("id"), p.optString("json"));
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
        if (num.indexOf('.') < 0 && num.indexOf(',') == num.lastIndexOf(',')) num = num.replace(',', '.');
        return num + s.substring(space);
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
