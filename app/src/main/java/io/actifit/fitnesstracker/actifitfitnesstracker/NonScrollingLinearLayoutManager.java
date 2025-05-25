package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import androidx.recyclerview.widget.LinearLayoutManager;

// Custom LayoutManager that disables vertical scrolling for the RecyclerView itself
public class NonScrollingLinearLayoutManager extends LinearLayoutManager {

    public NonScrollingLinearLayoutManager(Context context) {
        super(context);
    }

    // Override this method to disable vertical scrolling in the RecyclerView
    @Override
    public boolean canScrollVertically() {
        return true; // Tell the LayoutManager not to allow vertical scrolling
    }

    // Optional: Override this method to disable horizontal scrolling if needed
    // @Override
    // public boolean canScrollHorizontally() {
    //     return false; // Keep this true if you still need horizontal scrolling for columns
    // }
}