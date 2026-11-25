"""Fails when a built APK carries a Google API key that is not expected to be there.

Anything inside an APK is public: anyone can download it from Google Play and unpack it.
Google suspended Actifit's Gemini project after the key built into the app was harvested,
so run this on every release APK before uploading it:

    python scripts/check_apk_keys.py "app/release/Actifit v0.16.2.apk"

Allowed: the Firebase keys from app/google-services.json (public by design and restricted
to this app) and keys Google's own SDKs bundle (listed by SHA-256 in KNOWN_SDK_KEYS).
Prints only the last 4 characters of each key it finds.
"""
import hashlib
import json
import os
import re
import sys
import zipfile

KEY = re.compile(rb'AIza[0-9A-Za-z_\-]{35}|AQ\.A[0-9A-Za-z_\-]{40,}')

# Google's keys inside its SDKs, not ours (sha256 of the key)
KNOWN_SDK_KEYS = {
    # play-services-ads-lite (com/google/android/gms/internal/ads), ends ...xNPE
    'd595943ef9e36bf1bf1bce4fe6401db1c978e293abc5ab2d2fea349fd780fa68',
}


def sha(k):
    return hashlib.sha256(k).hexdigest()


def firebase_keys(root):
    path = os.path.join(root, 'app', 'google-services.json')
    if not os.path.exists(path):
        return set()
    data = json.load(open(path, encoding='utf-8'))
    return {sha(k['current_key'].encode()) for c in data.get('client', []) for k in c.get('api_key', [])}


def scan(apk):
    found = {}
    with zipfile.ZipFile(apk) as z:
        for name in z.namelist():
            data = z.read(name)
            for k in set(KEY.findall(data)):
                found.setdefault(k, set()).add(name)
    return found


def main(argv):
    if len(argv) != 2:
        print(__doc__)
        return 2
    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    allowed = KNOWN_SDK_KEYS | firebase_keys(root)
    bad = 0
    for k, where in sorted(scan(argv[1]).items()):
        ok = sha(k) in allowed
        bad += not ok
        print(('ok      ' if ok else 'UNKNOWN ') + '...' + k[-4:].decode() + '  ' + ', '.join(sorted(where)[:3]))
    if bad:
        print('\n%d unknown key(s) in the APK. Do not publish it: move the key to the bot.' % bad)
        return 1
    print('no unexpected keys')
    return 0


if __name__ == '__main__':
    sys.exit(main(sys.argv))
