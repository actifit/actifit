package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.annotation.SuppressLint;
import android.content.Context;
import android.widget.TextView;

import com.github.mikephil.charting.components.MarkerView;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.highlight.Highlight;
import com.github.mikephil.charting.utils.MPPointF;

import java.util.List;
import java.util.Locale;

/**
 * Small popup shown when a point on the body-metrics chart is tapped, displaying that
 * point's date + value + unit. The series' date labels and unit are set per rendered
 * metric via {@link #setSeries}.
 */
@SuppressLint("ViewConstructor")
public class ChartValueMarker extends MarkerView {

    private final TextView tvContent;
    private List<String> dateLabels;
    private String unit = "";

    public ChartValueMarker(Context context) {
        super(context, R.layout.chart_marker);
        tvContent = findViewById(R.id.marker_text);
    }

    public void setSeries(List<String> dateLabels, String unit) {
        this.dateLabels = dateLabels;
        this.unit = (unit != null) ? unit : "";
    }

    @Override
    public void refreshContent(Entry e, Highlight highlight) {
        int idx = (int) e.getX();
        String date = (dateLabels != null && idx >= 0 && idx < dateLabels.size())
                ? dateLabels.get(idx) + "   " : "";
        float y = e.getY();
        String value = (y == Math.rint(y))
                ? String.valueOf((int) y)
                : String.format(Locale.getDefault(), "%.1f", y);
        tvContent.setText(date + value + (unit.isEmpty() ? "" : " " + unit));
        super.refreshContent(e, highlight);
    }

    @Override
    public MPPointF getOffset() {
        // center horizontally above the tapped point
        return new MPPointF(-(getWidth() / 2f), -getHeight() - 8f);
    }
}
