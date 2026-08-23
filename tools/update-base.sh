#!/usr/bin/env bash
# ============================================================================
# DevGram — помощник обновления базы на новую версию Forkgram/Telegram.
#
# НЕ «одна кнопка»: полный автомёрж невозможен из-за глубокого кастома DevGram
# (dgws-прокси, плагины Chaquopy, iOS-стиль, история сообщений). Скрипт берёт
# на себя рутину и оставляет человеку только реальные конфликты в наших файлах.
#
# База DevGram = «backup»-тег (наш эталон фич), НЕ git-предок upstream-тегов
# (истории несвязанные — Forkgram ребейзит форк каждый релиз). Поэтому мёрж
# идёт дельтой: diff(OLD_TAG, NEW_TAG) применяется поверх нашего дерева.
#
# Обновляй ПОЭТАПНО, если версий несколько (напр. 12.9.7 -> 12.9.8 -> 12.10.x),
# по одному тегу за раз — так конфликтов меньше и они понятнее.
#
# Использование:
#   tools/update-base.sh analyze     OLD_TAG NEW_TAG        # что менялось, списки
#   tools/update-base.sh apply-clean OLD_TAG NEW_TAG        # чистые upstream-файлы
#   tools/update-base.sh merge       OLD_TAG NEW_TAG        # 3-way наших файлов
#   tools/update-base.sh conflicts                          # показать оставшиеся
#   tools/update-base.sh show        <файл>                 # блоки конфликта
#   tools/update-base.sh resolve     <файл> ours|theirs|both
#   tools/update-base.sh dedup-strings                      # убрать дубли <string>
#   tools/update-base.sh check                              # наши фичи на месте?
#   tools/update-base.sh compile                            # пробная сборка Java
#
# Порядок обычного прогона: analyze -> apply-clean -> merge -> (руками conflicts/
# show/resolve) -> dedup-strings -> check -> compile. Нативка (jni) — отдельно
# по ошибкам компилятора: устаревшие под новый ffmpeg файлы без наших правок
# берутся из NEW_TAG (см. reference_devgram_version_update в памяти).
# ============================================================================
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
SRC="TMessagesProj/src/main/java"
RES="TMessagesProj/src/main/res"
WORK="${TMPDIR:-/tmp}/devgram-update"
mkdir -p "$WORK"

# backup-тег — наш эталон фич (последний по дате)
BACKUP="$(git tag | grep '^devgram-release-backup' | sort | tail -1)"
DGMARK='DevGram\|devgram\|dgws\|startWsProxy'

c_red(){ printf '\033[31m%s\033[0m\n' "$*"; }
c_grn(){ printf '\033[32m%s\033[0m\n' "$*"; }
c_ylw(){ printf '\033[33m%s\033[0m\n' "$*"; }

need_tags(){
  git rev-parse "$1" >/dev/null 2>&1 || { c_red "нет тега: $1"; exit 1; }
  git rev-parse "$2" >/dev/null 2>&1 || { c_red "нет тега: $2 (git fetch origin --tags?)"; exit 1; }
  [ -n "$BACKUP" ] || { c_red "не найден devgram-release-backup-* тег"; exit 1; }
}

# Списки: чистые (только upstream) / наши (пересечение с backup-дельтой)
build_lists(){ # OLD NEW GLOB...
  local old="$1" new="$2"; shift 2
  git diff --name-only "$old" "$new" -- "$@" 2>/dev/null | sort > "$WORK/up.txt"
  git diff --name-only "$old" "$BACKUP" -- "$@" 2>/dev/null | sort > "$WORK/ours.txt"
  comm -23 "$WORK/up.txt" "$WORK/ours.txt" > "$WORK/clean.txt"
  comm -12 "$WORK/up.txt" "$WORK/ours.txt" > "$WORK/conflict.txt"
}

phase_analyze(){
  need_tags "$1" "$2"
  c_ylw "=== Обновление $1 -> $2 (база: $BACKUP) ==="
  for grp in "код:*.java *.kt" "ресурсы:$RES/" "нативка:TMessagesProj/jni/" \
             "сборка:TMessagesProj/build.gradle gradle.properties settings.gradle TMessagesProj/src/main/AndroidManifest.xml .gitmodules buildSrc/"; do
    local name="${grp%%:*}" globs="${grp#*:}"
    build_lists "$1" "$2" $globs
    printf '%-10s upstream=%-4s чистых=%-4s конфликтных=%s\n' \
      "$name" "$(wc -l <"$WORK/up.txt")" "$(wc -l <"$WORK/clean.txt")" "$(wc -l <"$WORK/conflict.txt")"
  done
  echo; c_ylw "Дальше: apply-clean, затем merge (те же OLD NEW)."
}

