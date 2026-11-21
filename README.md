# MM4 Save Data Editor

Native Android application for browsing and editing Motorsport Manager 4 career save files.

## Included features

- Import a save through Android's system document picker.
- Decode the observed MM4 container profile: 24-byte little-endian header, Save Info LZ4 block, and Game Data LZ4 block.
- Browse nested objects, arrays, and primitive values in both data sections.
- Search field paths and serialized primitive values.
- Replace JSON values while preserving their JSON types.
- Add object properties and array items.
- Delete properties and array items with confirmation.
- Export to a separate `.msv` file through Android's system document picker.
- Decode the generated output again and compare both JSON trees before writing.
- Preserve unknown JSON fields that are not directly understood by the editor.

## Observed container profile

The current decoder is based on the supplied `SlotTwo.msv` sample:

- Magic: `0x99131871`
- Container version: `40`
- Header: six little-endian Int32 values (24 bytes): magic, version, compressed and plain lengths for Save Info, then compressed and plain lengths for Game Data.
- Payload: LZ4 Save Info block followed by LZ4 Game Data block.

This is a sample-derived profile, not proof that every MM4 version or platform uses the same format. Unknown magic/version values and invalid lengths are rejected rather than guessed.

## Build and signing

GitHub Actions runs unit tests, Android release lint, and builds both APK variants.

- **Debug APK** is signed with the Android debug key and intended for development/testing only.
- **Release APK** must be signed with the private release keystore configured in GitHub Actions secrets:
  - `KEYSTORE_BASE64`
  - `KEYSTORE_PASSWORD`
  - `KEY_ALIAS`
  - `KEY_PASSWORD`

The release workflow fails instead of silently falling back to a debug key. It verifies the APK signature and checks that the signing certificate matches the configured keystore. Never commit or share the keystore or passwords.

## Local build

Requirements: JDK 17, Android SDK 35, Gradle 8.9.

The release build requires these environment variables:

- `MM4_KEYSTORE_PATH`
- `MM4_KEYSTORE_PASSWORD`
- `MM4_KEY_ALIAS`
- `MM4_KEY_PASSWORD`

Commands:

```bash
gradle testDebugUnitTest
gradle lintRelease
gradle assembleDebug assembleRelease
```

## Validation boundaries

Automated codec tests and successful APK signing do not prove that Motorsport Manager 4 accepts every edited value. The editor currently provides structural JSON editing; it does not implement game-specific semantic validation for every field.

Before treating a build as a fully game-validated release:

1. Keep the original career save untouched and make a duplicate.
2. Open the duplicate in the editor, make a small reversible change, and export to a new file.
3. Load the exported copy in Motorsport Manager 4.
4. Confirm the career loads and the edited value behaves as expected.
5. Repeat with representative Save Info and Game Data fields.

Do not overwrite the original save. Compatibility with other MM4 versions remains unverified until tested with samples from those versions.
