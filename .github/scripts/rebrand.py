from pathlib import Path
import re
import shutil
import xml.etree.ElementTree as ET

ROOT = Path.cwd()
SCRIPT_PATH = ".github/scripts/rebrand.py"
WORKFLOW_PATHS = {
    ".github/workflows/android.yml",
    ".github/workflows/validate-strings-no-translations.yml",
    ".github/workflows/deku-rebrand.yml",
    SCRIPT_PATH,
}
PROTECTED_FILES = {
    "LICENSE",
    "UPSTREAM.md",
    "docs/automation/crowdin-upload-upstream.yml.disabled",
    "docs/automation/crowdin-upload.yml.disabled",
    "docs/automation/crowdin-upstream.yml.disabled",
}
PROTECTED_PARTS = {"licenses", "third_party", "third-party"}
PROTECTED_SUFFIXES = {
    ".license", ".spdx", ".pdf", ".jar", ".class", ".keystore", ".jks", ".db",
    ".sqlite", ".sqlite3", ".png", ".jpg", ".jpeg", ".webp", ".gif", ".ttf",
    ".otf", ".apk", ".aab", ".zip", ".gz", ".so", ".dylib", ".dll", ".woff",
    ".woff2", ".mp4", ".mov", ".wav", ".mp3",
}
ADDONS_HOST = "beta.addons.florisboard.org"
ADDONS_TOKEN = "__DEKU_UPSTREAM_ADDONS_HOST__"

PATH_REPLACEMENTS = [
    ("dev/patrickgold/florisboard", "org/deku/foundation/dekukeyboard"),
    ("org/florisboard", "org/deku/foundation/dekukeyboard"),
    ("dev.patrickgold.florisboard", "org.deku.foundation.dekukeyboard"),
    ("org.florisboard", "org.deku.foundation.dekukeyboard"),
    ("dev_patrickgold_florisboard", "org_deku_foundation_dekukeyboard"),
    ("org_florisboard", "org_deku_foundation_dekukeyboard"),
    ("fl_native_rust", "deku_native_rust"),
    ("fl_native", "deku_native"),
    ("FlorisBoard", "DekuKeyboard"),
    ("Florisboard", "DekuKeyboard"),
    ("Floris", "Deku"),
    ("florisboard", "dekukeyboard"),
    ("floris", "deku"),
]
CONTENT_REPLACEMENTS = [
    ("dev.patrickgold.florisboard", "org.deku.foundation.dekukeyboard"),
    ("org.florisboard", "org.deku.foundation.dekukeyboard"),
    ("dev/patrickgold/florisboard", "org/deku/foundation/dekukeyboard"),
    ("org/florisboard", "org/deku/foundation/dekukeyboard"),
    ("dev_patrickgold_florisboard", "org_deku_foundation_dekukeyboard"),
    ("org_florisboard", "org_deku_foundation_dekukeyboard"),
    ("FLORISBOARD", "DEKU_KEYBOARD"),
    ("FLORIS", "DEKU"),
    ("FLADDONS", "DEKU_ADDONS"),
    ("fl_native_rust", "deku_native_rust"),
    ("fl_native", "deku_native"),
    ("FlorisBoard", "DekuKeyboard"),
    ("Florisboard", "DekuKeyboard"),
    ("Floris", "Deku"),
    ("florisboard", "dekukeyboard"),
    ("floris", "deku"),
]


def protected_content(path: str) -> bool:
    p = Path(path)
    lower_parts = {part.lower() for part in p.parts}
    if path in PROTECTED_FILES:
        return True
    if lower_parts.intersection(PROTECTED_PARTS):
        return True
    if p.name.upper() in {"NOTICE", "COPYING", "COPYRIGHT", "LICENSE"}:
        return True
    if p.suffix.lower() in PROTECTED_SUFFIXES:
        return True
    if p.name.lower() == "ofl.txt":
        return True
    return False


