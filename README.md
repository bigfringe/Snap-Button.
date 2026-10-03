# Snap-Button

Android floating screenshot button redesign.

## UI
- Round gold eye is the floating snapshot control.
- Eye gently blinks/moves while idle.
- Pressing the eye gives a clear blink/close response.
- Control sits at the bottom-right while remaining inside Android safe bounds.

## Snapshot behaviour
- Tap eye to capture the screen using Android MediaProjection.
- Play a classic camera shutter sound when a capture succeeds.
- Keep the overlay available above other apps while capture permission is active.

The Android project source and generated eye animation assets will be added next.
