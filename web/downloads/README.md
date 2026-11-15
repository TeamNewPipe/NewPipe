# Direct-download files

Upload the current signed release APKs here using these fixed names:

- `wizestream-arm.apk` for the combined ARM64/ARMv7 build
- `wizestream-x86_64.apk` for the x86_64 build

The APKs are intentionally ignored by Git. Verify their signatures and hashes against the matching
GitHub release before replacing the hosted copies. `download.php` counts the initial request and
then redirects to these locally hosted files, so website downloads do not also increase GitHub's
release-asset count.
