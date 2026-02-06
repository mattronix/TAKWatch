# TAKWatch

<img src="https://raw.githubusercontent.com/TDF-PL/TAKWatch-IQ/main/images/screenshot-2.jpeg" width="200" height="200">

## Description
TAKWatch is an ATAK plugin that communicates with Garmin devices running TAKWatch-IQ (https://github.com/TDF-PL/TAKWatch-IQ) application.

## Features
- Sending heart rate to ATAK
- Receiving waypoints from ATAK (persisted on the watch)
- Receiving markers from ATAK (not persisted on the watch)
- Triggering Emergency alert from watch (when SELECT button pressed 5 times rapidly)
- Creating vectors to markers
- Sending routes from ATAK to watch
- Sending chat messages
- Triggering ATAK wipe from watch (when BACK button pressed 5 times rapidly)

## Equipment supported
- epix™ (Gen 2) / quatix® 7 Sapphire
- epix™ Pro (Gen 2) 42mm
- epix™ Pro (Gen 2) 47mm
- epix™ Pro (Gen 2) 51mm
- Forerunner® 945 LTE
- Forerunner® 945
- Forerunner® 955 / Solar
- Forerunner® 965
- fēnix® 5 Plus
- fēnix® 5S Plus
- fēnix® 5X / tactix® Charlie
- fēnix® 5X Plus
- fēnix® 6 Pro / 6 Sapphire / 6 Pro Solar / 6 Pro Dual Power / quatix® 6
- fēnix® 6S Pro / 6S Sapphire / 6S Pro Solar / 6S Pro Dual Power
- fēnix® 6X Pro / 6X Sapphire / 6X Pro Solar / tactix® Delta Sapphire / Delta Solar / Delta Solar - Ballistics Edition / quatix® 6X / 6X Solar / 6X Dual Power
- fēnix® 7 / quatix® 7
- fēnix® 7 Pro
- fēnix® 7S Pro
- fēnix® 7S
- fēnix® 7X / tactix® 7 / quatix® 7X Solar / Enduro™ 2
- fēnix® 7X Pro

## Screenshots

<img src="https://raw.githubusercontent.com/TDF-PL/TAKWatch-IQ/main/images/screenshot-1.png" width="200" height="200">
<img src="https://raw.githubusercontent.com/TDF-PL/TAKWatch-IQ/main/images/screenshot-3.jpeg" width="200" height="200">


## Building from Source

### Prerequisites

- **Java 17** (e.g. `brew install openjdk@17` on macOS)
- **Android SDK** with `platforms;android-36`, `platforms;android-34`, and `build-tools;34.0.0`
- **ATAK CIV 5.6.0 SDK** — download from [tak.gov](https://tak.gov/)
- **Garmin Connect IQ SDK 8.4.1+** (for the watch app only)

### Setup

1. Extract the ATAK SDK into the project root:

   ```
   TAKWatch/
   └── ATAKSDK/
       └── ATAK-CIV-5.6.0.12-SDK/
           ├── atak-gradle-takdev.jar
           ├── main.jar
           ├── android_keystore
           └── ...
   ```

2. Create `local.properties` in the project root:

   ```properties
   sdk.dir=/path/to/Android/sdk
   takdev.plugin=/path/to/TAKWatch/ATAKSDK/ATAK-CIV-5.6.0.12-SDK/atak-gradle-takdev.jar
   takrepo.url=https\://artifacts.tak.gov/artifactory/maven
   takrepo.user=YOUR_TAK_GOV_USERNAME
   takrepo.password=YOUR_TAK_GOV_PASSWORD
   takDebugKeyFile=/path/to/TAKWatch/app/build/android_keystore
   takDebugKeyFilePassword=tnttnt
   takDebugKeyAlias=wintec_mapping
   takDebugKeyPassword=tnttnt
   takReleaseKeyFile=/path/to/TAKWatch/app/build/android_keystore
   takReleaseKeyFilePassword=tnttnt
   takReleaseKeyAlias=wintec_mapping
   takReleaseKeyPassword=tnttnt
   ```

### Build the ATAK Plugin

```bash
# The keystore lives in app/build/ which gets deleted by clean — copy it first
mkdir -p app/build && cp ATAKSDK/ATAK-CIV-5.6.0.12-SDK/android_keystore app/build/android_keystore

# Build the debug APK
JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew assembleCivDebug
```

The APK will be at `app/build/outputs/apk/civ/debug/ATAK-Plugin-TAKWatch-*.apk`. Install it on your ATAK device via `adb install` or side-load.

### Build the Garmin Watch App

1. Clone the watch app:

   ```bash
   git clone https://github.com/TDF-PL/TAKWatch-IQ.git
   cd TAKWatch-IQ
   ```

2. Generate a developer key (one-time):

   ```bash
   openssl genrsa -out developer_key.pem 4096
   openssl pkcs8 -topk8 -inform PEM -outform DER -in developer_key.pem -out developer_key.der -nocrypt
   rm developer_key.pem
   ```

3. Build for your target device (e.g. Fenix 7S Pro):

   ```bash
   monkeyc -o TAKWatch.prg -f monkey.jungle -y developer_key.der -d fenix7spro
   ```

   See the [TAKWatch-IQ manifest.xml](https://github.com/TDF-PL/TAKWatch-IQ/blob/main/manifest.xml) for all supported device IDs.

### Test in the Garmin Simulator

1. Open the Connect IQ SDK Manager and install device profiles.
2. Launch the simulator from the SDK `bin/` directory:

   ```bash
   open "/path/to/ConnectIQ/Sdks/connectiq-sdk-mac-*/bin/ConnectIQ.app"
   ```

3. Load the app:

   ```bash
   monkeydo TAKWatch.prg fenix7spro
   ```

## Releases

https://github.com/TDF-PL/TAKWatch/releases

