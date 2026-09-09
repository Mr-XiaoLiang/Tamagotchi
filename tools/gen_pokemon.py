#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
重生成 assets/pokemon.json：把旧的 pokemon_names.json（扁平 NAME -> {en,zh}）
变为   NAME -> { name:{en,zh}, prev:[...], next:[...], form:bool }

关键正确性约束（修复「按 _N 串成进化链」的错误）：
- 真正的进化链来自 Pokemon-Showdown 的 pokedex.ts 的 prevo / evos 字段，
  这些引用的是「基础物种」的独立名（如 RAICHU，不是 PIKACHU_2）。
- 带 baseSpecies 的条目（ALCREMIE 的 36 个颜色装饰、PIKACHU_female 等性别形态）
  属于同一物种的外观变体，NOT 进化目标：form=true 且 prev/next 置空，
  且不进入宠物选择列表（selection 用 !form && prev 空）。
- 每个精灵文件（assets/sprite/*.png）都生成一条，保证 display 全覆盖。

用法：
    python3 tools/gen_pokemon.py
（pokedex.ts 缺失时自动下载；原始名从 git 历史读取。）
"""
import json
import os
import re
import csv
import subprocess
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "app", "src", "main", "assets")
SPRITE_DIR = os.path.join(ASSETS, "sprite")
OUT = os.path.join(ASSETS, "pokemon.json")
POKEDEX = "/tmp/pokedex.ts"
ORIG_NAMES_GIT = "HEAD:app/src/main/assets/pokemon_names.json"
# PokeAPI 本地数据库（用户下载）：含 pokemon_species_names.csv 的 zh-Hans 官方简中名
POKEAPI_CSV = "/Users/lollipop/Develop/Web/pokeapi/data/v2/csv"
# 个别 PokeAPI zh-Hans 偶用繁体/异字，按 52poke 简中订正（key=大写主名）
ZH_OVERRIDES = {"MINUN": "负电拍拍"}
# 个别精灵素材命名与 pokedex id 不一致（性别写法），做双向别名映射，
# 否则进化链会断（如 NIDORANfE/NIDORANmA <-> nidoranf/nidoranm）。
# pokedex 归一化名 -> 我们实际的精灵主名
ID_ALIASES = {"nidoranf": "NIDORANfE", "nidoranm": "NIDORANmA"}
# 反向：精灵主名归一化 -> pokedex 归一化名（用于查 pokedex 条目/中文名）
SPRITE_TO_PK = {v.lower(): k for k, v in ID_ALIASES.items()}


def norm(s: str) -> str:
    return re.sub(r"[^a-z0-9]", "", s.lower())


def ensure_pokedex():
    if os.path.exists(POKEDEX) and os.path.getsize(POKEDEX) > 100000:
        return
    url = "https://raw.githubusercontent.com/Zarel/Pokemon-Showdown/master/data/pokedex.ts"
    for _ in range(8):
        r = subprocess.run(["curl", "-sSL", "--max-time", "120", "-C", "-",
                            "-o", POKEDEX, url])
        if os.path.exists(POKEDEX) and os.path.getsize(POKEDEX) > 100000:
            return
    sys.exit("下载 pokedex.ts 失败")


def load_zh_map(csv_dir):
    """从 PokeAPI CSV 取官方简中名（zh-Hans）：species identifier -> 中文名。
    列：languages.csv(id, iso639, iso3166, name,...); pokemon_species.csv(id, identifier,...);
    pokemon_species_names.csv(pokemon_species_id, local_language_id, name, genus)。"""
    if not os.path.isdir(csv_dir):
        return {}
    zh_hans_id = None
    with open(os.path.join(csv_dir, "languages.csv"), encoding="utf-8") as f:
        for row in csv.reader(f):
            if len(row) > 3 and row[3] == "zh-hans":
                zh_hans_id = row[0]
    if not zh_hans_id:
        return {}
    sp = {}
    with open(os.path.join(csv_dir, "pokemon_species.csv"), encoding="utf-8") as f:
        for row in csv.reader(f):
            if row:
                sp[row[0]] = row[1]  # id -> identifier
    zh = {}
    with open(os.path.join(csv_dir, "pokemon_species_names.csv"), encoding="utf-8") as f:
        for row in csv.reader(f):
            if len(row) >= 3 and row[1] == zh_hans_id:
                zh[row[0]] = row[2]
    out = {}
    for sid, ident in sp.items():
        if sid in zh:
            out[norm(ident)] = zh[sid]
    return out


def load_original_names():
    # 优先本地文件，其次 git 历史
    raw = None
    try:
        raw = subprocess.check_output(
            ["git", "show", ORIG_NAMES_GIT], cwd=ROOT).decode("utf-8")
    except Exception:
        local = os.path.join(ASSETS, "pokemon_names.json")
        if os.path.exists(local):
            raw = open(local, encoding="utf-8").read()
    if not raw:
        sys.exit("找不到原始 pokemon_names.json")
    return json.loads(raw)


def parse_pokedex(path):
    # pokedex.ts 是单个 `export const Pokedex: ... = { ... }` 大对象，
    # 用 node 把对象字面量求值成规整 JSON（键=基础物种 id，含 prevo/evos/baseSpecies/name）。
    norm_json = "/tmp/pokedex_norm.json"
    node = (
        "const fs=require('fs');"
        "const t=fs.readFileSync(%r,'utf8');"
        "const i=t.indexOf('Pokedex:');"
        "const start=t.indexOf('{',i);"
        "let depth=0,end=-1;"
        "for(let j=start;j<t.length;j++){const c=t[j];"
        "if(c==='{')depth++;else if(c==='}'){depth--;if(depth===0){end=j;break;}}}"
        "const obj=t.slice(start,end+1);"
        "const data=eval('('+obj+')');"
        "const out={};"
        "for(const k in data){const e=data[k]||{};"
        "out[k]={name:e.name||k,prevo:e.prevo||null,"
        "baseSpecies:e.baseSpecies||null,evos:(e.evos||[]).map(String)};}"
        "fs.writeFileSync(%r,JSON.stringify(out));"
    ) % (path, norm_json)
    subprocess.run(["node", "-e", node], check=True)
    raw = json.loads(open(norm_json, encoding="utf-8").read())
    table = {}
    for key, e in raw.items():
        table[norm(key)] = {
            "name": e.get("name") or key,
            "prevo": norm(e["prevo"]) if e.get("prevo") else None,
            "baseSpecies": norm(e["baseSpecies"]) if e.get("baseSpecies") else None,
            "evos": [norm(x) for x in e.get("evos", [])],
        }
    return table


def main():
    ensure_pokedex()
    orig = load_original_names()
    pk = parse_pokedex(POKEDEX)
    zh_by_norm = load_zh_map(POKEAPI_CSV)  # 官方简中名（52poke 风格）

    # 收集精灵主名（已大写，去 .png）
    sprite_ids = []
    for fn in os.listdir(SPRITE_DIR):
        if fn.endswith(".png"):
            sprite_ids.append(fn[:-4])

    # 反向映射：归一化名 -> 我们的主名（仅取基础物种，排除 form 后缀）
    rev = {}
    for sid in sprite_ids:
        rev.setdefault(norm(sid), sid)
    # 别名：pokedex id 也能映射到实际素材名，保证进化链不断
    for pk_id, sid in ID_ALIASES.items():
        if sid in sprite_ids:
            rev.setdefault(pk_id, sid)

    old = {}
    if os.path.exists(OUT):
        old = json.load(open(OUT, encoding="utf-8"))

    out = {}
    changed = []
    for sid in sorted(sprite_ids):
        n = SPRITE_TO_PK.get(norm(sid), norm(sid))  # 性别等命名别名 -> pokedex id
        entry = pk.get(n)
        # 外观变体（非可选物种）：按文件名后缀（_N 颜色装饰、_female/_male 性别）
        # 或 pokedex 的 baseSpecies（拼接名形态，如 alcremievanillarubycream）判定。
        suffix_form = bool(re.search(r"_(female|male|\d+)$", sid))
        pk_form = bool(entry and entry["baseSpecies"] and entry["baseSpecies"] != n)
        is_form = suffix_form or pk_form

        base_candidate = re.sub(r"_(female|male|\d+)$", "", sid)
        auth_zh = zh_by_norm.get(n) or zh_by_norm.get(norm(base_candidate))
        name_obj = orig.get(sid) or orig.get(base_candidate)
        en = name_obj["en"] if name_obj and name_obj.get("en") else (
            entry["name"] if entry else sid.title())
        # 中文名优先用 PokeAPI 官方简中名，缺失才回退原始数据
        zh = auth_zh or (name_obj["zh"] if name_obj and name_obj.get("zh") else "")
        zh = ZH_OVERRIDES.get(sid, zh)  # 按 52poke 简中订正个别异字

        if is_form:
            prev, nxt = [], []
        else:
            prev = []
            if entry and entry["prevo"] and entry["prevo"] in rev:
                prev = [rev[entry["prevo"]]]
            nxt = []
            if entry:
                for e in entry["evos"]:
                    if e in rev:
                        nxt.append(rev[e])

        out[sid] = {
            "name": {"en": en, "zh": zh},
            "prev": prev,
            "next": nxt,
            "form": is_form,
        }

        if old.get(sid, {}).get("name", {}).get("zh") != zh:
            changed.append((sid, old.get(sid, {}).get("name", {}).get("zh", ""), zh))

    with open(OUT, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    bases = sum(1 for v in out.values() if not v["form"] and not v["prev"])
    print(f"写入 {OUT}：共 {len(out)} 条，可选基础形态(初始) {bases} 个")
    print(f"中文名变更 {len(changed)} 条（旧 -> 新）：")
    for sid, o, nw in changed:
        print(f"  {sid}: {o} -> {nw}")


if __name__ == "__main__":
    main()
