# Game device test results

## 2026-07-29 Phase 5.1

- Device: SOV41
- Android: 11
- App: 1.0.0 (versionCode 1)
- Starting implementation commit: `a643e47`
- Physical walking test: not performed

### Scenario A: basic match

Result: **Fail; rerun required**

Initial state after the first Debug reset:

- Daily activity: 0 records
- Hourly activity: 0 records
- DailyMatch: 0 records
- Rating: 1,000 / Bronze III
- Wins and win streak: 0 / 0
- Duplicate running service: not observed

Operations and database result:

- Created one DailyMatch.
- Added Counter +5,000 exactly once from the zero-step state.
- Set the NPC target to 4,000 steps.
- Finalized the match once.
- Daily steps: 5,000
- DailyMatch: one record, `WIN`
- Eligible steps: 5,000
- Rating: 1,000 -> 1,025
- Wins and current win streak: 1 / 1
- A second finalization attempt did not add another rating change, win, or streak.

UI result:

- The normal Home screen still displayed 0 steps although the Debug daily record contained
  5,000 steps.
- Root cause: the initial Debug clock used `Etc/GMT-9`, while the normal Home path observes
  `ZoneId.systemDefault()`.
- Fix commit: `29c3383`.
- Scenario A must be reset and rerun after the Debug-data isolation issue below is fixed.

### Blocking Debug-data isolation issue

The current Debug operations update the normal `game_player_profile` row. Generated match
notifications also use a normal daily-match source ID rather than a `debug-` source ID.
Consequently, the current Debug reset cannot restore rating/win state or reliably remove all
events while preserving normal user data. Scenarios A-E therefore cannot be isolated safely.

The following initial manual-test blocker was fixed separately in `baea337`:

- Debug reset and maintenance actions were not reachable from the Debug UI.
- Reset immediately recreated a DailyMatch, preventing the required zero-match initial state.
- The required 4,000-step NPC target was not available.

### Unverified items

- Scenario A restart and Settings force-stop persistence after a passing rerun
- Scenarios B-E
- Achievement UI checks
- 30,000-step cap UI checks
- Crash-triggered process recreation checks for Phase 5.1 game data
- Final SOV41 instrumentation/DAO/migration/Compose UI gate
- Final clean/build/release/lint/non-contamination gates
- Main merge and push
