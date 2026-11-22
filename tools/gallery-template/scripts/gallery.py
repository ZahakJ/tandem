#!/usr/bin/env python3
"""The Tandem look gallery's checks and index, with Python's standard library only.

    gallery.py check FILE                     check one .tandem file, print what is wrong
    gallery.py check-all                      check every look in looks/ against its meta.json
    gallery.py intake --body FILE --issue N --submitter LOGIN --report FILE
                                              read a share-look issue, download its file, check it, and add it to
                                              looks/<id>/ (the workflow then opens a pull request)
    gallery.py index                          write index.json from looks/*/meta.json

The checks are the ones Tandem itself makes when it imports a look (app/src/main/java/.../data/LookFile.kt in the
Tandem repository): the same size limits, the same allowed paths, real PNG, JPEG or WebP pictures of a sane size,
and a look.json of the format and a version Tandem knows. Nothing in a submitted file is ever run.
"""

import argparse
import datetime
import hashlib
import io
import json
import os
import re
import struct
import sys
import urllib.request
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
LOOKS = os.path.join(ROOT, "looks")
INDEX = os.path.join(ROOT, "index.json")

FORMAT = "tandem-look"
VERSION = 1
MAX_FILE = 24 << 20
MAX_UNPACKED = 48 << 20
MAX_ENTRIES = 1200
MAX_IMAGE = 8 << 20
MAX_JSON = 256 << 10
MAX_SIDE = 4096
MAX_PIXELS = 4096 * 4096
MAX_PREVIEW = 1 << 20

IMAGE = r"(png|jpg|jpeg|webp)"
ALLOWED = [re.compile(p) for p in (
    r"look\.json", r"theme\.json",
    r"preview\." + IMAGE,
    r"wallpapers/(top|bottom)\." + IMAGE,
    r"systems/[a-z0-9_]{1,48}/(icon|hero|logo)\." + IMAGE,
    r"collections/(favorites|most_played|newly_added|recent)/(icon|hero)\." + IMAGE,
)]

# Where GitHub keeps files attached to an issue. Nothing else is downloaded.
ATTACHMENT = re.compile(r"https://github\.com/(?:user-attachments/files/\d+/|[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/files/\d+/)[A-Za-z0-9_.%()+-]+\.(?:zip|tandem)")
ID = re.compile(r"[a-z0-9][a-z0-9-]{0,47}")
TAG = re.compile(r"[a-z0-9][a-z0-9-]{0,23}")
LICENSES = {"CC BY 4.0", "CC BY-SA 4.0", "CC0 1.0"}


class Bad(Exception):
    pass


def image_size(data):
    """(kind, width, height) from a picture's first bytes, or None: PNG, JPEG and WebP only."""
    if len(data) >= 24 and data[:8] == b"\x89PNG\r\n\x1a\n" and data[12:16] == b"IHDR":
        w, h = struct.unpack(">II", data[16:24])
        return ("png", w, h)
    if len(data) >= 30 and data[:4] == b"RIFF" and data[8:12] == b"WEBP":
        chunk = data[12:16]
        if chunk == b"VP8 ":
            w, h = struct.unpack("<HH", data[26:30])
            return ("webp", w & 0x3FFF, h & 0x3FFF)
        if chunk == b"VP8L" and data[20] == 0x2F:
            bits = struct.unpack("<I", data[21:25])[0]
            return ("webp", (bits & 0x3FFF) + 1, ((bits >> 14) & 0x3FFF) + 1)
        if chunk == b"VP8X":
            w = int.from_bytes(data[24:27], "little") + 1
            h = int.from_bytes(data[27:30], "little") + 1
            return ("webp", w, h)
        return None
    if data[:2] == b"\xff\xd8":
        i = 2
        while i + 4 <= len(data):
            if data[i] != 0xFF:
                return None
            while i < len(data) and data[i] == 0xFF:
                i += 1
            if i >= len(data):
                return None
            marker = data[i]
            i += 1
            if marker in (0xD9, 0xDA):
                return None
            if 0xD0 <= marker <= 0xD7 or marker == 0x01:
                continue
            seg = struct.unpack(">H", data[i:i + 2])[0]
            if 0xC0 <= marker <= 0xCF and marker not in (0xC4, 0xC8, 0xCC):
                h, w = struct.unpack(">HH", data[i + 3:i + 7])
                return ("jpeg", w, h)
            i += seg
    return None


