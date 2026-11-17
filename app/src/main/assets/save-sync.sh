# Moves one game's save between two emulators' storage, as root (SaveSync.kt). Every step is its own command:
#   save-sync.sh users SAVE_ROOT TITLE      the user folders under Eden's save root that hold TITLE, newest first
#   save-sync.sh age DIR                    the newest file time in DIR (seconds), 0 when it has no files
#   save-sync.sh stage SRC STAGE REF        copies SRC to STAGE, owned and labelled like REF (Tandem's own folder)
#   save-sync.sh install SRC DST BACKUP [work] [keep]
# install first copies the save in DST, if any, whole into BACKUP/<time>, keeping the newest [keep] (20) there. The new
# save is copied next to DST and swapped in, so DST is the old save or the new one, never half of each. File times are
# kept, so both copies then read as the same age and nothing is copied back. Owner, mode and SELinux label come from
# DST's parent folder (a save with the wrong label can be called damaged). With "work", DST.work (Peanuts Native's
# working copy) gets the same.
set -e
cmd="$1"; shift
case "$cmd" in
users)
  root="$1"; title="$2"
  for u in $(ls -t "$root"); do
    [ -d "$root/$u/$title" ] && [ -n "$(ls -A "$root/$u/$title")" ] && echo "$u"
  done
  exit 0 ;;
age)
  [ -d "$1" ] || { echo 0; exit 0; }
  newest=0
  for t in $(find "$1" -type f -exec stat -c %Y {} +); do [ "$t" -gt "$newest" ] && newest="$t"; done
  echo "$newest"
  exit 0 ;;
stage)
  src="$1"; stage="$2"; ref="$3"
  [ -d "$src" ] && [ -n "$(ls -A "$src")" ] || { echo "no save in $src"; exit 2; }
  rm -rf "$stage"; mkdir -p "$(dirname "$stage")"
  cp -a "$src" "$stage"
  chown -R "$(stat -c %u:%g "$ref")" "$stage"
  chmod -R u+rwX "$stage"
  chcon -R "$(stat -c %C "$ref")" "$stage"
  exit 0 ;;
install)
  src="$1"; dst="$2"; backup="$3"; work="$4"; keep="${5:-20}"
  [ -d "$src" ] && [ -n "$(ls -A "$src")" ] || { echo "no save in $src"; exit 2; }
  parent="$(dirname "$dst")"
  mkdir -p "$parent" "$backup"
  owner="$(stat -c %u:%g "$parent")"
  label="$(stat -c %C "$parent")"
  if [ -d "$dst" ] && [ -n "$(ls -A "$dst")" ]; then
    stamp="$(date +%Y%m%d-%H%M%S)"
    cp -a "$dst" "$backup/$stamp"
    ls -t "$backup" | tail -n +$((keep + 1)) | while read -r old; do rm -rf "$backup/$old"; done
  fi
  put() {
    rm -rf "$1.sync"
    cp -a "$src" "$1.sync"
    chown -R "$owner" "$1.sync"
    find "$1.sync" -type d -exec chmod 2770 {} +
    find "$1.sync" -type f -exec chmod 660 {} +
    chcon -R "$label" "$1.sync"
    rm -rf "$1"
    mv "$1.sync" "$1"
  }
  put "$dst"
  if [ "$work" = work ]; then put "$dst.work"; fi
  echo "synced $src -> $dst"
  exit 0 ;;
esac
echo "unknown command $cmd"; exit 1
