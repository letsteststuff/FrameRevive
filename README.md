# FrameRevive

> Reviving discontinued Android digital photo frames with a modern, local-first platform.

## Overview

FrameRevive is an open-source project that restores unsupported Android-based digital photo frames by replacing discontinued cloud services with a modern, privacy-conscious software platform.

Instead of throwing away perfectly good hardware, FrameRevive gives it a second life.

Our goal is simple:

- Turn it on.
- Display beautiful photos.
- Stay updated automatically.
- Keep working for years to come.

No subscriptions.
No vendor lock-in.
No abandoned cloud service.

---

## Current Status

🚧 Prototype Development

Current hardware:

- Lenovo Smart Frame
- Model CD-3L501F
- Android 10

---

## Version 1.0 Goals

- Google Photos integration
- Multiple albums
- Local photo cache
- Fullscreen slideshow
- Sleep and wake scheduling
- Local web configuration
- Automatic startup after reboot
- One-click installer

---

## Technology Stack

### Android

- Kotlin
- Android 10+
- WebView

### Frontend

- React
- Vite
- TypeScript
- Tailwind CSS

### Configuration

- JSON
- SQLite (when appropriate)

---

## Current Architecture

```
React UI
      │
      ▼
 Android WebView
      │
      ▼
 Kotlin
      │
      ▼
 Android APIs
```

---

## Development Philosophy

FrameRevive is built incrementally.

Every milestone produces a working application.

We prioritize:

- Simplicity
- Reliability
- Maintainability
- Privacy
- Local-first operation

---

## Current Milestones

- ✅ Milestone 1 – Native Android application
- ✅ Milestone 2 – Fullscreen WebView
- 🚧 Milestone 3 – Android ↔ JavaScript bridge
- ⏳ Local configuration
- ⏳ Slideshow engine
- ⏳ Google Photos integration
- ⏳ Local image cache

---

## Supported Hardware

### Current

- Lenovo Smart Frame (CD-3L501F)

### Planned

- Android tablets
- Amazon Fire tablets
- Android TV
- Other Android-based digital photo frames

---

## Why?

Thousands of digital picture frames become electronic waste every year because manufacturers discontinue their cloud services.

The hardware still works.

FrameRevive exists to keep that hardware useful.

---

## Contributing

Documentation and contributions are welcome.

As hardware support expands, additional Android-based frames will be added.

---

## License

MIT License (coming soon)
