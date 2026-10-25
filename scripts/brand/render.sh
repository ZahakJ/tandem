#!/usr/bin/env bash
# Renders Tandem's PNG brand images from scripts/brand/card.html and the SVGs in
# site/assets/brand/, using headless Chromium. Run from the repository root:
#
#   scripts/brand/render.sh
#
# Needs node and chromium (on Nix: nix shell nixpkgs#chromium -c scripts/brand/render.sh).
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
port=8719
brand="$root/site/assets/brand"
fastlane="$root/fastlane/metadata/android/en-US/images"
mkdir -p "$fastlane"

# A throwaway static server, so the card page can fetch the landing page.
node -e '
  const http = require("http"), fs = require("fs"), path = require("path");
  const root = process.argv[1];
  const types = { ".html": "text/html", ".css": "text/css", ".svg": "image/svg+xml", ".woff2": "font/woff2", ".png": "image/png", ".js": "text/javascript" };
  http.createServer((req, res) => {
    const p = path.join(root, decodeURIComponent(new URL(req.url, "http://x").pathname));
    if (!p.startsWith(root)) { res.writeHead(403); return res.end(); }
    fs.readFile(p, (err, body) => {
      if (err) { res.writeHead(404); return res.end(); }
      res.writeHead(200, { "content-type": types[path.extname(p)] || "application/octet-stream" });
      res.end(body);
    });
  }).listen(Number(process.argv[2]), "127.0.0.1");
' "$root" "$port" &
server=$!
trap 'kill $server' EXIT
sleep 0.5

shot() { # url out width height
  chromium --headless --no-sandbox --disable-gpu --hide-scrollbars --force-device-scale-factor=1 \
    --force-prefers-reduced-motion --default-background-color=00000000 --virtual-time-budget=3000 \
    --window-size="$3,$4" --screenshot="$2" "http://127.0.0.1:$port/scripts/brand/card.html?$1" > /dev/null 2>&1
  echo "  $2 ($3x$4)"
}

echo "Rendering brand images:"
shot "v=social" "$brand/social-preview.png" 1280 640
shot "v=banner" "$brand/banner.png" 1280 560
shot "v=icon&src=favicon" "$brand/favicon-32.png" 32 32
shot "v=icon&src=logo" "$brand/apple-touch-icon.png" 180 180
shot "v=icon&src=logo" "$brand/icon-512.png" 512 512
shot "v=feature" "$fastlane/featureGraphic.png" 1024 500
cp "$brand/icon-512.png" "$fastlane/icon.png"
