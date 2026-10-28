package io.actifit.fitnesstracker.actifitfitnesstracker;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads the backend's reply to a gadget confirm call (buy / activate / deactivate).
 * Pure Java so it can be unit-tested; mirrors the web's $confirmFailed + gadgetBuyErrorText.
 */
public final class GadgetReply {

    private GadgetReply() {}

    public enum Kind {
        OK,
        /** A key-rotation / try-again gate: show the server's own message. */
        GATE,
        INSUFFICIENT_AFIT,
        /** Another AFIT action for this account is in flight; nothing was charged. */
        BUSY,
        /** The product can't be bought (gone, not a gadget, bad price); nothing was charged. */
        UNAVAILABLE,
        /** Session missing/expired (checkHdrs answers 200 with success:false). */
        SESSION,
        FAILED
    }

    private static final List<String> GATE_CODES =
            Arrays.asList("KEY_ROTATION_REQUIRED", "KEY_ROTATION_HOLD", "KEY_ROTATION_RECOMMENDED", "TRY_AGAIN");
    private static final Pattern INSUFFICIENT = Pattern.compile("not have enough AFIT funds", Pattern.CASE_INSENSITIVE);
    private static final Pattern BUSY = Pattern.compile("AFIT transaction for this account is being processed",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern UNAVAILABLE = Pattern.compile(
            "^(Product not found|Product is not an in-game gadget|Product cannot be bought|Product price is not valid)",
            Pattern.CASE_INSENSITIVE);

    /**
     * @param hasError  the reply has a truthy "error" field
     * @param error     that field as text ("" if it isn't a string)
     * @param status    the "status" field ("" if absent)
     * @param success   the "success" field, or null if absent
     * @param code      the "code" field ("" if absent)
     */
    public static Kind classify(boolean hasError, String error, String status, Boolean success, String code) {
        if (code != null && GATE_CODES.contains(code)) return Kind.GATE;
        // Allowlist: every gadget route answers {status:'Success'} or {status:'success'} when
        // it worked. Anything else — including an empty object — is not a success.
        boolean ok = !hasError && ("success".equalsIgnoreCase(status)
                || ((status == null || status.isEmpty()) && Boolean.TRUE.equals(success)));
        if (ok) return Kind.OK;
        String e = error == null ? "" : error;
        if (INSUFFICIENT.matcher(e).find()) return Kind.INSUFFICIENT_AFIT;
        if (BUSY.matcher(e).find()) return Kind.BUSY;
        if (UNAVAILABLE.matcher(e).find()) return Kind.UNAVAILABLE;
        if (Boolean.FALSE.equals(success) && !hasError && !"error".equals(status)) return Kind.SESSION;
        return Kind.FAILED;
    }

    private static final Pattern ALREADY = Pattern.compile("trx already exists|Transaction already verified",
            Pattern.CASE_INSENSITIVE);

    /** The server has already used this transaction id and won't process it again. */
    public static boolean alreadyProcessed(String error) {
        return error != null && ALREADY.matcher(error).find();
    }
}
