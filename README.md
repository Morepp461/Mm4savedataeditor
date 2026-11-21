# MM4 Save Data Editor

Android application for browsing and editing Motorsport Manager 4 career save files.

## Features in this build

- Import a save through Android's system file picker.
- Read the observed MM4 container profile: 24-byte little-endian header, Save Info LZ4 block, Game Data LZ4 block.
- Browse nested objects, arrays, and primitive values in both Save Info and Game Data.
- Search paths and serialized values.
- Edit any existing node by replacing its JSON value while preserving JSON types.
- Add properties to objects and append items to arrays.
- Delete an existing property or array item with a confirmation step.
- Export to a new `.msv` file through Android's system document picker.
- Decode the generated output again before writing it, to verify container round-trip integrity.
- Keep unknown fields in the loaded JSON tree rather than dropping fields the editor does not understand.

## Observed save container profile

The profile below was inspected from the supplied `SlotTwo.msv` sample:

- Magic bits: `0x99131871`
- Container version: `40`
- Header: six little-endian Int32 values (24 bytes): magic, version, info compressed length, info plain length, data compressed length, data plain length.
- Payload: LZ4 Save Info block followed by LZ4 Game Data block.

This is a sample-derived profile, not proof that every MM4 release or platform uses the same format. The decoder rejects unknown magic/version values instead of guessing.

## Build outputs

GitHub Actions runs JVM codec tests and builds two APK variants:

- **Debug APK**: signed with the Android debug key and installable for device testing.
- **Release APK**: unsigned release artifact. It must be signed with the owner's private release key before Play Store distribution or a public release.

The workflow uploads both artifacts to its GitHub Actions run.

## Build locally

Requirements: JDK 17, Android SDK 35, Gradle 8.9.

```bash
gradle testDebugUnitTest
gradle assembleDebug assembleRelease
```

## Validation status and limitations

- Codec unit tests cover JSON round-trip, unknown magic, and truncated input.
- The editor has structural JSON operations but does not yet have game-specific semantic rules for every MM4 field.
- A successful compression/decompression round-trip does **not** prove that the game accepts every edited value.
- The supplied sample profile needs real in-game validation with a duplicate career save before claiming full compatibility.
- Keep the original save untouched. Export to a separate file and test that copy in Motorsport Manager 4.
- A signed public release requires a release keystore controlled and backed up by the project owner. Never commit that private key to this repository.


## Career editor improvements (1.2.0)
- Driver profiles are collected from all fully stored Driver entities throughout the save graph, including embedded race/standings records.
- Team catalog resolves team references from `teamManager.m_Teams`; car editor exposes each team's stored car performance data.
- Headquarters reads the actual `Key` / `Value` records and edits status, unlock timer and upgrade path with typed controls.
- Advanced data exposes searchable typed primitive fields instead of requiring raw JSON input.
- Round-trip validation compares canonical JSON so key ordering and numeric wrapper differences do not falsely reject a valid export.