def new_path(path: str) -> str:
    if path == "crowdin.yml":
        return "docs/automation/crowdin-upstream.yml.disabled"
    path = path.replace(
        "docs/automation/crowdin-upload.yml.disabled",
        "docs/automation/crowdin-upload-upstream.yml.disabled",
    )
    for old, new in PATH_REPLACEMENTS:
        path = path.replace(old, new)
    return path


def replace_content(content: str, old_path: str) -> str:
    if protected_content(old_path) or old_path in WORKFLOW_PATHS:
        return content

    content = content.replace(
        "https://github.com/florisboard/florisboard",
        "https://github.com/Deku-Foundation/deku-keyboard",
    )
    content = content.replace(
        "http://github.com/florisboard/florisboard",
        "https://github.com/Deku-Foundation/deku-keyboard",
    )
    content = content.replace(
        "https://florisboard.org/legal/privacy/",
        "https://github.com/Deku-Foundation/deku-keyboard/blob/main/PRIVACY.md",
    )
    content = content.replace(
        "https://docs.florisboard.org",
        "https://github.com/Deku-Foundation/deku-keyboard/tree/main/docs",
    )
    content = content.replace(
        "https://crowdin.florisboard.org",
        "https://github.com/Deku-Foundation/deku-keyboard",
    )
    content = content.replace(
        "https://florisboard.org",
        "https://github.com/Deku-Foundation/deku-keyboard",
    )
    content = content.replace(
        "http://florisboard.org",
        "https://github.com/Deku-Foundation/deku-keyboard",
    )
    content = content.replace(
        "https://matrix.to/#/#florisboard:matrix.org",
        "https://github.com/Deku-Foundation/deku-keyboard/issues",
    )
    content = content.replace(
        "https://matrix.to/#/#florisboard-dev:matrix.org",
        "https://github.com/Deku-Foundation/deku-keyboard/issues",
    )
    content = content.replace(
        "florisboard@patrickgold.dev",
        "Deku Foundation maintainers",
    )
    # Keep links to separate upstream repositories intact rather than manufacturing
    # URLs by changing their owner. The main upstream project URL was remapped above.
    preserved_urls = []
    def preserve_other_upstream_url(match):
        preserved_urls.append(match.group(0))
        return f"__DEKU_PRESERVED_GITHUB_URL_{len(preserved_urls)-1}__"
    content = re.sub(
        r"https?://github\.com/florisboard/(?!florisboard(?:/|$))[^\\s\\)\\]\\\"']+",
        preserve_other_upstream_url,
        content,
    )
    content = content.replace(ADDONS_HOST, ADDONS_TOKEN)

    code_file = Path(old_path).suffix.lower() in {
        ".kt", ".kts", ".java", ".rs", ".c", ".h", ".cc", ".cpp",
    }
    output = []
    for line in content.splitlines(keepends=True):
        # Preserve ownership and license notices verbatim on source-derived files.
        if re.search(
            r"(SPDX-FileCopyrightText|SPDX-License-Identifier|Copyright\s*\(|Copyright\s+|Derived from:)",
            line,
            re.IGNORECASE,
        ):
            output.append(line)
            continue
        for old, new in CONTENT_REPLACEMENTS:
            line = line.replace(old, new)
        if not code_file:
            line = line.replace("DekuKeyboard", "Deku Keyboard")
        output.append(line)

    content = "".join(output)
    content = content.replace(ADDONS_TOKEN, ADDONS_HOST)
    for index, url in enumerate(preserved_urls):
        content = content.replace(f"__DEKU_PRESERVED_GITHUB_URL_{index}__", url)

    if old_path == "README.md":
        content = content.replace(
            "This repository begins from the [Deku Keyboard](https://github.com/Deku-Foundation/deku-keyboard) open-source codebase and will be developed independently. The current baseline retains upstream implementation and third-party components; the project will be rebranded and modernized in reviewed stages rather than by indiscriminate search-and-replace.",
            "This independent project began from the FlorisBoard open-source codebase. It is now being adapted and maintained independently by Deku Foundation; see [UPSTREAM.md](UPSTREAM.md) for attribution and license context.",
        )
        content = content.replace(
            "This project is derived from Deku Keyboard and retains its upstream license, copyright statements, and applicable third-party notices.",
            "This project is derived from FlorisBoard and retains its upstream license, copyright statements, and applicable third-party notices.",
        )
        content = content.replace(
            "Deku Keyboard is an independent project and is not the official Deku Keyboard project.",
            "Deku Keyboard is an independent derivative, not the official FlorisBoard project.",
        )
    if old_path == "AI_POLICY.md":
        content = content.replace(
            "This policy has been adapted for Deku Foundation from the upstream Deku Keyboard policy.",
            "This policy has been adapted from the upstream FlorisBoard policy for Deku Foundation.",
        )
    if old_path == "fastlane/README.md":
        content = content.replace(
            "the inherited screenshots, feature graphics, store icons, translated listing text, and historical changelog files still include upstream Deku Keyboard material.",
            "the inherited screenshots, feature graphics, store icons, translated listing text, and historical changelog files still include upstream FlorisBoard material.",
        )
    if old_path == "docs/automation/README.md":
        content = content.replace("crowdin-upload.yml.disabled", "crowdin-upload-upstream.yml.disabled")
    return content


