#!/usr/bin/env python3
"""Check generated publications without a server or third-party packages.
Usage: python3 scripts/verify-public-web-ig.py guide.zip [guide2.zip ...]
"""
import json
import posixpath
import sys
import zipfile
from html.parser import HTMLParser
from urllib.parse import unquote, urlsplit


class Page(HTMLParser):
    def __init__(self, content):
        super().__init__(convert_charrefs=True)
        self.links, self.ids = [], set()
        self.feed(content)

    def handle_starttag(self, tag, attrs):
        attrs = dict(attrs)
        if attrs.get('id'):
            self.ids.add(attrs['id'])
        for key in ('href', 'src'):
            if attrs.get(key):
                self.links.append(attrs[key])


def verify(path):
    missing = []
    with zipfile.ZipFile(path) as archive:
        names = set(archive.namelist())
        pages = {name: Page(archive.read(name).decode('utf-8')) for name in names if name.endswith('.html')}
        checked = 0
        for name, page in pages.items():
            for href in page.links:
                url = urlsplit(href)
                if url.scheme or url.netloc or href.startswith('/'):
                    continue
                target = posixpath.normpath(posixpath.join(posixpath.dirname(name), unquote(url.path))) if url.path else name
                checked += 1
                if target not in names:
                    missing.append({'page': name, 'href': href, 'reason': 'Missing file'})
                elif url.fragment and target in pages and unquote(url.fragment) not in pages[target].ids:
                    missing.append({'page': name, 'href': href, 'reason': 'Missing fragment'})
        result = {'zip': path, 'entries': len(names), 'htmlPages': len(pages), 'localLinksChecked': checked, 'missing': missing}
        print(json.dumps(result, ensure_ascii=False))
        return bool(missing)


if __name__ == '__main__':
    if len(sys.argv) == 1:
        sys.exit('Provide one or more Public web IG ZIP files.')
    sys.exit(1 if any([verify(path) for path in sys.argv[1:]]) else 0)
