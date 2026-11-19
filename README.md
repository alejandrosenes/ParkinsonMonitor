# Parkinson Monitor

Parkinson Monitor is an open-source Android application designed to help people
living with Parkinson’s disease (and their caregivers) track daily symptoms,
medication intake, tremor evaluations and health data.

## Features

- **Medication management**: catalog of Parkinson medications, custom medications,
  favorites, scheduled doses and intake history with filtering.
- **Tremor evaluation**: active tremor tests (rest, postural, spiral) with
  spectral analysis and automatic saving to history.
- **Dashboard & timeline**: daily overview and trend timeline of symptoms and
  medication events.
- **Caregiver mode**: support for caregivers to monitor and mark medication
  intake.
- **Reports**: generate structured PDF reports to share with neurologists.
- **Health Connect integration**: read steps, heart rate, sleep and exercise
  data.
- **Backup & restore**: full backup (medication schedules, intakes, custom
  medications, favorites, settings and tremor results) as a ZIP file.
- **Settings**: language, color palette, text size.

## Technical details

- Package name: `com.pkmonitor`
- Min SDK: 26 (Android 8.0)
- Target SDK: 36 (Android 16)
- Built with Kotlin + Jetpack Compose
- Database: Room
- License: GNU Affero General Public License v3.0 (AGPL-3.0)

## Building

```bash
./gradlew assembleRelease
./gradlew bundleRelease
```

Signing is optional and only enabled when `keystore.properties` is present.

## Privacy

Parkinson Monitor does not include tracking, analytics or advertising. All data
is stored locally on the device.
