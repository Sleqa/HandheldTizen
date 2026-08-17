// Copyright 2025 The Cobalt Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package dev.cobalt.util;

import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

/**
 * Suppresses Android's "default focus highlight", which otherwise paints a full-screen white wash
 * over the app as soon as a controller or any other non-touch input is used.
 *
 * <p>Since API 26, {@link View} draws {@code android.R.drawable.default_focus_highlight} over any
 * focused view whose background does not itself describe a focused state. The drawable is tinted
 * with {@code ?attr/colorControlHighlight}, which resolves to translucent white under the dark
 * AppCompat theme Cobalt uses. Cobalt has no Android-side widgets: the view that takes focus is the
 * full-screen root, and its background is explicitly transparent, so the highlight covers the
 * entire window. It is drawn in {@link View#draw} after children, so it also lands on top of the
 * z-ordered SurfaceView the web contents render into.
 *
 * <p>The highlight is only drawn while the window is out of touch mode, and a window leaves touch
 * mode the moment a D-pad or gamepad key arrives. That is why the wash appears exactly when a
 * controller is picked up and disappears again after a tap. Televisions never hit this because
 * AOSP's television resource overlay sets {@code config_useDefaultFocusHighlight} to false, which
 * disables the feature device-wide; handhelds use the default of true, so the app has to opt out
 * itself.
 */
public final class FocusHighlightUtil {

  private FocusHighlightUtil() {}

  /**
   * Disables the default focus highlight on {@code root} and every view currently beneath it, and
   * keeps doing so for views added later.
   *
   * <p>Views are created programmatically all over Cobalt's startup path (and by Chromium itself,
   * for things like the IME's container), so a one-shot walk is not enough. A global layout
   * listener re-walks the tree whenever it changes; the hierarchy is only a handful of views deep,
   * so this is cheap.
   */
  public static void disableDefaultFocusHighlight(View root) {
    if (root == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
      return;
    }
    applyRecursively(root);

    ViewTreeObserver observer = root.getViewTreeObserver();
    if (observer == null || !observer.isAlive()) {
      return;
    }
    observer.addOnGlobalLayoutListener(
        new ViewTreeObserver.OnGlobalLayoutListener() {
          @Override
          public void onGlobalLayout() {
            applyRecursively(root);
          }
        });
  }

  private static void applyRecursively(View view) {
    if (view == null) {
      return;
    }
    view.setDefaultFocusHighlightEnabled(false);
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        applyRecursively(group.getChildAt(i));
      }
    }
  }
}
