from __future__ import annotations

import argparse
import json
import subprocess
import sys
import time
from pathlib import Path

from .core import DEFAULT_ARCHIVE_ROOT, ArchiveCrawler, Downloader, Manifest
from .index import KnowledgeIndex


DEFAULT_LOCAL_ROOT = Path("data/3gpp")


def _add_sync_options(parser: argparse.ArgumentParser) -> None:
    parser.add_argument(
        "--series",
        action="append",
        default=[],
        metavar="NN",
        help="limit discovery to one series; repeat for multiple series (default: all)",
    )
    parser.add_argument("--workers", type=int, default=4, help="parallel downloads, 1-8")
    parser.add_argument("--max-files", type=int, default=None, help="bounded download/index smoke run")
    parser.add_argument("--source", default=DEFAULT_ARCHIVE_ROOT, help="official archive root URL")


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        prog="python -m tools.threegpp_kb",
        description="Mirror and search the official 3GPP specification archive.",
    )
    parser.add_argument("--root", type=Path, default=DEFAULT_LOCAL_ROOT, help="local corpus directory")
    subparsers = parser.add_subparsers(dest="command", required=True)

    sync = subparsers.add_parser("sync", help="discover and download official archive files")
    _add_sync_options(sync)

    index = subparsers.add_parser("index", help="extract downloaded files and update SQLite FTS5")
    index.add_argument("--force", action="store_true", help="re-extract every downloaded archive")
    index.add_argument("--max-files", type=int, default=None)

    build = subparsers.add_parser("build", help="run complete sync followed by indexing")
    _add_sync_options(build)
    build.add_argument("--force-index", action="store_true")

    search = subparsers.add_parser("search", help="search the offline full-text index")
    search.add_argument("query", nargs="+")
    search.add_argument("--limit", type=int, default=10)
    search.add_argument("--all-versions", action="store_true")
    search.add_argument("--json", action="store_true", dest="as_json")

    subparsers.add_parser("status", help="show mirror and indexing progress")
    return parser


def _sync(args: argparse.Namespace, manifest: Manifest) -> int:
    print(f"discover source={args.source} series={','.join(args.series) if args.series else 'all'}", flush=True)
    discovered = 0

    def persist_page(page_records):
        nonlocal discovered
        manifest.upsert_archives(page_records, refresh_latest=False)
        discovered += len(page_records)
        if discovered % 100 < len(page_records):
            print(f"discovery_progress={discovered}", flush=True)

    records = ArchiveCrawler(args.source, series=args.series).discover(
        args.max_files,
        on_records=persist_page,
        should_crawl=lambda url: not manifest.is_directory_crawled(url),
        on_directory_crawled=manifest.mark_directory_crawled,
    )
    manifest.refresh_latest()
    print(f"discovered_this_run={len(records)}", flush=True)
    completed, failed = Downloader(manifest, workers=args.workers).download_pending(args.max_files)
    print(f"downloaded_this_run={completed} failed_this_run={failed}", flush=True)
    return 0 if failed == 0 else 2


def _index(args: argparse.Namespace, manifest: Manifest, force: bool = False) -> int:
    total_indexed = 0
    failed = 0
    for attempt in range(3):
        indexed, failed = KnowledgeIndex(manifest).index_pending(
            force=force and attempt == 0, max_files=getattr(args, "max_files", None)
        )
        total_indexed += indexed
        print(
            f"index_attempt={attempt + 1} indexed={indexed} failed={failed}",
            flush=True,
        )
        if failed == 0:
            break
        time.sleep(attempt + 1)
    print(f"indexed_this_run={total_indexed} failed_this_run={failed}", flush=True)
    return 0 if failed == 0 else 3


def _print_status(manifest: Manifest) -> None:
    status = manifest.status()
    keys = ["discovered", "downloaded", "failed", "indexed", "bytes"]
    print(" ".join(f"{key}={status.get(key, 0)}" for key in keys))


def _run_isolated_index(args: argparse.Namespace) -> int:
    command = [sys.executable, "-m", "tools.threegpp_kb", "--root", str(args.root), "index"]
    if args.force_index:
        command.append("--force")
    if args.max_files is not None:
        command.extend(["--max-files", str(args.max_files)])
    return subprocess.run(command, check=False).returncode


def main(argv: list[str] | None = None) -> int:
    args = build_parser().parse_args(argv)
    manifest = Manifest(args.root)
    try:
        if args.command == "sync":
            code = _sync(args, manifest)
            _print_status(manifest)
            return code
        if args.command == "index":
            code = _index(args, manifest, force=args.force)
            _print_status(manifest)
            return code
        if args.command == "build":
            sync_code = _sync(args, manifest)
            manifest.connection.commit()
            index_code = _run_isolated_index(args)
            _print_status(manifest)
            return sync_code or index_code
        if args.command == "search":
            results = KnowledgeIndex(manifest).search(
                " ".join(args.query), limit=args.limit, all_versions=args.all_versions
            )
            if args.as_json:
                print(json.dumps([item.__dict__ for item in results], ensure_ascii=False, indent=2))
            else:
                for number, item in enumerate(results, 1):
                    print(
                        f"[{number}] {item.spec or '-'} {item.version_code or '-'} | "
                        f"{item.member_name}\n{item.snippet}\n{item.source_url}\n"
                    )
            return 0
        if args.command == "status":
            _print_status(manifest)
            return 0
        return 1
    finally:
        manifest.close()


if __name__ == "__main__":
    sys.exit(main())
