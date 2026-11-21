# MM4 Save Data Editor

An Android career-save editor for Motorsport Manager 4. It reads the supported v40 .msv container, presents game-oriented editing workspaces, and writes a separately selected save file after a codec round-trip integrity check.

## Workspaces

- **Overview:** career summary and shortcuts.
- **Drivers:** roster resolved through save references; personal profile, attributes, potential/modifiers, salary and contract.
- **Staff:** staff and applicant records discovered from the serialized role schema; profile, role attributes, salary and contract.
- **Teams:** team identity, reputation and top-level financial data.
- **Cars:** per-team car performance attributes.
- **Finance:** team balance and transaction history.
- **Headquarters:** facility upgrade state, timers, and upgrade path.
- **World:** discovered championship, marketplace, supplier, research, session, circuit and weather groups.
- **Save Info / Advanced:** typed primitive editing for supported values.
- **God Mode:** explicit save-backed bulk actions.

## Editing safeguards and usability

- Team and staff workspaces use category-specific field allowlists. Nested car, driver and staff attributes are not exposed as team financial fields.
- Car attributes are edited through dedicated attribute cards.
- Undo/redo supports the last 50 primitive field edits. Bulk God Mode actions clear the edit history to prevent stale-path undo.
- Numeric edits use decimal-safe arithmetic and preserve large integer precision.
- Workspace changes use short fade/slide transitions.
- The app includes the persistent **Created by : Morepp** watermark.

## Container profile

The codec supports the observed MM4 sample profile:

- Magic: 0x99131871
- Container version: 40
- Six little-endian Int32 header values (24 bytes)
- LZ4-compressed Save Info and Game Data blocks

Unknown magic/version values, invalid lengths, truncated payloads, and oversized input are rejected. This profile is sample-derived and is not a claim of compatibility with every future MM4 save format.

## Export safety

- Import uses Android's system document picker.
- Export uses Android's create-document picker and a destination separate from the source URI.
- Before writing, the generated container is decoded again and both JSON trees are compared.
- Explicit null fields and unknown fields are preserved.
- A failed validation prevents output from being written.
- Keep the original save unchanged and test an exported duplicate in Motorsport Manager 4.

The project owner has confirmed that an exported edited save from a recent build loads and is playable in MM4. This is real-game validation for that tested save/build, not a guarantee for every possible field or career.

## Build and CI

GitHub Actions runs JVM unit tests, Android lint, debug APK packaging, package identity verification, and artifact upload. The personal-use APK is debug-signed by the Android build pipeline.

## Version

- Version: **1.4.0**
- Version code: **5**
- Application ID: com.morepp.mm4savedataeditor
- Minimum Android: API 26
- Target Android: API 35
- JDK 17 / Gradle 8.9 / Android SDK 35

A public Play Store release requires a private release keystore controlled by the project owner. Never commit private signing material or passwords to this repository.
