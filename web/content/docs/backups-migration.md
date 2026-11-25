Backups are portable recovery files. Device Sync is an exchange between paired devices. Keep both concepts separate, and preserve your original export until you have checked the imported data in WizeStream.

## Make a WizeStream backup

Open **Settings → Backup and restore** to export supported app data to a location you choose. Store the file somewhere you can recover if the device is lost or its app storage is cleared.

Backups can contain personal library information, history and settings. Treat them as private files. Downloaded video/audio files, local file ownership and all external playlists are not automatically covered by an app database export.

Automatic local backups can run daily or weekly to a selected folder and retain the seven newest automatic backups. Android controls exact background timing. Periodically check that recent files actually exist and that the destination remains accessible.

## Import from NewPipe or PipePipe

1. Export a compatible backup from the source application.
2. Keep an untouched copy of that export.
3. Open WizeStream’s Backup and restore settings and select the import.
4. Review the resulting subscriptions, playlists and history.

The apps use separate application IDs and do not automatically share their private storage. A file from a newer or different database format may require additional compatibility handling; do not uninstall the source app before verifying the result.

## Import a LibreTube backup

Choose **Import from LibreTube** in Backup and restore. WizeStream supports the appropriate advanced JSON backup, with preview and category selection. The import can merge supported subscriptions/groups, history, playback positions, local playlists and remote bookmarks into the profile captured when the file was selected.

| Supported | Not transferred by this importer |
| --- | --- |
| Compatible channel and group records | LibreTube accounts or credentials |
| Supported playlist/bookmark data | Instance configuration and app settings |
| Supported history and positions | Downloaded media files and search history |

The import accepts JSON up to 16 MiB, not arbitrary ZIP archives or SQLite databases. Repeated imports avoid supported duplicates. Imported local lists use LibreTube naming to help identify their origin. This is an import path, not a promise of export back to LibreTube.

## FreeTube subscriptions and playlists

**Available since 1.42.0, including 1.52.2:** use **FreeTube import and export** under Backup and restore.

Choose a modern newline-delimited `.db` export or supported older JSON file. Review the preview, select subscriptions/groups or playlists, and confirm the target profile. FreeTube profiles become subscription groups in that WizeStream profile.

Existing data is preserved and repeated imports are idempotent. Exports include the current profile’s YouTube subscriptions or supported local playlists in FreeTube-compatible `.db` form. Other platforms and local media files are skipped. The import size limit is 16 MiB.

## Import a text playlist

In **Bookmarked Playlists → overflow → Import URL list**, select a UTF-8 text file with one supported YouTube video link per line. Preview the entries and choose an existing or new local playlist. Duplicate skipping is on by default.

Supported files are bounded to 1 MiB and 2,000 lines. Video identifiers are canonicalized, with tracking, timestamp and playlist parameters removed. Missing metadata may require internet access; invalid or unavailable lines are reported. Remote service playlists are not edited by this import.

## Preserve external sources

Keep original M3U files, Live TV playlist URLs and important local media separately. Do not assume that every new feature’s data is included in a full app export. Proxy credentials and private SponsorBlock contribution identities are intentionally excluded from normal WizeStream backup/sync paths.

Before a large import, profile merge, device change or app uninstall, create an independent backup and inspect it through the relevant app workflow rather than uploading it to a public issue or third-party converter.
