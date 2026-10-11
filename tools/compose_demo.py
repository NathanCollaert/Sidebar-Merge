"""
Turn the frames recorded by `gradlew dev -Pdemo` (build/demo/) into a vertical 1080x1920 MP4
for Reddit. The whole sidebar runs down the left at nearly full height, so every merged panel
is on screen at once; the right column carries the name and a caption per scene.

    python tools/compose_demo.py [--ffmpeg PATH] [--out demo/sidebar-merge-reddit.mp4]
"""
import argparse
import csv
import glob
import os
import subprocess
import sys
import zipfile

from PIL import Image, ImageDraw, ImageFilter, ImageFont

W, H, FPS = 1080, 1920, 30
HOLD_END_S = 0.4
ORANGE = (255, 152, 31)
WHITE = (255, 255, 255)
GREY = (160, 160, 160)
DIM = (70, 70, 70)
MARGIN = 64
DEMO = os.path.join('build', 'demo')


def set_demo(folder):
    global DEMO
    DEMO = folder


def find_ffmpeg(explicit):
    for c in [explicit, r'E:\Program Files\Jellyfin\Server\ffmpeg.exe', 'ffmpeg']:
        if c and (os.path.isfile(c) or c == 'ffmpeg'):
            return c
    sys.exit('ffmpeg not found; pass --ffmpeg')


def runescape_fonts():
    """RuneLite ships the RuneScape fonts in its client jar; use those for an on-brand look."""
    jars = sorted(glob.glob(os.path.expanduser(
        '~/.gradle/caches/modules-2/files-2.1/net.runelite/client/*/*/client-*.jar')), key=os.path.getmtime)
    jars = [j for j in jars if 'sources' not in j]
    out = os.path.join(DEMO, 'fonts')
    os.makedirs(out, exist_ok=True)
    with zipfile.ZipFile(jars[-1]) as z:
        for name in ('runescape.ttf', 'runescape_bold.ttf'):
            with open(os.path.join(out, name), 'wb') as f:
                f.write(z.read('net/runelite/client/ui/' + name))
    return os.path.join(out, 'runescape_bold.ttf'), os.path.join(out, 'runescape.ttf')


def shadowed(draw, xy, text, font, fill, shadow=4, anchor='la'):
    """OSRS-style text: a hard black drop shadow under the colour."""
    x, y = xy
    draw.text((x + shadow, y + shadow), text, font=font, fill=(0, 0, 0), anchor=anchor)
    draw.text((x, y), text, font=font, fill=fill, anchor=anchor)


def wrap(text, font, width):
    words, lines, line = text.split(), [], ''
    for w in words:
        trial = (line + ' ' + w).strip()
        if font.getlength(trial) <= width or not line:
            line = trial
        else:
            lines.append(line)
            line = w
    lines.append(line)
    return lines


