/**
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.facebook.react.uimanager;

import javax.annotation.Nullable;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import android.content.Context;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Display;
import android.view.Surface;
import android.view.WindowManager;
import android.view.View;
import android.app.Activity;

import com.facebook.infer.annotation.Assertions;
import com.facebook.react.bridge.WritableNativeMap;

import com.microsoft.device.display.DisplayMask;
import android.graphics.Rect;
import java.util.List;

/**
 * Holds an instance of the current DisplayMetrics so we don't have to thread it through all the
 * classes that need it.
 * Note: windowDisplayMetrics are deprecated in favor of ScreenDisplayMetrics: window metrics
 * are supposed to return the drawable area but there's no guarantee that they correspond to the
 * actual size of the {@link ReactRootView}. Moreover, they are not consistent with what iOS
 * returns. Screen metrics returns the metrics of the entire screen, is consistent with iOS and
 * should be used instead.
 */
public class DisplayMetricsHolder {

  private static @Nullable DisplayMetrics sWindowDisplayMetrics;
  private static @Nullable DisplayMetrics sScreenDisplayMetrics;
  private static @Nullable DisplayMask sDisplayMask;
  private static @Nullable Activity sActivity;
  private static @Nullable Rect sVisibleWindowRect;

  public static void setVisibleWindowRect(Rect visibleWindowRect) {
    if (sVisibleWindowRect == null) {
      sVisibleWindowRect = new Rect();
    }
    sVisibleWindowRect = visibleWindowRect;
  }

  /**
   * @deprecated Use {@link #setScreenDisplayMetrics(DisplayMetrics)} instead. See comment above as
   *    to why this is not correct to use.
   */
  public static void setWindowDisplayMetrics(DisplayMetrics displayMetrics) {
    sWindowDisplayMetrics = displayMetrics;
  }

  public static void initDisplayMetricsIfNotInitialized(Context context) {
    if (DisplayMetricsHolder.getScreenDisplayMetrics() != null) {
      return;
    }
    initDisplayMetrics(context);
    if (context.getPackageManager().hasSystemFeature("com.microsoft.device.display.displaymask")) {
      sDisplayMask = DisplayMask.fromResourcesRectApproximation(context);
    }
  }

