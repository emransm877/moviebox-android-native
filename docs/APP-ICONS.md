# Selectable MovieBox app icons

Native phone feature, 23 November 2026. Ten independently generated MovieBox
concepts are packaged alongside the existing Original icon. No publisher or trademark verification is implied.

`AppLogos.java` owns the compact native selector, persisted icon preference and
launcher components. `PhoneActivity` remains the real activity; eleven manifest
aliases point to it. Only Original is enabled on a fresh installation. A change
enables the replacement before disabling other aliases with `DONT_KILL_APP`.
The preference is committed after the component change; failures attempt to
restore the previous selection. Startup reconciliation handles interrupted changes.

The ten assets have legacy API23 bitmap wrappers and API26 adaptive wrappers.
Each 432px WebP retains its generated proportions, adds no network dependency,
and the ten files total 99,048 bytes. The existing Original resources are unchanged.

Activity integration:

```java
AppLogos.restore(this); // normal initialization

// Existing Settings row, using the current theme tokens and actual rerender callback:
settingRow("App icon", AppLogos.name(this), () ->
    AppLogos.show(this, bg, panel, ink, accent, secondary, this::renderSettings));

// Optional in-app branding preview:
logoImage.setImageResource(AppLogos.preview(this));
```

`renderSettings` above denotes the application's existing Settings-rendering method;
it is not a method supplied by this helper.

`AppLogosTest` covers stable choice identities and invalid preference fallback.
`AppLogosScenarios` changes each alias, verifies exactly one enabled launcher,
loads each icon and label, checks restoration, then restores the previously chosen
icon in `finally`. Run it as part of the coordinated device QA before reporting
launcher behavior as tested. Private device evidence is kept outside the public source repository.

Launchers control their own refresh timing. A pinned shortcut created before this
feature may keep a cached icon independently from the app-drawer entry. The app
does not kill the launcher, delete shortcuts or reset user data to force refresh.
Adaptive monochrome/themed-icon artwork is not part of these color concepts.