# Archive upstream Crowdin settings rather than allowing them to look like a Deku project setup.
crowdin_source = ROOT / "crowdin.yml"
if crowdin_source.exists():
    crowdin_target = ROOT / "docs/automation/crowdin-upstream.yml.disabled"
    crowdin_target.parent.mkdir(parents=True, exist_ok=True)
    original = crowdin_source.read_text(encoding="utf-8")
    header = (
        "# Archived upstream FlorisBoard Crowdin configuration.\n"
        "# Not an active Deku integration; do not upload with this file.\n"
    )
    crowdin_target.write_text(header + original, encoding="utf-8")
    crowdin_source.unlink()

entries = []
for src in ROOT.rglob("*"):
    if not src.is_file():
        continue
    rel = src.relative_to(ROOT).as_posix()
    if rel == ".git" or rel.startswith(".git/") or rel in WORKFLOW_PATHS:
        continue

    raw = src.read_bytes()
    data = raw
    if b"\x00" not in raw and not protected_content(rel):
        try:
            decoded = raw.decode("utf-8")
        except UnicodeDecodeError:
            decoded = None
        if decoded is not None:
            data = replace_content(decoded, rel).encode("utf-8")

    target_rel = new_path(rel)
    entries.append((src, ROOT / target_rel, rel, target_rel, data))

source_relpaths = {entry[2] for entry in entries}
destinations = {}
for src, dest, old_rel, new_rel, data in entries:
    if new_rel in destinations:
        raise SystemExit(
            f"Path collision: {old_rel} and {destinations[new_rel]} both map to {new_rel}"
        )
    destinations[new_rel] = old_rel
    if dest.exists() and dest != src and dest.relative_to(ROOT).as_posix() not in source_relpaths:
        raise SystemExit(f"Destination already exists: {new_rel}")

staging = ROOT / ".deku-rebrand-staging"
if staging.exists():
    shutil.rmtree(staging)
staging.mkdir()
try:
    final_root = staging / "final"
    for index, (src, dest, old_rel, new_rel, data) in enumerate(entries):
        temp = staging / str(index)
        temp.write_bytes(data)
        final = final_root / new_rel
        final.parent.mkdir(parents=True, exist_ok=True)
        shutil.move(str(temp), str(final))

    # The staging tree now contains every source file and transformed destination.
    for src, dest, old_rel, new_rel, data in entries:
        if src.exists():
            src.unlink()
    for src, dest, old_rel, new_rel, data in entries:
        final = final_root / new_rel
        dest.parent.mkdir(parents=True, exist_ok=True)
        if dest.exists():
            raise SystemExit(f"Unexpected overwrite while applying rename: {new_rel}")
        shutil.move(str(final), str(dest))
