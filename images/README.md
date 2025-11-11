# Reference Screenshots

This directory contains reference screenshots from E2E tests.

## main-view.png

The main view of the Location Tracker app on first launch.

This screenshot is automatically updated by CI when the UI changes:
- E2E tests capture the main view screenshot
- Screenshot is normalized (metadata stripped with optipng) for consistent comparison
- If it differs from this reference, a PR is automatically created
- Review the visual changes and merge if correct

**Note:** All screenshots are normalized to remove EXIF/metadata to ensure byte-for-byte comparison only reflects actual visual changes, not timestamp or tool metadata differences.

This serves as both documentation and visual regression testing.
