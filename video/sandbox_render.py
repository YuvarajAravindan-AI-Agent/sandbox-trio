"""Sandbox comparison demo video: one governed agent, three code sandboxes, real terminal captures.

    cd /opt/ai-digest && VEO=1 venv/bin/python sandbox/sandbox_render.py   -> sandbox/out/sandbox-trio.mp4 (+ .srt)

Reuses the weekly pipeline's look (game_render chrome, intro, outro, captions, Puck narration).
The three LIVE scenes replay captures/*.txt verbatim: real commands and real output recorded on
2026-10-07 from the GCP build (trio-kata VM, trio-gke cluster, trio-openshell VM).
"""
import array, os, subprocess, sys, wave
from PIL import Image, ImageDraw, ImageFont
sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
import imageio_ffmpeg
import game_render as g
from game_render import (W, H, FPS, BASE, CYAN, MAGENTA, LIME, AMBER, ORANGE, VIOLET, WHITE, SOFT, DIM, PANEL,
                         ease, pill, text_block, put_glow, check_mark, chime, mix_in, sc_intro, sc_outro, chrome)
import render
from render import F, tts, read_wav, chunks, srt_time

HERE = os.path.dirname(os.path.abspath(__file__))
RED = (248, 72, 72)
MONO = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf", 22)
MONO_B = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSansMono-Bold.ttf", 22)
COLS = 70  # characters per terminal row at 22 px

def cap(name):
    return open(os.path.join(HERE, "captures", name)).read().rstrip("\n").splitlines()

def rows(lines):
    """Wrap long capture lines to terminal width, keeping the source line index for highlighting."""
    out = []
    for i, l in enumerate(lines):
        while len(l) > COLS:
            out.append((i, l[:COLS])); l = "    " + l[COLS:]
        out.append((i, l))
    return out