def matches(kind, name):
    ext = name.rsplit(".", 1)[-1].lower()
    return {"png": "png", "jpg": "jpeg", "jpeg": "jpeg", "webp": "webp"}.get(ext) == kind


def check(data):
    """Checks a .tandem file's bytes. Returns (manifest, files); raises Bad with what is wrong."""
    if len(data) > MAX_FILE:
        raise Bad(f"The file is {len(data) >> 20} MB; looks are at most {MAX_FILE >> 20} MB.")
    try:
        z = zipfile.ZipFile(io.BytesIO(data))
    except zipfile.BadZipFile:
        raise Bad("That isn't a .tandem file (it isn't a zip).")
    files = {}
    total = 0
    for info in z.infolist():
        name = info.filename
        if "\\" in name or name.startswith("/") or any(p in ("..", ".") for p in name.split("/")):
            raise Bad(f"`{name}` tries to leave the look's folder.")
        if info.is_dir():
            continue
        if name.startswith("__MACOSX/") or name.rsplit("/", 1)[-1].startswith("."):
            continue
        if not any(p.fullmatch(name) for p in ALLOWED):
            raise Bad(f"`{name}` isn't something a look can hold.")
        if name in files:
            raise Bad(f"`{name}` is in the file twice.")
        if len(files) >= MAX_ENTRIES:
            raise Bad("Too many files.")
        cap = MAX_JSON if name.endswith(".json") else MAX_IMAGE
        if info.file_size > cap:
            raise Bad(f"`{name}` is too big.")
        with z.open(info) as f:
            body = f.read(cap + 1)
        if len(body) > cap:
            raise Bad(f"`{name}` is too big.")
        total += len(body)
        if total > MAX_UNPACKED:
            raise Bad("Unpacked, the look is too big.")
        files[name] = body
    if "look.json" not in files or "theme.json" not in files:
        raise Bad("A look needs look.json and theme.json: make it with Tandem's Share my look.")
    try:
        manifest = json.loads(files["look.json"])
        json.loads(files["theme.json"])
    except ValueError:
        raise Bad("look.json or theme.json isn't valid JSON.")
    if not isinstance(manifest, dict) or manifest.get("format") != FORMAT:
        raise Bad("look.json isn't a Tandem look.")
    version = manifest.get("version")
    if not isinstance(version, int) or version < 1 or version > VERSION:
        raise Bad(f"look.json version {version!r} isn't one this gallery knows.")
    for name, body in files.items():
        if name.endswith(".json"):
            continue
        size = image_size(body)
        if not size or not matches(size[0], name):
            raise Bad(f"`{name}` isn't a real {name.rsplit('.', 1)[-1].upper()} picture.")
        _, w, h = size
        if w <= 0 or h <= 0 or w > MAX_SIDE or h > MAX_SIDE or w * h > MAX_PIXELS:
            raise Bad(f"`{name}` is {w}×{h}; pictures are at most {MAX_SIDE}×{MAX_SIDE}.")
    preview = next((n for n in files if n.startswith("preview.")), None)
    if not preview:
        raise Bad("The look has no preview picture: make it again with Share my look.")
    if len(files[preview]) > MAX_PREVIEW:
        raise Bad("The preview picture is over 1 MB.")
    return manifest, files


def sha256(data):
    return hashlib.sha256(data).hexdigest()


def clean(s, size):
    s = "".join(c for c in str(s or "") if c == "\n" or (c.isprintable() and c not in "‪‫‬‭‮⁦⁧⁨⁩"))
    return s.strip()[:size].strip()


