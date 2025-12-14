# AnkiDroid Companion

Original author: [unalkalkan](https://github.com/unalkalkan)  
Current maintainer (this fork): hsk  
Thanks to the original author and OpenAI (Codex) for their work and tooling support.
Current version: 0.1.1

![](.github/output.gif)

## Features (current fork)
- **Notification + Widget review**: Study cards via persistent notification and a home-screen widget; both respect deck/template filters and field mode (front/back/both).
- **Widget modes**: Queue mode follows Anki’s queue with 4 feedback buttons; Random mode shows cached random cards with Prev/Next controls. The top-right refresh reloads the current card (queue = latest top card; random = same random card refreshed).
- **Deck-aware template filtering**: Switching decks auto-selects all templates of that deck; you can uncheck templates per deck.
- **Sanitized display**: Strips HTML tags, links, images, sound placeholders; keeps basic styling (bold/italic/sub/sup), adds readable prefixes for common sections, trims extra blanks and double newlines.
- **Configurable limits**: Set max lines for notification content (1–50) and widget rotation interval (minutes). Widget auto-rotates cards on a schedule and on manual refresh. Narrow widgets auto-shortens button labels.
- **Notification toggle**: Turn notifications on/off. OFF = widget only; ON = notification + widget.
- **Lock-screen visibility**: Notifications are public visibility for lock/AOD where supported by the system.

## Getting Started
1. Install AnkiDroid.
2. Install AnkiDroid Companion (this fork) from your build.
3. Grant AnkiDroid API permission and notification permission when prompted.
4. Open the app, pick a deck, adjust field mode, templates, notification toggle, max lines, and widget rotation interval if needed.
5. Tap “Refresh” to fetch a card, push notification (if enabled), and refresh the widget immediately. Widgets also rotate on the configured schedule.

## Notes & Limits
- HTML/media: images/audio/links are removed from notification/widget text; only basic inline styling is kept.
- Widget rotation uses WorkManager periodic jobs (system may defer when Doze/background limits apply).
- API scope: depends on AnkiDroid’s exposed API; audio/image playback via API is currently disabled in this fork.

## Contributing
PRs and issues are welcome. Please describe deck/template cases and expected display if you report rendering issues.

## License
MIT License (see LICENSE).