def terminal(title, lines, marks, callouts):
    """A terminal window that replays a capture: commands type out, output appears line by line."""
    R = rows(lines)
    def fn(img, d, t, T, s):
        x0, y0, x1 = 44, 180, W - 44
        lh = min(31, (990 - y0 - 90) // len(R))
        y1 = y0 + 70 + lh * len(R) + 20
        d.rounded_rectangle((x0, y0, x1, y1), 22, fill=(8, 6, 22, 240), outline=DIM + (255,), width=3)
        d.rounded_rectangle((x0, y0, x1, y0 + 52), 22, fill=(36, 28, 78, 255))
        d.rectangle((x0, y0 + 30, x1, y0 + 52), fill=(36, 28, 78, 255))
        for k, col in enumerate(((248, 113, 113), AMBER, LIME)):
            d.ellipse((x0 + 24 + 30 * k, y0 + 17, x0 + 42 + 30 * k, y0 + 35), fill=col)
        d.text(((x0 + x1) // 2, y0 + 26), title, font=F("Bold", 24), fill=SOFT, anchor="mm")
        d.text((x1 - 20, y0 + 26), "● LIVE CAPTURE", font=F("Bold", 20), fill=RED, anchor="rm")
        shown = len(R) * ease(t / (0.72 * T)) * 1.0
        y = y0 + 70
        for n, (src, txt) in enumerate(R):
            if n >= shown: break
            part = txt if n + 1 <= shown else txt[: int(len(txt) * (shown - n))]
            col = CYAN if lines[src].startswith("$") else SOFT
            font = MONO_B if lines[src].startswith("$") else MONO
            for key, mc in marks:
                if key in lines[src]: col, font = mc, MONO_B
            d.text((x0 + 22, y), part, font=font, fill=col); y += lh
        x = 64
        for k, (txt, col) in enumerate(callouts):
            if t > T * (0.76 + 0.07 * k): x = pill(d, x, y1 + 22, txt, col, 26) + 14
    return fn

def sc_problem(img, d, t, T, s):
    d.text((540, 250), "AI agents now write", font=F("Black", 62), fill=WHITE, anchor="mm")
    d.text((540, 325), "and run their own code", font=F("Black", 62), fill=WHITE, anchor="mm")
    d.text((540, 405), "against claims, payments and patient records", font=F("Bold", 32), fill=SOFT, anchor="mm")
    risks = [("Escape to the host", "one kernel bug away"), ("Leak the data", "any outbound call"),
             ("No evidence", "nothing for the auditor")]
    for i, (a, b) in enumerate(risks):
        p = ease((t - (0.25 + 0.15 * i) * T) / (0.1 * T))
        if p <= 0: continue
        y = 500 + 150 * i
        d.rounded_rectangle((120, y, 960, y + 120), 26, fill=RED + (int(45 * p),), outline=RED + (int(255 * p),), width=4)
        d.line((170, y + 40, 210, y + 80), fill=RED, width=8); d.line((170, y + 80, 210, y + 40), fill=RED, width=8)
        d.text((250, y + 22), a, font=F("Black", 42), fill=WHITE + (int(255 * p),))
        d.text((250, y + 74), b, font=F("Regular", 28), fill=SOFT + (int(255 * p),))
    if t > 0.85 * T: pill(d, 540, 980, "No regulator signs off on that", AMBER, 32, 80, anchor="m")

def sc_workflow(img, d, t, T, s):
    pill(d, 540, 175, "Java · Spring AI 2.0.1 · LangGraph4j 1.9.3", VIOLET, 28, 70, anchor="m")
    nodes = [("1  Generate", "LLM writes Python from the schema only", CYAN),
             ("2  Execute", "throwaway sandbox · no network", LIME),
             ("3  Fix & retry", "on error, up to 3 attempts", AMBER),
             ("4  Summarize", "plain-English answer, no member IDs", MAGENTA)]
    for i, (a, b, col) in enumerate(nodes):
        p = ease((t - (0.08 + 0.13 * i) * T) / (0.1 * T))
        if p <= 0: continue
        y = 270 + 165 * i
        d.rounded_rectangle((110, y, 830, y + 120), 26, fill=col + (int(55 * p),), outline=col + (int(255 * p),), width=4)
        d.text((150, y + 20), a, font=F("Black", 44), fill=WHITE + (int(255 * p),))
        d.text((150, y + 76), b, font=F("Regular", 28), fill=SOFT + (int(255 * p),))
        if i < 3: d.line((470, y + 124, 470, y + 161), fill=col + (int(255 * p),), width=6)
    if t > 0.5 * T:  # retry loop arrow: fix -> execute
        d.line((830, 660, 900, 660), fill=AMBER, width=6); d.line((900, 660, 900, 495), fill=AMBER, width=6)
        d.line((900, 495, 838, 495), fill=AMBER, width=6); d.polygon(((834, 495), (856, 482), (856, 508)), fill=AMBER)
    p = ease((t - 0.7 * T) / (0.12 * T))
    if p > 0:
        d.rounded_rectangle((110, 940, 970, 1060), 24, fill=PANEL + (235,), outline=AMBER + (int(255 * p),), width=4)
        d.text((150, 958), "AUDIT TRAIL", font=F("Black", 30), fill=AMBER)
        d.text((150, 1004), "every step · SHA-256 of code, output, answer", font=MONO, fill=SOFT)

def sc_infra(img, d, t, T, s):
    p0 = ease(t / (0.15 * T))
    d.rounded_rectangle((50, 175, W - 50, 1070), 30, outline=CYAN + (int(255 * p0),), width=4)
    d.text((80, 192), "Google Cloud project · one trial account", font=F("Black", 32), fill=CYAN)
    boxes = [("trio-kata", "n2-standard-4 · us-east1-b", ["nested virtualization ON", "k3s + Agent Sandbox v1.0.5", "Kata 4.2.0 (kata-qemu)"], LIME),
             ("trio-gke", "GKE · us-central1-a", ["gvisor node pool (e2-standard-4)", "Agent Sandbox + deny-all policy", "least-privilege service account"], VIOLET),
             ("trio-openshell", "n2-standard-2 · us-east1-b", ["nested virtualization ON", "OpenShell 0.1.2 gateway", "VM driver (libkrun microVM)"], MAGENTA)]
    for i, (name, sub, items, col) in enumerate(boxes):
        p = ease((t - (0.15 + 0.15 * i) * T) / (0.1 * T))
        if p <= 0: continue
        y = 260 + 225 * i
        d.rounded_rectangle((90, y, 990, y + 195), 24, fill=col + (int(40 * p),), outline=col + (int(255 * p),), width=4)
        d.text((125, y + 18), name, font=F("Black", 40), fill=WHITE + (int(255 * p),))
        d.text((965, y + 28), sub, font=F("Bold", 26), fill=col + (int(255 * p),), anchor="ra")
        for k, it in enumerate(items):
            d.text((150, y + 78 + 36 * k), "• " + it, font=F("Regular", 29), fill=SOFT + (int(255 * p),))
    p = ease((t - 0.62 * T) / (0.1 * T))
    if p > 0:
        d.rounded_rectangle((90, 945, 990, 1045), 24, fill=PANEL + (235,), outline=AMBER + (int(255 * p),), width=4)
        d.text((540, 970), "Agent app: Spring Boot 4.1 · DeepSeek chat", font=F("Bold", 30), fill=WHITE, anchor="ma")
        d.text((540, 1008), "No bare metal · everything scales to zero when idle", font=F("Regular", 26), fill=AMBER, anchor="ma")

SCORE = [("Kata + Agent Sandbox", "own VM kernel", "4.1 s", LIME),
         ("GKE Sandbox (gVisor)", "userspace kernel", "5.7 s", VIOLET),
         ("OpenShell microVM", "own VM kernel", "7.3 s", MAGENTA)]
def sc_score(img, d, t, T, s):
    d.text((540, 215), "Same question · same verified answer", font=F("Black", 44), fill=WHITE, anchor="mm")
    d.text((90, 290), "SANDBOX", font=F("Bold", 24), fill=DIM); d.text((560, 290), "ISOLATION", font=F("Bold", 24), fill=DIM)
    d.text((990, 290), "RUN", font=F("Bold", 24), fill=DIM, anchor="ra")
    for i, (a, b, c, col) in enumerate(SCORE):
        p = ease((t - (0.1 + 0.14 * i) * T) / (0.1 * T))
        if p <= 0: continue
        y = 335 + 120 * i
        d.rounded_rectangle((70, y, 1010, y + 100), 22, fill=col + (int(40 * p),), outline=col + (int(255 * p),), width=3)
        d.text((95, y + 50), a, font=F("Black", 34), fill=WHITE, anchor="lm")
        d.text((560, y + 50), b, font=F("Bold", 28), fill=SOFT, anchor="lm")
        d.text((985, y + 50), c, font=F("Black", 36), fill=col, anchor="rm")
    p = ease((t - 0.55 * T) / (0.12 * T))
    if p > 0:
        d.rounded_rectangle((70, 730, 1010, 1060), 26, fill=PANEL + (235,), outline=LIME + (int(255 * p),), width=4)
        check_mark(d, 125, 790, 22, LIME, 8)
        d.text((170, 768), "Answer · synthetic claims data", font=F("Black", 34), fill=LIME)
        for k, line in enumerate(["BCBS: highest denial rate, 63 of 100", "Top reason: CO-197 no prior auth",
                                  "Denied billed amount: $27,714.87", "Checked independently against the CSV"]):
            d.text((125, 835 + 52 * k), line, font=F("Bold", 32), fill=WHITE if k < 3 else SOFT)

def sc_lesson(img, d, t, T, s):
    pts = [("Kubernetes alone doesn't isolate code", "Kata, gVisor or a microVM does", CYAN),
           ("Configuration decides safety", "OpenShell on Docker shares the host kernel", AMBER),
           ("Keep the agent, swap the sandbox", "match each client's risk appetite", LIME)]
    for i, (a, b, col) in enumerate(pts):
        p = ease((t - (0.05 + 0.2 * i) * T) / (0.1 * T))
        if p <= 0: continue
        y = 200 + 270 * i
        put_glow(img, col, 140, y + 70, 40)
        d.text((140, y + 70), str(i + 1), font=F("Black", 90), fill=col, anchor="mm")
        text_block(d, 230, y + 10, a, 48, WHITE, 780, "Black", 1.1)
        text_block(d, 230, y + 130, b, 32, SOFT, 780, "Regular")

SCENES = [
    ("intro", None, sc_intro, ["A I Agentic Enterprises. Founder Yuvaraj Aravindan has been solving enterprise IT for three decades. "
                               "Today: where should an A I agent run the code it writes, when the data is regulated?"]),
    ("problem", "THE PROBLEM", sc_problem, ["A I agents now write and run their own code, against claims, payments and patient records.",
                                           "If that code can reach the host, the network, or leaves no evidence, no regulator will sign off."]),
    ("workflow", "THE WORKFLOW", sc_workflow, ["So we built one governed agent in Java, with Spring A I and LangGraph for Java.",
                                              "The model sees only the schema. It writes Python, the code runs in a throwaway sandbox, errors are retried, and every step is hashed into an audit trail."]),
    ("kata", "LIVE · KATA CONTAINERS",
     terminal("ubuntu@trio-kata · GCP us-east1-b", cap("kata.txt"),
              [("6.18.35", LIME), ("7.0.0", AMBER), ("deny-all", LIME), ("exit=0", LIME), ("> ", WHITE)],
              [("host 7.0 → guest 6.18", LIME), ("network: deny-all", CYAN)]),
     ["Option one. Kata Containers on Kubernetes, managed by the Agent Sandbox controller.",
      "The host runs kernel seven. Inside the sandbox, six point eighteen: its own virtual machine. Network denied by policy. Answer in about four seconds."]),
    ("gke", "LIVE · GKE SANDBOX (gVisor)",
     terminal("trio-agent@trio-gke · scoped token", cap("gke-full.txt"),
              [("  yes", LIME), ("  no", RED), ("4.4.0", LIME), ("gvisor", VIOLET), ("exit=0", LIME), ("> ", WHITE)],
              [("can't read secrets", RED), ("gVisor kernel 4.4.0", LIME)]),
     ["Option two. Google's G K E Sandbox, powered by g Visor.",
      "The agent's service account can create sandboxes, and nothing else. It can't read secrets. Inside, g Visor answers as its own kernel. Under six seconds."]),
    ("openshell", "LIVE · NVIDIA OPENSHELL",
     terminal("ubuntu@trio-openshell · GCP us-east1-b", cap("openshell.txt"),
              [("6.12.76", LIME), ("7.0.0", AMBER), ("COMPUTE_DRIVER=vm", MAGENTA), ("Permission denied", RED), ("exit=0", LIME), ("> ", WHITE)],
              [("microVM kernel 6.12", LIME), ("egress refused", RED)]),
     ["Option three. NVIDIA OpenShell, switched to its micro V M driver.",
      "Again, its own kernel, and the outbound internet call is refused. About seven seconds."]),
    ("infra", "THE INFRASTRUCTURE", sc_infra, ["The infrastructure: one Google Cloud project.",
                                               "Two N2 virtual machines with nested virtualization, so no bare metal, and one G K E cluster with a g Visor node pool. Everything scales to zero when idle."]),
    ("score", "THE SCORECARD", sc_score, ["Three sandboxes, one question, and the same verified answer every time.",
                                         "B C B S has the highest denial rate, with missing prior authorization the top reason."]),
    ("lesson", "THE LESSONS", sc_lesson, ["Kubernetes alone doesn't isolate untrusted code. Kata, g Visor or a micro V M does.",
                                         "Configuration decides safety. And keep the agent fixed: choose the isolation each client's risk appetite needs."]),
    ("outro", None, sc_outro, ["Want governed A I agents built for your regulated enterprise? Visit A I agentic enterprises dot com."]),
]
VEO_BG = {  # cinematic opening (no people, per render.STYLE)
    "intro": "Slow dolly down a dark, empty data centre aisle at night, rows of server racks with teal status lights, light haze.",
    "problem": "Macro push-in on a grid of glowing translucent glass cubes on a dark surface, each sealing a small swirling orb of teal light, amber reflections.",
}
class Bg:
    """Streams a scene-fitted Veo clip as RGB frames; darkened so the drawn text stays readable."""
    def __init__(self, clip, dur, name):
        path = render.scene_background(clip, dur, os.path.join(HERE, "out", f"bg-{name}.mp4"))
        self.p = subprocess.Popen([imageio_ffmpeg.get_ffmpeg_exe(), "-loglevel", "error", "-i", path, "-f", "rawvideo",
                                   "-pix_fmt", "rgb24", "-"], stdout=subprocess.PIPE)
        self.last = BASE
    def next(self):
        b = self.p.stdout.read(W * H * 3)
        if len(b) == W * H * 3: self.last = Image.blend(Image.frombytes("RGB", (W, H), b), BASE, 0.55)
        return self.last.copy()

SAY = [("A I agentic enterprises dot com", g.URL), ("A I Agentic Enterprises", "AI Agentic Enterprises"), ("A I", "AI"),
       ("G K E", "GKE"), ("g Visor", "gVisor"), ("micro V M", "microVM"), ("LangGraph for Java", "LangGraph4j"),
       ("B C B S", "BCBS"), ("kernel seven", "kernel 7"), ("six point eighteen", "6.18")]

def main():
    g.SCENE_COL[:] = [LIME, RED, CYAN, LIME, VIOLET, MAGENTA, CYAN, LIME, AMBER, VIOLET]
    params = None; audio = array.array("h"); caps = []; spans = []; t = 0.0
    for key, label, fn, segs in SCENES:
        t0 = t
        for gi, seg in enumerate(segs):
            pr, fr = read_wav(tts(seg)); params = params or pr
            gap = g.P_SCENE + (0.6 if key in ("kata", "gke", "openshell") else 0) if gi == len(segs) - 1 else g.P_SEG
            audio.frombytes(fr); audio.extend([0] * int(pr.framerate * gap))
            sp = len(fr) / (pr.framerate * 2); c = seg
            for a, b in SAY: c = c.replace(a, b)
            cs = chunks(c); tot = sum(len(x) for x in cs)
            for k, x in enumerate(cs):
                dur = sp * len(x) / tot + (gap if k == len(cs) - 1 else 0)
                caps.append((t, t + dur, x)); t += dur
        spans.append((t0, t))
    rate = params.framerate
    for i, (key, *_r) in enumerate(SCENES):
        a, b = spans[i]; T = b - a
        if key in ("kata", "gke", "openshell"): mix_in(audio, rate, a + 0.76 * T, chime(rate, 880, 0.25))
        if key == "score":
            for k in range(3): mix_in(audio, rate, a + (0.1 + 0.14 * k) * T, chime(rate, 660 + 220 * k, 0.18))
    mix_in(audio, rate, spans[0][0] + 0.6 * (spans[0][1] - spans[0][0]), chime(rate, 1046, 0.3))
    os.makedirs(os.path.join(HERE, "out"), exist_ok=True)
    bgs = {}
    for i, (key, *_r) in enumerate(SCENES):
        clip = render.veo_clip(VEO_BG.get(key))
        if clip: bgs[i] = Bg(clip, spans[i][1] - spans[i][0], key)
    print("veo backgrounds:", sorted(SCENES[i][0] for i in bgs), flush=True)
    base = os.path.join(HERE, "out", "sandbox-trio")
    with wave.open(base + ".wav", "wb") as w: w.setparams(params); w.writeframes(audio.tobytes())
    with open(base + ".srt", "w") as f:
        for i, (a, b, x) in enumerate(caps, 1): f.write(f"{i}\n{srt_time(a)} --> {srt_time(b)}\n{x}\n\n")
    total = int(t * FPS) + 1
    only = os.environ.get("STILLS")  # STILLS=1 -> just write one PNG per scene for review
    ff = None if only else subprocess.Popen(
        [imageio_ffmpeg.get_ffmpeg_exe(), "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24",
         "-s", f"{W}x{H}", "-r", str(FPS), "-i", "-", "-i", base + ".wav", "-c:v", "libx264", "-preset", "veryfast",
         "-crf", "21", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "160k", "-ar", "48000", "-shortest",
         "-movflags", "+faststart", base + ".mp4"], stdin=subprocess.PIPE)
    ci = 0
    for fno in range(total):
        tau = fno / FPS
        si = next((k for k, (a, b) in enumerate(spans) if tau < b), len(spans) - 1)
        while ci < len(caps) - 1 and tau >= caps[ci][1]: ci += 1
        a, b = spans[si]; key, label, fn, _ = SCENES[si]
        bg = bgs[si].next() if si in bgs else None
        if only and abs((tau - a) - 0.9 * (b - a)) > 0.5 / FPS: continue
        img = bg if bg is not None else BASE.copy(); d = ImageDraw.Draw(img, "RGBA")
        fn(img, d, tau - a, b - a, {})
        chrome(img, d, tau, label, si, len(SCENES), caps[ci][2])
        if only: img.save(os.path.join(HERE, "out", f"still-{si}-{key}.png")); continue
        ff.stdin.write(img.tobytes())
        if fno % (FPS * 10) == 0: print(f"  frame {fno}/{total}", flush=True)
    if ff: ff.stdin.close(); ff.wait()
    os.remove(base + ".wav")
    print(f"done {base}.mp4  {t:.1f} s")

if __name__ == "__main__":
    main()
