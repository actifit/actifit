#!/usr/bin/env python3
"""
Translate missing Android strings.xml entries to all supported locale files.

DeepL is used for all languages it supports. Yoruba (yo) falls back to the
MyMemory Translation API (free, no key required, uses urllib — no extra deps).

Usage:
    py -3 scripts/translate_strings.py [--api-key KEY] [--locale LOCALE] [--dry-run]
                                        [--mymemory-email EMAIL]

Environment:
    DEEPL_API_KEY  DeepL authentication key (free or pro)

Requirements:
    pip install deepl

Supported locales: ar, de, es, hi, it, ko, nl, pt, pt-rBR, tr, uk, yo, zh-rCN
"""

import argparse
import json
import os
import re
import sys
import urllib.parse
import urllib.request
from pathlib import Path

try:
    import deepl
except ImportError:
    print("Error: deepl package not installed. Run: pip install deepl")
    sys.exit(1)

PROJECT_DIR = Path(__file__).parent.parent
VALUES_DIR = PROJECT_DIR / "app" / "src" / "main" / "res"
DEFAULT_STRINGS = VALUES_DIR / "values" / "strings.xml"

# Android locale dir suffix -> DeepL target language code
# None = DeepL doesn't support this language; MyMemory is used instead.
LOCALE_DEEPL_MAP = {
    "ar":     "AR",
    "de":     "DE",
    "es":     "ES",
    "hi":     "HI",
    "it":     "IT",
    "ko":     "KO",
    "nl":     "NL",
    "pt":     "PT-PT",
    "pt-rBR": "PT-BR",
    "tr":     "TR",
    "uk":     "UK",
    "yo":     None,   # not supported by DeepL → falls back to MyMemory
    "zh-rCN": "ZH",
}

# MyMemory language codes for locales where DeepL returns None
LOCALE_MYMEMORY_MAP = {
    "yo": "yo",
}


def parse_translatable_strings(content):
    """Return ordered list of (name, raw_text) for all translatable strings."""
    pattern = re.compile(
        r'<string\s+((?:(?!>).)*?)>(.*?)</string>',
        re.DOTALL,
    )
    results = []
    for attrs, text in pattern.findall(content):
        if 'translatable="false"' in attrs:
            continue
        name_match = re.search(r'name="([^"]+)"', attrs)
        if name_match:
            results.append((name_match.group(1), text.strip()))
    return results


def get_existing_keys(file_path):
    """Return set of string names already in a locale file."""
    if not file_path.exists():
        return set()
    content = file_path.read_text(encoding="utf-8")
    return set(re.findall(r'<string\s+name="([^"]+)"', content))


def protect_placeholders(text):
    """Wrap Android format specifiers (%1$s, %2$,d, etc.) in <keep> tags."""
    return re.sub(r'(%\d+\$[^a-zA-Z]*[a-zA-Z])', r'<keep>\1</keep>', text)


def restore_placeholders(text):
    """Remove <keep> wrapper tags from placeholders."""
    return re.sub(r'<keep>(.*?)</keep>', r'\1', text, flags=re.DOTALL)


def translate_deepl(translator, text, target_lang):
    """Translate text via DeepL, preserving HTML tags and Android placeholders."""
    if not text.strip():
        return text
    protected = protect_placeholders(text)
    result = translator.translate_text(
        protected,
        source_lang="EN",
        target_lang=target_lang,
        tag_handling="html",
        ignore_tags=["keep"],
    )
    return restore_placeholders(result.text)


