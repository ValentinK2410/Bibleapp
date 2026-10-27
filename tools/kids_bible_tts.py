#!/usr/bin/env python3
"""Голос «Детской Библии»: facebook/mms-tts-rus, профиль mms-natural-1.

Темп 0.90, ударения ruaccent (ё звучит как ударное е), паузы после точки,
двоеточия и запятой, лучшее из пяти семян, подъём до 48 кГц через soxr,
тёплый эквалайзер, де-эссер, лёгкая комната, громкость около −16 LUFS.
На выходе моно AAC 80 кбит/с и тайминги абзацев для подсветки.

  python3 tools/kids_bible_tts.py
  python3 tools/kids_bible_tts.py gen_creation:5_7 jonah:11_13
"""
from __future__ import annotations

import hashlib
import json
import re
import subprocess
import sys
import wave
from pathlib import Path

import torch
from ruaccent import RUAccent
from transformers import AutoTokenizer, VitsModel

ROOT = Path(__file__).resolve().parents[1]
STORIES = ROOT / "app/src/main/assets/kids_bible/stories.json"
AUDIO = ROOT / "app/src/main/assets/kids_bible/audio"
CACHE = Path("/tmp/kids_mms_cache")
WORK = Path("/tmp/kids_bible_mms")
SR = 16000
SEEDS = (7, 21, 42, 88, 156)
VOWELS = set("аеёиоуыэюя")
VARIANT = {
    "rate": 0.90,
    "noise": 0.32,
    "noise_dur": 0.28,
    "stress": 1.18,
    "pause": {",": 0.15, ":": 0.22, ".": 0.40, "?": 0.40, "!": 0.40, ";": 0.28},
}
GAP_SEC = 0.22
BITRATE = "80k"


def duration_ms(path: Path) -> int:
    with wave.open(str(path), "rb") as handle:
        frames = handle.getnframes()
        rate = handle.getframerate()
    return int(round(frames / rate * 1000))


def split_units(accented: str):
    parts = re.split(r"([,.:?!;])", accented)
    units = []
    pending = ""
    for part in parts:
        if part in VARIANT["pause"]:
            chunk = pending.strip()
            if chunk:
                units.append((chunk, part))
            pending = ""
        else:
            pending += part
    tail = pending.strip()
    if tail:
        units.append((tail, "."))
    return units


def model_chars(accented_chunk: str):
    chars = []
    stress = []
    mark_next = False
    for ch in accented_chunk:
        if ch == "+":
            mark_next = True
            continue
        low = ch.lower().replace("ё", "е")
        if low == "е" and ch.lower() == "ё":
            chars.append("е")
            stress.append(True)
            mark_next = False
            continue
        if low in "абвгдежзийклмнопрстуфхцчшщъыьэюя ":
            chars.append(low)
            stress.append(bool(mark_next and low in VOWELS))
            mark_next = False
        else:
            mark_next = False
    while chars and chars[0] == " ":
        chars.pop(0)
        stress.pop(0)
    while chars and chars[-1] == " ":
        chars.pop()
        stress.pop()
    return "".join(chars), stress


@torch.no_grad()
def synthesize(model, input_ids, stress_at):
    model.noise_scale = VARIANT["noise"]
    model.noise_scale_duration = VARIANT["noise_dur"]
    input_padding_mask = torch.ones_like(input_ids, dtype=torch.float).unsqueeze(-1)
    text_out = model.text_encoder(
        input_ids=input_ids,
        padding_mask=input_padding_mask,
        attention_mask=None,
        output_attentions=False,
        output_hidden_states=False,
        return_dict=True,
    )
    hidden = text_out.last_hidden_state.transpose(1, 2)
    input_padding_mask = input_padding_mask.transpose(1, 2)
    if model.config.use_stochastic_duration_prediction:
        log_duration = model.duration_predictor(
            hidden,
            input_padding_mask,
            None,
            reverse=True,
            noise_scale=model.noise_scale_duration,
        )
    else:
        log_duration = model.duration_predictor(hidden, input_padding_mask, None)
    length_scale = 1.0 / VARIANT["rate"]
    duration = torch.ceil(torch.exp(log_duration) * input_padding_mask * length_scale)
    if stress_at:
        gain = torch.ones_like(duration)
        for index in stress_at:
            if 0 <= index < gain.shape[-1]:
                gain[..., index] = VARIANT["stress"]
        duration = torch.ceil(duration * gain)
    predicted_lengths = torch.clamp_min(torch.sum(duration, [1, 2]), 1).long()
    indices = torch.arange(predicted_lengths.max(), dtype=predicted_lengths.dtype)
    output_padding_mask = (indices.unsqueeze(0) < predicted_lengths.unsqueeze(1)).unsqueeze(1).to(input_padding_mask.dtype)
    attn_mask = torch.unsqueeze(input_padding_mask, 2) * torch.unsqueeze(output_padding_mask, -1)
    batch_size, _, output_length, input_length = attn_mask.shape
    cum_duration = torch.cumsum(duration, -1).view(batch_size * input_length, 1)
    out_indices = torch.arange(output_length, dtype=duration.dtype)
    valid = (out_indices.unsqueeze(0) < cum_duration).to(attn_mask.dtype).view(batch_size, input_length, output_length)
    padded = valid - torch.nn.functional.pad(valid, [0, 0, 1, 0, 0, 0])[:, :-1]
    attn = padded.unsqueeze(1).transpose(2, 3) * attn_mask
    prior_means = torch.matmul(attn.squeeze(1), text_out.prior_means).transpose(1, 2)
    prior_log_variances = torch.matmul(attn.squeeze(1), text_out.prior_log_variances).transpose(1, 2)
    prior_latents = prior_means + torch.randn_like(prior_means) * torch.exp(prior_log_variances) * model.noise_scale
    latents = model.flow(prior_latents, output_padding_mask, None, reverse=True)
    spectrogram = latents * output_padding_mask
    waveform = model.decoder(spectrogram, None).squeeze(1).squeeze(0)
    return waveform.cpu()


