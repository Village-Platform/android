# Tutorial video — Egyptian Arabic narration (issue #28, app 10.1.0)

One audio file per segment, so each clip is cut to its own narration. Files are named
`segNN.wav` and go in `$AUDIO` (default `/tmp/narration`) for `assemble_video.sh`.

## Voice (Gemini TTS)

Generated in Google AI Studio → *Generate speech* (Gemini TTS, single speaker), which downloads a
WAV per run. Style instruction, pasted before each segment:

> اقرأ النص ده بلهجة مصرية عامية طبيعية، بصوت راجل دافي وودود، كأنك بتشرح لجارك في القرية
> إزاي يستخدم التطبيق. سرعة متوسطة، ووقفة خفيفة عند الفواصل. متقراش بالفصحى.

Voice used for the 10.1.0 video: **Cruz** (Gemini 3.8 Flash TTS, "polished and professional, deep
pitch"), with this style text in English, which the model follows better than the Arabic one:

> Native speaker from a village in Qalyubia, Egypt, speaking everyday Egyptian Arabic (Masri), not
> Modern Standard Arabic and not a news-anchor voice. Pronounce ج as a hard G (as in "gamal"),
> pronounce ق as a glottal stop ("'ahwa"), ث as s or t, ذ as z or d. Relaxed Egyptian intonation with
> the natural rise and fall of Cairo street speech; friendly, smiling, like telling a neighbor about
> the app over tea. Medium pace.

Naming the Egyptian sound shifts (ج → g, ق → glottal stop) made the accent clearly Egyptian; a plain
"Egyptian dialect" instruction still came out close to Standard Arabic. Cruz was picked over Finn,
Gero and Daro in a side-by-side test of segment 01.

Keep the same voice for every segment; regenerate all of them if it changes.

## Segments

| # | Clip | Narration |
|---|------|-----------|
| 01 | intro card | أهلاً بيك في تطبيق أجهور الكبرى. التطبيق ده معمول عشان تلاقي كل خدمات البلد في مكان واحد. يلّا نشوف بنستخدمه إزاي. |
| 02 | `01_home` | دي الشاشة الرئيسية. من فوق تقدر تدوّر على أي حاجة: دكتور، صيدلية، أو مطعم. وتحت البحث هتلاقي الأقسام، وبعدها الأماكن المميزة في القرية. |
| 03 | `02_category` | لو عايز دكتور مثلاً، دوس على قسم الأطباء، وهيظهرلك كل العيادات اللي في البلد. |
| 04 | `03_firm` | دوس على أي مكان عشان تشوف تفاصيله: العنوان والتليفون ومواعيد الشغل. ومن هنا تتصل بيهم على طول، أو تكلّمهم واتساب، أو تفتح مكانهم على الخريطة. |
| 05 | `04_explore` | ولو عايز تشوف كل الأماكن، افتح صفحة استكشف، واكتب اسم المكان أو اختار القسم. |
| 06 | `05_news_photos` | ومن صفحة الأخبار هتتابع كل جديد في أجهور. والجديد في التحديث ده إن الأخبار والأماكن بقت بالصور: دوس على أي صورة وكبّرها، وقلّب بصباعك. |
| 07 | `06_account` | ومن حسابي تقدر دلوقتي تعدّل اسمك وتحط صورتك. ولو عندك محل أو عيادة، ضيفها وارفع لحد خمس صور ليها. والتصفح نفسه مجاني ومن غير حساب. |
| 08 | outro card | وبكده تكون عرفت تستخدم تطبيق أجهور الكبرى. نزّله دلوقتي من جوجل بلاي، وشاركه مع أهلك وجيرانك. |

The intro and outro cards are the feature graphic (`screenshots/feature_graphic_1024x500.png`)
and the download post (`social/post_download_1080.png`).
