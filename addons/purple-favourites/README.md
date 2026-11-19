# Favoritos

A one-key shortcut into Purple IPTV's favourites list: the TV's **Netflix button** — dead since Netflix was
uninstalled, where it used to open the store listing — opens the player and presses its own *FAVORITOS* tile.
The app also declares the launcher's module card, so the same shortcut is a tile called *Favoritos*.

Purple IPTV keeps its favourites in a private database and publishes nothing to `TvProvider`, so no launcher
can read them; this app does what a person does instead, through an accessibility service:

1. It takes the remote's key before the system's global-key handler does (that handler is what opens the Play
   Store page for `com.netflix.ninja`).
2. It starts the player's splash activity.
3. It waits for the dashboard and clicks the clickable item with id `ll_favourite` (fallback: the text
   `FAVORITOS`). If the favourites screen is already up — its header reads *Favoritos* — it does nothing.

Nothing else is read from any app, and no screen coordinates are hard-coded.

## Install

```bash
TV_HOST=<tv address> addons/purple-favourites/build-and-install.sh
```

The script builds, installs and appends this service to `enabled_accessibility_services` (appending: the
launcher's `HomeKeyService` lives in the same list).

## Undo

```bash
adb shell settings put secure enabled_accessibility_services com.gathin.tvhome/.HomeKeyService
adb uninstall com.pikita.purplefavs
```

## Notes

- The button reports consumer usage `0x0c0078` with no keycode (`KEY_UNKNOWN`), so the service matches the
  scancode, with `KEYCODE_BUTTON_4` as a fallback for remotes that do map it.
- Every filtered key is logged under the `PurpleFavs` tag: that is how the code was found, and it still helps
  if the remote is paired again.
- Tested on the Chromecast with Google TV 4K (`sabrina`), Google TV 14, Purple IPTV 7.0.7.
