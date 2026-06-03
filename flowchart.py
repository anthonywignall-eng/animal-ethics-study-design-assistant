"""Simple PNG flowchart rendering for Streamlit and Word reports."""

from __future__ import annotations

from io import BytesIO
from textwrap import wrap

from PIL import Image, ImageDraw, ImageFont


BOX_WIDTH = 860
BOX_HEIGHT = 86
MARGIN_X = 60
MARGIN_Y = 40
GAP = 34
ARROW_HEIGHT = 24
BACKGROUND = "white"
BOX_FILL = "#f8fafc"
BOX_OUTLINE = "#64748b"
TEXT = "#0f172a"
ACCENT = "#1f4e79"


def render_flowchart_png(steps: list[str]) -> BytesIO:
    """Render a vertical flowchart as PNG bytes without external Graphviz dependencies."""
    if not steps:
        steps = ["No flowchart steps available"]

    height = MARGIN_Y * 2 + len(steps) * BOX_HEIGHT + (len(steps) - 1) * (GAP + ARROW_HEIGHT)
    width = BOX_WIDTH + MARGIN_X * 2
    image = Image.new("RGB", (width, height), BACKGROUND)
    draw = ImageDraw.Draw(image)
    title_font, body_font = _fonts()

    y = MARGIN_Y
    for index, step in enumerate(steps, start=1):
        x = MARGIN_X
        box = [x, y, x + BOX_WIDTH, y + BOX_HEIGHT]
        draw.rounded_rectangle(box, radius=10, fill=BOX_FILL, outline=BOX_OUTLINE, width=2)
        draw.text((x + 20, y + 16), f"Step {index}", fill=ACCENT, font=title_font)
        wrapped = wrap(step, width=92)
        text_y = y + 40
        for line in wrapped[:2]:
            draw.text((x + 20, text_y), line, fill=TEXT, font=body_font)
            text_y += 20
        y += BOX_HEIGHT
        if index < len(steps):
            arrow_x = width // 2
            draw.line((arrow_x, y + 5, arrow_x, y + GAP + ARROW_HEIGHT - 6), fill=ACCENT, width=3)
            draw.polygon(
                [
                    (arrow_x - 8, y + GAP + ARROW_HEIGHT - 10),
                    (arrow_x + 8, y + GAP + ARROW_HEIGHT - 10),
                    (arrow_x, y + GAP + ARROW_HEIGHT + 2),
                ],
                fill=ACCENT,
            )
            y += GAP + ARROW_HEIGHT

    output = BytesIO()
    image.save(output, format="PNG")
    output.seek(0)
    return output


def _fonts():
    try:
        title_font = ImageFont.truetype("arialbd.ttf", 16)
        body_font = ImageFont.truetype("arial.ttf", 15)
    except OSError:
        title_font = ImageFont.load_default()
        body_font = ImageFont.load_default()
    return title_font, body_font
