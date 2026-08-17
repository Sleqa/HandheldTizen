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

import android.os.Handler;
import android.os.Looper;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import dev.cobalt.util.Log;

/**
 * Makes a game controller behave like a TV remote.
 *
 * <p>Cobalt has no Chromium {@code ContentView}, so the browser-side Gamepad API is never fed and
 * the web app only ever sees key events. Gamepad face buttons are not key codes the app knows, and
 * {@link CobaltActivity} deliberately keeps them away from the IME, so on a handheld such as the
 * AYN Thor the D-pad moved focus but nothing could actually select. This maps the face and shoulder
 * buttons onto the remote keys the app does understand, and turns the analog sticks into D-pad
 * presses with the auto-repeat you would expect from holding a direction.
 */
public class GamepadRemoteMapper {

  /** Injects a synthetic remote key press. */
  public interface KeyInjector {
    void injectKey(int keyCode);
  }

  /** Stick deflection past which a direction is considered held. */
  private static final float STICK_THRESHOLD = 0.5f;

  /** Deflection below which a held direction is released, giving hysteresis around the threshold. */
  private static final float STICK_RELEASE_THRESHOLD = 0.35f;

  private static final long REPEAT_INITIAL_DELAY_MS = 400;
  private static final long REPEAT_INTERVAL_MS = 120;

  private final KeyInjector mKeyInjector;
  private final Handler mHandler = new Handler(Looper.getMainLooper());

  // The direction the stick is currently held in, or 0 when centered.
  private int mHeldDirectionKeyCode;

  private final Runnable mRepeatRunnable =
      new Runnable() {
        @Override
        public void run() {
          if (mHeldDirectionKeyCode == 0) {
            return;
          }
          mKeyInjector.injectKey(mHeldDirectionKeyCode);
          mHandler.postDelayed(this, REPEAT_INTERVAL_MS);
        }
      };

  public GamepadRemoteMapper(KeyInjector keyInjector) {
    mKeyInjector = keyInjector;
  }

  /**
   * Returns the remote key code a gamepad button should act as, or {@link KeyEvent#KEYCODE_UNKNOWN}
   * if the button has no remote equivalent and should be left alone.
   */
  public static int mapButton(int keyCode) {
    switch (keyCode) {
      case KeyEvent.KEYCODE_BUTTON_A:
        return KeyEvent.KEYCODE_DPAD_CENTER;
      case KeyEvent.KEYCODE_BUTTON_B:
        return KeyEvent.KEYCODE_BACK;
      case KeyEvent.KEYCODE_BUTTON_X:
      case KeyEvent.KEYCODE_BUTTON_START:
        return KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE;
      case KeyEvent.KEYCODE_BUTTON_Y:
      case KeyEvent.KEYCODE_BUTTON_SELECT:
        return KeyEvent.KEYCODE_MENU;
      case KeyEvent.KEYCODE_BUTTON_L1:
        return KeyEvent.KEYCODE_MEDIA_REWIND;
      case KeyEvent.KEYCODE_BUTTON_R1:
        return KeyEvent.KEYCODE_MEDIA_FAST_FORWARD;
      default:
        return KeyEvent.KEYCODE_UNKNOWN;
    }
  }

  /**
   * Converts analog stick and hat movement into D-pad presses.
   *
   * @return whether the event was consumed.
   */
  public boolean onGenericMotionEvent(MotionEvent event) {
    if (!isFromJoystick(event)) {
      return false;
    }

    // The hat axes are what a physical D-pad reports on some controllers; when it is present it
    // takes precedence, otherwise fall back to the left stick.
    float x = event.getAxisValue(MotionEvent.AXIS_HAT_X);
    float y = event.getAxisValue(MotionEvent.AXIS_HAT_Y);
    if (x == 0 && y == 0) {
      x = event.getAxisValue(MotionEvent.AXIS_X);
      y = event.getAxisValue(MotionEvent.AXIS_Y);
    }

    updateHeldDirection(directionKeyCode(x, y));
    return true;
  }

  /** Stops any in-flight auto-repeat, e.g. when the activity is no longer in the foreground. */
  public void reset() {
    mHeldDirectionKeyCode = 0;
    mHandler.removeCallbacks(mRepeatRunnable);
  }

  private void updateHeldDirection(int directionKeyCode) {
    if (directionKeyCode == mHeldDirectionKeyCode) {
      return;
    }
    mHandler.removeCallbacks(mRepeatRunnable);
    mHeldDirectionKeyCode = directionKeyCode;
    if (directionKeyCode == 0) {
      return;
    }
    mKeyInjector.injectKey(directionKeyCode);
    mHandler.postDelayed(mRepeatRunnable, REPEAT_INITIAL_DELAY_MS);
  }

  /**
   * Returns the D-pad key code for a stick position, or 0 when the stick is centered. The dominant
   * axis wins, so a diagonal push produces a single unambiguous direction.
   */
  private int directionKeyCode(float x, float y) {
    // Hysteresis: once a direction is held it takes a smaller deflection to keep it.
    boolean holdingHorizontal =
        mHeldDirectionKeyCode == KeyEvent.KEYCODE_DPAD_LEFT
            || mHeldDirectionKeyCode == KeyEvent.KEYCODE_DPAD_RIGHT;
    boolean holdingVertical =
        mHeldDirectionKeyCode == KeyEvent.KEYCODE_DPAD_UP
            || mHeldDirectionKeyCode == KeyEvent.KEYCODE_DPAD_DOWN;

    float horizontalThreshold = holdingHorizontal ? STICK_RELEASE_THRESHOLD : STICK_THRESHOLD;
    float verticalThreshold = holdingVertical ? STICK_RELEASE_THRESHOLD : STICK_THRESHOLD;

    if (Math.abs(x) >= Math.abs(y)) {
      if (Math.abs(x) >= horizontalThreshold) {
        return x > 0 ? KeyEvent.KEYCODE_DPAD_RIGHT : KeyEvent.KEYCODE_DPAD_LEFT;
      }
    } else if (Math.abs(y) >= verticalThreshold) {
      return y > 0 ? KeyEvent.KEYCODE_DPAD_DOWN : KeyEvent.KEYCODE_DPAD_UP;
    }
    return 0;
  }

  private static boolean isFromJoystick(MotionEvent event) {
    int source = event.getSource();
    boolean isJoystick =
        (source & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK
            || (source & InputDevice.SOURCE_DPAD) == InputDevice.SOURCE_DPAD;
    if (isJoystick && event.getAction() != MotionEvent.ACTION_MOVE) {
      Log.d(TAG, "Ignoring non-move joystick motion, action=" + event.getAction());
      return false;
    }
    return isJoystick;
  }
}
