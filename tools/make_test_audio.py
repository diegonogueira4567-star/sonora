import math, os, struct, sys, wave, zlib
out = sys.argv[1]

def png(w, h, c1, c2):
    rows = b''
    for y in range(h):
        rows += b'\x00'
        for x in range(w):
            t = (x + y) / (w + h)
            rows += bytes(int(c1[i] * (1 - t) + c2[i] * t) for i in range(3))
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xffffffff)
    return b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0)) + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b'')

def u16(s):  # encoding 1: UTF-16 with BOM
    return b'\x01' + b'\xff\xfe' + s.encode('utf-16-le')

def frame(fid, data):
    return fid.encode() + struct.pack('>I', len(data)) + b'\x00\x00' + data

def id3(tags, cover=None, lyrics=None):
    body = b''
    for fid, val in tags.items():
        body += frame(fid, u16(val))
    if cover:
        body += frame('APIC', b'\x00' + b'image/png\x00' + b'\x03' + b'\x00' + cover)
    if lyrics:
        body += frame('USLT', b'\x01' + b'por' + b'\xff\xfe\x00\x00' + b'\xff\xfe' + lyrics.encode('utf-16-le'))
    size = len(body)
    ss = bytes([(size >> 21) & 0x7f, (size >> 14) & 0x7f, (size >> 7) & 0x7f, size & 0x7f])
    return b'ID3\x03\x00\x00' + ss + body

def silent_mp3(seconds):
    # MPEG-1 Layer III, 128 kbps, 44.1 kHz, mono, zeroed side info -> valid silent frames
    header = bytes([0xFF, 0xFB, 0x90, 0xC4])
    frame_len = 417
    n = int(seconds * 44100 / 1152)
    return (header + b'\x00' * (frame_len - 4)) * n

def write_mp3(name, seconds, tags, colors=None, lyrics=None):
    cover = png(96, 96, *colors) if colors else None
    with open(os.path.join(out, name), 'wb') as f:
        f.write(id3(tags, cover, lyrics) + silent_mp3(seconds))

def tone_wav(name, freq, seconds):
    rate = 44100
    with wave.open(os.path.join(out, name), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(rate)
        frames = bytearray()
        for i in range(int(rate * seconds)):
            env = min(1.0, i / 2000, (rate * seconds - i) / 2000)
            frames += struct.pack('<h', int(9000 * env * math.sin(2 * math.pi * freq * i / rate)))
        w.writeframes(bytes(frames))

lrc = "[00:00.00]Primeira linha da letra\n[00:05.00]Segunda linha sincronizada\n[00:10.00]Terceira linha\n[00:20.00]Final"
demo = [("Amanhecer", 34), ("Brisa do Mar", 22), ("Caminhos", 40), ("Despedida", 26)]
for i, (title, secs) in enumerate(demo, 1):
    write_mp3(f"Banda Teste - {title}.mp3", secs,
              {"TIT2": title, "TPE1": "Banda Teste", "TALB": "Sonora Demo", "TPE2": "Banda Teste", "TCON": "Pop", "TRCK": f"{i}/4", "TYER": "2024"},
              colors=((30, 210, 164), (20, 40, 90)), lyrics=lrc if i == 1 else None)
for i, (title, secs) in enumerate([("Ritmo Quente", 31), ("Noite de São João", 28)], 1):
    write_mp3(f"{title}.mp3", secs, {"TIT2": title, "TPE1": "Ana Clara", "TALB": "Ritmos do Brasil", "TCON": "Forró", "TRCK": str(i), "TYER": "2021"},
              colors=((255, 107, 107), (90, 20, 60)))
# No tags at all: title must come from the file name
with open(os.path.join(out, "gravacao_sem_tags.mp3"), "wb") as f:
    f.write(silent_mp3(12))
tone_wav("Tom 440Hz.wav", 440, 15)
tone_wav("Tom 660Hz.wav", 660, 15)
with open(os.path.join(out, "arquivo_corrompido.mp3"), "wb") as f:
    f.write(os.urandom(64 * 1024))
print("ok", sorted(os.listdir(out)))
