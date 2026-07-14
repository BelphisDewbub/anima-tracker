# Anima Tracker

Shows a small draggable status indicator (same style as RuneLite's potion/prayer timer buff icons)
for your Farming Guild anima patch (Iasor, Kronos, or Attas) whenever you're near any farming
patch — since the anima buff is global, this is a handy reminder while doing farm runs anywhere,
not just at the Farming Guild. Drag it anywhere on screen like any other buff indicator; RuneLite
remembers where you put it.

- **Gray** — nothing planted
- **Green** — healthy
- **Yellow** — withering, or estimated to die soon
- **Red** — dead

Hover the icon for the plant's name and estimated time remaining.

The patch's status is only actually broadcast to the client while you're near it in the Farming
Guild - everywhere else the plugin shows the last reading it confirmed there (remembered across
sessions), and the tooltip notes its age, e.g. "as of 3h ago", so it's clear when it's not live.

## Settings

- **Detection radius** — how close (in tiles) you need to be to a farming patch for the frame to appear
- **Warn before estimated death** — minutes of advance warning before the plant is estimated to die
- **Assumed anima lifespan** — assumed total lifespan in hours, used only for the estimate

## Credits

Dedicated to OneNutNick, whose idea this plugin is based on.
