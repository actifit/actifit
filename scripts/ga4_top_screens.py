#!/usr/bin/env python3
"""
ga4_top_screens.py — Report the most-visited screens in the Actifit Android app.

Pulls Firebase/GA4 `screen_view` data (auto-collected by the firebase-analytics
SDK, keyed by Activity class) via the Google Analytics Data API and prints a
ranked table. Use it to decide where to surface the DHF vote prompt.

Prerequisites
-------------
1. GA4 Property ID for the Firebase project (numeric, e.g. 123456789).
   Firebase Console -> Project Settings -> Integrations -> Google Analytics,
   or GA4 Admin -> Property Settings.
2. A service-account JSON key whose service account has been granted at least
   Viewer on that GA4 property (GA4 Admin -> Property Access Management), and
   the "Google Analytics Data API" enabled in the Cloud project.
3. pip install google-analytics-data

Usage
-----
  python ga4_top_screens.py --property 123456789 --credentials sa.json --days 30

Or via env vars (handy for CI):
  set GA4_PROPERTY_ID=123456789
  set GOOGLE_APPLICATION_CREDENTIALS=sa.json
  python ga4_top_screens.py
"""

import argparse
import os
import sys

try:
    from google.analytics.data_v1beta import BetaAnalyticsDataClient
    from google.analytics.data_v1beta.types import (
        DateRange,
        Dimension,
        Metric,
        OrderBy,
        RunReportRequest,
    )
    from google.oauth2 import service_account
except ImportError:
    sys.exit(
        "Missing dependency. Run:\n    pip install google-analytics-data\n"
    )


def parse_args():
    p = argparse.ArgumentParser(description="Top GA4 screens for the Actifit app")
    p.add_argument(
        "--property",
        default=os.environ.get("GA4_PROPERTY_ID"),
        help="GA4 numeric property ID (or set GA4_PROPERTY_ID)",
    )
    p.add_argument(
        "--credentials",
        default=os.environ.get("GOOGLE_APPLICATION_CREDENTIALS"),
        help="Path to service-account JSON (or set GOOGLE_APPLICATION_CREDENTIALS)",
    )
    p.add_argument("--days", type=int, default=30, help="Look-back window in days")
    p.add_argument("--limit", type=int, default=25, help="Max rows to show")
    return p.parse_args()


def build_client(credentials_path):
    if credentials_path:
        creds = service_account.Credentials.from_service_account_file(
            credentials_path
        )
        return BetaAnalyticsDataClient(credentials=creds)
    # Falls back to Application Default Credentials.
    return BetaAnalyticsDataClient()


def main():
    args = parse_args()
    if not args.property:
        sys.exit("Error: --property (or GA4_PROPERTY_ID) is required.")

    client = build_client(args.credentials)

    request = RunReportRequest(
        property=f"properties/{args.property}",
        # screenClass = Activity class (reliable for auto-tracking).
        # unifiedScreenName falls back to screenClass when screen_name is unset.
        dimensions=[
            Dimension(name="unifiedScreenName"),
            Dimension(name="screenClass"),
        ],
        metrics=[
            Metric(name="screenPageViews"),
            Metric(name="activeUsers"),
            Metric(name="userEngagementDuration"),
        ],
        date_ranges=[DateRange(start_date=f"{args.days}daysAgo", end_date="today")],
        order_bys=[
            OrderBy(
                metric=OrderBy.MetricOrderBy(metric_name="screenPageViews"),
                desc=True,
            )
        ],
        limit=args.limit,
    )

    response = client.run_report(request)

    if not response.rows:
        print("No screen_view data returned for the selected window.")
        return

    print(
        f"\nTop screens (last {args.days} days) — property {args.property}\n"
        + "=" * 78
    )
    header = f"{'Screen':32} {'Class':22} {'Views':>8} {'Users':>8} {'Avg s/user':>11}"
    print(header)
    print("-" * 78)

    for row in response.rows:
        screen = row.dimension_values[0].value or "(not set)"
        cls = row.dimension_values[1].value or "(not set)"
        views = int(float(row.metric_values[0].value or 0))
        users = int(float(row.metric_values[1].value or 0))
        eng = float(row.metric_values[2].value or 0)
        avg_per_user = (eng / users) if users else 0.0
        print(
            f"{screen[:32]:32} {cls[:22]:22} {views:>8} {users:>8} {avg_per_user:>11.1f}"
        )

    print("=" * 78)
    print("Tip: put the persistent vote card on the highest-views screen that")
    print("isn't the posting flow (usually MainActivity).\n")


if __name__ == "__main__":
    main()
