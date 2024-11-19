package io.actifit.fitnesstracker.actifitfitnesstracker;

import static android.view.View.VISIBLE;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

import org.checkerframework.checker.nullness.qual.NonNull;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

public class VoteModalDialogFragment extends DialogFragment {
    public Context ctx;
    //JSONArray extraVotesList;
    SingleHivePostModel postEntry;
    ListView socialView;

    public VoteModalDialogFragment() {

    }

    public VoteModalDialogFragment(Context ctx, SingleHivePostModel postEntry,
                                   JSONArray extraVotesList, ListView socialView) {
        this.ctx = ctx;
        this.postEntry = postEntry;
        //this.extraVotesList = extraVotesList;
        this.socialView = socialView;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dialog = super.onCreateDialog(savedInstanceState);
        //dialog.getWindow().requestFeature(STYLE_NO_TITLE);
        return dialog;
    }

    @Override
    public void onResume() {
        super.onResume();

    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.vote_modal, container, false);
        //View voteModalLayout = LayoutInflater.from(ctx).inflate(R.layout.vote_modal, null);
        renderVoteContent(view);


        return view;
    }

    private void renderVoteContent(View view){

        //View voteModalLayout = LayoutInflater.from(ctx).inflate(R.layout.vote_modal, null);
        View voteModalLayout = view;
        TextView author_txt = voteModalLayout.findViewById(R.id.vote_author);
        author_txt.setText("@"+postEntry.author+" 's content");

        EditText vote_weight = voteModalLayout.findViewById(R.id.vote_weight);

        vote_weight.setText(Utils.grabUserDefaultVoteWeight());

        ProgressBar taskProgress = voteModalLayout.findViewById(R.id.loader);

        Button upvoteButton = view.findViewById(R.id.proceed_vote_btn);

        Button add10Button = view.findViewById(R.id.add_10);
        Button sub10Button = view.findViewById(R.id.sub_10);

        add10Button.setOnClickListener(v -> {
            int newVal = Integer.parseInt(vote_weight.getText().toString());
            newVal += 10;
            if (newVal > 100){
                newVal = 100;
            }
            vote_weight.setText(newVal+"");
        });

        sub10Button.setOnClickListener(v -> {
            int newVal = Integer.parseInt(vote_weight.getText().toString());
            newVal -= 10;
            if (newVal < 0){
                newVal = 0;
            }
            vote_weight.setText(newVal+"");
        });

        upvoteButton.setOnClickListener(v ->{


            //proceed_vote_btn.setOnClickListener(subview -> {
            if (!TextUtils.isDigitsOnly(vote_weight.getText())) {
                Toast.makeText(ctx, ctx.getString(R.string.vote_percent_incorrect), Toast.LENGTH_SHORT).show();
                return;
            }
            int voteVal = Integer.parseInt(String.valueOf(vote_weight.getText()));
            if (voteVal < 1 || voteVal > 100) {
                Toast.makeText(ctx, ctx.getString(R.string.vote_percent_incorrect), Toast.LENGTH_SHORT).show();
                return;
            }

            taskProgress.setVisibility(VISIBLE);

            Activity activity = getActivity();

            //run on its own thread to avoid hiccups
            Thread voteThread = new Thread(() -> {
                //runOnUiThread(() -> {
                try {
                    String op_name = "vote";

                    JSONObject cstm_params = new JSONObject();
                    cstm_params.put("voter", MainActivity.username);
                    cstm_params.put("author", postEntry.author);
                    cstm_params.put("permlink", postEntry.permlink);
                    cstm_params.put("weight", Integer.parseInt(String.valueOf(vote_weight.getText())) * 100);

                    Utils.queryAPI(getContext(), MainActivity.username, op_name,
                            cstm_params, taskProgress,
                            new Utils.APIResponseListener() {
                                @Override
                                public void onResponse(boolean success) {
                                    // Step 5: Perform another API call
                                    activity.runOnUiThread(() -> {
                                        taskProgress.setVisibility(View.GONE);
                                        if (success) {
                                            Toast.makeText(ctx, ctx.getString(R.string.vote_success), Toast.LENGTH_LONG).show();

                                            //store vote for proper tracking on display
                                            if (PostAdapter.extraVotesList == null) {
                                                PostAdapter.extraVotesList = new JSONArray();
                                            }
                                            JSONObject entry = new JSONObject();
                                            try {
                                                entry.put("voter", MainActivity.username);
                                                //entry.put("post_id", postEntry.post_id);
                                                entry.put("permlink", postEntry.permlink);
                                                PostAdapter.extraVotesList.put(entry);

                                            } catch (JSONException e) {
                                                e.printStackTrace();
                                            }
                                            //if (modal.getListView().getVisibility() == View.VISIBLE) {
                                            //modal.dismiss();

                                            //pointer.dismiss();

                                            //refresh display
                                            //case for maintaining scroll position upon append
                                            int currentPosition = socialView.getFirstVisiblePosition();
                                            View v = socialView.getChildAt(0);
                                            int topOffset = (v == null) ? 0 : v.getTop();

                                            // Set the new adapter
                                            socialView.setAdapter(socialView.getAdapter());

                                            // Restore the scroll position
                                            socialView.setSelectionFromTop(currentPosition, topOffset);

                                            //close modal
                                            dismiss();
                                            //}
                                        } else {
                                            Toast.makeText(ctx, ctx.getString(R.string.vote_error), Toast.LENGTH_LONG).show();
                                        }
                                    });
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    // Handle the error
                                    activity.runOnUiThread(() -> {
                                        taskProgress.setVisibility(View.GONE);
                                        Toast.makeText(ctx, ctx.getString(R.string.vote_error), Toast.LENGTH_LONG).show();
                                    });
                                }
                            }, activity);

                } catch (Exception exc) {
                    exc.printStackTrace();
                }
            });
            voteThread.start();
        });


        Button votersListButton = view.findViewById(R.id.voters_list_btn);
        votersListButton.setOnClickListener(v -> {

            //grab array from result
            JSONArray actVotes = postEntry.active_votes;

            final View votersListLayout = LayoutInflater.from(ctx).inflate(R.layout.voters_page, null);
            final ListView votersListItem = votersListLayout.findViewById(R.id.votersList);
            postEntry.voteRshares = 0;

            //calculate payout total value
            postEntry.calculateVoteRshares();
            postEntry.calculateSumPayout();
            postEntry.calculateRatio();
            ArrayList<VoteEntryAdapter.VoteEntry> voters = new ArrayList<>();

            for (int i = 0; i < actVotes.length(); i++) {
                try {
                    VoteEntryAdapter.VoteEntry vEntry = new VoteEntryAdapter.VoteEntry((actVotes.getJSONObject(i)), postEntry.ratio);
                    voters.add(vEntry);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }


            VoteEntryAdapter voteAdapter = new VoteEntryAdapter(getContext(), voters);

            //votersView = subview.findViewById(R.id.votersList);

            votersListItem.setAdapter(voteAdapter);

            final View votersListView = LayoutInflater.from(ctx).inflate(R.layout.voters_page, null);
            //AlertDialog.Builder votersListDialogBldr = new AlertDialog.Builder(mainContext);
            AlertDialog.Builder votersListDialogBldr = new AlertDialog.Builder(PostAdapter.keyMainContext);
            AlertDialog newpointer = votersListDialogBldr.setView(votersListLayout)
                    .setTitle(ctx.getString(R.string.voters_list_title))
                    .setIcon(ctx.getResources().getDrawable(R.drawable.actifit_logo))
                    .setPositiveButton(ctx.getString(R.string.close_button), null)
                    .create();

            votersListDialogBldr.show();

        });

/*
        pointer = voteDialogBuilder.setView(voteModalLayout)
                .setCancelable(false)

                .setTitle(ctx.getString(R.string.voting_note))
                .setIcon(ctx.getResources().getDrawable(R.drawable.actifit_logo))
                .setPositiveButton(ctx.getString(R.string.vote_action), handleVoteAction)
                .setNegativeButton(ctx.getString(R.string.voters_list), handleVotersList)
                .setNeutralButton( ctx.getString(R.string.cancel_button), null)
                .create();



        voteDialogBuilder.show();*/

        Button closeButton = view.findViewById(R.id.close_btn);
        closeButton.setOnClickListener(v -> {
            dismiss(); // Dismiss the DialogFragment when the close button is clicked
        });
    }
}
