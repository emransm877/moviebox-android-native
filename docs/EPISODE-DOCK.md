# Phone episode dock

The player uses a dedicated native right-side dock, following the TV menu's
translucent rounded surface and compact single-column episode rows. This replaces
the generic bottom sheet only for the in-player episode browser. Other playback
pickers are unchanged.

- The dock is at most 300 dp wide and is positioned 16 dp below the real top bar
  and 16 dp above the real bottom controls.
- The header, season selector and small footer remain fixed; episodes scroll
  within the remaining space. Sparse episode numbers come from the supplied
  season data.
- In short landscape, the title and season selector share one 48 dp header so a
  usable episode row remains between the player bars. Taller layouts show the
  separate header and compact season picker, like the TV design.
- Season browsing preserves the actual playing episode. Selecting that same
  episode simply closes the dock. Selecting another episode starts it.
- Close, Back and outside taps dismiss the dock without toggling playback.
- There is no blur, full-screen dim layer or remote HTML surface. The surface
  follows the phone's current palette.

`EpisodeDockScenarios` exercises portrait/landscape window bounds, available list
space, current-episode reuse, sparse episode selection, Back and outside taps with
owned local media and an isolated library. Build/device results must be recorded
by the coordinating QA run; source review alone is not physical-device approval.
