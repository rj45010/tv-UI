"""Fit an openpets.dev spritesheet for the launcher.

Drops every frame so its feet sit on the bottom of its cell (many sheets draw
frames at different heights, which makes the pet float and jitter), scales the
sheet to 768x936 to save memory, and prints which frames each row has.

    pip install pillow
    python3 tools/fit_pet.py ~/Downloads/neko/spritesheet.webp src/main/res/drawable-nodpi/pet_left.webp
"""
import sys

from PIL import Image

COLS, ROWS = 8, 9
OUT_W, OUT_H = 96, 104


def main(src_path, out_path):
    src = Image.open(src_path).convert("RGBA")
    cw, ch = src.width // COLS, src.height // ROWS
    out = Image.new("RGBA", (OUT_W * COLS, OUT_H * ROWS))
    for r in range(ROWS):
        frames = 0
        for c in range(COLS):
            cell = src.crop((c * cw, r * ch, (c + 1) * cw, (r + 1) * ch))
            box = cell.getbbox()
            if not box:
                continue
            frames += 1
            part = cell.crop(box)
            fixed = Image.new("RGBA", (cw, ch))
            fixed.alpha_composite(part, (box[0], ch - 2 - part.height))
            # Resize per cell so a frame's feet never bleed into the cell below.
            out.alpha_composite(fixed.resize((OUT_W, OUT_H), Image.LANCZOS), (c * OUT_W, r * OUT_H))
        print(f"row {r}: {frames} frames")
    out.save(out_path, "WEBP", quality=90)


if __name__ == "__main__":
    if len(sys.argv) != 3:
        sys.exit(__doc__)
    main(sys.argv[1], sys.argv[2])