def score(wave, nchars):
    wave = wave.float()
    peak = wave.abs().max().item()
    if peak < 0.01 or wave.numel() < 400:
        return 1e6
    window = torch.hann_window(512)
    spec = torch.stft(wave, 512, 128, window=window, return_complex=True).abs()
    freqs = torch.linspace(0, SR / 2, spec.shape[0])
    mag = spec.sum(dim=1).clamp(min=1e-8)
    centroid = float(((freqs * mag).sum() / mag.sum()).item())
    high = float((mag[freqs > 5500].sum() / mag.sum()).item())
    dur = wave.shape[-1] / SR
    expect = max(0.35, nchars * 0.075)
    return max(0.0, peak - 0.93) * 8 + abs(centroid - 1450) / 1450 + high * 3.2 + abs(dur - expect) / expect * 0.35


def fade(wave, n=80):
    if wave.numel() < n * 2:
        return wave
    ramp = torch.linspace(0, 1, n)
    wave = wave.clone()
    wave[:n] *= ramp
    wave[-n:] *= ramp.flip(0)
    return wave


def cache_key(chars: str, flags: list[bool]) -> str:
    payload = json.dumps({"c": chars, "s": flags, "v": VARIANT, "seeds": SEEDS}, ensure_ascii=False)
    return hashlib.sha1(payload.encode("utf-8")).hexdigest()


def read_wav(path: Path) -> torch.Tensor:
    with wave.open(str(path), "rb") as handle:
        frames = handle.readframes(handle.getnframes())
    return torch.frombuffer(bytearray(frames), dtype=torch.int16).float() / 32767


def write_wav(path: Path, audio: torch.Tensor, rate: int = SR) -> None:
    pcm = (audio.numpy() * 32767).clamp(-32767, 32767).astype("int16")
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "wb") as handle:
        handle.setnchannels(1)
        handle.setsampwidth(2)
        handle.setframerate(rate)
        handle.writeframes(pcm.tobytes())


def best_chunk(model, tokenizer, chars, flags) -> torch.Tensor:
    key = cache_key(chars, flags)
    cached = CACHE / f"{key}.wav"
    if cached.exists():
        return read_wav(cached)
    encoded = tokenizer(chars, return_tensors="pt")
    ids = encoded["input_ids"]
    stress_at = [i * 2 + 1 for i, flag in enumerate(flags) if flag]
    best = None
    best_score = 1e9
    for seed in SEEDS:
        torch.manual_seed(seed)
        wave = synthesize(model, ids, stress_at)
        value = score(wave, len(chars.replace(" ", "")))
        if value < best_score:
            best, best_score = wave, value
    audio = fade(best)
    write_wav(cached, audio)
    return audio


def render_part(model, tokenizer, accent, text: str) -> torch.Tensor:
    stressed = accent.process_all(text)
    units = split_units(stressed)
    pieces = []
    for chunk, mark in units:
        chars, flags = model_chars(chunk)
        if not chars:
            continue
        pieces.append(best_chunk(model, tokenizer, chars, flags))
        pieces.append(torch.zeros(int(SR * VARIANT["pause"].get(mark, 0.4))))
    pieces.append(torch.zeros(int(SR * 0.2)))
    audio = torch.cat(pieces)
    peak = audio.abs().max().clamp(min=1e-6)
    return (audio / peak * 0.9).clamp(-1, 1)