phase_apply_clean(){ # берём upstream-версию файлов, которых мы не трогали
  need_tags "$1" "$2"
  local n_co=0 n_rm=0
  for globs in "*.java" "*.kt" "$RES/" \
    "TMessagesProj/build.gradle" "gradle.properties" "settings.gradle" \
    "TMessagesProj/src/main/AndroidManifest.xml" ".gitmodules" "buildSrc/"; do
    build_lists "$1" "$2" $globs
    while IFS= read -r f; do
      [ -z "$f" ] && continue
      if git cat-file -e "$2:$f" 2>/dev/null; then
        git checkout "$2" -- "$f" 2>/dev/null && n_co=$((n_co+1))
      else
        [ -e "$f" ] && git rm -q -f "$f" 2>/dev/null && n_rm=$((n_rm+1)) || true
      fi
    done < "$WORK/clean.txt"
  done
  c_grn "чистых применено: checkout=$n_co, удалено=$n_rm"
  c_ylw "Дальше: merge $1 $2"
}

phase_merge(){ # 3-way наших файлов; конфликты оставляем человеку
  need_tags "$1" "$2"
  local ok=0 cf=0 fail=0
  for globs in "*.java" "*.kt" "$RES/values/strings.xml"; do
    build_lists "$1" "$2" $globs
    while IFS= read -r f; do
      [ -z "$f" ] && continue
      git diff "$1" "$2" -- "$f" > "$WORK/p.patch" 2>/dev/null
      [ -s "$WORK/p.patch" ] || continue
      local out; out="$(git apply --3way --whitespace=nowarn "$WORK/p.patch" 2>&1)" || true
      if grep -q conflict <<<"$out"; then cf=$((cf+1))
      elif [ -n "$out" ] && ! git apply --check --3way "$WORK/p.patch" 2>/dev/null; then fail=$((fail+1))
      else ok=$((ok+1)); fi
    done < "$WORK/conflict.txt"
  done
  c_grn "3-way: чисто=$ok, с конфликтами=$cf, не легло=$fail"
  phase_conflicts
}

phase_conflicts(){
  local files; files="$(grep -rlE '^<{7} ' "$SRC" "$RES" 2>/dev/null || true)"
  if [ -z "$files" ]; then c_grn "маркеров конфликтов нет ✓"; return; fi
  c_red "Файлы с конфликтами (разбери вручную: show / resolve):"
  while IFS= read -r f; do
    local h dg; h="$(grep -cE '^<{7} ' "$f")"
    dg="$(awk '/^<{7} /{c=1} c{print} /^>{7} /{c=0}' "$f" | grep -cE "$DGMARK" || true)"
    printf '  %-60s хунков=%-3s DevGram-в-блоках=%s\n' "${f#$SRC/}" "$h" "$dg"
  done <<<"$files"
  c_ylw "DevGram-в-блоках=0 -> обычно безопасно 'resolve <файл> theirs'."
  c_ylw "DevGram-в-блоках>0 -> смотри 'show <файл>' и сливай руками."
}

phase_show(){ # блоки конфликта: наша сторона vs upstream
  python3 - "$1" <<'PY'
import sys
lines=open(sys.argv[1],encoding="utf-8").read().splitlines()
state=0;o=[];t=[];idx=0;start=0;out=[]
for ln in lines:
    if ln.startswith("<<<<<<< "): state=1;o=[];t=[];continue
    if ln.startswith("=======") and state: state=2;continue
    if ln.startswith(">>>>>>> "):
        idx+=1
        out.append(f"----- блок {idx} -----")
        out.append("  OURS:"); out+=["   | "+x for x in (o or ["(пусто)"])]
        out.append("  THEIRS:"); out+=["   | "+x for x in (t or ["(пусто)"])]
        state=0;continue
    if state==1:o.append(ln)
    elif state==2:t.append(ln)
print("\n".join(out) or "нет маркеров конфликта")
PY
}

