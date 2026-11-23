Synthetic interoperability fixtures, generated for these tests with FFmpeg.
tone.mp3: 440 Hz sine, 0.35 seconds, 44100 Hz, libmp3lame 96 kbps, ID3v2.3.
tone.m4a: same source, AAC 96 kbps, ordinary (non-DASH) MP4/M4A.
tone-faststart.m4a: the same AAC samples with FFmpeg's +faststart placement.
Both retain title "Artwork fixture", artist "WizeStream tests", and comment
"Preserve this field". They contain no original artwork.
cover.png: solid RGB (17,108,174), 1200 x 720; original PNG bytes.
cover.jpg: solid RGB (186,78,33), 1600 x 900; original JPEG bytes, quality 95.
All files are base64 encoded; matching fixtures are in androidTest assets.
The device tests use Android's independent media reader, not our tag parser.
