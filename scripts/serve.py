#!/usr/bin/env python3
"""Generic static file server that honours HTTP Range (report-v13 §index).

Python's stdlib `http.server` ignores `Range` and returns the whole file (200),
which defeats the viewer's on-demand frame fetch — it would pull the entire
multi-hundred-MB .frames file per request. This subclass adds correct `206
Partial Content` so live.html can Range-fetch ONE frame (and one genome) at a
time, keeping browser memory bounded regardless of run size.

It is NOT a bespoke API server (no endpoints, no app logic) — just a static
file server that implements the part of HTTP the stdlib one omits. Stdlib only.

Usage:  python3 scripts/serve.py [PORT] [DIR]   (defaults: 8090 runs/)
"""
import http.server
import os
import re
import sys


class RangeHandler(http.server.SimpleHTTPRequestHandler):
    def do_GET(self):
        rng = self.headers.get("Range")
        path = self.translate_path(self.path)
        if rng and os.path.isfile(path):
            m = re.match(r"bytes=(\d+)-(\d*)", rng)
            if m:
                size = os.path.getsize(path)
                start = int(m.group(1))
                end = int(m.group(2)) if m.group(2) else size - 1
                end = min(end, size - 1)
                if 0 <= start <= end < size:
                    length = end - start + 1
                    self.send_response(206)
                    self.send_header("Content-Type", self.guess_type(path))
                    self.send_header("Content-Range", f"bytes {start}-{end}/{size}")
                    self.send_header("Content-Length", str(length))
                    self.end_headers()
                    with open(path, "rb") as f:
                        f.seek(start)
                        remaining = length
                        while remaining > 0:
                            chunk = f.read(min(65536, remaining))
                            if not chunk:
                                break
                            self.wfile.write(chunk)
                            remaining -= len(chunk)
                    return
        super().do_GET()

    def end_headers(self):
        self.send_header("Accept-Ranges", "bytes")
        self.send_header("Cache-Control", "no-store")
        super().end_headers()


def main():
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 8090
    directory = sys.argv[2] if len(sys.argv) > 2 else "runs"
    handler = lambda *a, **k: RangeHandler(*a, directory=directory, **k)
    with http.server.ThreadingHTTPServer(("", port), handler) as httpd:
        print(f"serving {directory}/ on http://localhost:{port}  (HTTP Range enabled)")
        httpd.serve_forever()


if __name__ == "__main__":
    main()
