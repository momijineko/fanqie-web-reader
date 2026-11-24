#!/usr/bin/env python3
"""Resolve SemVer bump level for a merged PR.

Priority (first match wins within each tier; tiers are ordered):

1. Explicit PR labels
   - bump:major | version:major | breaking     → major
   - bump:minor | version:minor | feature
     | enhancement                             → minor
   - bump:patch | version:patch | bug | fix    → patch
   - bump:none  | version:skip | skip-release  → none

2. Conventional commits in the PR (highest severity wins)
   - BREAKING CHANGE / <type>!:                → major
   - feat                                      → minor
   - fix / perf / refactor / docs / chore /
     test / ci / build / style / revert        → patch

3. 功能点大小 heuristic (when commits give no signal)
   - changed_files >= 25  OR  lines >= 800     → minor  (大型)
   - changed_files >= 8   OR  lines >= 150     → minor if title
     looks feature-like, else patch            (中型)
   - otherwise                                 → patch  (小型)

4. Default → patch

Prints one of: major | minor | patch | none
"""
from __future__ import annotations

import argparse
import json
import re
import sys

LABEL_MAJOR = {"bump:major", "version:major", "breaking"}
LABEL_MINOR = {"bump:minor", "version:minor", "feature", "enhancement"}
LABEL_PATCH = {"bump:patch", "version:patch", "bug", "fix"}
LABEL_NONE = {"bump:none", "version:skip", "skip-release"}

# Conventional Commits: type(optional-scope)optional-!: …
# Matches both feat!: and feat(api)!: (bang after scope per the spec).
COMMIT_TYPE_RE = re.compile(
    r"^(?P<type>feat|fix|perf|refactor|docs|chore|test|ci|build|style|revert)"
    r"(?:\([^)]*\))?"
    r"(?P<breaking>!)?"
    r"\s*:\s*",
    re.IGNORECASE,
)
BREAKING_FOOTER_RE = re.compile(r"^BREAKING CHANGE:", re.MULTILINE | re.IGNORECASE)

FEATURE_TITLE_RE = re.compile(
    r"(feat\b|feature|enhancement|添加|新增|支持|实现|接入|切换为|引入)",
    re.IGNORECASE,
)
FIX_TITLE_RE = re.compile(
    r"(fix\b|bug|hotfix|修复|修正|回滚)",
    re.IGNORECASE,
)


def _norm_labels(labels: list[str]) -> set[str]:
    return {x.strip().lower() for x in labels if x and x.strip()}


def bump_from_labels(labels: list[str]) -> str | None:
    names = _norm_labels(labels)
    if names & LABEL_NONE:
        return "none"
    if names & LABEL_MAJOR:
        return "major"
    if names & LABEL_MINOR:
        return "minor"
    if names & LABEL_PATCH:
        return "patch"
    return None


def bump_from_commits(messages: list[str]) -> str | None:
    level: str | None = None
    rank = {"patch": 1, "minor": 2, "major": 3}

    def raise_to(next_level: str) -> None:
        nonlocal level
        if level is None or rank[next_level] > rank[level]:
            level = next_level

    for raw in messages:
        msg = (raw or "").strip()
        if not msg:
            continue
        if BREAKING_FOOTER_RE.search(msg):
            raise_to("major")
            continue
        first = msg.splitlines()[0]
        m = COMMIT_TYPE_RE.match(first)
        if not m:
            continue
        if m.group("breaking"):
            raise_to("major")
            continue
        ctype = m.group("type").lower()
        if ctype == "feat":
            raise_to("minor")
        else:
            raise_to("patch")
    return level


def bump_from_size(title: str, changed_files: int, lines: int) -> str:
    """功能点大小：按改动体量 + 标题语义估 patch/minor（从不因体量 alone 升 major）。"""
    title = title or ""
    large = changed_files >= 25 or lines >= 800
    medium = changed_files >= 8 or lines >= 150

    if large:
        return "minor"
    if medium:
        if FEATURE_TITLE_RE.search(title):
            return "minor"
        return "patch"
    if FEATURE_TITLE_RE.search(title) and not FIX_TITLE_RE.search(title):
        # 小改动但标题明确是新功能 → minor
        return "minor"
    return "patch"


def resolve_bump(
    *,
    labels: list[str],
    commit_messages: list[str],
    title: str = "",
    changed_files: int = 0,
    additions: int = 0,
    deletions: int = 0,
) -> str:
    labeled = bump_from_labels(labels)
    if labeled is not None:
        return labeled

    from_commits = bump_from_commits(commit_messages)
    if from_commits is not None:
        return from_commits

    lines = max(0, int(additions)) + max(0, int(deletions))
    return bump_from_size(title, max(0, int(changed_files)), lines)


def bump_semver(tag: str, level: str) -> str:
    """Bump vMAJOR.MINOR.PATCH → next tag. level=none returns same tag."""
    raw = tag.strip()
    if raw.startswith("v"):
        raw = raw[1:]
    parts = raw.split(".")
    if len(parts) != 3 or not all(p.isdigit() for p in parts):
        raise ValueError(f"unsupported tag format: {tag!r} (want vMAJOR.MINOR.PATCH)")
    major, minor, patch = (int(parts[0]), int(parts[1]), int(parts[2]))
    if level == "none":
        return f"v{major}.{minor}.{patch}"
    if level == "major":
        return f"v{major + 1}.0.0"
    if level == "minor":
        return f"v{major}.{minor + 1}.0"
    if level == "patch":
        return f"v{major}.{minor}.{patch + 1}"
    raise ValueError(f"unknown bump level: {level!r}")


def _bind_dashed_title(argv: list[str]) -> list[str]:
    """Attach `--title VALUE` as `--title=VALUE`.

    argparse treats a separate token that starts with `-` as a new option
    (`argument --title: expected one argument`), which drops the release.
    """
    out: list[str] = []
    i = 0
    while i < len(argv):
        tok = argv[i]
        if tok == "--title" and i + 1 < len(argv):
            out.append(f"--title={argv[i + 1]}")
            i += 2
            continue
        out.append(tok)
        i += 1
    return out


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--title", default="")
    parser.add_argument("--labels", default="[]", help="JSON list of label names")
    parser.add_argument("--commits", default="[]", help="JSON list of commit messages")
    parser.add_argument("--changed-files", type=int, default=0)
    parser.add_argument("--additions", type=int, default=0)
    parser.add_argument("--deletions", type=int, default=0)
    parser.add_argument("--current-tag", default="", help="If set, also print next tag")
    parser.add_argument(
        "--json",
        action="store_true",
        help="Print {bump, next_tag?} as JSON",
    )
    if argv is None:
        argv = sys.argv[1:]
    args = parser.parse_args(_bind_dashed_title(argv))

    labels = json.loads(args.labels)
    commits = json.loads(args.commits)
    bump = resolve_bump(
        labels=labels,
        commit_messages=commits,
        title=args.title,
        changed_files=args.changed_files,
        additions=args.additions,
        deletions=args.deletions,
    )
    next_tag = bump_semver(args.current_tag, bump) if args.current_tag else None
    if args.json:
        payload = {"bump": bump}
        if next_tag is not None:
            payload["next_tag"] = next_tag
            payload["current_tag"] = (
                args.current_tag
                if args.current_tag.startswith("v")
                else f"v{args.current_tag}"
            )
        print(json.dumps(payload, ensure_ascii=False))
    else:
        if next_tag is not None:
            print(f"{bump} {next_tag}")
        else:
            print(bump)
    return 0


if __name__ == "__main__":
    sys.exit(main())
