# HTTPS management pairing (0.5.8)

The management listener now accepts TLS 1.2 with authenticated ECDHE_RSA/AES-GCM only. It has no HTTP fallback. The old bearer key rotates once on the first 0.5.8 startup; replace your old bookmark with the new HTTPS link shown on the frame. App upgrades preserve the device certificate and key. Uninstalling/resetting the app loses that identity and requires new pairing.

## Establish trust before sending credentials

Tap the slideshow for controls using your paired Bluetooth mouse. Read the new HTTPS link and certificate SHA-256 fingerprint directly from the frame. On a browser's first certificate warning, inspect the certificate and compare its SHA-256 fingerprint with the frame before creating a site exception. If the browser does not support inspecting and remembering a verified exception, use the USB certificate setup on Windows. Do not blindly bypass warnings or use an HTTP download to obtain a trusted certificate.

The Windows installer obtains `management-cert.der` over authorized ADB and saves it as `local/frame-management.cer`. It prints the SHA-256 fingerprint for comparison with the frame and asks you to type `TRUST` before importing this device certificate into your Windows user's trusted root store. This grants trust to a certificate for this device; do it only on your own trusted computer. It prints the command to remove the certificate later. Declining leaves browser pairing manual. This is per browser/device; installing a Windows certificate does not automatically pair your phone.

The certificate includes the persistent `framerevive-xxxxxxxx.local` hostname, localhost, 127.0.0.1, and the frame's IP at certificate creation. Use the hostname for Wi-Fi and `https://127.0.0.1:18765/` for the installer's authorized USB forwarding. A later IP address may not match the certificate: use the stable hostname or USB, rather than ignoring a name mismatch. Networks without mDNS may require hostname/DNS configuration. Do not regenerate certificates merely because the IP changes.

## After upgrading through the old webpage

Android still requires installation approval on the frame. Once 0.5.8 starts, the old page disconnects. Read and verify the new HTTPS address and fingerprint on the frame, or reconnect USB and run the new installer. No HTTP redirect is provided because that would put the new key back on an unencrypted connection. Existing photos, album selection and schedule remain in app data.

Keep the full controls URL, album link and exported cookies private. Do not expose TCP 8765 to the internet. HTTPS protects transit after correct pairing; it does not repair unsupported Android/Lenovo firmware or protect a compromised client computer. Shared-album mode avoids account-cookie imports.

Request handling has four bounded slots, five seconds total for TLS handshake and headers, a 32 KiB total header budget, and two minutes maximum for an authenticated body/response. Slow clients are closed and overload is rejected; callbacks remain serialized. These limits deliberately reject incomplete uploads on very slow networks.
