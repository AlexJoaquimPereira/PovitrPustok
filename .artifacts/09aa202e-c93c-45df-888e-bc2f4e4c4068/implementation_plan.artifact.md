# Fix "Android BaseExtension not found" Gradle sync error

The error "Android BaseExtension not found" occurs when the Hilt Gradle plugin is applied but cannot find the Android extension. This is often caused by incorrect plugin application order or missing plugin definitions in the root `build.gradle.kts`. Additionally, there is a syntax error in the `app/build.gradle.kts` file regarding `compileSdk`.

## Proposed Changes

### Root Project
#### [MODIFY] [build.gradle.kts](file:///home/alex/AndroidStudioProjects/PovitrPustok/build.gradle.kts)
- Add Hilt and KSP plugins to the `plugins` block with `apply false` to ensure they are correctly resolved and available to submodules.

### App Module
#### [MODIFY] [app/build.gradle.kts](file:///home/alex/AndroidStudioProjects/PovitrPustok/app/build.gradle.kts)
- Fix the `compileSdk` syntax.
- Ensure the `hilt` plugin is applied correctly.
- Add missing Hilt dependencies.

## Verification Plan

### Automated Tests
- Run Gradle sync to verify the "Android BaseExtension not found" error is resolved.
- Run `./gradlew assembleDebug` to ensure the project builds successfully.