def translate_mymemory(text, target_lang, email=None):
    """Translate text via the MyMemory API (free, no key required).

    Placeholders (%1$s, etc.) and HTML/XML tags are tokenised before the
    request and restored from the response so they survive translation intact.

    Args:
        text:        Source text (English).
        target_lang: MyMemory language code, e.g. "yo" for Yoruba.
        email:       Optional registered e-mail for a higher daily quota
                     (5 000 chars/day anonymous → 50 000 chars/day with email).
    """
    if not text.strip():
        return text

    # Tokenise everything that must not be translated
    tokens: dict[str, str] = {}

    def tokenise(match):
        tok = f"__T{len(tokens)}__"
        tokens[tok] = match.group(0)
        return tok

    protected = re.sub(r'%\d+\$[^a-zA-Z]*[a-zA-Z]', tokenise, text)  # %1$s etc.
    protected = re.sub(r'<[^>]+>', tokenise, protected)                # <br/> etc.
    protected = re.sub(r'&\w+;', tokenise, protected)                  # &amp; etc.

    params: dict = {"q": protected, "langpair": f"en|{target_lang}"}
    if email:
        params["de"] = email

    url = "https://api.mymemory.translated.net/get?" + urllib.parse.urlencode(params)
    req = urllib.request.Request(url, headers={"User-Agent": "actifit-android-strings/1.0"})
    with urllib.request.urlopen(req, timeout=15) as resp:
        data = json.loads(resp.read().decode("utf-8"))

    status = data.get("responseStatus", 0)
    if status != 200:
        details = data.get("responseDetails", "")
        raise RuntimeError(f"MyMemory returned status {status}: {details}")

    translated = data["responseData"]["translatedText"]

    # Restore tokenised segments
    for tok, original in tokens.items():
        translated = translated.replace(tok, original)

    return translated


def main():
    parser = argparse.ArgumentParser(
        description="Translate missing Android strings.xml entries via DeepL (+ MyMemory for Yoruba)."
    )
    parser.add_argument(
        "--api-key",
        default=os.environ.get("DEEPL_API_KEY"),
        help="DeepL API key (or set DEEPL_API_KEY env var)",
    )
    parser.add_argument(
        "--mymemory-email",
        default=os.environ.get("MYMEMORY_EMAIL"),
        metavar="EMAIL",
        help="Registered e-mail for MyMemory API (raises quota from 5k to 50k chars/day)",
    )
    parser.add_argument(
        "--locale",
        help="Only process this locale, e.g. --locale yo",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="Report what would be translated without writing files",
    )
    args = parser.parse_args()

    if not args.api_key:
        print(
            "Error: DeepL API key required.\n"
            "  Set DEEPL_API_KEY environment variable, or pass --api-key KEY\n"
            "  Get a free key at https://www.deepl.com/pro#developer"
        )
        sys.exit(1)

    translator = deepl.Translator(args.api_key)

    default_content = DEFAULT_STRINGS.read_text(encoding="utf-8")
    all_strings = parse_translatable_strings(default_content)
    print(f"Default strings.xml: {len(all_strings)} translatable strings")

    locales = [args.locale] if args.locale else list(LOCALE_DEEPL_MAP.keys())

    for locale in locales:
        deepl_lang = LOCALE_DEEPL_MAP.get(locale)
        mymemory_lang = LOCALE_MYMEMORY_MAP.get(locale)
        locale_file = VALUES_DIR / f"values-{locale}" / "strings.xml"

        if not locale_file.exists():
            print(f"\n[{locale}] SKIP — file not found: {locale_file}")
            continue

        existing_keys = get_existing_keys(locale_file)
        missing = [(name, text) for name, text in all_strings if name not in existing_keys]

        if not missing:
            print(f"[{locale}] up to date")
            continue

        print(f"\n[{locale}] {len(missing)} missing string(s):")

        if deepl_lang is None and mymemory_lang is None:
            print(f"  WARNING: no translation service configured for '{locale}'. Skipping.")
            continue

        if args.dry_run:
            service = "DeepL" if deepl_lang else "MyMemory"
            for name, _ in missing:
                print(f"  would translate via {service}: {name}")
            continue

        new_entries = []
        for name, text in missing:
            try:
                if deepl_lang:
                    translated = translate_deepl(translator, text, deepl_lang)
                else:
                    translated = translate_mymemory(text, mymemory_lang, email=args.mymemory_email)
                new_entries.append((name, translated))
                print(f"  {name}: {translated[:70]}")
            except Exception as exc:
                print(f"  ERROR translating '{name}': {exc}")

        if not new_entries:
            continue

        content = locale_file.read_text(encoding="utf-8")
        lines = "\n".join(
            f'    <string name="{name}">{text}</string>'
            for name, text in new_entries
        )
        updated = content.replace("</resources>", lines + "\n</resources>")
        locale_file.write_text(updated, encoding="utf-8")
        print(f"  -> wrote {len(new_entries)} translation(s) to {locale_file.name}")

    print("\nDone.")


if __name__ == "__main__":
    main()
