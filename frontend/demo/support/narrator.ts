import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import { existsSync } from 'node:fs';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import { join, resolve } from 'node:path';

/**
 * The demo's voiceover: each caption read aloud by a voice built into Windows (a female voice,
 * Microsoft Zira, by default) through PowerShell and System.Speech. Free, offline, no account.
 * On by default on Windows; DEMO_VOICE=off records silent videos, and DEMO_VOICE=<name> picks
 * another installed voice (e.g. "Microsoft David Desktop").
 *
 * Clips are cached in demo-audio-cache/, so recording again is quicker. After a recording the
 * clips are laid on one narration track at the moments their captions appeared, and ffmpeg
 * (when installed) joins it to the video as an .mp4.
 */

const VOICE = process.env.DEMO_VOICE ?? 'female';
/** System.Speech rate, -10 (slow) to 10 (fast); 0 is normal. */
const RATE = Number(process.env.DEMO_VOICE_RATE ?? 0);
const CACHE_DIR = process.env.DEMO_AUDIO_CACHE ?? 'demo-audio-cache';

/** Speaks the text in $env:DEMO_TTS_TEXT into the WAV file $env:DEMO_TTS_OUT (24 kHz, 16-bit, mono). */
const SPEAK_SCRIPT = `
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Speech
$speaker = New-Object System.Speech.Synthesis.SpeechSynthesizer
if ($env:DEMO_TTS_VOICE -eq 'female') {
  $speaker.SelectVoiceByHints([System.Speech.Synthesis.VoiceGender]::Female)
} else {
  $speaker.SelectVoice($env:DEMO_TTS_VOICE)
}
$speaker.Rate = [int]$env:DEMO_TTS_RATE
$format = New-Object System.Speech.AudioFormat.SpeechAudioFormatInfo(24000,
  [System.Speech.AudioFormat.AudioBitsPerSample]::Sixteen, [System.Speech.AudioFormat.AudioChannel]::Mono)
$speaker.SetOutputToWaveFile($env:DEMO_TTS_OUT, $format)
$speaker.Speak([IO.File]::ReadAllText($env:DEMO_TTS_TEXT))
$speaker.Dispose()
`;

/** One spoken sentence: 16-bit mono PCM. */
export interface Clip {
  samples: Buffer;
  sampleRate: number;
  ms: number;
}

/** Where a clip goes on the video's timeline. */
export interface Cue {
  atMs: number;
  clip: Clip;
}

export class Narrator {
  /** The voice for this text, from the cache or spoken by Windows. */
  async speak(text: string): Promise<Clip> {
    const spoken = forSpeech(text);
    const key = createHash('sha256').update(`${VOICE}|${RATE}|${spoken}`).digest('hex').slice(0, 24);
    await mkdir(CACHE_DIR, { recursive: true });
    const wavFile = resolve(join(CACHE_DIR, `${key}.wav`));
    if (!existsSync(wavFile)) {
      const textFile = resolve(join(CACHE_DIR, `${key}.txt`));
      await writeFile(textFile, spoken, 'utf8');
      const result = spawnSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', SPEAK_SCRIPT], {
        env: {
          ...process.env,
          DEMO_TTS_TEXT: textFile,
          DEMO_TTS_OUT: wavFile,
          DEMO_TTS_VOICE: VOICE,
          DEMO_TTS_RATE: String(RATE),
        },
        encoding: 'utf8',
      });
      if (result.error || result.status !== 0 || !existsSync(wavFile)) {
        throw new Error(
          `Windows could not speak "${spoken}" with voice ${VOICE}: ` +
            `${result.error?.message ?? result.stderr}. Set DEMO_VOICE=off to record without a voice.`,
        );
      }
    }
    return readWav(await readFile(wavFile));
  }
}

/** The narrator on Windows (unless DEMO_VOICE=off); elsewhere no voice. */
export function createNarrator(): Narrator | null {
  if (VOICE.toLowerCase() === 'off' || process.platform !== 'win32') {
    return null;
  }
  return new Narrator();
}

/** Captions use a few symbols that read badly aloud. */
function forSpeech(text: string): string {
  return text
    .replace(/\s*·\s*/g, ', ')
    .replace(/…/g, '.')
    .replace(/\.\.\./g, ',')
    .replace(/"/g, '');
}

/** The PCM samples and rate of a WAV file. */
function readWav(wav: Buffer): Clip {
  let sampleRate = 24_000;
  let offset = 12;
  while (offset + 8 <= wav.length) {
    const id = wav.toString('ascii', offset, offset + 4);
    const size = wav.readUInt32LE(offset + 4);
    if (id === 'fmt ') {
      sampleRate = wav.readUInt32LE(offset + 12);
    } else if (id === 'data') {
      const samples = wav.subarray(offset + 8, Math.min(wav.length, offset + 8 + size));
      return { samples, sampleRate, ms: Math.round((samples.length / 2 / sampleRate) * 1000) };
    }
    offset += 8 + size + (size % 2);
  }
  throw new Error("The voice's output was not a WAV file");
}

/** All cues on one mono 16-bit track, as a WAV file. */
export function narrationTrack(cues: Cue[]): Buffer {
  const sampleRate = cues[0]?.clip.sampleRate ?? 24_000;
  const end = Math.max(...cues.map((c) => c.atMs + c.clip.ms)) + 1_000;
  const pcm = Buffer.alloc(Math.ceil((end / 1000) * sampleRate) * 2);
  for (const { atMs, clip } of cues) {
    const start = Math.round((atMs / 1000) * sampleRate) * 2;
    for (let i = 0; i + 1 < clip.samples.length && start + i + 1 < pcm.length; i += 2) {
      const mixed = pcm.readInt16LE(start + i) + clip.samples.readInt16LE(i);
      pcm.writeInt16LE(Math.max(-32768, Math.min(32767, mixed)), start + i);
    }
  }
  const header = Buffer.alloc(44);
  header.write('RIFF', 0, 'ascii');
  header.writeUInt32LE(36 + pcm.length, 4);
  header.write('WAVE', 8, 'ascii');
  header.write('fmt ', 12, 'ascii');
  header.writeUInt32LE(16, 16);
  header.writeUInt16LE(1, 20); // PCM
  header.writeUInt16LE(1, 22); // mono
  header.writeUInt32LE(sampleRate, 24);
  header.writeUInt32LE(sampleRate * 2, 28);
  header.writeUInt16LE(2, 32);
  header.writeUInt16LE(16, 34);
  header.write('data', 36, 'ascii');
  header.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([header, pcm]);
}

/**
 * Joins the silent recording and the narration into an .mp4 (H.264 + AAC) with ffmpeg.
 * Returns false when ffmpeg is not installed.
 */
export function joinWithVideo(video: string, narration: string, output: string): boolean {
  const result = spawnSync(
    'ffmpeg',
    ['-y', '-loglevel', 'error', '-i', video, '-i', narration, '-map', '0:v', '-map', '1:a',
      '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '20', '-pix_fmt', 'yuv420p',
      '-c:a', 'aac', '-b:a', '160k', output],
    { stdio: 'inherit' },
  );
  if (result.error) {
    return false;
  }
  if (result.status !== 0) {
    throw new Error(`ffmpeg could not join ${video} and ${narration}`);
  }
  return true;
}