def slug(name):
    s = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")[:40].strip("-")
    return s or "look"


def sections(body):
    """An issue form's answers: {heading: text}."""
    out = {}
    for part in re.split(r"^### ", body or "", flags=re.M)[1:]:
        head, _, text = part.partition("\n")
        text = text.strip()
        out[head.strip().lower()] = "" if text == "_No response_" else text
    return out


def download(url):
    req = urllib.request.Request(url, headers={"User-Agent": "tandem-gallery-intake"})
    with urllib.request.urlopen(req, timeout=60) as r:
        if not r.geturl().startswith("https://"):
            raise Bad("The attachment moved somewhere that isn't https.")
        data = r.read(MAX_FILE + 1)
    if len(data) > MAX_FILE:
        raise Bad(f"The file is over {MAX_FILE >> 20} MB.")
    return data


def intake(args):
    report = []
    try:
        answers = sections(open(args.body, encoding="utf-8").read())
        name = clean(answers.get("look name") or answers.get("name"), 60)
        if not name:
            raise Bad("The form has no name for the look.")
        found = ATTACHMENT.findall(answers.get("the look file") or answers.get("file") or "")
        if len(found) != 1:
            raise Bad("Attach exactly one file to **The look file**: the `.tandem.zip` Tandem saved (GitHub doesn't take `.tandem`, so the name ends in `.zip`).")
        license_ = (answers.get("license") or "").strip()
        if license_ not in LICENSES:
            raise Bad("Choose a license for the look.")
        if "[x]" not in (answers.get("rules") or "").lower():
            raise Bad("Please tick the rules.")
        data = download(found[0])
        manifest, _ = check(data)
        os.makedirs(LOOKS, exist_ok=True)
        base = slug(name)
        look_id, n = base, 2
        while os.path.exists(os.path.join(LOOKS, look_id)):
            look_id, n = f"{base}-{n}", n + 1
        folder = os.path.join(LOOKS, look_id)
        os.makedirs(folder)
        with open(os.path.join(folder, f"{look_id}.tandem"), "wb") as f:
            f.write(data)
        z = zipfile.ZipFile(io.BytesIO(data))
        preview = next(n for n in z.namelist() if n.startswith("preview."))
        ext = preview.rsplit(".", 1)[-1].lower()
        with open(os.path.join(folder, f"preview.{ext}"), "wb") as f:
            f.write(z.read(preview))
        tags = [t for t in (re.sub(r"[^a-z0-9-]", "", t.strip().lower()) for t in (answers.get("tags") or "").split(",")) if TAG.fullmatch(t)][:8]
        meta = {
            "id": look_id,
            "name": name,
            "author": clean(answers.get("made by") or answers.get("author") or manifest.get("author"), 40),
            "description": clean(answers.get("description") or manifest.get("description"), 300),
            "version": manifest["version"],
            "tags": tags,
            "license": license_,
            "added": datetime.date.today().isoformat(),
            "issue": args.issue,
            "submitter": args.submitter,
        }
        with open(os.path.join(folder, "meta.json"), "w", encoding="utf-8") as f:
            json.dump(meta, f, ensure_ascii=False, indent=2)
            f.write("\n")
        write_outputs(id=look_id, name=name)
        report.append(f"Thanks! **{name}** passed the checks ({len(data) // 1024} KB). A pull request adds it as `looks/{look_id}/`; it shows up in Tandem once a maintainer has looked at it and merged it.")
        code = 0
    except Bad as e:
        report.append(f"This look couldn't be added yet: {e}\n\nEdit the issue to fix it, and the checks run again.")
        code = 1
    except Exception as e:  # a network error, a GitHub hiccup
        report.append(f"Something went wrong while checking the look ({type(e).__name__}). Edit the issue to try again.")
        code = 1
    if args.report:
        with open(args.report, "w", encoding="utf-8") as f:
            f.write("\n".join(report) + "\n")
    print("\n".join(report))
    return code


