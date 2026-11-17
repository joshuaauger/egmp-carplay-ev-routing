# 0.4.4-ceev.3 — experimental fork build

- Logs why the CarPlay EV service failed to start (`CarPlayEV start failed: …`, `CarPlayEV native load failed: …`) as one readable line, ahead of the stack trace.

# 0.4.4-ceev.2 — experimental fork build

- Setting `carplay_start_session` (default on): CarPlayStartSession (iAP2 `0x4301`), ported from the Ioniq 6 CarPlay Ultra-Lite bundle's EV Routing. The identification lists `0x4301` sent and `0x4300` received, and when the phone sends CarPlayAvailability (`0x4300`) the car answers with its Wi-Fi, AirPlay identity (written by CarPlay Ultra-Lite) and the theme asset, so the iPhone fetches the theme. Done by patching the stock handler table and message-list lengths in place from `libcarplayev.so`: no second identification builder hook, so it works beside Wireless's.

# 0.4.4-ceev.1 — experimental fork build

- Adds the 2023+ Ioniq 6 in Canada (`CEEV.CAN`, firmware `260903`): a `ceev` part and an Ioniq 6 profile (77.4 kWh Long Range, researched values, uncalibrated; not checked on a car). Before this the car matched no part, so `/system/etc/carplay.ev-routing/profile.json` was never installed and the mod failed at startup with "file not found".
- Setting `carplay_theme`: names a CarPlay theme asset in the identification through the Wireless CarPlay mod's `registerTheme` (needs `wireless-carplay-aa` 2.2.0-rc.4 or later). The EV identity is unchanged by it.
- Based on the `codex/carplay-identity-api` branch (0.4.3 plus the Wireless identification API): no own `libiap2session` patch, so it works beside Wireless's.

# 0.4.3 — experimental

- Send vehicle status every ten seconds instead of every five seconds.
- Average valid auxiliary-power samples over one-minute windows, round the result to the nearest 25 W and publish it only at minute boundaries. Hold the profile default until the first complete window and retain the last published average through missing samples.
- Add deterministic regression coverage for window timing, rounding and missing samples.

# 0.4.2 — experimental

- Floor live battery state of charge to a whole percent before sending it to Apple Maps, matching Hyundai's EV app treatment of the half-percent `DISPLAY_SOC` signal.
- Add regression coverage for half-percent SOC values and invalid inputs.
- Native patch, energy models, auxiliary-power behavior and five-second update policy are unchanged.

# 0.4.1 — experimental

- Accept the 2022 US Ioniq 5 (`NEEV.USA`, firmware `260507`) with the existing 77.4 kWh profile; on-car validation pending.
- Include both detected native-library MD5 hashes in rejection logs; exact-hash guards remain unchanged.
- Native patch, energy models and five-second update policy are unchanged.

# 0.4.0 — experimental first release

- One signed native HMG-API mod download with automatic profile selection at Apply, requiring official beta6 or newer.
- Live SOC/range/auxiliary power and bundled vehicle contracts.
- Exact native-hash gates, HMG-API map checks, signed updates, and rotating logs. Optional NEMESIS wrappers remain available for testing.
- Ioniq 5 integration tested; EV6 profile copied and unvalidated.
- Known issue: route recalculation at the five-second status cadence remains under investigation.
