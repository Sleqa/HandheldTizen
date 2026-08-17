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

package dev.cobalt.coat;

import static dev.cobalt.util.Log.TAG;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ViewConfiguration;
import androidx.annotation.Nullable;
import dev.cobalt.shell.ContentViewRenderView;
import dev.cobalt.util.Log;
import org.chromium.content_public.browser.WebContents;
import org.chromium.ui.base.EventForwarder;

/**
 * Turns touchscreen input into something the TV web app understands.
 *
 * <p>Cobalt does not use Chromium's {@code ContentView}, so nothing was forwarding
 * {@link MotionEvent}s into Blink at all and the app was entirely unreachable by touch. Simply
 * forwarding everything is also not enough: the web app's shelves and rows are not natively
 * scrollable, they move because focus moves, so a drag has to become D-pad presses rather than a
 * scroll.
 *
 * <p>The default {@link Mode#HYBRID} therefore splits the difference:
 *
 * <ul>
 *   <li>A tap is replayed into Blink as a real touch down/up pair. Blink turns an unconsumed tap
 *       into a compatibility mouse click, which is what the web app's clickable elements listen
 *       for.
 *   <li>A drag is swallowed and emitted as repeated D-pad presses along the dominant axis, one per
 *       {@link #SWIPE_STEP_DP} of travel, so swiping right walks the focus right.
 *   <li>A two-finger tap sends BACK, which the app maps to Escape. Handhelds normally have a system
 *       back gesture too, but that gesture is unreliable while the window is in sticky immersive
 *       mode.
 * </ul>
 */
public class TouchNavigationHelper implements ContentViewRenderView.TouchHandler {

  /** How the helper translates touch input. */
  public enum Mode {
    /** Touch is ignored entirely; the historical television behaviour. */
    OFF,
    /** Everything becomes key events: drags are D-pad presses, taps are DPAD_CENTER. */
    DPAD,
    /** Events are forwarded to Blink untouched, with no D-pad synthesis. */
    NATIVE,
    /** Taps are forwarded to Blink, drags become D-pad presses. */
    HYBRID;

    /** Parses a mode name, falling back to {@code fallback} for null or unrecognized values. */
    public static Mode fromString(@Nullable String value, Mode fallback) {
      if (TextUtils.isEmpty(value)) {
        return fallback;
      }
      for (Mode mode : values()) {
        if (mode.name().equalsIgnoreCase(value)) {
          return mode;
        }
      }
      Log.w(TAG, "Unrecognized touch navigation mode '" + value + "'; using " + fallback + ".");
      return fallback;
    }
  }

  /** Injects a synthetic key press, as if it had arrived from a remote. */
  public interface KeyInjector {
    void injectKey(int keyCode);
  }

  /** Supplies the currently active {@link WebContents}, which changes as shells are swapped. */
  public interface WebContentsProvider {
    @Nullable
    WebContents getActiveWebContents();
  }

  /** Travel, in dp, that advances the focus by one D-pad press during a drag. */
  private static final int SWIPE_STEP_DP = 48;

  private final Mode mMode;
  private final KeyInjector mKeyInjector;
  private final WebContentsProvider mWebContentsProvider;
  private final int mTouchSlopPx;
  private final float mSwipeStepPx;

  // Gesture state for the pointer stream currently in flight.
  private float mDownX;
  private float mDownY;
  // Position at which the most recent D-pad press was emitted, so a long drag emits evenly spaced
  // presses rather than one per MotionEvent.
  private float mLastStepX;
  private float mLastStepY;
  private boolean mIsDragging;
  private boolean mIsMultiTouch;
  @Nullable private MotionEvent mPendingDownEvent;

