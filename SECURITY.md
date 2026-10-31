# Security policy

## Supported version and private reports

Security fixes target the latest FrameRevive alpha on `main`. Older versions should update while preserving app data. This is a volunteer project with no guaranteed response time.

Report a vulnerability through GitHub's **Security → Report a vulnerability** for this repository when private reporting is enabled: https://github.com/letsteststuff/FrameRevive/security/advisories/new

Do not put exploitable details, credentials, private album links, full management URLs, frame photographs, or app-data backups in public issues. If private reporting is unavailable, open an issue requesting a private reporting channel without disclosing the vulnerability or secrets. Include the app version, frame model/Android version, impact and minimal reproduction in the private report. Use demonstration data.

## System and scope

FrameRevive is an Android 10 app for the Lenovo CD-3L501F. Its native slideshow reads Google Photos shared albums, caches images and serves an authenticated HTTPS controls page on the owner's LAN. The Windows installer uses authorized ADB to install the app and reversibly change the launcher. New Google session imports are retired; existing encrypted sessions remain a compatibility path.

Scope includes the Android app, management server, URL/HTTP handling, session compatibility, image/catalog storage, APK updater, installer, repository workflows and release packaging.

## Trust boundaries and required properties

- LAN peers and browser requests are untrusted. Authenticate before disclosing private state or applying mutations; reject unauthorized cross-origin writes and bound request sizes, concurrency and lifetime.
- Google responses, album metadata, remote URLs, uploaded APKs and owner-supplied form values are untrusted. Escape displayed text, constrain redirects/hosts and validate input before replacing working data.
- Keep account cookies, signing/private TLS keys, controls tokens, photo URLs, catalogs and cached images out of Git, release bundles, public logs and copied diagnostics. Public signing certificates and demonstration data are not private keys.
- A failed catalog load must preserve the last complete selected album. Retired session-import actions must not replace credentials or change the selected album.
- APK updates must validate package identity, compatible Android version, non-decreasing version and signing identity; Android confirmation is required. No silently trusted arbitrary APKs.
- Preserve per-frame HTTPS identity across normal updates. Certificate pairing requires verification through the frame or authorized USB; a value received through an unverified connection is not sufficient proof.
- Treat pull-request code as untrusted. CI must have read-only permissions, no release signing material or device/account credentials, and no automatic publishing of test APKs. Keep workflow dependencies pinned and reviewed.

These are requirements for review, not claims that every implementation has been proven correct. Bypasses with realistic impact are reportable even when the service is LAN-only or another control limits reachability. No vulnerability classes are excluded by this policy.

## Known limitations

The controls page is intended for a trusted home network and must not be exposed through router port forwarding. Anyone with an album sharing link can view the shared album. Google Photos web access is unofficial and can change. The Lenovo firmware and Android version are unsupported upstream; this project cannot supply OS security updates. Current alpha APKs are debuggable; access through authorized ADB can expose app-private data. These limitations do not waive reports of additional application vulnerabilities.
