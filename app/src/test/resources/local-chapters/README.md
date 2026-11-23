# Embedded chapter fixtures

These are complete playable files containing four seconds of generated silence or black video, stored as Base64
to keep fixtures portable through the repository's text-based tooling. No third-party recording is
included. The parser tests decode these files directly. Synthetic malformed inputs are constructed
separately in the tests.

Generated and independently checked using FFmpeg/ffprobe 7.1.5 (Debian `7.1.5-0+deb13u1`):

```sh
ffmpeg -f lavfi -i anullsrc=r=8000:cl=mono -f ffmetadata -i chapters.ffmetadata \
  -map 0:a -map_metadata 1 -map_chapters 1 -t 4 \
  -c:a libmp3lame -b:a 16k -id3v2_version 3 id3v23.mp3
ffmpeg -f lavfi -i anullsrc=r=8000:cl=mono -f ffmetadata -i chapters.ffmetadata \
  -map 0:a -map_metadata 1 -map_chapters 1 -t 4 \
  -c:a libmp3lame -b:a 16k -id3v2_version 4 id3v24.mp3
ffmpeg -f lavfi -i anullsrc=r=8000:cl=mono -f ffmetadata -i chapters.ffmetadata \
  -map 0:a -map_metadata 1 -map_chapters 1 -t 4 -c:a aac -b:a 16k nero.m4a
ffmpeg -f lavfi -i color=c=black:s=16x16:r=1 -f ffmetadata -i chapters.ffmetadata \
  -map 0:v -map_metadata 1 -map_chapters 1 -t 4 -c:v libx264 -pix_fmt yuv420p nero.mp4
ffprobe -v error -show_chapters -of compact FILE
```

Expected chapters for all four: `0 ms: Introduction`, `1500 ms: Résumé – فصل`,
`3500 ms: Closing`. The MP3 files contain CHAP/CTOC frames; M4A/MP4 contain a Nero `moov/udta/chpl`
box (and FFmpeg's ordinary chapter track). The reader supports the Nero table, not arbitrary
QuickTime chapter tracks, Matroska or Ogg chapters. ffprobe also verifies that each complete
fixture is a valid playable media container; it is not used at app runtime.

SHA-256 of decoded files:

| File | SHA-256 |
| --- | --- |
| id3v23.mp3 | `7f98c641ffb7a23fedceefd62df10f0266a4de8b285a047cd9480dd7aac0e186` |
| id3v24.mp3 | `898b1d05dc7472756fc46dc9d0c71089f8d621c11a801393c5b9fa46a8a12f87` |
| nero.m4a | `01e935c570041a5225cfa01e6db08f548a757ffa116139d4e1599b6df48c1f60` |
| nero.mp4 | `987b11a13ca5f2edb7d0e9e743cb22a69bffa50562693dd5cfb4711e6312c4ce` |

Format references:

- ID3 chapter addendum: <https://id3.org/id3v2-chapters-1.0>
- ID3v2.4 sizes and flags: <https://id3.org/id3v2.4.0-structure>
- Nero table producer: <https://github.com/FFmpeg/FFmpeg/blob/n7.1/libavformat/movenc.c>
  (`mov_write_chpl_tag`, using 100 ns units).
