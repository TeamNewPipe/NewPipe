PipePipe third-party binary compatibility fixture

Plugin: Twitch 1.2.1, version code 5, by Jonas Mohr
Package source:
https://github.com/MohrJonas/TwitchPipePipePlugin/releases/download/repo/twitch-5.ppp
Package SHA-256:
3b48b2fd3c773c36f1f3ab7ae4e18af0eb92cd3975f004ba074120b1e4f4228a
Package size: 239727 bytes

Source revision for this release:
https://github.com/MohrJonas/TwitchPipePipePlugin/tree/9204d76a1d45271b9887fef2ef0561ce07819a84
Catalog signature and key were recorded from:
https://github.com/MohrJonas/TwitchPipePipePlugin/releases/download/repo/index.json

The third-party package has an upstream "MIT NON-AI License", with additional
restrictions; do not describe it as standard MIT or as a WizeStream dependency.
Neither the package nor its implementation is committed to WizeStream or bundled
in the application. scripts/prepare-plugin-test-fixtures.py downloads the exact
published bytes and complete upstream notice into app/build/generated/plugin-test-assets.
The notice's SHA-256 is:
08c1c6ac745870031ef39014c9b3cc20b841444e24341c289b9a5932191e936c

Only instrumentation test APKs include these generated files. Do not publish those
test APKs or fixtures as release artifacts. Run the preparation script before
building compatibility tests. Instrumentation performs no internet access: all
extractor requests are answered by deterministic synthetic test responses.

This is an unmodified published package: compatibility tests must not repair or
replace its DEX. The publisher repository provides version 1.2.1, including its
Android 7 URL-encoding fix. The community catalog still listed older version 1.2.0
when this fixture was recorded; the publisher signing key is unchanged.
