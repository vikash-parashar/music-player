"""Send store-listing text from distribution/listings/<lang>/ to Google Play.

Uses the Play Developer API directly (edits.insert -> listings.update per language ->
edits.commit), so no build or version code is needed. Only the languages present as
folders are touched; the India listing (en-IN, hi-IN) is left alone.

Usage: PLAY_JSON=<service account json> python update_listings.py <listings_dir> <package>
"""
import json, os, sys
from pathlib import Path

import google.auth.transport.requests
from google.oauth2 import service_account

API = "https://androidpublisher.googleapis.com/androidpublisher/v3/applications"


def main():
    root, package = Path(sys.argv[1]), sys.argv[2]
    creds = service_account.Credentials.from_service_account_info(
        json.loads(os.environ["PLAY_JSON"]),
        scopes=["https://www.googleapis.com/auth/androidpublisher"],
    )
    session = google.auth.transport.requests.AuthorizedSession(creds)

    def call(method, path, **kw):
        r = session.request(method, f"{API}/{package}{path}", **kw)
        if r.status_code >= 300:
            sys.exit(f"Play API {method} {path} failed ({r.status_code}): {r.text}")
        return r.json() if r.text else {}

    edit = call("POST", "/edits")["id"]
    for lang_dir in sorted(p for p in root.iterdir() if p.is_dir()):
        read = lambda name: (lang_dir / name).read_text(encoding="utf-8").strip()
        body = {
            "language": lang_dir.name,
            "title": read("title.txt"),
            "shortDescription": read("short_description.txt"),
            "fullDescription": read("full_description.txt"),
        }
        call("PUT", f"/edits/{edit}/listings/{lang_dir.name}", json=body)
        print(f"listing {lang_dir.name}: {body['title']}")
    # With managed publishing on, the owner still presses "Publish changes" in Play Console.
    r = session.post(f"{API}/{package}/edits/{edit}:commit")
    if r.status_code >= 300 and "changesNotSentForReview" in r.text:
        r = session.post(f"{API}/{package}/edits/{edit}:commit", params={"changesNotSentForReview": "true"})
    if r.status_code >= 300:
        sys.exit(f"Commit failed ({r.status_code}): {r.text}")
    print("Committed. Check Play Console > Publishing overview.")


if __name__ == "__main__":
    main()
