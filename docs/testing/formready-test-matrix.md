# FormReady test matrix

| Area | Phase 0 automated evidence | Later required evidence |
|---|---|---|
| Domain | Job transitions and validation aggregation unit tests | Units, geometry, solver, preset migrations |
| Data | Room schema exported; compilation verifies queries | DAO, migration, process recreation tests |
| Processing | Staging limit constant and worker compilation | URI providers, cancellation, low storage, corrupt input, output reopen |
| UI | Compose debug assembly and physical-device Home-to-Settings smoke test | 200% font, TalkBack, RTL, Hindi, rotation |
| Privacy | Manifest audit; backup/cleartext disabled | File cleanup, metadata removal, diagnostics redaction |
| Build | Debug assembly, unit tests, lint, release/R8 bundle | Signed AAB validation, 16 KB native audit |

## Required device matrix before release

- API 24 compatibility device/emulator.
- API 37 behaviour device/emulator.
- Arm64 Android 14+ reference profile with 4 GB RAM and constrained storage.
- Phone, 7-inch tablet, 10-inch tablet, landscape, and foldable layouts.

Only deterministic synthetic fixtures may be used. Release evidence must record fixture hashes, device/API/RAM, cold/warm state, command, elapsed time, and pass/fail threshold.
