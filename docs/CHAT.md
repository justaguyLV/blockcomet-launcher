# BlockComet Chat

A friends/DM chat inside the launcher: add people by username, message them, and send a "join my server" invite
bubble with a button. Settings → Launcher appearance → Chat.

## How it's built

- **Backend: PocketBase**, a free, single-binary, open-source backend (auth + realtime database + admin UI,
  nothing to hand-write). `copper-custom/chat_web/pb_schema.json` is a ready-made schema you import from
  PocketBase's own admin UI (Settings → Import collections) — two collections (`friends`, `messages`) on top of
  PocketBase's built-in `users`.
- **Client: a WebView**, not native networking code. `copper-custom/chat_web/chat.html` is the whole chat UI; it
  talks to PocketBase with PocketBase's own JS SDK (loaded from a CDN). `ChatActivity.java` just opens that page
  and exposes two small callbacks to it (save/read the server URL, handle a tap on a "Join" button).
- `chat.py` wires it into the launcher the same way `studio.py` wires in Color Studio: a settings entry, the
  activity in the manifest, nothing else touched.

## Deploying the server (free, no server of your own needed)

**Easiest: pockethost.io** — a hosted PocketBase service with a free-forever tier (1 project, fair-use CPU/
bandwidth/storage, no credit card for the free tier). Zero setup, nothing to deploy or maintain:

1. Sign up at pockethost.io and create a project. You get a URL like `your-project.pockethost.io`.
2. Open its admin UI (same URL + `/_/`), create an admin account.
3. Settings → Import collections → paste the contents of `copper-custom/chat_web/pb_schema.json` → Review → Import.
4. Give that URL to your friend group — each person pastes it into the launcher's Chat screen once, then creates
   a username/password. That's it, no server rented or run by anyone.

**Alternative: self-host it yourself** (more control, more setup) — Render.com's free web service tier or Fly.io's
free allowance can run the official `ghcr.io/muchobien/pocketbase` Docker image; same import step (3) above.
Fly.io requires a card on file even for its free allowance; Render's free web tier does not, but its disk is not
persistent (chat history resets on redeploy) unless you add a paid persistent disk.

## What's not done yet: direct join

Right now tapping a "Join" invite just copies the address to the clipboard — you still open Multiplayer and
paste it in yourself.

What a real direct-join needs, confirmed against Copper's own source (`CopperLauncher/Copper-Android`, the
commit this repo pins):
- Minecraft 1.20+ (the bundled Meteor instances are 1.20.1+) accepts a vanilla `--quickPlayMultiplayer host:port`
  launch argument that auto-joins on start — no mod required.
- Instance launch in Copper goes through `GameService` → `GameActivity`, not through a simple Intent extra, so
  adding that argument means hooking the instance's launch step (most likely patching its version JSON's
  `arguments.game` the same way Copper already merges "inherited" version args — see `Tools.getVersionInfo` in
  Copper's source) right before the user presses Play on that instance.

This is a separate, riskier change to a part of Copper this repo doesn't touch yet, so it's intentionally left
out of this first pass rather than shipped half-verified. Suggested next step: build this branch once via the
existing CI (push to the `copper-custom` branch, which already builds + runs the emulator test), confirm the
chat screen itself works end to end, then tackle direct-join as its own change with its own test pass.
