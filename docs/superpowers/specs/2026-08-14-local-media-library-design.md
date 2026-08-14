# Local Media Library Design

## Goal

Replace the bottom-navigation Hot tab with a device-local video library and player, while retaining Hot as a menu item in the Mine page below Collections.

## Navigation

The main bottom tabs remain three fixed entries:

1. Home.
2. Local player.
3. Mine.

The second tab hosts `LocalMediaFragment`. It replaces `HotFragment` only in bottom navigation. The Mine page adds a `Hot videos` menu row immediately after the Collections row. Tapping it opens the existing Hot list in a dedicated `HotActivity`; the existing Hot presentation and remote data source remain unchanged.

## Local Media Library

`LocalMediaRepository` queries `MediaStore.Video.Media.EXTERNAL_CONTENT_URI` through `ContentResolver`. Each `LocalVideo` contains the stable MediaStore id, content URI, display name, duration, byte size, modified timestamp, and optional bucket name. The library excludes zero-duration records and sorts the default view by date added descending.

The fragment provides three views:

- All videos, sorted by date added.
- Recently added, using the same sort and a bounded recent result.
- Recently played, using locally persisted playback entries whose MediaStore rows still resolve.

File-name search is performed in memory over the current MediaStore result; no filesystem traversal is permitted. Android 13 and later request `READ_MEDIA_VIDEO`; Android 12 and earlier request `READ_EXTERNAL_STORAGE`. A permission-denied state includes a button that requests permission again. Empty libraries and files that no longer resolve have separate states.

## Local Playback

`LocalVideoPlayerActivity` accepts a single content URI and local metadata through Intent extras. It owns a Media3 ExoPlayer dedicated to local files. This isolates it from `VideoPlaybackController`, whose session model requires remote detail, episode, and proxy information.

First release controls:

- Restore and persist the last position per MediaStore id.
- Play/pause, seek, and standard Media3 controls.
- Playback speed selection.
- Existing vertical brightness/volume and horizontal seek gesture behavior, extracted into reusable policy code only where both activities can use it without coupling.
- Full-screen orientation and system picture-in-picture.

The local player does not implement remote episode selection, quality APIs, intro/outro auto-skip, remote comments, proxy routing, or the existing remote floating-player service. It handles unavailable or revoked URIs by showing an error and returning to the library.

## Persistence

`LocalPlaybackStore` persists recent-playback data in app-private SharedPreferences. A record contains MediaStore id, URI string, display name, duration, position, and update time. The store is bounded to the most recent 100 entries. On library load it removes records whose MediaStore ids no longer resolve, so deleted or moved files cannot remain playable.

Remote watch history and favorites retain their existing models and persistence behavior. The Mine page does not merge the two histories in this release; only the Local player tab shows local recent playback.

## UI States

The local library has explicit loading, permission-required, empty-library, search-empty, and media-unavailable states. All action text uses Android string resources. The list uses the existing poster/card visual language but uses system thumbnails and a local-file metadata row rather than remote status text.

## Testing

Local JUnit tests cover permission selection by SDK level, MediaStore row mapping, duration filtering, file-name search, recent-playback pruning and bounding, position restore rules, and the navigation replacement contract. Android instrumentation tests cover the declared media permissions and Activity manifest entries. Manual verification covers Android 12 and Android 13+, denied permission, revoked URI, empty library, device rotation, playback resume, speed selection, full-screen, and picture-in-picture.

## Non-Goals

This work does not implement a recursive filesystem scanner, document-picker-only playback, cloud sync, folder browsing, playlists, background media audio, downloads, remote playback changes, or automatic merging of local and remote histories.
