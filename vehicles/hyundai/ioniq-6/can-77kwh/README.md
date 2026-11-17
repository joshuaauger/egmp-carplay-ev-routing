# Hyundai Ioniq 6 Canada (77.4 kWh): researched, uncalibrated values

For CEEV CAN 260903. The car's native libraries match the MD5s this mod requires (libiap2session.so fc7cb675…, libiap2client.so 153a97fd…).

The numbers are the Ioniq 6 US Long Range profile (CCS1 and J1772 are the same in Canada), which is the Ioniq 5 profile with these changes:

| Field | Value | Basis |
|---|---|---|
| `maximum_energy_wh` | 74000 | Usable capacity of the 77.4 kWh pack |
| Charging curve | 205 kW at 0 %, peak 233 kW at 45 %, 12 kW at 100 % | evkx.net Ioniq 6 Long Range curve, on a 74 kWh axis |
| Consumption values | Ioniq 5 values × 0.87 | About 280 Wh/mi at 70 mph against about 320 for the Ioniq 5 AWD |
| Altitude gain / loss | 6.29 / 4.24 Wh per m | Ioniq 5 × 0.98 for the lighter car |

Not checked against a Canadian car. A Standard Range (53 kWh) Ioniq 6 needs its own capacity and charging curve: don't use this file for one.
