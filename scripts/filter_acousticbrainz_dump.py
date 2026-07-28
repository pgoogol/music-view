#!/usr/bin/env python3
"""Jednorazowy ETL dumpa AcousticBrainz (M1.4, D7).

Strumieniowo filtruje dump lowlevel po MBID-ach biblioteki i produkuje CSV
gotowy do `\\copy` w tabelę audio_features. Dump NIE trafia do repo.

Użycie:
    python3 scripts/filter_acousticbrainz_dump.py \
        --mapping mbid_mapping.csv \
        --output audio_features.csv \
        acousticbrainz-lowlevel-json-20220623-*.tar.zst

Wejście --mapping: CSV bez nagłówka `mbid,spotify_id` (eksport z bazy —
patrz docs/AB_ETL.md). Obsługiwane archiwa: .tar.zst (wymaga `zstd` w PATH),
.tar.bz2, .tar.gz, .tar.

Ekstrahowane pola lowlevel: rhythm.bpm, tonal.key_key + key_scale,
rhythm.danceability. Duplikaty MBID (-0/-1/…): wygrywa najniższy sufiks.
"""

import argparse
import csv
import json
import re
import subprocess
import sys
import tarfile

MBID_RE = re.compile(
    r"([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})-(\d+)\.json$"
)


def load_mapping(path):
    mapping = {}
    with open(path, newline="", encoding="utf-8") as handle:
        for row in csv.reader(handle):
            if len(row) >= 2 and row[0].strip():
                mapping[row[0].strip().lower()] = row[1].strip()
    return mapping


def open_archive(path):
    if path.endswith(".tar.zst"):
        process = subprocess.Popen(
            ["zstd", "-dc", path], stdout=subprocess.PIPE, bufsize=1024 * 1024
        )
        return tarfile.open(fileobj=process.stdout, mode="r|"), process
    return tarfile.open(path, mode="r|*"), None


def extract_features(document):
    rhythm = document.get("rhythm", {})
    tonal = document.get("tonal", {})
    bpm = rhythm.get("bpm")
    danceability = rhythm.get("danceability")
    key = tonal.get("key_key")
    scale = tonal.get("key_scale")
    musical_key = f"{key} {scale}".strip() if key else None
    return bpm, musical_key, danceability


def process_archive(path, mapping, results):
    matched = 0
    archive, process = open_archive(path)
    with archive:
        for member in archive:
            if not member.isfile():
                continue
            match = MBID_RE.search(member.name)
            if not match:
                continue
            mbid, offset = match.group(1), int(match.group(2))
            if mbid not in mapping:
                continue
            current = results.get(mbid)
            if current is not None and current[0] <= offset:
                continue
            payload = archive.extractfile(member)
            if payload is None:
                continue
            try:
                document = json.load(payload)
            except json.JSONDecodeError:
                print(f"UWAGA: pominięto niepoprawny JSON: {member.name}", file=sys.stderr)
                continue
            bpm, musical_key, danceability = extract_features(document)
            if current is None:
                matched += 1
            results[mbid] = (offset, [mbid, mapping[mbid], bpm, musical_key, danceability])
    if process is not None:
        process.stdout.close()
        process.wait()
    return matched


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--mapping", required=True, help="CSV mbid,spotify_id")
    parser.add_argument("--output", required=True, help="wynikowy CSV dla \\copy")
    parser.add_argument("archives", nargs="+", help="archiwa dumpa AB")
    args = parser.parse_args()

    mapping = load_mapping(args.mapping)
    print(f"MBID-y do znalezienia: {len(mapping)}")

    # wyniki w pamięci (≤ rozmiar biblioteki): duplikaty -0/-1/… rozstrzyga
    # najniższy sufiks, niezależnie od kolejności plików w tarze
    results = {}
    for path in args.archives:
        matched = process_archive(path, mapping, results)
        print(f"{path}: trafienia {matched} (łącznie {len(results)})")

    with open(args.output, "w", newline="", encoding="utf-8") as handle:
        writer = csv.writer(handle)
        for _, row in sorted(results.items()):
            writer.writerow(row[1])

    missing = len(mapping) - len(results)
    print(f"Gotowe: {len(results)} rekordów w {args.output}; bez dopasowania: {missing}")


if __name__ == "__main__":
    main()
