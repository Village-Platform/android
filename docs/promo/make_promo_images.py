#!/usr/bin/env python3
"""Store screenshots and social-media posts for the app (issue #28).

Each image is an HTML page rendered by headless Brave/Chrome, so Arabic shaping and layout come
from the browser, with the app's own Cairo font and brand colours (same tokens as the landing
page, village-platform/public/index.html).

    python3 docs/promo/make_promo_images.py            # writes into ../village-platform/public/promo

Inputs: raw phone screenshots (1080x2400, adb screencap with the demo status bar) in
public/promo/screenshots/raw. Outputs: screenshots/framed/*.png (Play Store, 1080x1920) and
social/*.png (1080x1080 feed posts, 1080x1920 stories).
"""
import html
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

PUBLIC = Path(__file__).resolve().parents[3] / "village-platform" / "public"
PROMO = PUBLIC / "promo"
RAW = PROMO / "screenshots" / "raw"
BROWSER = next((b for b in ("/snap/bin/brave", shutil.which("brave-browser") or "", shutil.which("google-chrome") or "",
                            shutil.which("chromium") or "") if b and os.path.exists(b)), None)

CSS = """
@font-face { font-family: Cairo; src: url("%(pub)s/brand/fonts/cairo_regular.ttf"); font-weight: 400; }
@font-face { font-family: Cairo; src: url("%(pub)s/brand/fonts/cairo_semibold.ttf"); font-weight: 600; }
@font-face { font-family: Cairo; src: url("%(pub)s/brand/fonts/cairo_black.ttf"); font-weight: 900; }
:root { --forest:#0E3A2C; --night:#072319; --gold:#C28D40; --sun:#E2B866; --wheat:#FAF4E6; }
* { box-sizing: border-box; margin: 0; }
html, body { width: %(w)dpx; height: %(h)dpx; overflow: hidden; }
body { font-family: Cairo, sans-serif; color: var(--wheat); direction: rtl; position: relative;
       background: radial-gradient(120%% 80%% at 85%% 0%%, #1C5A43 0%%, var(--forest) 45%%, var(--night) 100%%); }
.scene { position: absolute; inset: 0; background: url("%(pub)s/brand/web/scene.jpg") center/cover; opacity: .18; }
.copy { position: relative; text-align: center; padding: 0 70px; }
h1 { font-weight: 900; line-height: 1.25; }
h2 { font-weight: 600; color: var(--sun); line-height: 1.5; }
.phone { position: absolute; left: 50%%; transform: translateX(-50%%); background: #111; border-radius: 64px; padding: 16px;
         box-shadow: 0 30px 80px rgb(0 0 0 / .45); }
.phone img { display: block; width: 100%%; border-radius: 50px; }
.phone.tilt-r { transform: translateX(-50%%) rotate(5deg); }
.phone.tilt-l { transform: translateX(-50%%) rotate(-5deg); }
.badge { display: inline-flex; gap: 12px; align-items: center; background: var(--sun); color: var(--night);
         border-radius: 999px; padding: 14px 34px; font-weight: 900; }
.pill { display: inline-block; border: 2px solid rgb(250 244 230 / .45); border-radius: 999px; padding: 6px 26px;
        font-weight: 600; color: var(--wheat); }
.brand { position: absolute; display: flex; gap: 14px; align-items: center; font-weight: 900; }
.brand img { width: 74px; }
.cats { display: grid; grid-template-columns: repeat(4, 1fr); gap: 26px 18px; padding: 0 70px; position: relative; }
.cats div { display: grid; justify-items: center; gap: 10px; font-weight: 600; font-size: 32px; }
.cats img { width: 150px; height: 150px; border-radius: 34px; background: var(--wheat); padding: 10px; }
"""

PLAY = '<svg width="30" height="30" viewBox="0 0 24 24"><path fill="currentColor" d="M4.2 2.4 13.6 12l-9.4 9.6c-.3-.2-.5-.6-.5-1V3.4c0-.4.2-.8.5-1Zm10.5 10.7 2.6 2.6-11 6.3 8.4-8.9Zm3.8-4 2.9 1.7c.8.5.8 1.9 0 2.4l-2.9 1.7-2.8-2.9 2.8-2.9ZM6.3 2l11 6.3-2.6 2.6L6.3 2Z"/></svg>'


def raw(name):
    path = RAW / name
    if not path.exists():
        sys.exit(f"missing raw screenshot: {path}")
    return path.as_uri()


def page(w, h, body):
    css = CSS % {"pub": PUBLIC.as_uri(), "w": w, "h": h}
    return f'<!DOCTYPE html><html lang="ar" dir="rtl"><head><meta charset="utf-8"><style>{css}</style></head><body><div class="scene"></div>{body}</body></html>'


def store_shot(title, sub, shot):
    """Play Store screenshot, 1080x1920: headline on top, the phone below (as screenshots/framed)."""
    return page(1080, 1920, f"""
      <div class="copy" style="padding-top:120px"><h1 style="font-size:96px">{html.escape(title)}</h1>
        <h2 style="font-size:52px;margin-top:18px">{html.escape(sub)}</h2></div>
      <div class="phone" style="top:500px;width:680px"><img src="{raw(shot)}"></div>""")


