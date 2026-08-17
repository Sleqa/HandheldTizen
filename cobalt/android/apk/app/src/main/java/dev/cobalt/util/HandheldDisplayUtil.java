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

import static dev.cobalt.util.Log.TAG;

import android.app.Activity;
import android.app.UiModeManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.util.Size;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

/**
 * Helpers for running a TV-shaped web app on a handheld display (phones, and handheld consoles such
 * as the AYN Thor).
 *
 * <p>The web app is authored for a 1280x720 (or 1920x1080) television. On a television Cobalt
 * forces a device scale factor of 1, which makes one CSS pixel equal one physical pixel, so a 1080p
 * panel gets a 1920x1080 CSS viewport. Handhelds pack the same (or more) pixels into a few inches,
 * so a scale factor of 1 leaves the UI unreadably small and, on tall phone panels, badly
 * proportioned. This class derives a scale factor that maps the display's short edge onto the
 * app's authored 720px height instead, and applies the window flags a handheld needs (immersive
 * fullscreen, drawing into the display cutout).
 */
public final class HandheldDisplayUtil {

  /** The height, in CSS pixels, that the web app is authored against. */
  public static final int TARGET_UI_HEIGHT_PX = 720;

  /** Device scale factor used on televisions, matching upstream Cobalt behaviour. */
  public static final float TV_DEVICE_SCALE_FACTOR = 1.0f;

  private static final float MIN_DEVICE_SCALE_FACTOR = 1.0f;
  private static final float MAX_DEVICE_SCALE_FACTOR = 4.0f;

  private HandheldDisplayUtil() {}

  /**
   * Returns whether this device is a television, in which case the upstream (TV) defaults should be
   * left untouched.
   */
  public static boolean isTelevision(Context context) {
    if (context == null) {
      return false;
    }
    UiModeManager uiModeManager =
        (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);
    if (uiModeManager != null
        && uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_TELEVISION) {
      return true;
    }
    PackageManager packageManager = context.getPackageManager();
    if (packageManager == null) {
      return false;
    }
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
        || packageManager.hasSystemFeature("android.hardware.type.television")
        || packageManager.hasSystemFeature("android.software.leanback_only");
  }

  /** Returns whether the device reports a touchscreen we can take input from. */
  public static boolean hasTouchScreen(Context context) {
    if (context == null) {
      return false;
    }
    PackageManager packageManager = context.getPackageManager();
    return packageManager != null
        && packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);
  }

  /**
   * Returns the largest size, in physical pixels, that this activity's window can occupy on the
   * current display. This is the full panel size, so it does not change as system bars are shown or
   * hidden, and it is stable across rotation because callers only use its short edge.
   */
  public static Size getMaximumWindowSizePx(Activity activity) {
    if (activity == null) {
      return new Size(0, 0);
    }
    WindowManager windowManager = activity.getWindowManager();
    if (windowManager == null) {
      return new Size(0, 0);
    }
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      Rect bounds = windowManager.getMaximumWindowMetrics().getBounds();
      return new Size(bounds.width(), bounds.height());
    }
    return getMaximumWindowSizePxDeprecated(windowManager);
  }

  @SuppressWarnings("deprecation")
  private static Size getMaximumWindowSizePxDeprecated(WindowManager windowManager) {
    Display display = windowManager.getDefaultDisplay();
    if (display == null) {
      return new Size(0, 0);
    }
    DisplayMetrics metrics = new DisplayMetrics();
    display.getRealMetrics(metrics);
    return new Size(metrics.widthPixels, metrics.heightPixels);
  }

  /**
   * Returns the value to pass to Chromium's {@code --force-device-scale-factor} so that the web
   * app's authored layout fills the display.
   *
   * <p>Televisions keep the upstream value of 1. Everything else maps the display's short edge (the
   * height, since the app runs in landscape) onto {@link #TARGET_UI_HEIGHT_PX}, which gives a
   * 1920x1080 handheld a 1280x720 CSS viewport and a 2400x1080 phone a 1600x720 one. The scale is
   * never taken below 1, because rendering more CSS pixels than the panel has physical pixels only
   * makes the UI blurrier and slower.
   */
  public static float computeDeviceScaleFactor(Activity activity) {
    if (activity == null || isTelevision(activity)) {
      return TV_DEVICE_SCALE_FACTOR;
    }
    Size size = getMaximumWindowSizePx(activity);
    int shortEdgePx = Math.min(size.getWidth(), size.getHeight());
    if (shortEdgePx <= 0) {
      Log.w(TAG, "Could not determine the display size; using a device scale factor of 1.");
      return TV_DEVICE_SCALE_FACTOR;
    }
    float scale = (float) shortEdgePx / TARGET_UI_HEIGHT_PX;
    scale = Math.max(MIN_DEVICE_SCALE_FACTOR, Math.min(MAX_DEVICE_SCALE_FACTOR, scale));
    // Round to two decimals so the flag value stays stable and readable in logs.
    return Math.round(scale * 100f) / 100f;
  }

  /**
   * Formats a device scale factor for the command line, trimming a trailing ".0" so that the
   * television case still emits the historical "1".
   */
  public static String formatDeviceScaleFactor(float scale) {
    if (scale == Math.round(scale)) {
      return Integer.toString(Math.round(scale));
    }
    return Float.toString(scale);
  }

  /**
   * Puts the window into sticky immersive fullscreen and allows it to draw into the display cutout.
   *
   * <p>Televisions are left alone: they have no system bars or cutouts, and changing how the decor
   * fits system windows there would be a behaviour change for no benefit. On a handheld this is
   * what stops the status and navigation bars from stealing a band of the 16:9 UI, and what stops a
   * notch from letterboxing the whole app.
   */
  public static void applyImmersiveFullscreen(Activity activity) {
    if (activity == null || isTelevision(activity)) {
      return;
    }
    Window window = activity.getWindow();
    if (window == null) {
      return;
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
      WindowManager.LayoutParams attributes = window.getAttributes();
      if (attributes.layoutInDisplayCutoutMode
          != WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES) {
        attributes.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
        window.setAttributes(attributes);
      }
    }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
      window.setDecorFitsSystemWindows(false);
      WindowInsetsController controller = window.getInsetsController();
      if (controller != null) {
        controller.hide(WindowInsets.Type.systemBars());
        controller.setSystemBarsBehavior(
            WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
      }
    } else {
      applyImmersiveFullscreenDeprecated(window);
    }
  }

  @SuppressWarnings("deprecation")
  private static void applyImmersiveFullscreenDeprecated(Window window) {
    View decorView = window.getDecorView();
    decorView.setSystemUiVisibility(
        View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
  }
}
