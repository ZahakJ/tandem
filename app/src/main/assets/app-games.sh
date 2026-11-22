# Reads DroidDeck's and GameNative's libraries as root (AppLibraries.kt): both keep them in their private folders,
# which only root can read. Nothing here writes outside Tandem's own folder, and nothing of the apps' is changed:
# GameNative's database is copied out and opened read-only there. Every step is its own command, and each ends by
# printing "ok", so Tandem can tell root ran (without su the output is empty):
#   app-games.sh stat FILE...                    "<size> <mtime> <path>" for each FILE that exists
#   app-games.sh read FILE                       the file's text, then "ok" on a line of its own
#   app-games.sh copy DIR REF SRC NAME [...]     copies each SRC to DIR/NAME, owned and labelled like REF (Tandem's
#                                                folder), so Tandem can read and delete the copies
cmd="$1"; shift
case "$cmd" in
stat)
  for f in "$@"; do
    [ -f "$f" ] && echo "$(stat -c '%s %Y' "$f") $f"
  done
  echo ok
  exit 0 ;;
read)
  [ -f "$1" ] && cat "$1"
  echo
  echo ok
  exit 0 ;;
copy)
  dir="$1"; ref="$2"; shift 2
  [ -d "$dir" ] || { echo "no folder $dir"; exit 2; }
  owner="$(stat -c %u:%g "$ref")"
  label="$(stat -c %C "$ref")"
  while [ $# -ge 2 ]; do
    src="$1"; dst="$dir/$2"; shift 2
    [ -f "$src" ] || continue
    cp "$src" "$dst" && chown "$owner" "$dst" && chmod 600 "$dst" && chcon "$label" "$dst"
  done
  echo ok
  exit 0 ;;
*)
  echo "unknown command $cmd"
  exit 2 ;;
esac
