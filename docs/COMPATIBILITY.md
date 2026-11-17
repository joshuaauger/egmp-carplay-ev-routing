# Compatibility

One mod ZIP supports the configurations in `release.json`: NEEV23 USA 260507 (tested Ioniq 5 integration), NEEV USA 260507 (2022 Ioniq 5, newly accepted; on-car validation pending), CVEV USA 260507 (untested EV6), or CEEV CAN 260903 (Ioniq 6, Canada: values researched, nothing tested on a car). EV6 numerical values are copied from Ioniq 5 and likely inaccurate. All listed configurations currently use a 77.4 kWh battery model.

Required on-disk MD5s:

| Library | MD5 |
|---|---|
| libiap2session.so | fc7cb675b46a44bd893a1edc054c5e71 |
| libiap2client.so | 153a97fd6d2c62dc58286aa47c35feb3 |

HMG-API must resolve its map on the actual car. Our startup repeats native-hash and required-point checks; unknown native layouts remain inactive. HMG-API supporting a vehicle/firmware does not establish this mod's native ABI or contract accuracy on it. 260903 is accepted only for CEEV CAN, whose two native libraries have the MD5s above.

On a native-library mismatch, the mod remains inactive and logs both detected MD5 hashes (`unavailable` if a library cannot be read). The hash guard runs at startup; accepting a model identifier does not bypass it.
