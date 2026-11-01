"""Generates N small, valid, tagged MP3 files (silent audio) to stress-test large libraries.
Usage: python3 make_large_library.py OUT_DIR [N]"""
import os, random, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import importlib.util
spec = importlib.util.spec_from_file_location("mta", os.path.join(os.path.dirname(os.path.abspath(__file__)), "make_test_audio.py"))

out = sys.argv[1]; n = int(sys.argv[2]) if len(sys.argv) > 2 else 1500
os.makedirs(out, exist_ok=True)
# Reuse helpers without running the sample generator's main body.
src = open(spec.origin).read().split("lrc = ")[0].replace("out = sys.argv[1]", "out = None")
ns = {}; exec(compile(src, spec.origin, "exec"), ns)
rnd = random.Random(7)
words = "Sol Lua Mar Rio Vento Noite Dia Céu Estrela Fogo Chuva Areia Ponte Estrada Saudade Canção Ritmo Viagem Sonho Cidade Caminho Jardim Azul Verde Ouro".split()
covers = [ns["png"](64, 64, (rnd.randrange(256), rnd.randrange(256), rnd.randrange(256)), (rnd.randrange(80), rnd.randrange(80), rnd.randrange(80))) for _ in range(30)]
for i in range(n):
    artist = f"Artista {i % 60:02d}"
    album_no = i % 150
    title = f"{rnd.choice(words)} {rnd.choice(words)} {i}"
    tags = {"TIT2": title, "TPE1": artist, "TALB": f"Álbum {album_no:03d}", "TCON": rnd.choice(["Rock", "Pop", "MPB", "Samba", "Jazz"]), "TRCK": str(i // 150 + 1)}
    cover = covers[album_no % 30] if album_no % 3 == 0 else None
    data = ns["id3"](tags, cover) + ns["silent_mp3"](3 + i % 5)
    with open(os.path.join(out, f"{i:04d} - {title}.mp3"), "wb") as f:
        f.write(data)
print("generated", n)
