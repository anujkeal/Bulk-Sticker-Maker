# Bulk Sticker Maker

Create personal WhatsApp sticker packs from photos on your Android phone.

Bulk Sticker Maker is an offline-first Android app written in Kotlin. Select up to 30 images, convert them into WhatsApp-compatible sticker files, and send the pack to WhatsApp for import.

> **Project status:** Early development. APK builds are available through GitHub Actions. WhatsApp import should be tested on your device; compatibility can depend on the installed WhatsApp version.

## Features

- Select **3–30 photos** from your gallery in one session.
- Preview selected images before creating the pack.
- Enter a name for each sticker pack.
- Resize images to **512 × 512 px**, preserving the original aspect ratio.
- Compress stickers as WebP files to meet the **100 KB per-sticker** limit.
- Generate a tray icon from the first sticker.
- Create packs offline; photos are processed and stored locally by the app.
- Assign a unique pack ID to each newly created pack, so different pack names can be added as separate packs rather than always updating the same pack.
- Hand the generated pack to WhatsApp or WhatsApp Business through Android's sticker-pack integration.

### Image handling

The app keeps the full image visible. It does not remove backgrounds or intentionally crop the image. Images are fitted inside a 512 × 512 transparent canvas; empty space may remain around images that are not square.

## Download the APK

The latest successful GitHub Actions build is:

[**Download Bulk Sticker Maker APK (GitHub Actions)**](https://github.com/anujkeal/Bulk-Sticker-Maker/actions/runs/43759015645)

1. Open the build page.
2. Scroll to **Artifacts**.
3. Download **Bulk-Sticker-Maker-APK**.
4. Extract the downloaded ZIP file.
5. Install `app-debug.apk` on your Android phone. Android may ask you to allow installation from that source.

The artifact is a debug APK, not a Play Store release. GitHub Actions artifacts are retained for a limited time.

## How to use

1. Open Bulk Sticker Maker.
2. Enter a sticker pack name.
3. Tap **Select up to 30 photos** and choose at least 3 images.
4. Tap **Create Sticker Pack** and wait while the images are processed.
5. When Android opens the WhatsApp add-pack flow, review the pack and choose **Add**.
6. Repeat with another name to create another separate pack.

If WhatsApp displays **Update** instead of **Add**, check that you installed the latest APK containing the unique-pack-ID change. The app's visible pack name alone does not determine whether WhatsApp treats a pack as new.

## Build from source

### Requirements

- JDK 17
- Gradle 8.9
- Android SDK Platform 35
- Android SDK Build-Tools 35.0.0

### Local build

From the repository root:

```bash
gradle assembleDebug
```

The APK is generated at:

```
app/build/outputs/apk/debug/app-debug.apk
```

### Build with GitHub Actions

The workflow at [`.github/workflows/build-apk.yml`](.github/workflows/build-apk.yml) builds the debug APK whenever code is pushed to `main`, or when manually started using **Actions → Build Bulk Sticker Maker APK → Run workflow**.

The workflow uploads the APK as an artifact named `Bulk-Sticker-Maker-APK`.

## Technical details

| Item | Value |
|---|---|
| Platform | Android |
| Language | Kotlin |
| Application ID | `com.keal.bulkstickermaker` |
| Minimum Android version | Android 6.0 (API 23) |
| Compile / target SDK | 35 |
| Java / Kotlin JVM target | 17 |
| Sticker image format | WebP |
| Sticker dimensions | 512 × 512 px |
| Maximum sticker file size | 100 KB |
| Maximum stickers per pack | 30 |

The app exposes a content provider for WhatsApp to read sticker-pack metadata, sticker lists, sticker images, and the tray icon.

## Troubleshooting

- **The button stays on “Creating stickers…”:** keep the app open and allow the conversion to finish. Large images can take longer to process.
- **WhatsApp does not open:** make sure WhatsApp or WhatsApp Business is installed and updated.
- **WhatsApp shows “Update” rather than “Add”:** install the latest build from the Actions link above. Older builds used a fixed pack ID.
- **WhatsApp rejects the pack:** confirm that at least 3 stickers were selected and that the generated files meet the size limit. WhatsApp may also enforce its own compatibility checks.
- **The APK does not install:** uninstall an older conflicting build only if necessary, then install the downloaded APK again. Uninstalling the app clears its locally stored packs.

## Privacy

Sticker images are processed locally on the device. The app is designed to work offline and does not upload selected photos to a server.

## License

No license has been added to this repository yet. Unless a license is added, the source remains subject to the default copyright rules; do not assume it is open for reuse or redistribution.