def spoken_parts(story: dict, age: str) -> list[tuple[str, str]]:
    track = story["tracks"][age]
    parts = [(f"p{i}", text) for i, text in enumerate(track["paragraphs"])]
    parts += [
        ("verse", "Ключевой стих. " + story["keyVerse"]),
        ("meaning", "Что это значит? " + track["meaning"]),
        ("christ", "Как это связано с Иисусом. " + track["christLink"]),
        ("prayer", "Молитва. " + track["prayer"]),
        ("do", "Что мы можем сделать. " + track["activity"]),
    ]
    return parts


def text_hash(parts: list[tuple[str, str]]) -> str:
    payload = json.dumps({"parts": parts, "variant": VARIANT, "gap": GAP_SEC, "br": BITRATE}, ensure_ascii=False)
    return hashlib.sha1(payload.encode("utf-8")).hexdigest()


def post(src: Path, dest: Path) -> None:
    audio_filter = (
        "aresample=48000:resampler=soxr:precision=28,"
        "highpass=f=75,"
        "bass=g=2.4:f=170,"
        "equalizer=f=320:t=q:w=1.1:g=1.4,"
        "equalizer=f=3400:t=q:w=1.3:g=-1.6,"
        "deesser=i=0.22:m=0.45:f=0.58:s=o,"
        "aecho=0.86:0.92:16|28:0.09|0.05,"
        "loudnorm=I=-16:TP=-1.5:LRA=11"
    )
    dest.parent.mkdir(parents=True, exist_ok=True)
    subprocess.check_call(
        [
            "ffmpeg", "-y", "-i", str(src),
            "-af", audio_filter,
            "-ar", "48000", "-c:a", "aac", "-b:a", BITRATE, "-ac", "1",
            str(dest),
        ],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )


def build_track(model, tokenizer, accent, story: dict, age: str) -> None:
    parts = spoken_parts(story, age)
    digest = text_hash(parts)
    m4a = AUDIO / f"{story['id']}_{age}.m4a"
    timings = AUDIO / f"{story['id']}_{age}.timings.json"
    stamp = WORK / f"{story['id']}_{age}.stamp"
    if stamp.exists() and stamp.read_text(encoding="utf-8").strip() == digest and m4a.exists() and timings.exists():
        print(story["id"], age, "cached", flush=True)
        return
    folder = WORK / f"{story['id']}_{age}"
    folder.mkdir(parents=True, exist_ok=True)
    cues = []
    cursor = 0
    lines = []
    gap = folder / "gap.wav"
    if not gap.exists():
        write_wav(gap, torch.zeros(int(SR * GAP_SEC)))
    gap_ms = duration_ms(gap)
    for index, (part, text) in enumerate(parts):
        wav = folder / f"{index:02d}.wav"
        write_wav(wav, render_part(model, tokenizer, accent, text))
        length = duration_ms(wav)
        cues.append({"part": part, "startMs": cursor, "endMs": cursor + length})
        cursor += length
        lines.append(f"file '{wav}'")
        if index != len(parts) - 1:
            lines.append(f"file '{gap}'")
            cursor += gap_ms
    listing = folder / "list.txt"
    listing.write_text("\n".join(lines) + "\n", encoding="utf-8")
    joined = folder / "joined.wav"
    subprocess.check_call(
        ["ffmpeg", "-y", "-f", "concat", "-safe", "0", "-i", str(listing), "-c:a", "pcm_s16le", str(joined)],
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    # 16 кГц → 48 кГц ровно в три раза. Сухой сигнал aecho не сдвигается,
    # поэтому границы абзацев остаются на тех же миллисекундах.
    post(joined, m4a)
    timings.write_text(json.dumps({"cues": cues}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
    stamp.write_text(digest + "\n", encoding="utf-8")
    print(story["id"], age, f"{m4a.stat().st_size // 1024}K", f"{cues[-1]['endMs'] / 1000:.0f}s", flush=True)


def wanted(argv: list[str]) -> set[tuple[str, str]] | None:
    if not argv:
        return None
    chosen = set()
    for item in argv:
        story_id, age = item.split(":")
        chosen.add((story_id, age))
    return chosen


def main() -> None:
    only = wanted(sys.argv[1:])
    stories = json.loads(STORIES.read_text(encoding="utf-8"))["stories"]
    ages = ("5_7", "8_10", "11_13")
    CACHE.mkdir(parents=True, exist_ok=True)
    AUDIO.mkdir(parents=True, exist_ok=True)
    accent = RUAccent()
    accent.load(omograph_model_size="turbo3", use_dictionary=True)
    tokenizer = AutoTokenizer.from_pretrained("facebook/mms-tts-rus")
    model = VitsModel.from_pretrained("facebook/mms-tts-rus")
    model.eval()
    for story in stories:
        for age in ages:
            if only is not None and (story["id"], age) not in only:
                continue
            build_track(model, tokenizer, accent, story, age)


if __name__ == "__main__":
    main()