  public static void initDisplayMetrics(Context context) {
    DisplayMetrics displayMetrics = context.getResources().getDisplayMetrics();
    DisplayMetricsHolder.setWindowDisplayMetrics(displayMetrics);

    DisplayMetrics screenDisplayMetrics = new DisplayMetrics();
    screenDisplayMetrics.setTo(displayMetrics);
    WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    Assertions.assertNotNull(
        wm,
        "WindowManager is null!");
    Display display = wm.getDefaultDisplay();

    // Get the real display metrics if we are using API level 17 or higher.
    // The real metrics include system decor elements (e.g. soft menu bar).
    //
    // See: http://developer.android.com/reference/android/view/Display.html#getRealMetrics(android.util.DisplayMetrics)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
      display.getRealMetrics(screenDisplayMetrics);
    } else {
      // For 14 <= API level <= 16, we need to invoke getRawHeight and getRawWidth to get the real dimensions.
      // Since react-native only supports API level 16+ we don't have to worry about other cases.
      //
      // Reflection exceptions are rethrown at runtime.
      //
      // See: http://stackoverflow.com/questions/14341041/how-to-get-real-screen-height-and-width/23861333#23861333
      try {
        Method mGetRawH = Display.class.getMethod("getRawHeight");
        Method mGetRawW = Display.class.getMethod("getRawWidth");
        screenDisplayMetrics.widthPixels = (Integer) mGetRawW.invoke(display);
        screenDisplayMetrics.heightPixels = (Integer) mGetRawH.invoke(display);
      } catch (InvocationTargetException | IllegalAccessException | NoSuchMethodException e) {
        throw new RuntimeException("Error getting real dimensions for API level < 17", e);
      }
    }
    DisplayMetricsHolder.setScreenDisplayMetrics(screenDisplayMetrics);
  }

  /**
   * @deprecated Use {@link #getScreenDisplayMetrics()} instead. See comment above as to why this
   *    is not correct to use.
   */
  @Deprecated
  public static DisplayMetrics getWindowDisplayMetrics() {
    return sWindowDisplayMetrics;
  }

  public static void setScreenDisplayMetrics(DisplayMetrics screenDisplayMetrics) {
    sScreenDisplayMetrics = screenDisplayMetrics;
  }

  public static DisplayMetrics getScreenDisplayMetrics() {
    return sScreenDisplayMetrics;
  }

  public static Map<String, Map<String, Object>> getDisplayMetricsMap(double fontScale) {
    Assertions.assertNotNull(
        sWindowDisplayMetrics != null || sScreenDisplayMetrics != null,
        "DisplayMetricsHolder must be initialized with initDisplayMetricsIfNotInitialized or initDisplayMetrics");
    final Map<String, Map<String, Object>> result = new HashMap<>();
    result.put("windowPhysicalPixels", getPhysicalPixelsMap(sWindowDisplayMetrics, fontScale));
    result.put("screenPhysicalPixels", getPhysicalPixelsMap(sScreenDisplayMetrics, fontScale));
    result.put("displayMask", getDisplayMaskMap());
    return result;
  }

  public static WritableNativeMap getDisplayMetricsNativeMap(double fontScale) {
    Assertions.assertNotNull(
        sWindowDisplayMetrics != null || sScreenDisplayMetrics != null,
        "DisplayMetricsHolder must be initialized with initDisplayMetricsIfNotInitialized or initDisplayMetrics");
    final WritableNativeMap result = new WritableNativeMap();
//    result.putMap("windowPhysicalPixels", getPhysicalPixelsNativeMap(sWindowDisplayMetrics, fontScale));
    result.putMap("windowPhysicalPixels", getKeithWindowPhysicalPixelsNativeMap(sWindowDisplayMetrics, fontScale));
    result.putMap("screenPhysicalPixels", getPhysicalPixelsNativeMap(sScreenDisplayMetrics, fontScale));
    result.putMap("displayMask", getDisplayMaskNativeMap());
    return result;
  }

  private static Map<String, Object> getPhysicalPixelsMap(DisplayMetrics displayMetrics, double fontScale) {
    final Map<String, Object> result = new HashMap<>();
    result.put("width", displayMetrics.widthPixels);
    result.put("height", displayMetrics.heightPixels);
    result.put("scale", displayMetrics.density);
    result.put("fontScale", fontScale);
    result.put("densityDpi", displayMetrics.densityDpi);
    return result;
  }

  private static WritableNativeMap getPhysicalPixelsNativeMap(DisplayMetrics displayMetrics, double fontScale) {
    final WritableNativeMap result = new WritableNativeMap();
    result.putInt("width", displayMetrics.widthPixels);
    result.putInt("height", displayMetrics.heightPixels);
    result.putDouble("scale", displayMetrics.density);
    result.putDouble("fontScale", fontScale);
    result.putDouble("densityDpi", displayMetrics.densityDpi);
    return result;
  }

  private static WritableNativeMap getKeithWindowPhysicalPixelsNativeMap(DisplayMetrics displayMetrics, double fontScale) {
    final WritableNativeMap result = new WritableNativeMap();
    result.putInt("width", sVisibleWindowRect.width());
    result.putInt("height", sVisibleWindowRect.height());
    result.putDouble("scale", displayMetrics.density);
    result.putDouble("fontScale", fontScale);
    result.putDouble("densityDpi", displayMetrics.densityDpi);
    return result;
  }

  private static Map<String, Object> getDisplayMaskMap() {
    final Map<String, Object> result = new HashMap<>();
    if (sDisplayMask != null) {
      List<Rect> boundings = sDisplayMask.getBoundingRects();
      Rect first = boundings.get(0);
      result.put("left", first.left);
      result.put("top", first.top);
      result.put("width", first.width());
      result.put("height", first.height());
    }
    else {
      result.put("left", 0);
      result.put("top", 0);
      result.put("width", 0);
      result.put("height", 0);
    }
    result.put("isAppSpanned", isAppSpanned());

    return result;
  }

  private static WritableNativeMap getDisplayMaskNativeMap() {
    final WritableNativeMap result = new WritableNativeMap();
    if (sDisplayMask != null) {
      List<Rect> boundings = sDisplayMask.getBoundingRects();
      Rect first = boundings.get(0);
      result.putInt("left", first.left - sVisibleWindowRect.left);
      result.putInt("top", first.top - sVisibleWindowRect.top);
      result.putInt("width", first.width());
      result.putInt("height", first.height());
    }
    else {
      result.putInt("left", 0);
      result.putInt("top", 0);
      result.putInt("width", 0);
      result.putInt("height", 0);
    }
    result.putBoolean("isAppSpanned", isAppSpanned());
    return result;
  }

  private static boolean isAppSpanned() {
    if (sDisplayMask != null) {
      return sWindowDisplayMetrics.widthPixels >= sScreenDisplayMetrics.widthPixels &&
        sWindowDisplayMetrics.heightPixels >= sScreenDisplayMetrics.heightPixels;
    }
    return false;
  }

}
