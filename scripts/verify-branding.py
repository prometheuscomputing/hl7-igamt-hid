#!/usr/bin/env python3
"""Fail the build when the application jar still carries NIST wording or
NIST-era hooks.

Scans what a user can see or receive: the client's index page and every
client bundle under BOOT-INF/classes/public, application.yml, and the string
constants of the application's own classes (the account emails). Sentences
listed in scripts/brand-allowed.txt are removed first, matched as whole
sentences with loose whitespace so a re-wrapped paragraph still counts.
Anything left that says NIST or nist.gov fails, with the snippet, and so
does the federal analytics loader anywhere in the client.

    scripts/verify-branding.py [path/to/hl7-igamt.jar]
"""
import html
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
JAR = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "hl7-igamt.jar"
ALLOWED = ROOT / "scripts/brand-allowed.txt"

PATTERNS = ("NIST", "nist.gov")
# Third-party loaders that must not ship. The tool's Google Group is a
# support channel the users rely on, not a tracker, so it is not listed here.
HOOKS = ("dap.digitalgov.gov", "_fed_an_ua_tag", "googletagmanager")
PUBLIC = "BOOT-INF/classes/public/"
CLASSES = "BOOT-INF/classes/gov/"
ATTR = re.compile(r'(?:href|src|content|title|alt)\s*=\s*(?:"([^"]*)"|\'([^\']*)\'|([^\s>"\']+))', re.I)


def fail(msg):
    print("verify-branding: " + msg, file=sys.stderr)
    sys.exit(1)


def load_allowed():
    out = []
    for line in ALLOWED.read_text(encoding="utf-8").splitlines():
        phrase = line.strip()
        if not phrase or phrase.startswith("#"):
            continue
        words = phrase.split()
        file_name = len(words) == 1 and re.search(r"[_\-.]", phrase)
        if phrase.endswith((",", ";", "and", "or", "by", "that")) or (len(words) < 2 and not file_name):
            fail("allowed entry is a fragment, not a sentence or title: %r" % phrase)
        if not re.search(r"NIST|nist\.gov", phrase):
            fail("allowed entry never matches anything: %r" % phrase)
        out.append(phrase)
    return out


def scrub(text, allowed):
    for phrase in allowed:
        pattern = (r"(?<![A-Za-z0-9])" + r"\s+".join(re.escape(w) for w in phrase.split())
                   + r"(?![A-Za-z0-9])")
        text = re.sub(pattern, " ", text)
    return text


def visible(m):
    return " " + " ".join("".join(g) for g in ATTR.findall(m.group(0))) + " "


def hits_in(text, allowed, markup):
    text = html.unescape(text)
    if markup:
        text = re.sub(r"<!--.*?-->", " ", text, flags=re.S)
        text = re.sub(r"<[^>]+>", visible, text)
    text = scrub(text, allowed)
    found = []
    for pattern in PATTERNS:
        for m in re.finditer(re.escape(pattern), text):
            lo, hi = max(0, m.start() - 60), min(len(text), m.end() + 60)
            found.append(re.sub(r"\s+", " ", text[lo:hi]).strip())
    return found


def class_strings(data):
    # Class files keep string constants as modified UTF-8; the printable runs
    # are enough to catch a subject line or a signature. Package names are
    # lower case (gov/nist) and never match the patterns.
    return "\n".join(s.decode("utf-8", "replace") for s in re.findall(rb"[\x20-\x7e]{6,}", data))


def main():
    if not JAR.is_file():
        fail("missing %s (build the application first)" % JAR)
    allowed = load_allowed()
    failures = 0
    scanned = 0
    with zipfile.ZipFile(JAR) as z:
        names = z.namelist()
        client = [n for n in names if n.startswith(PUBLIC) and (n.endswith(".html") or n.endswith(".js"))]
        if not client:
            fail("no client under %s in the jar" % PUBLIC)
        for name in client:
            body = z.read(name).decode("utf-8", "replace")
            scanned += 1
            for hook in HOOKS:
                if hook in body:
                    print("%s: carries %s" % (name, hook))
                    failures += 1
            for snippet in hits_in(body, allowed, name.endswith(".html")):
                print("%s: %s" % (name, snippet))
                failures += 1
        yml = "BOOT-INF/classes/application.yml"
        if yml in names:
            scanned += 1
            live = "\n".join(l for l in z.read(yml).decode("utf-8", "replace").splitlines() if not l.lstrip().startswith("#"))
            for snippet in hits_in(live, allowed, False):
                print("%s: %s" % (yml, snippet))
                failures += 1
        for name in names:
            if name.startswith(CLASSES) and name.endswith(".class"):
                scanned += 1
                for snippet in hits_in(class_strings(z.read(name)), allowed, False):
                    print("%s: %s" % (name, snippet))
                    failures += 1
    if failures:
        fail("%d finding(s); see above" % failures)
    print("verify-branding: OK (%d entries scanned, %d approved sentences)" % (scanned, len(allowed)))


if __name__ == "__main__":
    main()
