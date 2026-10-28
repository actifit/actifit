package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.text.Html;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.cardview.widget.CardView;

import com.android.volley.AuthFailureError;
import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.bumptech.glide.Glide;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class ProductAdapter extends ArrayAdapter<SingleProductModel> {

    String username, pkey;

    // Shared across rows so the friends list is fetched once per Market session.
    private FriendAccountAdapter friendAccountAdapter;
    // JSONArray consumedProducts;
    ArrayList reqtsMet;
    ProgressDialog progress;
    Context ctx;

    public ProductAdapter(Context context, ArrayList<SingleProductModel> activityEntry,
            String username, String pkey) {// , JSONArray consumedProducts) {
        super(context, 0, activityEntry);

        reqtsMet = new ArrayList();

        this.username = username;
        this.pkey = pkey;
        this.ctx = context;
        // this.consumedProducts = consumedProducts;

    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {

        try {

            // Get the data item for this position
            final SingleProductModel postEntry = getItem(position);
            // Check if an existing view is being reused, otherwise inflate the view
            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.product_single_entry, parent, false);
            }

            // Lookup view for data population

            CardView entryContainer = convertView.findViewById(R.id.entryContainer);
            TextView name = convertView.findViewById(R.id.name);
            TextView level = convertView.findViewById(R.id.level);
            TextView count = convertView.findViewById(R.id.count);
            TextView type = convertView.findViewById(R.id.type);
            TextView validity = convertView.findViewById(R.id.validity);
            LinearLayout requirements = convertView.findViewById(R.id.requirements);
            TextView boosts = convertView.findViewById(R.id.boosts);
            ImageView image = convertView.findViewById(R.id.image);
            Button buyAFIT = convertView.findViewById(R.id.buyAFIT);
            Button buyHIVE = convertView.findViewById(R.id.buyHIVE);
            Button activateGadget = convertView.findViewById(R.id.activateGadget);
            Button deactivateGadget = convertView.findViewById(R.id.deactivateGadget);
            AutoCompleteTextView friendBeneficiary = convertView.findViewById(R.id.friendBeneficiary);
            LinearLayout boughtInfo = convertView.findViewById(R.id.boughtInfo);
            LinearLayout beneficiaryIdentity = convertView.findViewById(R.id.beneficiaryIdentity);
            ImageView beneficiaryAvatar = convertView.findViewById(R.id.beneficiaryAvatar);
            TextView beneficiaryUsername = convertView.findViewById(R.id.beneficiaryUsername);

            TextView totalBought = convertView.findViewById(R.id.totalBoughtCount);
            TextView totalConsumed = convertView.findViewById(R.id.totalConsumedCount);
            TextView remainingBoosts = convertView.findViewById(R.id.remainingBoosts);

            if (!Objects.equals(postEntry.id, friendBeneficiary.getTag())) {
                friendBeneficiary.setText("");
                friendBeneficiary.setTag(postEntry.id);
                // Friends-first (then any Hive account) autocomplete for the "reward a friend" field.
                friendBeneficiary.setAdapter(getFriendAccountAdapter());
                friendBeneficiary.setThreshold(1);
            }
            bindBeneficiaryIdentity(postEntry, beneficiaryIdentity, beneficiaryAvatar, beneficiaryUsername);

            // Populate the data into the template view using the data object

            // populate requirements
            String reqsText = getContext().getString(R.string.none);

            // checkmark
            String checkMark = "&#10003;";// "&#9989;";//"✅";
            String xMark = "&#10006;";// "&#10060;";//"❌";
            int colorSuccess = getContext().getResources().getColor(R.color.actifitDarkGreen);
            int colorFail = getContext().getResources().getColor(R.color.actifitRed);

            // boolean reqtMet = true;

            if (postEntry.requirements != null && postEntry.requirements.length() > 0) {

                // cleanup first
                requirements.removeAllViews();

                if (postEntry.requirements.length() < 1) {
                    TextView reqLine = new TextView(getContext());
                    reqLine.setText(Html.fromHtml(reqsText));
                    // reqLine.setTextColor(colorChoice);
                    reqLine.setPadding(0, 0, 0, 5);

                    requirements.addView(reqLine);
                } else {
                    for (int j = 0; j < postEntry.requirements.length(); j++) {
                        JSONObject reqEntry = postEntry.requirements.getJSONObject(j);

                        reqsText = "";

                        int colorChoice = colorSuccess;

                        // process requirement
                        if (reqEntry.has("item")) {
                            if (reqEntry.getBoolean("reqtMet")) {
                                reqsText += checkMark;
                            } else {
                                reqsText += xMark;
                                colorChoice = colorFail;
                                // reqtMet = false;
                            }
                            // reqsText += getContext().getString(R.string.user_rank_reqt).replace("_VAL_",
                            // postEntry.level+"");
                            if (reqEntry.getString("item").equals("User Rank")) {
                                /*
                                 * if (Double.parseDouble(MainActivity.userRank) >=
                                 * Integer.parseInt(reqEntry.getString("level"))) {
                                 * reqsText += checkMark;
                                 * } else {
                                 * reqsText += xMark;
                                 * colorChoice = colorFail;
                                 * reqtMet = false;
                                 * }
                                 */
                                reqsText += getContext().getString(R.string.user_rank_reqt_short).replace("_VAL_",
                                        reqEntry.getString("level")) + "<br/>";
                            } else if (reqEntry.has("AFIT")) {
                                /*
                                 * if (MainActivity.userFullBalance >=
                                 * Integer.parseInt(reqEntry.getString("count"))) {
                                 * reqsText += checkMark;
                                 * } else {
                                 * reqsText += xMark;
                                 * colorChoice = colorFail;
                                 * reqtMet = false;
                                 * }
                                 */
                                reqsText += getContext().getString(R.string.user_afit_balance).replace("_VAL_",
                                        reqEntry.getString("count")) + "<br/>";
                            } else {
                                // check if requirement is met
                                /*
                                 * int consumed_count = 0;
                                 * int consumed_target = Integer.parseInt(reqEntry.getString("count"));
                                 * for (int k = 0; k < consumedProducts.length(); k++) {
                                 * // Retrieve each JSON object within the JSON array
                                 * JSONObject jsonObject = consumedProducts.getJSONObject(k);
                                 * //validate this is a matching consumed gadget
                                 * if (reqEntry.getString("item").equals(jsonObject.getString("gadget_name"))
                                 * && reqEntry.getString("level").equals(jsonObject.getString("gadget_level")))
                                 * {
                                 * consumed_count += 1;
                                 * }
                                 * }
                                 * if (consumed_count >= consumed_target) {
                                 * reqsText += checkMark;
                                 * } else {
                                 * reqsText += xMark;
                                 * colorChoice = colorFail;
                                 * reqtMet = false;
                                 * }
                                 */

                                reqsText += getContext().getString(R.string.at_Least_consumed_reqt)
                                        .replace("_AMOUNT_", reqEntry.getString("count"))
                                        .replace("_ITEM_",
                                                reqEntry.getString("item") + " - L" + reqEntry.getString("level"))
                                        + "<br/>";

                            }
                            if (!reqsText.equals("")) {
                                TextView reqLine = new TextView(getContext());
                                reqLine.setText(Html.fromHtml(reqsText));
                                reqLine.setTextColor(colorChoice);
                                reqLine.setPadding(0, 0, 0, 5);
                                requirements.addView(reqLine);
                            }
                            // reqLine.setHeight(10);
                            // reqLine.setWidth();

                        }
                    }
                }
            } else {
                TextView reqLine = new TextView(getContext());
                reqLine.setText(Html.fromHtml(reqsText));
                // reqLine.setTextColor(colorChoice);
                reqLine.setPadding(0, 0, 0, 5);

                requirements.addView(reqLine);
            }

            String boostsText = "";

            if (postEntry.boosts != null && postEntry.boosts.length() > 0) {
                boostsText = "";
                for (int j = 0; j < postEntry.boosts.length(); j++) {
                    JSONObject reqEntry = postEntry.boosts.getJSONObject(j);

                    /*
                     * if (reqEntry.getString("boost_beneficiary").equals("friend")){
                     * //flag it as friend rewarding
                     * postEntry.isFriendRewarding = true;
                     * }
                     */

                    // process requirement
                    if (reqEntry.has("boost_amount")) {
                        // reqsText += getContext().getString(R.string.user_rank_reqt).replace("_VAL_",
                        // postEntry.level+"");
                        boostsText += getContext().getString(R.string.boost_match_note)
                                .replace("_AMOUNT_", reqEntry.getString("boost_amount"))
                                .replace("_TYPE_", reqEntry.getString("boost_type")
                                        .replace("percent_reward", "%")
                                        .replace("percent", "%")
                                        .replace("unit", " "))
                                .replace("_UNIT_", reqEntry.getString("boost_unit"))
                                .replace("_RECIPIENT_",
                                        (reqEntry.getString("boost_beneficiary").equals("friend")
                                                ? getContext().getString(R.string.to_friend)
                                                : getContext().getString(R.string.to_you)))
                                + "<br/>";
                    } else if (reqEntry.has("boost_min_amount")) {
                        boostsText += getContext().getString(R.string.boost_match_note)
                                .replace("_AMOUNT_", reqEntry.getString("boost_amount"))
                                .replace("percent_reward", "%")
                                .replace("percent", "%")
                                .replace("unit", " ")
                                .replace("_TYPE_", reqEntry.getString("boost_unit"))
                                .replace("_RECIPIENT_",
                                        (reqEntry.getString("boost_beneficiary").equals("friend")
                                                ? getContext().getString(R.string.to_friend)
                                                : getContext().getString(R.string.to_you)))
                                + "<br/>";
                    }
                }
            }

            // Buttons stay off while an action for this gadget is running (also after the
            // row is recycled), so one tap can't turn into two purchases.
            final boolean busy = GadgetApi.isBusy(MainActivity.username, postEntry.id);
            final boolean canBuy = postEntry.allReqtsMet && !busy;
            buyAFIT.setEnabled(canBuy);
            buyAFIT.setAlpha(canBuy ? 1.0f : 0.4f);
            buyHIVE.setEnabled(canBuy);
            buyHIVE.setAlpha(canBuy ? 1.0f : 0.4f);
            activateGadget.setEnabled(!busy);
            activateGadget.setAlpha(busy ? 0.4f : 1.0f);
            deactivateGadget.setEnabled(!busy);
            deactivateGadget.setAlpha(busy ? 0.4f : 1.0f);

            if (postEntry.nonConsumedCopy == SingleProductModel.BOUGHTCOPY) {
                buyAFIT.setVisibility(View.GONE);
                buyHIVE.setVisibility(View.GONE);
                if (postEntry.isFriendRewarding) {
                    // also show friend beneficiary to direct rewards over
                    friendBeneficiary.setVisibility(View.VISIBLE);
                } else {
                    friendBeneficiary.setVisibility(View.GONE);
                }
                activateGadget.setVisibility(View.VISIBLE);
                // activateGadget.setText(postEntry.name);
                deactivateGadget.setVisibility(View.GONE);
                boughtInfo.setVisibility(View.VISIBLE);
            } else if (postEntry.nonConsumedCopy == SingleProductModel.ACTIVECOPY) {
                buyAFIT.setVisibility(View.GONE);
                buyHIVE.setVisibility(View.GONE);
                activateGadget.setVisibility(View.GONE);
                friendBeneficiary.setVisibility(View.GONE);
                deactivateGadget.setVisibility(View.VISIBLE);
                boughtInfo.setVisibility(View.VISIBLE);
                // deactivateGadget.setText(postEntry.name);
            } else {
                buyAFIT.setVisibility(View.VISIBLE);
                // buyAFIT.setText(postEntry.name);
                buyHIVE.setVisibility(View.VISIBLE);
                activateGadget.setVisibility(View.GONE);
                friendBeneficiary.setVisibility(View.GONE);
                deactivateGadget.setVisibility(View.GONE);
                boughtInfo.setVisibility(View.GONE);
            }

            boosts.setText(Html.fromHtml(boostsText));
            // populate boosts

            validity.setText(postEntry.validity);

            name.setText(postEntry.name);

            // level.setText("" + postEntry.level);
            // display stars for every level entry
            String stars = "";
            for (int i = 0; i < postEntry.level; i++) {
                stars += '\uf005';
                stars += ' ';
            }
            level.setText(stars);
            count.setText("" + postEntry.count);
            type.setText(postEntry.type);

            totalBought.setText("" + postEntry.totalBought);
            totalConsumed.setText("" + postEntry.totalConsumed);
            remainingBoosts.setText("" + postEntry.remainingBoosts);

            final Context leaderboardContext = this.getContext();

            // set proper image
            String imgPath = getContext().getString(R.string.actifit_gadget_image);

            Handler uiHandler = new Handler(Looper.getMainLooper());
            uiHandler.post(new Runnable() {
                @Override
                public void run() {
                    // Picasso.with(ctx)
                    Glide.with(ctx)
                            .load(imgPath + postEntry.image)
                            .override(200, 200) // Resize to prevent "too large" images
                            .fitCenter() // Maintain aspect ratio
                            .into(image);
                }
            });

            // display proper price in HIVE
            String btnTitle = getContext().getString(R.string.buy_now);

            btnTitle += "<br/>" + postEntry.priceHIVE + " HIVE";

            buyHIVE.setText(Html.fromHtml(btnTitle));

            // display proper price in AFIT
            btnTitle = getContext().getString(R.string.buy_now);
            btnTitle += "<br/>" + postEntry.priceAFIT + " AFIT";

            buyAFIT.setText(Html.fromHtml(btnTitle));

            // handle click on username

            // View finalConvertView = convertView;
            buyAFIT.setOnClickListener(view -> {

                if (username == null || username.isEmpty()) {
                    Toast.makeText(getContext(), getContext().getString(R.string.username_missing), Toast.LENGTH_LONG)
                            .show();
                    return;
                }

                if (postEntry.priceAFIT > MarketActivity.afitBalance) {
                    Toast.makeText(getContext(),
                            getContext().getString(R.string.not_enough_afit_balance) + " " + postEntry.name,
                            Toast.LENGTH_LONG).show();
                    return;
                }

                // Signed on this phone with the posting key, then confirmed from the chain.
                GadgetApi.buyWithAfit(getContext(), MainActivity.username, postEntry.id, (ok, message) -> {
                    if (ok) {
                        postEntry.nonConsumedCopy = SingleProductModel.BOUGHTCOPY;
                        MarketActivity.afitBalance -= postEntry.priceAFIT;
                        toast(getContext().getString(R.string.success_buy_product) + " " + postEntry.name);
                    } else {
                        toast(message);
                    }
                    notifyDataSetChanged();
                });
                notifyDataSetChanged(); // greys the buttons while it runs
            });

            // activate gadget functionality
            activateGadget.setOnClickListener(view -> {

                if (username == null || username.isEmpty()) {
                    Toast.makeText(getContext(), getContext().getString(R.string.username_missing), Toast.LENGTH_LONG)
                            .show();
                    return;
                }

                final String activationBeneficiary = postEntry.isFriendRewarding
                        ? friendBeneficiary.getText().toString().replace("@", "").trim() : "";
                if (postEntry.isFriendRewarding && activationBeneficiary.isEmpty()) {
                    Toast.makeText(getContext(),
                            getContext().getString(R.string.error_activate_product_benefic), Toast.LENGTH_LONG)
                            .show();
                    return;
                }
                if (postEntry.isFriendRewarding
                        && !activationBeneficiary.matches("^[a-z][a-z0-9\\-.]{2,15}$")) {
                    Toast.makeText(getContext(), getContext().getString(R.string.invalid_username),
                            Toast.LENGTH_LONG).show();
                    return;
                }

                GadgetApi.activate(getContext(), MainActivity.username, postEntry.id,
                        postEntry.isFriendRewarding ? activationBeneficiary : null, (ok, message) -> {
                            if (ok) {
                                postEntry.nonConsumedCopy = SingleProductModel.ACTIVECOPY;
                                postEntry.beneficiary = postEntry.isFriendRewarding ? activationBeneficiary : "";
                                toast(getContext().getString(R.string.success_activate_product) + " " + postEntry.name);
                            } else {
                                toast(message);
                            }
                            notifyDataSetChanged();
                        });
                notifyDataSetChanged();
            });

            // deactivate gadget functionality
            deactivateGadget.setOnClickListener(view -> {

                if (username == null || username.isEmpty()) {
                    Toast.makeText(getContext(), getContext().getString(R.string.username_missing), Toast.LENGTH_LONG)
                            .show();
                    return;
                }

                GadgetApi.deactivate(getContext(), MainActivity.username, postEntry.id, (ok, message) -> {
                    if (ok) {
                        postEntry.beneficiary = "";
                        // refreshProductStateAfterDeactivation() redraws once with the real state.
                        refreshProductStateAfterDeactivation(postEntry);
                        toast(getContext().getString(R.string.success_deactivate_product) + " " + postEntry.name);
                    } else {
                        toast(message);
                        notifyDataSetChanged();
                    }
                });
                notifyDataSetChanged();
            });

            // Buy with HIVE
            // String finalHivePrice = hivePrice;
            buyHIVE.setOnClickListener(view -> {

                if (username == null || username.isEmpty()) {
                    Toast.makeText(getContext(), getContext().getString(R.string.username_missing), Toast.LENGTH_LONG)
                            .show();
                    return;
                }

                // A HIVE payment is a transfer, which needs the active key (set under Settings).
                if (HiveBroadcaster.storedKey(getContext(), HiveBroadcaster.KeyRole.ACTIVE).isEmpty()) {
                    Toast.makeText(getContext(), getContext().getString(R.string.unableBuyGadgetHive),
                            Toast.LENGTH_LONG).show();
                    return;
                }
                final GadgetApi.Callback onBought = (ok, message) -> {
                    if (ok) {
                        postEntry.nonConsumedCopy = SingleProductModel.BOUGHTCOPY;
                        toast(getContext().getString(R.string.success_buy_product) + " " + postEntry.name);
                    } else {
                        toast(message);
                    }
                    notifyDataSetChanged();
                };

                // Already paid but not confirmed: confirm that payment, never pay twice.
                if (GadgetApi.hasUnconfirmedHivePayment(getContext(), MainActivity.username, postEntry.id)) {
                    GadgetApi.buyWithHive(getContext(), MainActivity.username, postEntry.id, null, onBought);
                    notifyDataSetChanged();
                    return;
                }

                // Price it now from the confirming server and show the exact amount before paying.
                buyHIVE.setEnabled(false);
                GadgetApi.quoteHive(getContext(), postEntry.priceAFIT, (quote, error) -> {
                    buyHIVE.setEnabled(true);
                    if (quote == null) {
                        toast(error);
                        return;
                    }
                    new AlertDialog.Builder(ctx)
                            .setTitle(R.string.gadget_hive_confirm_title)
                            .setMessage(getContext().getString(R.string.gadget_hive_confirm_msg, quote.amount, postEntry.name))
                            .setPositiveButton(R.string.gadget_hive_confirm_pay, (d, w) -> {
                                GadgetApi.buyWithHive(getContext(), MainActivity.username, postEntry.id, quote, onBought);
                                notifyDataSetChanged();
                            })
                            .setNegativeButton(android.R.string.cancel, null)
                            .show();
                });
            });

        } catch (Exception exp) {
            exp.printStackTrace();
        }

        // Return the completed view to render on screen
        return convertView;
    }

    private void toast(String message) {
        if (message != null && !message.isEmpty()) {
            Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
        }
    }

    private void refreshProductStateAfterDeactivation(SingleProductModel product) {
        String url = Utils.apiUrl(getContext())
                + getContext().getString(R.string.non_consumed_gadgets_link) + username;
        JsonArrayRequest request = new JsonArrayRequest(Request.Method.GET, url, null, response -> {
            product.nonConsumedCopy = SingleProductModel.BOUGHTCOPY;
            product.beneficiary = "";
            product.remainingBoosts = product.validityVal;

            for (int i = 0; i < response.length(); i++) {
                JSONObject entry = response.optJSONObject(i);
                if (entry == null || !product.id.equals(entry.optString("gadget"))
                        || !"active".equals(entry.optString("status"))) {
                    continue;
                }

                product.nonConsumedCopy = SingleProductModel.ACTIVECOPY;
                Object beneficiary = entry.opt("benefic");
                if (beneficiary instanceof String) {
                    product.beneficiary = ((String) beneficiary).trim();
                }
                JSONArray postsConsumed = entry.optJSONArray("posts_consumed");
                if (postsConsumed != null) {
                    product.remainingBoosts -= postsConsumed.length();
                }
                break;
            }
            notifyDataSetChanged();
        }, error -> {
            Log.e(MainActivity.TAG, "Error refreshing gadget state after deactivation: " + error);
            // The deactivate already succeeded on-chain; if the refresh GET fails, fall back to the
            // bought state so the row doesn't keep showing "Deactivate" — which would otherwise let
            // the user fire a second, redundant deactivate custom_json for an already-off gadget.
            product.nonConsumedCopy = SingleProductModel.BOUGHTCOPY;
            product.beneficiary = "";
            product.remainingBoosts = product.validityVal;
            notifyDataSetChanged();
        });
        Volley.newRequestQueue(getContext()).add(request);
    }

    // Lazily builds the friends-first (Hive-fallback) autocomplete adapter, shared across rows.
    private FriendAccountAdapter getFriendAccountAdapter() {
        if (friendAccountAdapter == null) {
            friendAccountAdapter = new FriendAccountAdapter(getContext(), Utils.apiUrl(getContext()),
                    getContext().getString(R.string.hive_default_node), username);
        }
        return friendAccountAdapter;
    }

    private void bindBeneficiaryIdentity(SingleProductModel product, LinearLayout identity,
            ImageView avatar, TextView beneficiaryUsername) {
        identity.setVisibility(View.GONE);
        beneficiaryUsername.setText("");
        if (ctx instanceof Activity
                && (((Activity) ctx).isFinishing() || ((Activity) ctx).isDestroyed())) {
            avatar.setImageResource(R.drawable.default_pic);
            return;
        }
        Glide.with(ctx).clear(avatar);
        avatar.setImageResource(R.drawable.default_pic);

        if (product.nonConsumedCopy != SingleProductModel.ACTIVECOPY
                || product.beneficiary == null || product.beneficiary.trim().isEmpty()) {
            return;
        }

        String normalizedUsername = product.beneficiary.trim();
        while (normalizedUsername.startsWith("@")) {
            normalizedUsername = normalizedUsername.substring(1);
        }
        if (normalizedUsername.isEmpty()) {
            return;
        }

        beneficiaryUsername.setText("@" + normalizedUsername);
        identity.setVisibility(View.VISIBLE);
        String avatarUrl = getContext().getString(R.string.hive_image_host_url)
                .replace("USERNAME", normalizedUsername);
        Glide.with(ctx)
                .load(avatarUrl)
                .placeholder(R.drawable.default_pic)
                .error(R.drawable.default_pic)
                .into(avatar);
    }

}
