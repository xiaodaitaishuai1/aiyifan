"""Create packaged slideshow images without changing the source directory.

Requires Pillow. Run from the repository root:
    python tools/prepare_mine_header_images.py
"""

from pathlib import Path

from PIL import Image, ImageOps


def main():
    root = Path(__file__).resolve().parents[1]
    source = root / "image/ll"
    destination = root / "app/src/main/assets/mine_header"
    files = sorted(path for path in source.iterdir() if path.is_file())
    if len(files) != 188:
        raise ValueError(f"Expected 188 source images, found {len(files)}")
    destination.mkdir(parents=True, exist_ok=True)
    for index, path in enumerate(files, 1):
        with Image.open(path) as image:
            image.seek(0)
            output = ImageOps.exif_transpose(image).convert("RGB")
            output.thumbnail((1920, 1920), Image.Resampling.LANCZOS)
            output.save(destination / f"mine_header_{index:03}.webp", "WEBP", quality=85, method=6)
    print(f"Prepared {len(files)} images; {sum(p.stat().st_size for p in destination.glob('*.webp')):,} bytes")


if __name__ == "__main__":
    main()