def square_update():
    return page(1080, 1080, f"""
      <div class="copy" style="padding-top:70px"><span class="pill" style="font-size:30px">تحديث جديد</span>
        <h1 style="font-size:78px;margin-top:20px">الأخبار بقت بالصور</h1>
        <h2 style="font-size:38px">وكمان تقدر تعدّل بياناتك وتحط صورتك</h2></div>
      <div class="phone tilt-l" style="top:450px;width:390px;margin-left:-200px"><img src="{raw('08_news_photos.png')}"></div>
      <div class="phone tilt-r" style="top:430px;width:390px;margin-left:200px"><img src="{raw('10_edit_profile.png')}"></div>""")


def square_download():
    return page(1080, 1080, f"""
      <div class="brand" style="top:60px;right:70px;font-size:40px"><img src="{(PUBLIC / 'brand/web/mark-light.png').as_uri()}">أجهور الكبرى</div>
      <div class="copy" style="position:absolute;top:240px;right:0;width:560px;text-align:right;padding:0 70px 0 0">
        <h1 style="font-size:68px">أي خدمة محتاجها في البلد.. هتلاقيها</h1>
        <h2 style="font-size:36px;margin:22px 0 40px">دكاترة، صيدليات، مطاعم، محلات وحرفيين</h2>
        <span class="badge" style="font-size:32px">{PLAY} نزّله من Google Play</span></div>
      <div class="phone tilt-l" style="top:170px;width:420px;margin-left:-260px"><img src="{raw('02_home_v101.png')}"></div>""")


def square_categories():
    names = [("doctor", "أطباء"), ("pharmacy", "صيدليات"), ("restaurant", "مطاعم"), ("shop", "محلات"),
             ("craft", "حرفيون"), ("bakery", "مخابز"), ("school", "مدارس"), ("transport", "مواصلات")]
    tiles = "".join(f'<div><img src="{(PUBLIC / "brand/categories" / f"{k}.png").as_uri()}">{v}</div>' for k, v in names)
    return page(1080, 1080, f"""
      <div class="copy" style="padding-top:80px"><h1 style="font-size:80px">كل أماكن أجهور</h1>
        <h2 style="font-size:40px;margin-bottom:56px">في تطبيق واحد، ومجاناً</h2></div>
      <div class="cats">{tiles}</div>
      <div class="copy" style="position:absolute;bottom:60px;width:100%"><span class="badge" style="font-size:30px">{PLAY} نزّله من Google Play</span></div>""")


def story(title, sub, shot):
    return page(1080, 1920, f"""
      <div class="brand" style="top:90px;right:70px;font-size:40px"><img src="{(PUBLIC / 'brand/web/mark-light.png').as_uri()}">أجهور الكبرى</div>
      <div class="copy" style="padding-top:250px"><h1 style="font-size:92px">{html.escape(title)}</h1>
        <h2 style="font-size:46px;margin-top:14px">{html.escape(sub)}</h2></div>
      <div class="phone" style="top:600px;width:480px"><img src="{raw(shot)}"></div>
      <div class="copy" style="position:absolute;bottom:90px;width:100%"><span class="badge" style="font-size:38px">{PLAY} نزّله من Google Play</span></div>""")


JOBS = {
    "screenshots/framed/08_news_photos.png": (1080, 1920, lambda: store_shot("أخبار البلد بالصور", "شوف صور كل خبر وإعلان", "08_news_photos.png")),
    "screenshots/framed/09_photo_viewer.png": (1080, 1920, lambda: store_shot("كبّر الصورة وقلّب", "لحد ٥ صور لكل مكان وكل خبر", "09_photo_viewer.png")),
    "screenshots/framed/10_edit_profile.png": (1080, 1920, lambda: store_shot("حسابك على مزاجك", "عدّل اسمك وحط صورتك", "10_edit_profile.png")),
    "social/post_update_1080.png": (1080, 1080, square_update),
    "social/post_download_1080.png": (1080, 1080, square_download),
    "social/post_categories_1080.png": (1080, 1080, square_categories),
    "social/story_update_1080x1920.png": (1080, 1920, lambda: story("الجديد في أجهور", "الأخبار والأماكن بقت بالصور", "08_news_photos.png")),
    "social/story_download_1080x1920.png": (1080, 1920, lambda: story("كل خدمات قريتك", "في مكان واحد", "02_home_v101.png")),
}


def render(html_text, out, w, h):
    # Snap-confined browsers read and write only non-hidden paths under $HOME, so the temp page
    # lives next to the outputs rather than in /tmp or ~/.cache.
    tmp_dir = Path(tempfile.mkdtemp(prefix="render-", dir=PROMO))
    src = tmp_dir / "page.html"
    src.write_text(html_text, encoding="utf-8")
    out.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run([BROWSER, "--headless=new", "--disable-gpu", "--hide-scrollbars", "--allow-file-access-from-files",
                    "--force-device-scale-factor=1", f"--window-size={w},{h}", "--virtual-time-budget=3000",
                    f"--screenshot={out}", src.as_uri()], check=True, capture_output=True, text=True)
    if not out.exists():
        sys.exit(f"browser wrote nothing for {out.name}")
    shutil.rmtree(tmp_dir)


if __name__ == "__main__":
    if not BROWSER:
        sys.exit("needs Brave, Chrome or Chromium for headless rendering")
    only = sys.argv[1:]
    for name, (w, h, build) in JOBS.items():
        if only and not any(o in name for o in only):
            continue
        render(build(), PROMO / name, w, h)
        print("wrote", PROMO / name)
