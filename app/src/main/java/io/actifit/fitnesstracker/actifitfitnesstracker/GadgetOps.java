package io.actifit.fitnesstracker.actifitfitnesstracker;

/**
 * The gadget operations, built exactly as the web builds them (components/Product.vue).
 * Pure Java so the tests can pin them: the backend matches the signed operation against
 * the chain, so changing a string here changes what gets verified.
 */
public final class GadgetOps {

    private GadgetOps() {}

    public static HiveTx.Op buy(String user, String gadgetId) {
        return HiveTx.CustomJson.posting(user, "actifit",
                "{\"transaction\": \"buy-gadget\", \"gadget\": \"" + gadgetId + "\"}");
    }

    /** benefic: the friend receiving the boost, or null/empty. */
    public static HiveTx.Op activate(String user, String gadgetId, String benefic) {
        String json = benefic == null || benefic.isEmpty()
                ? "{\"transaction\": \"activate-gadget\" , \"gadget\": \"" + gadgetId + "\"}"
                : "{\"transaction\": \"activate-gadget\" , \"gadget\": \"" + gadgetId + "\", \"benefic\": \"" + benefic + "\"}";
        return HiveTx.CustomJson.posting(user, "actifit", json);
    }

    public static HiveTx.Op deactivate(String user, String gadgetId) {
        return HiveTx.CustomJson.posting(user, "actifit",
                "{\"transaction\": \"deactivate-gadget\" , \"gadget\": \"" + gadgetId + "\"}");
    }

    /** amount: e.g. "12.346 HIVE" (see HiveTx.formatAmount). */
    public static HiveTx.Op buyWithHive(String user, String market, String gadgetId, String amount) {
        return new HiveTx.Transfer(user, market, amount, "buy-gadget:" + gadgetId);
    }
}
