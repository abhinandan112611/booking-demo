# ADDS data sources and licences

## Stations (the only REAL data in ADDS)

| Item | Value |
|---|---|
| File in this repo | `data/stations.json` |
| Source | DataMeet "railways" repository, https://github.com/datameet/railways |
| Raw URL downloaded | https://raw.githubusercontent.com/datameet/railways/master/stations.json |
| Downloaded | 2026-11-25 17:44:21 UTC, HTTP 200 |
| Size | 1,864,683 bytes |
| SHA-256 | 9BD5E1DA3A859E5359A95F40B6009AA468EFB177DA4FAAC57FFDCD7DAEB4DF18 |
| Last upstream commit touching the file | 21aa4a0, 2016-08-08 |
| Licence | CC0 (public domain dedication), as stated in the repository README under "License": https://wiki.creativecommons.org/wiki/CC0 |
| Licence check note | GitHub's licence API reported no separate LICENSE file; the CC0 statement comes from the README. |
| Format | GeoJSON FeatureCollection, 8,990 features; properties: code, name, zone, state, address; geometry is a Point or null |

The data is from 2016 and is not an official or current list. ADDS uses only station codes, names and zones,
after cleaning (rules and counts are logged in evidence/ during Phase 3).

## Everything else is SYNTHETIC

Trains, timetables, stops, distances, run dates, berth inventory, users, fares and the Tatkal share are generated
demo data. They do not describe real services.
