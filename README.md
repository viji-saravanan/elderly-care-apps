# Elderly Care — Phone & Wear OS Apps

This repository contains the extracted Android source projects from the original archive, organized as **two independent Gradle projects**:

- `elderlycarephone/` — companion Android phone app, including Health Connect and a dashboard.
- `elderlycarewatch/` — Wear OS app for health monitoring and synchronization.

## Prerequisites

- Android Studio with a compatible Android SDK (projects use `compileSdk = 35`).
- A Firebase project configured for the phone and watch Android package IDs.
- Supported phone and watch devices for health permissions/sensors.

## Local setup

1. Open **each subfolder separately** in Android Studio.
2. Download each Android app's `google-services.json` from your own Firebase project settings and put it at `elderlycarephone/app/google-services.json` and `elderlycarewatch/app/google-services.json` respectively.
3. Configure appropriate Firebase Authentication, Realtime Database rules and access policies **before testing with personal health data**. Avoid permissive database rules. Do not use production health data during development.
4. Let Android Studio generate `local.properties` and synchronize Gradle.
5. Build and run the phone and watch apps separately.

## Public-repository security notes

- The original Firebase configuration files have **not** been included, including redundant project-root copies.
- `local.properties`, local IDE/project caches, build artifacts, APKs, generated files and build logs have also been excluded.
- The `.gitignore` prevents these files being accidentally added in later commits.
- **Never commit signing keys, service-account JSON, bearer tokens, passwords or private Firebase rules.**
- Firebase `google-services.json` is typically client configuration, not a service-account credential, but it is excluded here for a conservative public-source release.
- A public source release is *not* evidence that backend access rules are secure. Review Firebase Realtime Database rules, data retention, authentication, permissions and deployed endpoints independently.

## Project status

These are exported source files from the supplied archive, not a verified reproducible release. Build and runtime functionality have **not** been tested in this environment.
