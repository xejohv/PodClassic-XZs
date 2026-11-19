package io.github.xejohv.podclassicxzs.gestures;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.os.Vibrator;
import android.view.KeyEvent;

import java.lang.reflect.Method;
import java.util.List;

import android.content.Intent;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import io.github.xejohv.podclassicxzs.activity.MainActivity;

/**
 * PodClassic 内置手势/状态栏管控层（原 PodGesturesAPK 已合并进来）。
 *
 * 手势：
 *   底部上滑            -> HOME
 *   底部上滑并停顿      -> RECENTS
 *   左/右边缘向内滑动   -> BACK
 *
 * 状态栏：任何界面都不允许状态栏/通知栏停留，一旦展开立即收起。
 */
public class GestureService extends AccessibilityService {

    /** 服务单例，供 App 内部调用（例如电源菜单要代点系统按钮） */
    private static GestureService instance;

    public static GestureService getInstance() {
        return instance;
    }

    /** 退出 PodClassic 自己的电源菜单时，把还留在下面的系统菜单也关掉 */
    public static void closeSystemPowerMenu() {
        GestureService s = instance;
        if (s == null) {
            return;
        }
        try {
            if (s.hasPowerMenu()) {
                s.performGlobalAction(GLOBAL_ACTION_BACK);
            }
        } catch (Exception e) {
            Log.w(TAG, "closeSystemPowerMenu failed", e);
        }
    }

    private boolean hasPowerMenu() {
        return findLabelNode("关机") != null;
    }

    /** 让无障碍去点被盖住的系统电源菜单里的按钮：关机 / 重新启动 */
    public static boolean clickPowerLabel(String label) {
        GestureService s = instance;
        return s != null && s.clickLabel(label);
    }

    private static final String TAG = "PodGestureService";

    /** 底部触控条高度 */
    private static final float BOTTOM_STRIP_DP = 26f;
    /** 左右触控条宽度 */
    private static final float SIDE_STRIP_DP = 16f;
    /** 顶部拦截条高度 */
    private static final float TOP_STRIP_DP = 20f;
    /** 判定为滑动的最小位移 */
    private static final float SWIPE_MIN_DP = 32f;
    /** 上滑停顿多少毫秒判定为多任务 */
    private static final long HOLD_MS = 320L;
    /** 状态栏巡检间隔 */
    private static final long SHADE_GUARD_MS = 300L;

    private WindowManager wm;
    private View bottomStrip, leftStrip, rightStrip, pill, topStrip;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private Vibrator vibrator;

    private Object statusBarManager;
    private Method collapsePanels;