phase_resolve(){ # взять сторону конфликта целиком: ours|theirs|both
  python3 - "$1" "$2" <<'PY'
import sys
path,side=sys.argv[1],sys.argv[2]
out,state=[],0
for line in open(path,encoding="utf-8"):
    if line.startswith("<<<<<<< "): state=1; continue
    if line.startswith("=======") and state: state=2; continue
    if line.startswith(">>>>>>> "): state=0; continue
    if side=="both": out.append(line)
    elif state==0 or (state==1 and side=="ours") or (state==2 and side=="theirs"):
        out.append(line)
open(path,"w",encoding="utf-8").writelines(out)
print("resolved %s -> %s" % (path, side))
PY
}

phase_dedup(){ # убрать дубли <string>/<plurals> (после take-theirs в локалях)
  python3 - "$RES"/values*/strings.xml <<'PY'
import re,sys
for path in sys.argv[1:]:
    try: txt=open(path,encoding="utf-8").read()
    except FileNotFoundError: continue
    pat=re.compile(r'[ \t]*<(string|string-array|plurals)\s+name="([^"]+)"[^>]*?(?:/>|>.*?</\1>)\s*\n',re.DOTALL)
    seen=set();rm=0
    def repl(m):
        global rm
        k=(m.group(1),m.group(2))
        if k in seen: rm+=1; return ''
        seen.add(k); return m.group(0)
    o=pat.sub(repl,txt)
    if rm: open(path,"w",encoding="utf-8").write(o); print(f"{path.split('/res/')[-1]}: -{rm} дублей")
PY
  c_grn "дедуп строк готов"
}

phase_check(){ # наши фичи на месте + нет маркеров
  c_ylw "=== контроль наших фич ==="
  local so_ws dgws ghost plug
  dgws="$(grep -c 'WebSocket\|dgws' TMessagesProj/jni/tgnet/ConnectionSocket.cpp 2>/dev/null || echo 0)"
  ghost="$(grep -rc 'sendReadPackets\|DevGramProxy' "$SRC/org/telegram/tgnet/ConnectionsManager.java" 2>/dev/null || echo 0)"
  plug="$([ -f "$SRC/org/telegram/messenger/DevGramPlugins.java" ] && echo ok || echo НЕТ)"
  printf '  dgws (ConnectionSocket.cpp): %s\n' "$dgws"
  printf '  режим призрака (ConnectionsManager): %s\n' "$ghost"
  printf '  плагины (DevGramPlugins.java): %s\n' "$plug"
  local m; m="$(grep -rlE '^<{7} ' "$SRC" "$RES" 2>/dev/null | wc -l)"
  [ "$m" = 0 ] && c_grn "маркеров конфликтов: 0 ✓" || c_red "ОСТАЛИСЬ маркеры в $m файлах — см. conflicts"
  # файлы NEW_TAG, потерянные при переносе (передай NEW_TAG 2-м арг, опц.)
  if [ "${1:-}" != "" ]; then
    local lost; lost="$(comm -23 \
      <(git ls-tree -r --name-only "$1" -- '*.java' '*.kt' 2>/dev/null | sort) \
      <(git ls-tree -r --name-only HEAD -- '*.java' '*.kt' 2>/dev/null | sort) | wc -l)"
    printf '  файлов %s потеряно: %s (0 = все на месте)\n' "$1" "$lost"
  fi
}

phase_compile(){
  c_ylw "пробная компиляция Java (без нативки)…"
  ./gradlew :TMessagesProj_App:compileAfatReleaseJavaWithJavac -x buildNativeDeps \
    --no-parallel --console=plain 2>&1 | tail -25
}

cmd="${1:-}"; shift || true
case "$cmd" in
  analyze)       phase_analyze "$@";;
  apply-clean)   phase_apply_clean "$@";;
  merge)         phase_merge "$@";;
  conflicts)     phase_conflicts;;
  show)          phase_show "$@";;
  resolve)       phase_resolve "$@";;
  dedup-strings) phase_dedup;;
  check)         phase_check "${1:-}";;
  compile)       phase_compile;;
  *) grep -E '^#( |$)' "$0" | sed 's/^# \{0,1\}//'; exit 1;;
esac
