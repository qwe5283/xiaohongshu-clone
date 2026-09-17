#!/usr/bin/env python3
"""等大对照图：把若干图标按 24dp 画布等大渲染，用于目检线宽/占位/配对是否一致。

用法（两步，先导出几何再渲染）：
    node audit.mjs --json /tmp/icons.json
    python contact-sheet.py --json /tmp/icons.json -o sheet.png ic_heart.xml ic_star.xml ic_comment.xml

判读要点（渲染规则与 Android 一致，别用错，否则会自己吓自己）：
  * 多子路径按 nonzero 规则填充：同向子路径取并集，反向子路径挖洞。
    —— 但「环形图标」的两条轮廓是反向的，用「并集」渲染会变成实心，看着像改坏了。
  * 只要 XML 里写了 fillType="evenOdd"，就逐子路径 XOR。
  * 纯描边路径（只有 strokeColor/strokeWidth、没有 fillColor）只画线，不填充。
依赖：Pillow。
"""
import argparse
import json
import os
import re
import sys

from PIL import Image, ImageDraw, ImageChops

SS = 8          # 超采样倍数
PPD = 8         # 每 dp 像素数（最终输出）
DRAWABLE = os.path.join(os.path.dirname(os.path.abspath(__file__)), *(['..'] * 0))
DEFAULT_DRAWABLE = None  # 由 --drawable 指定；不指定则只看 dump.json 里的几何


def xml_style(drawable_dir, name):
    if not drawable_dir:
        return 0.0, True, False
    path = os.path.join(drawable_dir, name)
    if not os.path.exists(path):
        return 0.0, True, False
    xml = open(path, encoding='utf8').read()
    sw = float((re.search(r'android:strokeWidth="([\d.]+)"', xml) or [0, '0'])[1])
    fill = 'android:fillColor="#FF000000"' in xml
    evenodd = 'fillType="evenOdd"' in xml
    return sw, fill, evenodd


def signed_area(sp):
    a = 0.0
    for i in range(len(sp) - 1):
        a += sp[i][0] * sp[i + 1][1] - sp[i + 1][0] * sp[i][1]
    if sp[0] != sp[-1]:
        a += sp[-1][0] * sp[0][1] - sp[0][0] * sp[-1][1]
    return a / 2


def render(geom, sw, fill, evenodd, dp=24, px=PPD):
    vw = geom['vw']
    vh = geom.get('vh', vw)
    W = int(dp * px * SS)
    s = px * SS * (geom['wdp'] / vw)
    ox = (W - vw * s) / 2
    oy = (W - vh * s) / 2
    subpaths = [sp for subs in geom['paths'] for sp in subs]
    ink = Image.new('1', (W, W), 0)
    if fill:
        if evenodd:
            for sp in subpaths:
                m = Image.new('1', (W, W), 0)
                ImageDraw.Draw(m).polygon([(x * s + ox, y * s + oy) for x, y in sp], fill=1)
                ink = ImageChops.difference(ink, m)
        else:
            pos = Image.new('1', (W, W), 0)
            neg = Image.new('1', (W, W), 0)
            for sp in subpaths:
                pts = [(x * s + ox, y * s + oy) for x, y in sp]
                ImageDraw.Draw(pos if signed_area(sp) > 0 else neg).polygon(pts, fill=1)
            ink = ImageChops.difference(pos, neg) if neg.getbbox() else pos
    if sw > 0:
        drw = ImageDraw.Draw(ink)
        w = max(1, round(sw * px * SS))
        for sp in subpaths:
            drw.line([(x * s + ox, y * s + oy) for x, y in sp], fill=1, width=w, joint='curve')
    return Image.eval(ink.convert('L'), lambda v: 255 - v).convert('RGB').resize(
        (int(dp * px) + 2, int(dp * px) + 2), Image.LANCZOS)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--json', required=True, help='audit.mjs --json 导出的几何')
    ap.add_argument('--drawable', default=None, help='drawable 目录（读 strokeWidth/fillType；默认不读，全部按填充处理）')
    ap.add_argument('-o', '--out', default='contact-sheet.png')
    ap.add_argument('--cols', type=int, default=5)
    ap.add_argument('names', nargs='+')
    a = ap.parse_args()

    data = json.load(open(a.json, encoding='utf8'))
    cols = a.cols
    cell = int(24 * PPD) + 8
    rows = (len(a.names) + cols - 1) // cols
    sheet = Image.new('RGB', (cell * cols, cell * rows), 'white')
    missing = []
    for i, name in enumerate(a.names):
        key = name if name in data else next((k for k in data if os.path.basename(k) == name), None)
        if key is None:
            missing.append(name)
            continue
        sw, fill, eo = xml_style(a.drawable, os.path.basename(key))
        sheet.paste(render(data[key], sw, fill, eo), ((i % cols) * cell, (i // cols) * cell))
    sheet.resize((int(sheet.width * 1.5), int(sheet.height * 1.5)), Image.LANCZOS).save(a.out)
    print(f'已保存 {a.out}（{len(a.names) - len(missing)} 个，{cols} 列）')
    for r in range(rows):
        print('  ', ' | '.join(a.names[r * cols:(r + 1) * cols]))
    if missing:
        print('未找到几何：', ', '.join(missing), file=sys.stderr)
        print('提示：先运行 node audit.mjs --json <dump.json>', file=sys.stderr)


if __name__ == '__main__':
    main()