def write_outputs(**values):
    out = os.environ.get("GITHUB_OUTPUT")
    if not out:
        return
    with open(out, "a", encoding="utf-8") as f:
        for k, v in values.items():
            f.write(f"{k}={str(v).replace(chr(10), ' ')}\n")


def looks():
    for look_id in sorted(os.listdir(LOOKS)) if os.path.isdir(LOOKS) else []:
        folder = os.path.join(LOOKS, look_id)
        if os.path.isdir(folder):
            yield look_id, folder


def check_all(_args):
    bad = 0
    for look_id, folder in looks():
        try:
            if not ID.fullmatch(look_id):
                raise Bad("the folder name must be lowercase letters, digits and dashes")
            meta = json.load(open(os.path.join(folder, "meta.json"), encoding="utf-8"))
            if meta.get("id") != look_id:
                raise Bad("meta.json's id isn't the folder's name")
            data = open(os.path.join(folder, f"{look_id}.tandem"), "rb").read()
            check(data)
            previews = [n for n in os.listdir(folder) if n.startswith("preview.")]
            if len(previews) != 1:
                raise Bad("needs exactly one preview picture")
            p = open(os.path.join(folder, previews[0]), "rb").read()
            size = image_size(p)
            if len(p) > MAX_PREVIEW or not size or not matches(size[0], previews[0]) or size[1] > 2048 or size[2] > 2048:
                raise Bad("the preview isn't a picture under 1 MB and 2048 pixels")
            extra = set(os.listdir(folder)) - {"meta.json", f"{look_id}.tandem", previews[0]}
            if extra:
                raise Bad(f"unexpected files: {sorted(extra)}")
            print(f"ok   {look_id}")
        except (Bad, OSError, ValueError) as e:
            print(f"FAIL {look_id}: {e}")
            bad += 1
    return 1 if bad else 0


def index(_args):
    entries = []
    for look_id, folder in looks():
        meta = json.load(open(os.path.join(folder, "meta.json"), encoding="utf-8"))
        data = open(os.path.join(folder, f"{look_id}.tandem"), "rb").read()
        preview = next(n for n in sorted(os.listdir(folder)) if n.startswith("preview."))
        entries.append({
            "id": look_id,
            "name": clean(meta.get("name"), 60),
            "author": clean(meta.get("author"), 40),
            "description": clean(meta.get("description"), 300),
            "version": int(meta.get("version", 1)),
            "preview": f"looks/{look_id}/{preview}",
            "file": f"looks/{look_id}/{look_id}.tandem",
            "sha256": sha256(data),
            "size": len(data),
            "tags": [t for t in meta.get("tags", []) if TAG.fullmatch(str(t))][:8],
            "license": meta.get("license", ""),
            "added": meta.get("added", ""),
        })
    entries.sort(key=lambda e: (e["added"], e["id"]), reverse=True)
    with open(INDEX, "w", encoding="utf-8") as f:
        json.dump({"format": "tandem-gallery", "version": 1, "looks": entries}, f, ensure_ascii=False, indent=2)
        f.write("\n")
    print(f"index.json: {len(entries)} looks")
    return 0


def main():
    p = argparse.ArgumentParser()
    sub = p.add_subparsers(dest="cmd", required=True)
    c = sub.add_parser("check")
    c.add_argument("file")
    sub.add_parser("check-all")
    i = sub.add_parser("intake")
    i.add_argument("--body", required=True)
    i.add_argument("--issue", type=int, required=True)
    i.add_argument("--submitter", required=True)
    i.add_argument("--report")
    sub.add_parser("index")
    args = p.parse_args()
    if args.cmd == "check":
        try:
            m, files = check(open(args.file, "rb").read())
            print(f"ok: {m.get('name', '')!r}, {len(files)} files")
            return 0
        except Bad as e:
            print(f"not ok: {e}")
            return 1
    return {"check-all": check_all, "intake": intake, "index": index}[args.cmd](args)


if __name__ == "__main__":
    sys.exit(main())
