# Maintainer signing and release checklist

## Preserve the current signing identity

The published 0.5.24 alpha APK uses the local Android debug signing identity. Its public certificate SHA-256 is:

`E9:A7:C6:2D:57:AD:E1:23:46:89:9B:9C:4E:2E:8B:6C:E6:92:60:66:E9:BF:F8:58:AA:DB:6C:E4:DF:A9:60:6F`

The fingerprint is public information. The matching private keystore must remain private. Current installs and the webpage updater require the matching signing identity. A fresh CI runner's debug key will not match. Do not delete or regenerate the maintainer key, switch keys casually, or tell users to uninstall to work around an update mismatch.

Make an encrypted offline backup of the existing keystore and preserve its alias/password securely, outside the repository, release ZIPs, public cloud links and CI artifacts. Confirm a restored copy has the same certificate fingerprint before depending on it. This checklist does not create a backup or rotate the key.

Before a stable release, prepare a non-debuggable release build with a deliberate signing/migration plan that preserves existing users' update path. Changing the keystore password does not by itself change the certificate; changing the private key does. Do not commit signing passwords in Gradle files.

## Publication

1. Build from the intended reviewed commit. Run the Android build, tests, lint and secret checks.
2. Verify the final distributed APK's package, version and signing certificate against the maintained identity; test an in-place update on the frame.
3. Package only the APK, reviewed installer scripts and public instructions. Never include a keystore, account export, real catalog, photos, TLS private key or private controls link.
4. Publish SHA-256 hashes of the APK and installer ZIP. Where GitHub displays asset digests, compare them with the locally verified files.
5. Keep alpha limitations and installation changes visible in the release notes.

The pull-request workflow deliberately uses no signing secrets and publishes no APK artifacts. Test-build success does not make a CI APK suitable for an existing installation.
