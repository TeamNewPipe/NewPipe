# Android 10 detail-panel rendering workaround

Issue #1032 reports disappearing video details on a Motorola One Macro running Android 10.
The reporter confirmed persistence on v1.35.0 after the original text-only software layers.
The supplied recording also shows action icons disappearing while the channel image and
content below the panel remain visible, so the symptom is not restricted to text glyphs.

The follow-up rasterizes `detailContentRootLayout` as one software layer on Android 10.
This groups the title, channel labels, and action controls in the same bounded panel.
The player container and scrolling pager remain outside that layer. A defensive check
rejects any root containing a SurfaceView or TextureView to avoid rasterizing video output.
Visibility, opacity, selection, accessibility, and event handling are not rewritten.
Other Android versions retain their existing rendering path.

Tests cover layer scope, repeated application, unchanged child state, video-surface guards,
and the API 28/30 exclusions. They cannot reproduce the Motorola GPU/compositor behavior.
The exact device-specific cause remains unconfirmed: this is a targeted compatibility
workaround and #1032 must remain open until the reporter verifies a build containing it.

## Opt-in rendering diagnostics

The reporter still observes the issue on 1.52.2. The original screenshots include a state
where content outside the detail panel is also absent. Another software-layer workaround
is not established by that evidence.

Debug builds on Android 10 provide **Debug settings → Capture Android 10 detail rendering**.
Start the capture, return to the affected details screen within two minutes, and reproduce
the issue. Sampling lasts at most 30 seconds / 60 samples and pauses with the view lifecycle.
View recreation can continue the same bounded capture; changing profiles stops it. Sampling
does not redraw, invalidate, or change the rendering properties of any view.

Return to **Debug settings → View rendering report** to review, explicitly copy, or clear
the report. It remains in memory only and is lost when the process exits. There are no
automatic screenshots, uploads, or logcat exports. The allowlisted view state contains
geometry, visibility, opacity, clipping, layer and hardware flags, text lengths and layout
metrics, up to eight ancestors, and player-surface presence/validity. Repeated states are
grouped. The export is capped at 128,000 characters, retaining recent frames and reporting
the count of omitted earlier frames if needed. Media text, channel names, URLs, descriptions,
profile identifiers, arbitrary view tags, and bitmap contents are not included.

Compare a fully stopped player with paused and active playback, and start with the System
font and a standard opaque theme. If the view state remains visible and in bounds while the
screen is blank, investigate drawing/composition with new affected-device evidence. If the
state itself changes, follow the binding or ancestor-layout path represented in the report.
The real-layout tests cover the diagnostic data and its lifecycle, not the Motorola GPU
failure. This feature is diagnostic support and must not be described as a flicker fix.
