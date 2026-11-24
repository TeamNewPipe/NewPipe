# Legacy comment serialization fixture

`legacy-comment-v1.51.ser` was serialized with the unmodified `InfoItem` and
`CommentsInfoItem` sources from WizeStream 1.51.0, before the plugin compatibility
change to `getCommentText()`. Its comment text field is a `String`, and it has no
description type field.

- Source baseline: `22d2c2b9bee11b6ee3ae1d117385c85e4b537b97`.
- `CommentsInfoItem` computed serial UID: `5981935524260474052`.
- `InfoItem` computed serial UID: `1646112878447920251`.
- SHA-256: `9ef71deae7dc4ffb632ca07c402f65d8f24aa89fd864a5508456c7e951b394e7`.

The fixture was generated with JDK 21 `ObjectOutputStream`. Unused `Image`,
`Page`, and `DateWrapper` types were represented by compile-time placeholders;
no instances of those placeholders are serialized. Annotation placeholders do
not affect the serialization descriptors.

The object has service ID `7`, URL `https://fixture.example/watch`, name
`Legacy comment`, comment ID `legacy-123`, author `Legacy author`, 12 likes, and
the pinned flag. Its text is `A legacy comment\nwith <text> & symbols`, with an
actual newline. Keeping these old bytes catches field-type and serial UID
regressions that serializing only the new model would miss.

## Legacy stream, channel, playlist and live-comment items

`legacy-items-v1.51.ser` contains an `ArrayList` of those four original item
models from the same baseline. Their original computed UIDs are retained by
the new classes. The live-comment model's `Duration` field/method descriptors
were compiled as `j$.time.Duration` to reproduce Android core-library
desugaring; its duration is null, so no placeholder object is serialized.

| Model | Original serial UID |
| --- | --- |
| `StreamInfoItem` | `3952272970319787469` |
| `ChannelInfoItem` | `444064663111604366` |
| `PlaylistInfoItem` | `3771611321187138122` |
| `BulletCommentsInfoItem` on Android | `7010924713763063802` |

The fixture SHA-256 is
`33aef60ba7ebae6fd2ac539c17d664ac967ede6b0137276a26d04028a41c4ace`.
The regression checks both existing metadata and the null/zero defaults of
new uploader and live-comment fields.

## Legacy nested media streams

`legacy-stream-info-v1.51.ser` contains one baseline `StreamInfo` with audio,
video, and subtitle streams built using the original builders. It includes a
descriptive audio track, a 720p video, English subtitles, and the existing
spherical/upcoming flags. Its SHA-256 is
`86f564db6f7ec6af502559285c5e8c54fcc86eee9ce61741b4a3950a0d416f82`.

| Model | Original serial UID |
| --- | --- |
| `Stream` | `-4537218748438632119` |
| `AudioStream` | `-5432602896957400844` |
| `VideoStream` | `7658657907775256506` |
| `SubtitlesStream` | `-9212190024188518774` |
| `StreamInfo` | `-150285387809501941` |

The UIDs were checked both from the baseline declaration descriptors and by
JDK 21 `ObjectStreamClass` lookup. `StreamInfo` method bodies were omitted
during its isolated compilation; constructors, fields, method signatures,
modifiers, and class initialization were preserved. Empty referenced result
types used compile-time placeholders, with no placeholder instances written.
These five baseline models do not expose Java types whose descriptors change
under Android core-library desugaring.

The regression verifies the complete nested old object and ensures absent new
metadata receives its original behavior: unknown availability, no request
headers, and live comments enabled unless a provider explicitly opts out.