    @Override
    public void onServiceConnected() {
        super.onServiceConnected();
        Log.i(TAG, "service connected");
        instance = this;
        wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        initStatusBar();
        install();
        handler.post(shadeGuard);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        handler.postDelayed(new Runnable() {
            public void run() {
                remove();
                install();
            }
        }, 300);
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null) {
            return;
        }
        CharSequence pkg = event.getPackageName();
        if (pkg != null && "com.android.systemui".equals(pkg.toString())) {
            // 状态栏/通知栏一出现就立即收起
            collapseShade();
            // 长按电源键弹出的系统电源菜单，一出现就换成 PodClassic 自己的
            maybeReplacePowerMenu(event);
        }
    }

    // ---------------- 硬件键逃生（相机等外部 App 退不出来时的兜底） ----------------

    private static final long VOL_LONG_MS = 1200L;
    private boolean volLongFired = false;

    private final Runnable volLongTask = new Runnable() {
        @Override
        public void run() {
            volLongFired = true;
            buzz();
            performGlobalAction(GLOBAL_ACTION_HOME);
        }
    };

    /**
     * 长按音量下键 1.2 秒 = 回桌面。只认 KEYCODE_VOLUME_DOWN：
     * 音量上键保持系统原生行为，避免"想调大音量结果被弹回桌面"。
     * 短按完全放行，音量加减照常。
     */
    @Override
    protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN) {
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                volLongFired = false;
                handler.postDelayed(volLongTask, VOL_LONG_MS);
            }
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            handler.removeCallbacks(volLongTask);
            if (volLongFired) {
                volLongFired = false;
                return true;
            }
        }
        return false;
    }

    @Override
    public void onInterrupt() {
    }

    @Override
    public void onDestroy() {
        instance = null;
        handler.removeCallbacksAndMessages(null);
        remove();
        super.onDestroy();
    }

    // ---------------- 状态栏管控 ----------------

    private void initStatusBar() {
        try {
            statusBarManager = getSystemService("statusbar");
            if (statusBarManager != null) {
                collapsePanels = statusBarManager.getClass().getMethod("collapsePanels");
            }
        } catch (Throwable t) {
            Log.w(TAG, "status bar init failed", t);
        }
    }

    private void collapseShade() {
        try {
            if (collapsePanels != null && statusBarManager != null) {
                collapsePanels.invoke(statusBarManager);
            }
        } catch (Throwable ignored) {
        }
    }

    private final Runnable shadeGuard = new Runnable() {
        @Override
        public void run() {
            collapseShade();
            handler.postDelayed(this, SHADE_GUARD_MS);
        }
    };

    // ---------------- 手势层 ----------------

    private int dp(float v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private WindowManager.LayoutParams params(int w, int h, int gravity, boolean touchable) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams();
        lp.type = Build.VERSION.SDK_INT >= 22
                ? WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY;
        lp.format = PixelFormat.TRANSLUCENT;
        lp.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!touchable) {
            lp.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        lp.width = w;
        lp.height = h;
        lp.gravity = gravity;
        lp.x = 0;
        lp.y = 0;
        lp.setTitle("PodGestureLayer");
        return lp;
    }

    private void install() {
        try {
            bottomStrip = new View(this);
            bottomStrip.setOnTouchListener(new SwipeWatcher(MODE_BOTTOM));
            wm.addView(bottomStrip,
                    params(WindowManager.LayoutParams.MATCH_PARENT, dp(BOTTOM_STRIP_DP), Gravity.BOTTOM, true));

            leftStrip = new View(this);
            leftStrip.setOnTouchListener(new SwipeWatcher(MODE_LEFT));
            wm.addView(leftStrip,
                    params(dp(SIDE_STRIP_DP), WindowManager.LayoutParams.MATCH_PARENT,
                            Gravity.LEFT | Gravity.CENTER_VERTICAL, true));

            rightStrip = new View(this);
            rightStrip.setOnTouchListener(new SwipeWatcher(MODE_RIGHT));
            wm.addView(rightStrip,
                    params(dp(SIDE_STRIP_DP), WindowManager.LayoutParams.MATCH_PARENT,
                            Gravity.RIGHT | Gravity.CENTER_VERTICAL, true));

            // 底部手势指示条，和机身金色协调
            pill = new View(this);
            GradientDrawable shape = new GradientDrawable();
            shape.setShape(GradientDrawable.RECTANGLE);
            shape.setCornerRadius(dp(3));
            shape.setColor(Color.parseColor("#66E8DFAF"));
            pill.setBackground(shape);
            WindowManager.LayoutParams pillParams =
                    params(dp(128), dp(6), Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, false);
            pillParams.y = dp(7);
            wm.addView(pill, pillParams);

            // 顶部拦截条：任何界面从顶部下滑都不会拉出状态栏
            topStrip = new View(this);
            topStrip.setOnTouchListener(new View.OnTouchListener() {
                @Override
                public boolean onTouch(View v, MotionEvent e) {
                    if (e.getActionMasked() == MotionEvent.ACTION_DOWN) {
                        Log.i(TAG, "top strip consumed down");
                        collapseShade();
                    }
                    return true;
                }
            });
            wm.addView(topStrip,
                    params(WindowManager.LayoutParams.MATCH_PARENT, dp(TOP_STRIP_DP), Gravity.TOP, true));
            Log.i(TAG, "overlays installed");
        } catch (Throwable t) {
            Log.e(TAG, "install failed", t);
        }
    }

    private void remove() {
        for (View v : new View[]{bottomStrip, leftStrip, rightStrip, pill, topStrip}) {
            if (v != null) {
                try {
                    wm.removeView(v);
                } catch (Throwable ignored) {
                }
            }
        }
        bottomStrip = leftStrip = rightStrip = pill = topStrip = null;
    }

    private void buzz() {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(24);
            }
        } catch (Throwable ignored) {
        }
    }

    private static final int MODE_BOTTOM = 0;
    private static final int MODE_LEFT = 1;
    private static final int MODE_RIGHT = 2;

    private class SwipeWatcher implements View.OnTouchListener {
        private final int mode;
        private float startX, startY;
        private long startAt;

        SwipeWatcher(int mode) {
            this.mode = mode;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    startX = e.getRawX();
                    startY = e.getRawY();
                    startAt = System.currentTimeMillis();
                    return true;

                case MotionEvent.ACTION_UP:
                    float dx = e.getRawX() - startX;
                    float dy = e.getRawY() - startY;
                    long cost = System.currentTimeMillis() - startAt;

                    if (mode == MODE_BOTTOM) {
                        if (-dy >= dp(SWIPE_MIN_DP) && Math.abs(dx) <= dp(260)) {
                            boolean held = cost >= HOLD_MS;
                            performGlobalAction(held ? GLOBAL_ACTION_RECENTS : GLOBAL_ACTION_HOME);
                        }
                    } else if (mode == MODE_LEFT) {
                        if (dx >= dp(SWIPE_MIN_DP)) {
                            performGlobalAction(GLOBAL_ACTION_BACK);
                        }
                    } else {
                        if (-dx >= dp(SWIPE_MIN_DP)) {
                            performGlobalAction(GLOBAL_ACTION_BACK);
                        }
                    }
                    return true;

                default:
                    return true;
            }
        }
    }

    /** 系统电源菜单的窗口类名（Sony Android 8 上是 GlobalActionsDialog 之类） */
    private long lastPowerMenuAt = 0L;

    private void maybeReplacePowerMenu(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            return;
        }
        CharSequence cls = event.getClassName();
        if (cls == null) {
            return;
        }
        if (!cls.toString().contains("GlobalActions")) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastPowerMenuAt < 900L) {
            return;
        }
        lastPowerMenuAt = now;
        openPodPowerMenu();
    }

    private void openPodPowerMenu() {
        try {
            Intent intent = new Intent(this, MainActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            intent.putExtra("pod_power_menu", true);
            startActivity(intent);
        } catch (Exception e) {
            Log.w(TAG, "open pod power menu failed", e);
        }
    }

    @Override
    public boolean onUnbind(Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    private AccessibilityNodeInfo findLabelNode(String label) {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows == null || windows.isEmpty()) {
                return null;
            }
            for (AccessibilityWindowInfo w : windows) {
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null) {
                    continue;
                }
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(label);
                if (nodes == null || nodes.isEmpty()) {
                    continue;
                }
                for (AccessibilityNodeInfo n : nodes) {
                    Rect r = new Rect();
                    n.getBoundsInScreen(r);
                    if (r.width() > 0 && r.height() > 0) {
                        return n;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "findLabelNode failed: " + label, e);
        }
        return null;
    }

    private boolean clickLabel(String label) {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows == null || windows.isEmpty()) {
                return false;
            }
            for (AccessibilityWindowInfo w : windows) {
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null) {
                    continue;
                }
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(label);
                if (nodes == null) {
                    continue;
                }
                for (AccessibilityNodeInfo n : nodes) {
                    AccessibilityNodeInfo target = n;
                    int guard = 0;
                    while (target != null && !target.isClickable() && guard < 4) {
                        target = target.getParent();
                        guard++;
                    }
                    if (target != null && target.isClickable()
                            && target.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
                        return true;
                    }
                    Rect r = new Rect();
                    n.getBoundsInScreen(r);
                    if (r.width() > 0 && r.height() > 0) {
                        return tapAt(r.centerX(), r.centerY());
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "clickLabel failed: " + label, e);
        }
        return false;
    }

    private boolean tapAt(int x, int y) {
        try {
            Path path = new Path();
            path.moveTo(x, y);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(path, 0, 60);
            GestureDescription gesture = new GestureDescription.Builder().addStroke(stroke).build();
            return dispatchGesture(gesture, null, null);
        } catch (Exception e) {
            Log.w(TAG, "tapAt failed", e);
            return false;
        }
    }

}