  public TouchNavigationHelper(
      Context context,
      Mode mode,
      KeyInjector keyInjector,
      WebContentsProvider webContentsProvider) {
    mMode = mode;
    mKeyInjector = keyInjector;
    mWebContentsProvider = webContentsProvider;
    mTouchSlopPx = ViewConfiguration.get(context).getScaledTouchSlop();
    float swipeStepPx =
        TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            SWIPE_STEP_DP,
            context.getResources().getDisplayMetrics());
    // Never let a step be smaller than the slop, or a gesture would start emitting presses on the
    // same move that first classified it as a drag.
    mSwipeStepPx = Math.max(swipeStepPx, mTouchSlopPx * 2f);
    Log.i(TAG, "Touch navigation mode: " + mode + ", step: " + mSwipeStepPx + "px");
  }

  public Mode getMode() {
    return mMode;
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    if (mMode == Mode.OFF) {
      return false;
    }
    if (mMode == Mode.NATIVE) {
      return forwardToWebContents(event);
    }

    switch (event.getActionMasked()) {
      case MotionEvent.ACTION_DOWN:
        onGestureStart(event);
        return true;

      case MotionEvent.ACTION_POINTER_DOWN:
        mIsMultiTouch = true;
        return true;

      case MotionEvent.ACTION_MOVE:
        onGestureMove(event);
        return true;

      case MotionEvent.ACTION_UP:
        onGestureEnd(event);
        return true;

      case MotionEvent.ACTION_CANCEL:
        clearGesture();
        return true;

      default:
        return true;
    }
  }

  private void onGestureStart(MotionEvent event) {
    clearGesture();
    mDownX = event.getX();
    mDownY = event.getY();
    mLastStepX = mDownX;
    mLastStepY = mDownY;
    mIsDragging = false;
    mIsMultiTouch = false;
    // Keep a copy so a tap can be replayed into Blink as a complete down/up pair once we know the
    // gesture was not a drag.
    mPendingDownEvent = MotionEvent.obtain(event);
  }

  private void onGestureMove(MotionEvent event) {
    if (mIsMultiTouch) {
      return;
    }
    if (!mIsDragging) {
      if (Math.hypot(event.getX() - mDownX, event.getY() - mDownY) <= mTouchSlopPx) {
        return;
      }
      mIsDragging = true;
    }

    float dx = event.getX() - mLastStepX;
    float dy = event.getY() - mLastStepY;
    if (Math.abs(dx) >= Math.abs(dy)) {
      int steps = (int) (Math.abs(dx) / mSwipeStepPx);
      if (steps > 0) {
        injectRepeated(
            dx > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT, steps);
        mLastStepX += Math.signum(dx) * steps * mSwipeStepPx;
        mLastStepY = event.getY();
      }
    } else {
      int steps = (int) (Math.abs(dy) / mSwipeStepPx);
      if (steps > 0) {
        injectRepeated(dy > 0 ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP, steps);
        mLastStepY += Math.signum(dy) * steps * mSwipeStepPx;
        mLastStepX = event.getX();
      }
    }
  }

  private void onGestureEnd(MotionEvent event) {
    boolean wasMultiTouch = mIsMultiTouch;
    boolean wasDragging = mIsDragging;
    MotionEvent downEvent = mPendingDownEvent;
    mPendingDownEvent = null;

    try {
      if (wasMultiTouch) {
        mKeyInjector.injectKey(KeyEvent.KEYCODE_BACK);
        return;
      }
      if (wasDragging) {
        return;
      }
      if (mMode == Mode.DPAD) {
        mKeyInjector.injectKey(KeyEvent.KEYCODE_DPAD_CENTER);
        return;
      }
      // HYBRID: replay the tap so Blink synthesizes a click at the touched point.
      if (downEvent != null) {
        forwardToWebContents(downEvent);
      }
      forwardToWebContents(event);
    } finally {
      if (downEvent != null) {
        downEvent.recycle();
      }
      mIsDragging = false;
      mIsMultiTouch = false;
    }
  }

  private void clearGesture() {
    if (mPendingDownEvent != null) {
      mPendingDownEvent.recycle();
      mPendingDownEvent = null;
    }
    mIsDragging = false;
    mIsMultiTouch = false;
  }

  private void injectRepeated(int keyCode, int count) {
    for (int i = 0; i < count; i++) {
      mKeyInjector.injectKey(keyCode);
    }
  }

  private boolean forwardToWebContents(MotionEvent event) {
    WebContents webContents = mWebContentsProvider.getActiveWebContents();
    if (webContents == null) {
      return false;
    }
    EventForwarder eventForwarder = webContents.getEventForwarder();
    if (eventForwarder == null) {
      return false;
    }
    return eventForwarder.onTouchEvent(event);
  }
}
