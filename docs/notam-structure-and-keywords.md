# NOTAM Structure & Keywords — Parsing & Scoring (v1)

## 1) Fields we will parse (FAA GeoJSON)
- `properties.coreNOTAMData.notam.id` – ID (stable key)
- `...number` – NOTAM number shown to users (e.g., "10/182")
- `...type` – N (new), R (replaces), C (cancelled)
- `...classification` – DOM/INTL/FDC/MIL/LMIL
- `...icaoLocation` – e.g., KDFW
- `...location` – short code (e.g., DFW)
- `...issued` – issued timestamp
- `...effectiveStart`, `...effectiveEnd` – time window (or "PERM")
- `...text` – human-readable NOTAM
- `geometry` – Point/Polygon of area

## 2) Normalization
- Uppercase text; collapse whitespace.
- Preserve runway/taxiway tokens (`RWY`, `TWY`, `13R/31L`).
- Prefer `effectiveStart`/`effectiveEnd`. Treat `"PERM"` as open-ended.

## 3) Keyword sets
- **Runway/Surface**: RWY, TWY, APRON, RAMP, STAND, GATE; CLSD, WIP, REDUCED, BRAKING, FICON
- **Lighting/NAVAIDs**: LGT, PAPI, VASI, REIL, MALSR, HIRL, MIRL; U/S, INOP, UNAVBL, OTS; ILS, LOC, GS, VOR, NDB, RNAV, RNP, LPV
- **Airspace/Restrictions**: TFR, UAS/DRONE, LASER, RSTR, MOA, PARACHUTE
- **Obstacles/Hazards**: OBST, CRANE, TOWER, BIRD, WILDLIFE, SMOKE
- **Meta/Time**: NOTAMR, NOTAMC, H24, DLY, SR/SS, EST

## 4) Pattern snippets (first pass)
- RWY: `\bRWY\s*(\d{1,2}[LRC]?)(?:/\d{1,2}[LRC]?)?\b`
- TWY: `\bTWY\s*[A-Z0-9]+(?:\s*-\s*[A-Z0-9]+)*\b`
- Closed/U/S: `\b(CLSD|CLOSED|U/S|UNSERVICEABLE|INOP|UNAVBL|OTS)\b`
- Lighting: `\b(PAPI|VASI|REIL|MALS[R]?|HIRL|MIRL|SFL)\b`
- Obstacle: `\b(OBST|CRANE|TOWER)\b.+?\b(\d{3,4}(\.\d+)?FT)\b`
- Airspace: `\b(TFR|UAS|DRONE|LASER|RSTR|MOA|PARACHUTE)\b`
- Time tokens: `\b(20)?\d{2}(0[1-9]|1[0-2])([0-2]\d|3[01])([01]\d|2[0-3])([0-5]\d)\b`

## 5) Scoring cues (v1)
- RWY closed → +10
- RWY lighting U/S → +8
- TWY closed → +5
- Airspace restriction → +6
- Obstacle near field → +5
- Active now → ×1.2 multiplier
- Type = R/C → downgrade or drop

## 6) Examples (annotated)
- *Example 1*: “RWY 13R/31L CLSD …” → RWY=13R/31L, CLOSED → +10
- *Example 2*: “PAPI RWY 17 U/S …” → RWY=17, PAPI U/S → +8
- *Example 3*: “CRANE … 1453FT AGL …” → Obstacle → +5

## 7) Next steps
- Implement parser that:
  - Normalizes text
  - Extracts runway/taxiway mentions, closure/U/S flags, airspace/obstacle tokens
  - Computes score using the cues above
- Add unit tests with the examples above