def background():
    top, bottom = (28, 24, 20), (11, 11, 11)
    grad = Image.linear_gradient('L').resize((W, H))
    bg = Image.composite(Image.new('RGB', (W, H), bottom), Image.new('RGB', (W, H), top), grad)
    glow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(glow).ellipse((-W * 0.3, H * 0.15, W * 0.7, H * 0.85), fill=ORANGE + (30,))
    glow = glow.filter(ImageFilter.GaussianBlur(170))
    return Image.alpha_composite(bg.convert('RGBA'), glow)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--ffmpeg')
    ap.add_argument('--out', default=os.path.join('demo', 'sidebar-merge-reddit.mp4'))
    ap.add_argument('--demo', default=DEMO, help='folder with the recorded frames')
    ap.add_argument('--still', type=int, help='write one PNG at this time (ms) instead of the video')
    args = ap.parse_args()
    set_demo(args.demo)

    frames = [(int(r[0]), int(r[1])) for r in csv.reader(open(os.path.join(DEMO, 'frames.csv')), delimiter='\t')]
    captions = [(int(r[0]), r[1]) for r in csv.reader(open(os.path.join(DEMO, 'captions.csv'), encoding='utf-8'), delimiter='\t')]
    bold_path, regular_path = runescape_fonts()

    sample = Image.open(os.path.join(DEMO, 'frames', 'f%05d.jpg' % frames[0][0]))
    # Frames caught while the window was briefly another size would be squashed; drop them.
    ok = [f for f in frames if Image.open(os.path.join(DEMO, 'frames', 'f%05d.jpg' % f[0])).size == sample.size]
    if len(ok) < len(frames):
        print('dropping %d frame(s) at the wrong size' % (len(frames) - len(ok)))
    frames = ok

    # The whole sidebar, as tall as the frame allows.
    fit = (H - 2 * MARGIN) / sample.height
    sw, sh = int(sample.width * fit), int(sample.height * fit)
    sx, sy = MARGIN, (H - sh) // 2
    col_x = sx + sw + 56
    col_w = W - col_x - 56

    # The RuneScape fonts are drawn on a 16px grid; whole multiples keep them crisp.
    title_font = ImageFont.truetype(bold_path, 64)
    sub_font = ImageFont.truetype(regular_path, 32)
    caption_font = ImageFont.truetype(bold_path, 80)

    base = background()
    radius = 18
    shadow = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    ImageDraw.Draw(shadow).rounded_rectangle((sx + 6, sy + 16, sx + sw + 6, sy + sh + 16), radius, fill=(0, 0, 0, 210))
    base = Image.alpha_composite(base, shadow.filter(ImageFilter.GaussianBlur(26)))
    d = ImageDraw.Draw(base)
    d.rounded_rectangle((sx - 3, sy - 3, sx + sw + 2, sy + sh + 2), radius + 3, fill=(58, 58, 58))

    logo = Image.open(os.path.join(DEMO, 'logo.png')).convert('RGBA').resize((96, 96), Image.NEAREST)
    base.alpha_composite(logo, (col_x, 150))
    shadowed(d, (col_x, 280), 'Sidebar Merge', title_font, ORANGE, shadow=4)
    shadowed(d, (col_x, 356), 'a RuneLite plugin', sub_font, GREY, shadow=2)
    d.rectangle((col_x, 430, col_x + 90, 435), fill=ORANGE)
    base = base.convert('RGB')

    mask = Image.new('L', (sw, sh), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, sw - 1, sh - 1), radius, fill=255)

    def caption_layer(index, text):
        layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
        dl = ImageDraw.Draw(layer)
        lines = wrap(text, caption_font, col_w)
        line_h = 96
        top = 960 - len(lines) * line_h // 2
        for i, line in enumerate(lines):
            shadowed(dl, (col_x, top + i * line_h), line, caption_font, WHITE, shadow=5)
        # Progress dots: where this scene sits in the tour.
        for i in range(len(captions)):
            x = col_x + i * 30
            dl.ellipse((x, 1760, x + 14, 1774), fill=ORANGE if i == index else DIM)
        return layer

    layers = [caption_layer(i, text) for i, (_, text) in enumerate(captions)]

    duration_ms = frames[-1][1] + HOLD_END_S * 1000
    total = int(duration_ms / 1000 * FPS)
    os.makedirs(os.path.dirname(args.out) or '.', exist_ok=True)
    if args.still is not None:
        total = 1
    ff = None if args.still is not None else subprocess.Popen([find_ffmpeg(args.ffmpeg), '-y', '-loglevel', 'error', '-f', 'rawvideo', '-pix_fmt', 'rgb24',
                           '-s', '%dx%d' % (W, H), '-r', str(FPS), '-i', '-', '-c:v', 'libx264', '-preset', 'slow',
                           '-crf', '17', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', args.out],
                          stdin=subprocess.PIPE)

    cache = {}
    src = 0
    for i in range(total):
        t = args.still if args.still is not None else i * 1000 / FPS
        while src + 1 < len(frames) and frames[src + 1][1] <= t:
            src += 1
        n = frames[src][0]
        if n not in cache:
            cache.clear()
            img = Image.open(os.path.join(DEMO, 'frames', 'f%05d.jpg' % n)).convert('RGB')
            cache[n] = img.resize((sw, sh), Image.LANCZOS)
        out = base.copy()
        out.paste(cache[n], (sx, sy), mask)

        current = [k for k, c in enumerate(captions) if c[0] <= t]
        if current:
            k = current[-1]
            age = (t - captions[k][0]) / 1000
            a = min(1.0, age / 0.25)
            ease = 1 - (1 - a) ** 3
            layer = layers[k]
            if a < 1:
                layer = layer.copy()
                layer.putalpha(layer.getchannel('A').point(lambda v: int(v * ease)))
            rgba = out.convert('RGBA')
            rgba.alpha_composite(layer, (int(-24 * (1 - ease)), 0))
            out = rgba.convert('RGB')

        if ff is None:
            out.save(args.out)
            print('wrote still', args.out)
            return
        ff.stdin.write(out.tobytes())
        if i % 60 == 0:
            print('frame %d/%d' % (i, total), flush=True)
    ff.stdin.close()
    ff.wait()
    print('wrote', args.out, '(%.1fs)' % (duration_ms / 1000))


if __name__ == '__main__':
    main()