finally:
    if staging.exists():
        shutil.rmtree(staging)

automation_readme = ROOT / "docs/automation/README.md"
if automation_readme.exists():
    text = automation_readme.read_text(encoding="utf-8")
    text = text.replace("crowdin-upload.yml.disabled", "crowdin-upload-upstream.yml.disabled")
    if "crowdin-upstream.yml.disabled" not in text:
        text += (
            "\n- crowdin-upstream.yml.disabled is an archived upstream configuration, not a Deku project configuration.\n"
        )
    automation_readme.write_text(text, encoding="utf-8")

build_file = ROOT / "app/build.gradle.kts"
build_text = build_file.read_text(encoding="utf-8")
if 'namespace = "org.deku.foundation.dekukeyboard"' not in build_text:
    raise SystemExit("Android namespace migration did not reach the expected Deku namespace.")
if 'applicationId = "org.deku.foundation.dekukeyboard"' not in build_text:
    raise SystemExit("Android applicationId is not the expected Deku package ID.")

xml_count = 0
for path in ROOT.rglob("*.xml"):
    rel = path.relative_to(ROOT).as_posix()
    if rel.startswith(".git/") or "license" in {part.lower() for part in path.parts}:
        continue
    try:
        ET.parse(path)
        xml_count += 1
    except ET.ParseError as exc:
        raise SystemExit(f"Malformed XML after migration: {rel}: {exc}") from exc

stale_paths = []
for path in ROOT.rglob("*"):
    if path.exists():
        rel = path.relative_to(ROOT).as_posix()
        if any(token in rel for token in ("dev/patrickgold/florisboard", "org/florisboard")):
            stale_paths.append(rel)
if stale_paths:
    raise SystemExit("Old source package paths remain: " + ", ".join(stale_paths[:20]))

legacy_patterns = [
    "dev.patrickgold.florisboard",
    "dev/patrickgold/florisboard",
    "org.florisboard",
    "org/florisboard",
    "FlorisApplication",
    "FlorisImeService",
    "floris_app_icon",
    "floris_app_name",
    "https://github.com/florisboard/florisboard",
    ADDONS_HOST,
]
residuals = []
for path in ROOT.rglob("*"):
    if not path.is_file():
        continue
    rel = path.relative_to(ROOT).as_posix()
    if protected_content(rel) or rel == "UPSTREAM.md" or rel in WORKFLOW_PATHS:
        continue
    try:
        text = path.read_text(encoding="utf-8")
    except (UnicodeDecodeError, OSError):
        continue
    for pattern in legacy_patterns:
        if pattern in text:
            residuals.append((rel, pattern, text.count(pattern)))

report = [
    "# Deku identity migration report",
    "",
    "This migration updates Android and library package namespaces, source paths, class and file names, JNI symbols, Android component references, resource names, built-in extension identifiers, labels, project links, issue templates, and development documentation.",
    "",
    "## Preserved attribution and dependencies",
    "",
    "- LICENSE, UPSTREAM.md, source copyright/SPDX notices, and third-party license data are preserved.",
    "- The live add-ons-store host remains beta.addons.florisboard.org until Deku operates a replacement. It is not renamed to a fabricated domain.",
    "- Store-listing images and historic metadata still require review and replacement; see fastlane/README.md before publication.",
    "- The original Crowdin configuration is archived under docs/automation as disabled reference material. No upstream Crowdin credentials or upload workflow are enabled.",
    "",
    "## Remaining reference scan",
    "",
]
if residuals:
    report += [
        "| File | Pattern | Count |",
        "|---|---|---:|",
    ]
    report += [f"| {path} | {pattern} | {count} |" for path, pattern, count in residuals]
else:
    report.append("No unprotected legacy package or branding references were found.")
(ROOT / "BRANDING_MIGRATION.md").write_text("\n".join(report) + "\n", encoding="utf-8")

print(f"Processed {len(entries)} files; validated {xml_count} XML files.")
print(f"Remaining reviewed legacy references: {len(residuals)}.")
