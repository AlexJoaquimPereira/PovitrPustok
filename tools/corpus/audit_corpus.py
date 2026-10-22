#!/usr/bin/env python3
"""Audit the generated corpus for two importer defects (read-only).

1. Heading anchors: a chapter with more than one heading where every heading
   points at the chapter's first verse (the "all titles at the top" artifact
   fixed for John 1 by the prefix/anchor repair).
2. Verse-one coverage: a chapter whose first verse record starts after verse
   1. For some sources that is genuine versification; for others it is
   verse-1 text folded into the verse-2 record.

Run after regenerating assets:

    python3 tools/corpus/audit_corpus.py [--assets app/src/main/assets/bible]

Exit status is 1 when a defect class is found, so it can gate a release
check once the corpus is clean. It never writes to the assets.
"""

from __future__ import annotations

import argparse
import os
import sqlite3
import sys

DEFAULT_ASSETS = "app/src/main/assets/bible"


def audit(assets: str) -> int:
    files = sorted(f for f in os.listdir(assets) if f.endswith(".sqlite"))
    if not files:
        print(f"no .sqlite assets under {assets}", file=sys.stderr)
        return 2

    anchor_issues: list[str] = []
    verse_one_issues: list[str] = []

    for filename in files:
        book_id = filename[: -len(".sqlite")]
        path = os.path.join(assets, filename)
        db = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
        try:
            rows = db.execute(
                """
                SELECT c.id, c.position, c.number,
                       (SELECT COUNT(*) FROM headings h WHERE h.chapter_id = c.id)
                           AS headings,
                       (SELECT v.verse_start FROM verses v
                        WHERE v.chapter_id = c.id
                        ORDER BY v.position, v.id LIMIT 1) AS first_verse
                FROM chapters c
                ORDER BY c.position
                """
            ).fetchall()
            for chapter_id, position, number, headings, first_verse in rows:
                if headings and headings > 1 and first_verse is not None:
                    pinned = db.execute(
                        """
                        SELECT COUNT(*) FROM headings
                        WHERE chapter_id = ? AND before_verse = ?
                        """,
                        (chapter_id, first_verse),
                    ).fetchone()[0]
                    if pinned == headings:
                        anchor_issues.append(
                            f"{book_id} ch {number} (position {position}): "
                            f"{headings} headings all at verse {first_verse}"
                        )
                if first_verse is not None and first_verse > 1:
                    verse_one_issues.append(
                        f"{book_id} ch {number} (position {position}): "
                        f"first verse starts at {first_verse}"
                    )
        finally:
            db.close()

    print(f"scanned {len(files)} books under {assets}")
    print(f"heading-anchor issues: {len(anchor_issues)}")
    for line in anchor_issues[:40]:
        print("  " + line)
    if len(anchor_issues) > 40:
        print(f"  ... {len(anchor_issues) - 40} more")
    print(f"verse-one coverage issues: {len(verse_one_issues)}")
    for line in verse_one_issues[:40]:
        print("  " + line)
    if len(verse_one_issues) > 40:
        print(f"  ... {len(verse_one_issues) - 40} more")

    return 1 if (anchor_issues or verse_one_issues) else 0


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--assets", default=DEFAULT_ASSETS)
    args = parser.parse_args()
    return audit(args.assets)


if __name__ == "__main__":
    raise SystemExit(main())
