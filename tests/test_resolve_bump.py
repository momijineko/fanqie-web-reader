import json

from scripts.resolve_bump import (
    bump_from_commits,
    bump_from_labels,
    bump_from_size,
    bump_semver,
    main,
    resolve_bump,
)


def test_labels_override_commits():
    assert (
        resolve_bump(
            labels=["bump:patch"],
            commit_messages=["feat: big feature"],
            title="feat: big",
            changed_files=100,
            additions=900,
            deletions=50,
        )
        == "patch"
    )


def test_skip_release_label():
    assert bump_from_labels(["skip-release"]) == "none"


def test_breaking_commit():
    assert bump_from_commits(["feat!: drop unidbg"]) == "major"
    assert bump_from_commits(["feat(api)!: drop unidbg"]) == "major"
    assert bump_from_commits(["feat: x\n\nBREAKING CHANGE: api gone"]) == "major"


def test_feat_and_fix_commits():
    assert bump_from_commits(["fix: typo", "feat: shelf sort"]) == "minor"
    assert bump_from_commits(["fix: typo", "chore: deps"]) == "patch"


def test_size_large_is_minor():
    assert bump_from_size("杂项调整", changed_files=30, lines=100) == "minor"
    assert bump_from_size("杂项调整", changed_files=2, lines=900) == "minor"


def test_size_medium_feature_title():
    assert bump_from_size("添加书架排序切换", changed_files=10, lines=200) == "minor"
    assert bump_from_size("修复代理 502", changed_files=10, lines=200) == "patch"


def test_size_small_feature_title():
    assert bump_from_size("新增关于页版本展示", changed_files=2, lines=40) == "minor"
    assert bump_from_size("修复版本显示", changed_files=2, lines=40) == "patch"


def test_default_small_patch():
    assert (
        resolve_bump(
            labels=[],
            commit_messages=["Update deploy.yml"],
            title="Update deploy.yml",
            changed_files=2,
            additions=20,
            deletions=5,
        )
        == "patch"
    )


def test_cli_title_leading_dash(capsys):
    rc = main(
        [
            "--json",
            "--title",
            "- Update deploy",
            "--labels",
            "[]",
            "--commits",
            '["Update deploy.yml"]',
            "--changed-files",
            "2",
            "--additions",
            "20",
            "--deletions",
            "5",
            "--current-tag",
            "v0.2.2",
        ]
    )
    assert rc == 0
    payload = json.loads(capsys.readouterr().out)
    assert payload["bump"] == "patch"
    assert payload["next_tag"] == "v0.2.3"


def test_bump_semver():
    assert bump_semver("v0.2.2", "patch") == "v0.2.3"
    assert bump_semver("v0.2.2", "minor") == "v0.3.0"
    assert bump_semver("v0.2.2", "major") == "v1.0.0"
    assert bump_semver("0.2.2", "none") == "v0.2.2"
