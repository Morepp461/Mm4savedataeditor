# MM4 Save Data Editor

A personal Android editor for Motorsport Manager 4 career saves, with a game-oriented interface and typed controls. The app is designed to preserve unknown save data rather than requiring users to understand or manually edit serialized structures.

## Current editor workspaces

- **Drivers:** discover the career roster through the save's driver references; edit personal profile, driver attributes, potential/modifiers, salary and contract fields.
- **Staff:** discover stored staff and applicants by their actual profile/role structures; edit personal profile, role attributes, salary and contract data.
- **Teams:** browse all teams referenced by the career; expose team name, short name, reputation and financial fields only.
- **Cars:** expose each team's car attributes through dedicated attribute controls; unrelated car fields are not presented as editable.
- **Finance:** view and edit the player's team balance and inspect transaction history.
- **Headquarters:** show facility status, unlock timers and upgrade paths using readable labels.
- **World & Rules:** inspect supported career groups such as championships, component marketplace, suppliers, research, sessions, circuits and weather.
- **God Mode:** provide explicit save-backed actions for balance, HQ, research/debug and supplier unlocks.
- **Save Info / Advanced:** provide typed controls for supported primitive values. No arbitrary JSON text entry is required for normal editing.

The editor deliberately limits the Teams, Cars, Drivers and Staff workspaces to their intended game fields. It does not expose arbitrary object/array creation or deletion as normal career-editing features.

## Save container profile

The current codec supports the observed sample profile from `SlotTwo.msv`:

- Magic: `0x99131871`
- Container version: `40`
- Header: six little-endian Int32 values (24 bytes)
- Payload: LZ4 Save Info block followed by LZ4 Game Data block

The decoder rejects unknown magic/version values and invalid block lengths rather than guessing. This is a sample-derived profile, not proof that every MM4 release or platform uses the same format.

## Export safety

- Import uses Android's system file picker.
- Export always targets a separate document selected through Android's system document picker.
- Export re-decodes the generated container and compares both JSON trees before writing.
- Explicit JSON null fields and unknown fields are preserved by the codec.
- The app refuses to overwrite the exact source URI selected for import.
- Keep the original save untouched and test an exported duplicate in Motorsport Manager 4.

A successful codec round-trip proves container integrity only. It does **not** prove that every edited game field is semantically accepted by the game.

## Build and CI

GitHub Actions runs:

1. JVM unit tests
2. Android lint
3. Debug APK build
4. APK package identity verification
5. Artifact upload

The workflow uses JDK 17, Gradle 8.9 and Android SDK 35. The installable personal-use debug APK is uploaded to the successful Actions run.

## Validation boundary

Automated tests cover codec round-trip behavior, explicit null preservation, malformed/truncated containers, JSON path mutation and the current team/staff/car field-visibility policy.

The exact supplied `SlotTwo.msv` must still be tested on-device after export. Only a duplicate career save should be used for this test. A passing CI build is not a substitute for loading the exported file in Motorsport Manager 4.

## App version

- Version: **1.3.1**
- Application ID: `com.morepp.mm4savedataeditor`
- Minimum Android: API 26
- Target Android: API 35

The debug APK is for personal sideload testing. A public Play Store release requires a private release keystore controlled by the project owner. Never commit the private keystore or its passwords to this repository.
