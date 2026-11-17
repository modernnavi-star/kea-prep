#!/usr/bin/env python3
"""Build a transparent, key-free KEA current-affairs cache from public government sources.

The script intentionally does not invent facts or call a generative model. It stores the source
headline, a short source description, date and URL, then creates recognition-style four-option
revision checks from four *different* published headlines. The Android app labels every item
VERIFY and links it to its source URL.
"""
from __future__ import annotations

import hashlib
import html
import json
import re
import sys
import xml.etree.ElementTree as ET
from datetime import datetime, timezone
from pathlib import Path
from urllib.parse import urljoin
from urllib.request import Request, urlopen

ROOT = Path(__file__).resolve().parents[1]
SOURCES_PATH = ROOT / "data/live/feed-sources.json"
OUTPUT_PATH = ROOT / "data/live/kea-live-feed.json"
USER_AGENT = "KEA-Prep-Live-Feed/1.0 (+https://github.com/modernnavi-star/kea-prep)"


def clean(text: str) -> str:
    text = re.sub(r"<[^>]+>", " ", text or "")
    text = html.unescape(text)
    return re.sub(r"\s+", " ", text).strip()


def fetch(url: str) -> str:
    request = Request(url, headers={"User-Agent": USER_AGENT, "Accept": "application/rss+xml, application/xml, text/html;q=0.9"})
    with urlopen(request, timeout=20) as response:  # nosec B310: URLs are repository-reviewed public sources
        return response.read().decode(response.headers.get_content_charset() or "utf-8", errors="replace")


def rss_items(source: dict, payload: str) -> list[dict]:
    root = ET.fromstring(payload)
    result = []
    for element in root.findall(".//item") + root.findall(".//{http://www.w3.org/2005/Atom}entry"):
        title = clean(element.findtext("title") or element.findtext("{http://www.w3.org/2005/Atom}title") or "")
        link = clean(element.findtext("link") or "")
        atom_link = element.find("{http://www.w3.org/2005/Atom}link")
        if atom_link is not None:
            link = atom_link.attrib.get("href", link)
        summary = clean(
            element.findtext("description")
            or element.findtext("{http://purl.org/rss/1.0/modules/content/}encoded")
            or element.findtext("{http://www.w3.org/2005/Atom}summary")
            or ""
        )
        published = clean(element.findtext("pubDate") or element.findtext("{http://www.w3.org/2005/Atom}updated") or "")
        if title and link:
            result.append(make_item(source, title, summary, link, published))
    return result


def html_heading_items(source: dict, payload: str) -> list[dict]:
    # Conservative fallback for an official site without a published RSS feed: retain only visible
    # heading/anchor text and its official URL; nothing is summarized or inferred.
    result = []
    pattern = re.compile(r"<a\b[^>]*href=[\"']([^\"'#?]+)[\"'][^>]*>(.*?)</a>", re.I | re.S)
    for href, body in pattern.findall(payload):
        title = clean(body)
        if len(title) < 18 or len(title) > 220:
            continue
        if not re.search(r"news|press|scheme|government|karnataka|release|announcement", title, re.I):
            continue
        result.append(make_item(source, title, "Official Karnataka Government page headline.", urljoin(source["url"], href), ""))
        if len(result) >= 12:
            break
    return result


def make_item(source: dict, title: str, summary: str, link: str, published: str) -> dict:
    digest = hashlib.sha256(f"{source['name']}|{title}|{link}".encode("utf-8")).hexdigest()[:16]
    return {
        "id": f"live-{digest}",
        "title": title[:300],
        "summary": summary[:700] or "Open the official source for the complete announcement.",
        "sourceName": source["name"],
        "sourceUrl": link,
        "publishedAt": published,
        "paper": source.get("paper", "Paper 1"),
        "requiresVerification": True,
    }


def build_mcqs(items: list[dict]) -> list[dict]:
    # The question only tests whether the learner recognizes a published source headline. It makes
    # no unverified claim beyond the retained official item and uses other source headlines as options.
    mcqs = []
    for index, item in enumerate(items[:24]):
        alternatives = [candidate["title"] for candidate in items if candidate["id"] != item["id"]]
        if len(alternatives) < 3:
            break
        options = [item["title"], *alternatives[:3]]
        rotation = index % 4
        options = options[rotation:] + options[:rotation]
        mcqs.append({
            "id": f"mcq-{item['id']}",
            "topicId": "topic-09",
            "paper": item["paper"],
            "question": f"Which update was published by {item['sourceName']} in the live KEA feed?",
            "options": options,
            "answer": options.index(item["title"]),
            "explanation": f"The correct headline is retained exactly from {item['sourceName']}. Re-open the official source before using it for an exam.",
            "sourceUrl": item["sourceUrl"],
            "requiresVerification": True,
        })
    return mcqs


def main() -> int:
    config = json.loads(SOURCES_PATH.read_text(encoding="utf-8"))
    all_items: list[dict] = []
    failures: list[str] = []
    for source in config.get("sources", []):
        try:
            payload = fetch(source["url"])
            if source.get("format") == "rss":
                all_items.extend(rss_items(source, payload))
            elif source.get("format") == "html-headings":
                all_items.extend(html_heading_items(source, payload))
            else:
                failures.append(f"unsupported format: {source.get('name')}")
        except Exception as error:  # The prior valid cache is deliberately kept if a source is temporarily unavailable.
            failures.append(f"{source.get('name', 'unknown')}: {error}")

    # Stable dedupe protects the Android cache and daily commits from duplicate headlines.
    deduped = []
    seen = set()
    for item in all_items:
        if item["id"] not in seen:
            seen.add(item["id"])
            deduped.append(item)
    if not deduped:
        print("No feed items fetched; leaving the previous verified cache unchanged.", file=sys.stderr)
        return 0

    output = {
        "schemaVersion": 1,
        "generatedAt": datetime.now(timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z"),
        "notice": "Public-source current-affairs cache. All items are automatically marked VERIFY; check the linked official/current source before an exam.",
        "items": deduped[:60],
        "mcqs": build_mcqs(deduped),
        "sourceFailures": failures,
    }
    OUTPUT_PATH.write_text(json.dumps(output, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    print(f"Wrote {len(output['items'])} live items and {len(output['mcqs'])} source-derived checks.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
