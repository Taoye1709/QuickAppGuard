#!/usr/bin/env python3
"""工程静态一致性校验：资源引用、组件声明、特征库与 manifest 对齐。"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MAIN = ROOT / "app" / "src" / "main"
errors = []

# 1. XML 良构性
import xml.etree.ElementTree as ET
for xml in list(MAIN.rglob("*.xml")):
    try:
        ET.parse(xml)
    except ET.ParseError as e:
        errors.append(f"XML 解析失败 {xml.relative_to(ROOT)}: {e}")

# 2. Kotlin 中 R.* 引用与资源对齐
res = MAIN / "res"
available = {"layout": set(), "string": set(), "color": set(), "drawable": set(), "id": set()}
for f in (res / "layout").glob("*.xml"):
    available["layout"].add(f.stem)
    available["id"] |= set(re.findall(r'@\+id/(\w+)', f.read_text(encoding="utf-8")))
values = (res / "values").glob("*.xml")
for f in values:
    text = f.read_text(encoding="utf-8")
    for tag in ("string", "color"):
        available[tag] |= set(re.findall(rf'<{tag} name="(\w+)"', text))
for f in (res / "drawable").glob("*.xml"):
    available["drawable"].add(f.stem)

used = set()
for kt in MAIN.rglob("*.kt"):
    used |= set(re.findall(r'\bR\.(layout|string|color|drawable|id)\.(\w+)', kt.read_text(encoding="utf-8")))
for kind, name in used:
    if name not in available[kind]:
        errors.append(f"R.{kind}.{name} 在代码中使用但资源不存在")

# 3. Kotlin 资源 id 定义了但未使用（仅提示，不算错）
unused = {i for i in available["id"] if i != "view_status_dot"} - {n for _, n in used}
for i in sorted(unused):
    print(f"  [提示] id/{i} 已定义但未在代码中引用")

# 4. Manifest 组件类文件存在性
manifest = (MAIN / "AndroidManifest.xml").read_text(encoding="utf-8")
for cls in re.findall(r'android:name="\.(\w+(?:\.\w+)*)"', manifest):
    # 相对类名基于 namespace com.qaguard
    candidates = list(MAIN.rglob(f"{cls.split('.')[-1]}.kt"))
    if not candidates:
        errors.append(f"Manifest 引用的类 .{cls} 找不到对应 Kotlin 文件")

# 5. 特征库与 manifest <queries> 对齐（与单测互为备份）
db = (MAIN / "java/com/qaguard/detect/EngineDatabase.kt").read_text(encoding="utf-8")
db_pkgs = set(re.findall(r'EngineEntry\(\s*"([\w.]+)"', db))
queries_pkgs = set(re.findall(r'<package android:name="([\w.]+)"', manifest))
if db_pkgs != queries_pkgs:
    errors.append(f"特征库与 manifest queries 不一致: 仅库={db_pkgs - queries_pkgs}, 仅清单={queries_pkgs - db_pkgs}")

# 6. 禁止 INTERNET 权限（合规红线）
if "android.permission.INTERNET" in manifest:
    errors.append("合规违规：manifest 声明了 INTERNET 权限")

# 7. Kotlin 花括号/圆括号配平（编译前的粗校验）
for kt in list(MAIN.rglob("*.kt")) + list(ROOT.joinpath("app/src/test").rglob("*.kt")):
    t = kt.read_text(encoding="utf-8")
    t = re.sub(r'"(?:\\.|[^"\\])*"', '""', t)      # 去字符串
    t = re.sub(r'//.*', '', t)                      # 去行注释
    t = re.sub(r'/\*.*?\*/', '', t, flags=re.S)     # 去块注释
    if t.count("{") != t.count("}"):
        errors.append(f"花括号不配平: {kt.relative_to(ROOT)}")
    if t.count("(") != t.count(")"):
        errors.append(f"圆括号不配平: {kt.relative_to(ROOT)}")

if errors:
    print("\n== 校验失败 ==")
    for e in errors:
        print("  ✗", e)
    sys.exit(1)
print("== 静态一致性校验全部通过 ==")
