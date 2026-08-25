package io.actifit.fitnesstracker.actifitfitnesstracker;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.toolbox.JsonArrayRequest;
import com.android.volley.toolbox.Volley;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.AxisBase;
import com.github.mikephil.charting.components.Description;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.components.YAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IAxisValueFormatter;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Native trend charts for the user's body metrics (weight, waist, chest, thighs,
 * body-fat) over time. Data comes from the ready {@code /trackedMeasurements/:user}
 * endpoint — the same measurements the post composer already collects per report.
 * Card #45.
 */
public class BodyMetricsActivity extends AppCompatActivity {

    // metric keys
    private static final int WEIGHT = 0, WAIST = 1, CHEST = 2, THIGHS = 3, BODYFAT = 4, HEIGHT = 5;

    private LineChart chart;
    private ChipGroup metricChips;
    private ProgressBar loading;
    private TextView emptyView, errorView, noMetricView, retryBtn;

    private final List<BodyMeasurementEntry> entries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_body_metrics);

        findViewById(R.id.body_metrics_back).setOnClickListener(v -> finish());
        chart = findViewById(R.id.body_metrics_chart);
        metricChips = findViewById(R.id.body_metrics_chips);
        loading = findViewById(R.id.body_metrics_loading);
        emptyView = findViewById(R.id.body_metrics_empty);
        errorView = findViewById(R.id.body_metrics_error);
        noMetricView = findViewById(R.id.body_metrics_no_metric);
        retryBtn = findViewById(R.id.body_metrics_retry);

        styleChart();

        metricChips.setOnCheckedChangeListener((group, checkedId) -> {
            Chip c = group.findViewById(checkedId);
            if (c != null && c.getTag() != null) {
                renderMetric((Integer) c.getTag());
            }
        });
        retryBtn.setOnClickListener(v -> fetchMeasurements());

        fetchMeasurements();
    }

    private String currentUser() {
        return getSharedPreferences("actifitSets", Context.MODE_PRIVATE)
                .getString("actifitUser", "");
    }

    private void fetchMeasurements() {
        String username = currentUser();
        if (username == null || username.isEmpty()) {
            showState(false, false, true, getString(R.string.body_metrics_login_required), false);
            return;
        }

        showState(true, false, false, null, false);

        String url = Utils.apiUrl(this) + getString(R.string.tracked_measurements_url) + username;
        RequestQueue queue = Volley.newRequestQueue(this);
        JsonArrayRequest req = new JsonArrayRequest(Request.Method.GET, url, null,
                response -> {
                    parseResponse(response);
                    if (entries.isEmpty()) {
                        showState(false, true, false, null, false);
                    } else {
                        showState(false, false, false, null, true);
                        buildChips();
                    }
                },
                error -> showState(false, false, true, getString(R.string.body_metrics_error), true));
        queue.add(req);
    }

    private void parseResponse(JSONArray response) {
        entries.clear();
        if (response == null) return;
        for (int i = 0; i < response.length(); i++) {
            JSONObject report = response.optJSONObject(i);
            if (report == null) continue;
            JSONObject meta = report.optJSONObject("json_metadata");
            if (meta == null) continue;

            BodyMeasurementEntry e = new BodyMeasurementEntry();
            e.date = parseDate(report.optString("date", ""));
            e.weight = firstFloat(meta, "weight");
            e.waist = firstFloat(meta, "waist");
            e.chest = firstFloat(meta, "chest");
            e.thighs = firstFloat(meta, "thighs");
            e.bodyFat = firstFloat(meta, "bodyfat");
            e.height = firstFloat(meta, "height");
            e.weightUnit = firstString(meta, "weightUnit");
            e.waistUnit = firstString(meta, "waistUnit");
            e.chestUnit = firstString(meta, "chestUnit");
            e.thighsUnit = firstString(meta, "thighsUnit");
            e.heightUnit = firstString(meta, "heightUnit");

            // keep only reports that carry at least one usable metric + a date
            if (e.date != null && (e.weight != null || e.waist != null || e.chest != null
                    || e.thighs != null || e.bodyFat != null || e.height != null)) {
                entries.add(e);
            }
        }
        // chart oldest -> newest
        Collections.sort(entries, new Comparator<BodyMeasurementEntry>() {
            @Override public int compare(BodyMeasurementEntry a, BodyMeasurementEntry b) {
                return a.date.compareTo(b.date);
            }
        });
    }

    private void buildChips() {
        metricChips.removeAllViews();
        addChipIfHasData(WEIGHT, getString(R.string.body_metrics_weight));
        addChipIfHasData(HEIGHT, getString(R.string.body_metrics_height));
        addChipIfHasData(WAIST, getString(R.string.body_metrics_waist));
        addChipIfHasData(CHEST, getString(R.string.body_metrics_chest));
        addChipIfHasData(THIGHS, getString(R.string.body_metrics_thighs));
        addChipIfHasData(BODYFAT, getString(R.string.body_metrics_bodyfat));

        // select the first available chip (Weight first when present)
        if (metricChips.getChildCount() > 0) {
            Chip first = (Chip) metricChips.getChildAt(0);
            first.setChecked(true);
            renderMetric((Integer) first.getTag());
        }
    }

    private void addChipIfHasData(int metric, String label) {
        boolean hasData = false;
        for (BodyMeasurementEntry e : entries) {
            if (valueFor(e, metric) != null) { hasData = true; break; }
        }
        if (!hasData) return;

        Chip chip = new Chip(this);
        chip.setId(View.generateViewId());
        chip.setText(label);
        chip.setCheckable(true);
        chip.setTag(metric);
        metricChips.addView(chip);
    }

    private void renderMetric(int metric) {
        List<Entry> points = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        SimpleDateFormat labelFmt = new SimpleDateFormat("MMM d ''yy", Locale.getDefault());
        String unit = null;

        int idx = 0;
        for (BodyMeasurementEntry e : entries) {
            Float v = valueFor(e, metric);
            if (v == null) continue;
            points.add(new Entry(idx, v));
            labels.add(labelFmt.format(e.date));
            if (unit == null) unit = unitFor(e, metric);
            idx++;
        }

        if (points.isEmpty()) {
            chart.setVisibility(View.GONE);
            noMetricView.setVisibility(View.VISIBLE);
            return;
        }
        noMetricView.setVisibility(View.GONE);
        chart.setVisibility(View.VISIBLE);

        String seriesLabel = metricLabel(metric) + (unit != null && !unit.isEmpty() ? " (" + unit + ")" : "");
        LineDataSet set = new LineDataSet(points, seriesLabel);
        int red = getResources().getColor(R.color.actifitRed);
        set.setColor(red);
        set.setCircleColor(red);
        set.setLineWidth(2f);
        set.setCircleRadius(4f);
        set.setDrawCircleHole(false);
        set.setDrawValues(points.size() <= 12);
        set.setValueTextSize(9f);
        set.setMode(LineDataSet.Mode.LINEAR);
        set.setHighlightEnabled(true);
        set.setDrawHighlightIndicators(false);

        chart.setData(new LineData(set));

        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IAxisValueFormatter() {
            @Override public String getFormattedValue(float value, AxisBase axis) {
                int i = Math.round(value);
                return (i >= 0 && i < labels.size()) ? labels.get(i) : "";
            }
        });
        xAxis.setLabelRotationAngle(labels.size() > 6 ? -45f : 0f);
        xAxis.setLabelCount(Math.min(labels.size(), 6), false);

        chart.animateX(500);
        chart.invalidate();
    }

    private void styleChart() {
        chart.getDescription().setEnabled(false);
        chart.setNoDataText("");
        chart.setDrawGridBackground(false);
        chart.setScaleYEnabled(false);
        chart.getAxisRight().setEnabled(false);
        chart.getLegend().setEnabled(true);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(chartTextColor());

        YAxis yAxis = chart.getAxisLeft();
        yAxis.setDrawGridLines(true);
        yAxis.setTextColor(chartTextColor());

        Description desc = new Description();
        desc.setText("");
        chart.setDescription(desc);
    }

    private int chartTextColor() {
        // resolve the theme's primary text color so labels are legible in light + dark
        android.util.TypedValue tv = new android.util.TypedValue();
        if (getTheme().resolveAttribute(android.R.attr.textColorPrimary, tv, true)) {
            return tv.resourceId != 0
                    ? androidx.core.content.ContextCompat.getColor(this, tv.resourceId)
                    : tv.data;
        }
        return Color.GRAY;
    }

    // ---- metric helpers ----

    private Float valueFor(BodyMeasurementEntry e, int metric) {
        switch (metric) {
            case WEIGHT: return e.weight;
            case WAIST: return e.waist;
            case CHEST: return e.chest;
            case THIGHS: return e.thighs;
            case BODYFAT: return e.bodyFat;
            case HEIGHT: return e.height;
            default: return null;
        }
    }

    private String unitFor(BodyMeasurementEntry e, int metric) {
        switch (metric) {
            case WEIGHT: return e.weightUnit;
            case WAIST: return e.waistUnit;
            case CHEST: return e.chestUnit;
            case THIGHS: return e.thighsUnit;
            case HEIGHT: return e.heightUnit;
            case BODYFAT: return getString(R.string.body_metrics_bodyfat_unit);
            default: return null;
        }
    }

    private String metricLabel(int metric) {
        switch (metric) {
            case WEIGHT: return getString(R.string.body_metrics_weight);
            case WAIST: return getString(R.string.body_metrics_waist);
            case CHEST: return getString(R.string.body_metrics_chest);
            case THIGHS: return getString(R.string.body_metrics_thighs);
            case HEIGHT: return getString(R.string.body_metrics_height);
            case BODYFAT: return getString(R.string.body_metrics_bodyfat);
            default: return "";
        }
    }

    // ---- json helpers (metadata values are single-element string arrays) ----

    private Float firstFloat(JSONObject meta, String key) {
        String s = firstString(meta, key);
        if (s == null || s.isEmpty()) return null;
        try {
            float f = Float.parseFloat(s.trim());
            return f > 0 ? f : null; // 0/blank = not recorded
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String firstString(JSONObject meta, String key) {
        JSONArray arr = meta.optJSONArray(key);
        if (arr != null && arr.length() > 0) {
            return arr.optString(0, null);
        }
        String direct = meta.optString(key, null);
        return (direct != null && !direct.isEmpty()) ? direct : null;
    }

    private Date parseDate(String raw) {
        if (raw == null || raw.length() < 10) return null;
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(raw.substring(0, 10));
        } catch (Exception ex) {
            return null;
        }
    }

    // ---- state toggling ----

    private void showState(boolean isLoading, boolean isEmpty, boolean isError,
                           String errorMsg, boolean showChartArea) {
        loading.setVisibility(isLoading ? View.VISIBLE : View.GONE);
        emptyView.setVisibility(isEmpty ? View.VISIBLE : View.GONE);
        errorView.setVisibility(isError ? View.VISIBLE : View.GONE);
        retryBtn.setVisibility(isError ? View.VISIBLE : View.GONE);
        if (isError && errorMsg != null) errorView.setText(errorMsg);

        int chartAreaVis = showChartArea ? View.VISIBLE : View.GONE;
        metricChips.setVisibility(chartAreaVis);
        chart.setVisibility(chartAreaVis);
        if (!showChartArea) noMetricView.setVisibility(View.GONE);
    }
}
