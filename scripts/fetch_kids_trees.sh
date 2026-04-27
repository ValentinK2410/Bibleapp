#!/usr/bin/env bash
# Устарело: случайные Pexels-ID давали неверные фото. Используйте Wikimedia:
#   python3 scripts/fetch_kids_nature_search.py trees --force
# (ниже — старая схема Pexels + Unsplash, w≈1200).
set -euo pipefail
DEST="${1:-app/src/main/res/drawable-nodpi}"
cd "$(dirname "$0")/.."

download_pexels() {
  local id="$1" out="$2"
  curl -sL -A "Mozilla/5.0" --max-time 45 \
    -o "$out" "https://images.pexels.com/photos/${id}/pexels-photo-${id}.jpeg?auto=compress&cs=tinysrgb&w=1200"
  file -b "$out" | grep -q JPEG || { echo "Not JPEG (pexels $id) -> $out"; exit 1; }
}

download_unsplash() {
  local slug="$1" out="$2"
  curl -sL -A "Mozilla/5.0" --max-time 45 \
    -o "$out" "https://images.unsplash.com/${slug}?auto=format&fit=crop&w=1200&q=80"
  file -b "$out" | grep -q JPEG || { echo "Not JPEG (unsplash $slug) -> $out"; exit 1; }
}

# 27 × Pexels (проверенные ID)
declare -a P=(
  "kids_tree_bereza:1001682"
  "kids_tree_dub:358532"
  "kids_tree_el:1632790"
  "kids_tree_sosna:567540"
  "kids_tree_lipa:1631000"
  "kids_tree_klen:1459359"
  "kids_tree_ryabina:1459360"
  "kids_tree_yablonya:1459361"
  "kids_tree_vishnya:1459362"
  "kids_tree_topol:1459363"
  "kids_tree_iva:1459365"
  "kids_tree_kashtan:1459367"
  "kids_tree_buk:1459368"
  "kids_tree_grab:1459369"
  "kids_tree_olha:1547813"
  "kids_tree_tis:1563355"
  "kids_tree_mozhzhevelnik:1673972"
  "kids_tree_pihta:2396104"
  "kids_tree_kedr:2396105"
  "kids_tree_listvennitsa:3581369"
  "kids_tree_tuya:1028220"
  "kids_tree_kiparis:3493777"
  "kids_tree_sekvoiya:1674040"
  "kids_tree_metasekvoiya:2014422"
  "kids_tree_barkhat_amur:2014423"
  "kids_tree_oreshnik:2014424"
  "kids_tree_orekh_gretskiy:1787423"
)

# 23 × Unsplash (проверенные slug)
declare -a U=(
  "kids_tree_vyaz:photo-1441974231531-c6227db76b6e"
  "kids_tree_boyaryshnik:photo-1464822759023-fed622ff2c3b"
  "kids_tree_shelkovitsa:photo-1502082553048-f009c37129b9"
  "kids_tree_inzhir:photo-1469474968028-56623f02e42e"
  "kids_tree_tutovnik:photo-1472214103451-9374bd1c798e"
  "kids_tree_evkalipt:photo-1490750967868-88aa4486c946"
  "kids_tree_baobab:photo-1523712999610-f77fbcfc3843"
  "kids_tree_palma:photo-1532274402911-5a369e4c4bb5"
  "kids_tree_kokos:photo-1542273917363-3b1817f69a2d"
  "kids_tree_banan:photo-1578662996442-48f60103fc96"
  "kids_tree_sakura:photo-1516214104703-d870798883c5"
  "kids_tree_magnoliya:photo-1518495973542-4542c06a5843"
  "kids_tree_platan:photo-1542601906990-b4d3fb778b09"
  "kids_tree_sikomor:photo-1566438480900-0609be27a4be"
  "kids_tree_cheremukha:photo-1418065460487-3e41a6c84dc5"
  "kids_tree_osina:photo-1500530855697-b586d89ba3ee"
  "kids_tree_kalina:photo-1506905925346-21bda4d32df4"
  "kids_tree_zhimolost:photo-1518837695005-2083093ee35b"
  "kids_tree_smorodina:photo-1559827260-dc66d52bef19"
  "kids_tree_malina:photo-1568605117036-5fe5e7bab0b7"
  "kids_tree_ezhevika:photo-1600585154340-be6161a56a0c"
  "kids_tree_oblepiha:photo-1625246333195-78d9c38ad449"
  "kids_tree_shipovnik:photo-1661956602116-aa6865609028"
)

mkdir -p "$DEST"
tmp="$(mktemp /tmp/kids_tree_XXXX.jpg)"

for ent in "${P[@]}"; do
  name="${ent%%:*}"
  id="${ent##*:}"
  download_pexels "$id" "$tmp"
  sips -Z 1200 "$tmp" --out "$DEST/${name}.jpg" >/dev/null
  echo "OK pexels $id -> ${name}.jpg"
  sleep 0.15
done

for ent in "${U[@]}"; do
  name="${ent%%:*}"
  slug="${ent##*:}"
  download_unsplash "$slug" "$tmp"
  sips -Z 1200 "$tmp" --out "$DEST/${name}.jpg" >/dev/null
  echo "OK unsplash $slug -> ${name}.jpg"
  sleep 0.15
done

rm -f "$tmp"
echo "Готово: $(ls -1 "$DEST"/kids_tree_*.jpg | wc -l | tr -d ' ') файлов"
