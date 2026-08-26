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
public class BodyMetricsActivity extends BaseActivity {

    // metric keys
    private static final int WEIGHT = 0, WAIST = 1, CHEST = 2, THIGHS = 3, BODYFAT = 4, HEIGHT = 5;

    private LineChart chart;
    private ChartValueMarker marker;
    private ChipGroup metricChips;
    private ProgressBar loading;
    private TextView emptyView, errorView, noMetricView, retryBtn, metricLabelView;

    private final List<BodyMeasurementEntry> entries = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_body_metrics);

        chart = findViewById(R.id.body_metrics_chart);
        metricChips = findViewById(R.id.body_metrics_chips);
        loading = findViewById(R.id.body_metrics_loading);
        emptyView = findViewById(R.id.body_metrics_empty);
        errorView = findViewById(R.id.body_metrics_error);
        noMetricView = findViewById(R.id.body_metrics_no_metric);
        retryBtn = findViewById(R.id.body_metrics_retry);
        metricLabelView = findViewById(R.id.body_metrics_metric_label);

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
        addChipIfHasData(WEIGHT);
        addChipIfHasData(HEIGHT);
        addChipIfHasData(WAIST);
        addChipIfHasData(CHEST);
        addChipIfHasData(THIGHS);
        addChipIfHasData(BODYFAT);

        // select the first available chip (Weight first when present)
        if (metricChips.getChildCount() > 0) {
            Chip first = (Chip) metricChips.getChildAt(0);
            first.setChecked(true);
            renderMetric((Integer) first.getTag());
        }
    }

    private void addChipIfHasData(int metric) {
        boolean hasData = false;
        for (BodyMeasurementEntry e : entries) {
            if (valueFor(e, metric) != null) { hasData = true; break; }
        }
        if (!hasData) return;

        float dp = getResources().getDisplayMetrics().density;
        Chip chip = new Chip(this);
        chip.setId(View.generateViewId());
        chip.setCheckable(true);
        chip.setCheckedIconVisible(false);
        chip.setContentDescription(metricLabel(metric));
        chip.setTag(metric);

        // Icon-only chip using the official Actifit measurement icons (already brand-red, so no tint),
        // centered (no text padding + symmetric chip padding). The word is kept as the accessibility
        // label and is still shown in the chart legend when the metric is selected.
        chip.setChipIcon(androidx.core.content.ContextCompat.getDrawable(this, metricIconRes(metric)));
        chip.setChipIconVisible(true);
        chip.setChipIconSize(34 * dp);
        chip.setText("");
        chip.setChipStrokeWidth(0f);                    // no border — give the icon the room
        chip.setEnsureMinTouchTargetSize(false);        // don't inflate the chip past its content
        chip.setTextStartPadding(0f);
        chip.setTextEndPadding(0f);
        chip.setIconStartPadding(0f);
        chip.setIconEndPadding(0f);
        chip.setChipStartPadding(8 * dp);
        chip.setChipEndPadding(8 * dp);

        metricChips.addView(chip);
    }

    private int metricIconRes(int metric) {
        switch (metric) {
            case WEIGHT:  return R.drawable.ic_meas_weight;
            case HEIGHT:  return R.drawable.ic_meas_height;
            case WAIST:   return R.drawable.ic_meas_waist;
            case CHEST:   return R.drawable.ic_meas_chest;
            case THIGHS:  return R.drawable.ic_meas_thighs;
            case BODYFAT: return R.drawable.ic_meas_bodyfat;
            default:      return R.drawable.ic_meas_weight;
        }
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
        if (metricLabelView != null) metricLabelView.setText(seriesLabel);

        // Fit the y-axis to this metric's data (with padding) so a tall metric (e.g. height ~180)
        // isn't squashed against a forced 0 baseline.
        float dMin = Float.MAX_VALUE, dMax = -Float.MAX_VALUE;
        for (Entry en : points) { dMin = Math.min(dMin, en.getY()); dMax = Math.max(dMax, en.getY()); }
        float range = dMax - dMin;
        float pad = range > 0 ? range * 0.12f : Math.max(Math.abs(dMax) * 0.1f, 1f);
        YAxis yA = chart.getAxisLeft();
        yA.setAxisMinimum(Math.max(0f, dMin - pad));
        yA.setAxisMaximum(dMax + pad);

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
        set.setDrawHorizontalHighlightIndicator(false);
        set.setDrawVerticalHighlightIndicator(true);
        set.setHighlightLineWidth(1f);
        set.setHighLightColor(red);

        if (marker != null) marker.setSeries(labels, unit);
        chart.highlightValues(null);   // drop any marker/highlight carried over from the previous metric
        chart.fitScreen();             // reset any pan/zoom so every metric renders from the same state
        chart.setData(new LineData(set));

        XAxis xAxis = chart.getXAxis();
        xAxis.setValueFormatter(new IAxisValueFormatter() {
            @Override public String getFormattedValue(float value, AxisBase axis) {
                int i = Math.round(value);
                return (i >= 0 && i < labels.size()) ? labels.get(i) : "";
            }
        });
        xAxis.setLabelRotationAngle(-45f);   // constant rotation so the bottom offset (and chart height) never changes
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
        chart.getLegend().setEnabled(false);           // shown as a header label instead (was overlapping x-axis)
        chart.setExtraBottomOffset(6f);

        XAxis xAxis = chart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        xAxis.setGranularity(1f);
        xAxis.setTextColor(chartTextColor());

        YAxis yAxis = chart.getAxisLeft();
        yAxis.setDrawGridLines(true);
        yAxis.setDrawZeroLine(false);
        yAxis.setMinWidth(40f);        // fixed y-axis width so the plot area doesn't shift between metrics
        yAxis.setLabelCount(6, false); // consistent gridlines across metrics
        yAxis.setTextColor(chartTextColor());

        Description desc = new Description();
        desc.setText("");
        chart.setDescription(desc);

        // tap any point to see its value
        chart.setHighlightPerTapEnabled(true);
        marker = new ChartValueMarker(this);
        marker.setChartView(chart);
        chart.setMarker(marker);
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
