package io.github.xejohv.podclassicxzs.gestures;

import android.accessibilityservice.AccessibilityService;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.view.accessibility.AccessibilityEvent;
import android.os.Vibrator;
import android.view.KeyEvent;

import java.lang.reflect.Method;
import java.util.List;

import android.content.Intent;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import io.github.xejohv.podclassicxzs.activity.MainActivity;
import io.github.xejohv.podclassicxzs.values.Colors;

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

    /** 本应用包名：遍历窗口找系统按钮时必须跳过自己的窗口，否则会点回自己造成死循环 */
    private static final String SELF_PKG = "io.github.xejohv.podclassicxzs";

    /** 服务单例，供 App 内部调用（例如电源菜单要代点系统按钮） */
    private static GestureService instance;

    public static GestureService getInstance() {
        return instance;
    }

    /** 回 PodClassic 桌面（激活设备管理员后从系统设置页脱身用） */
    public static void goHome() {
        GestureService s = instance;
        if (s == null) {
            return;
        }
        try {
            s.performGlobalAction(GLOBAL_ACTION_HOME);
        } catch (Exception e) {
            Log.w(TAG, "goHome failed", e);
        }
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
    private static final float SIDE_STRIP_DP = 24f;
    /** 侧边返回手势：横向要够长、纵向不能太斜，否则点列表时容易误触 */
    private static final float SIDE_SWIPE_MIN_DP = 56f;
    private static final float SIDE_SWIPE_MAX_SLOPE_DP = 60f;
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
        if (pendingExitBar != 0) {
            boolean raised = pendingExitBar == 2;
            pendingExitBar = 0;
            showExitBarInternal(raised);
        }
        // 开机/服务重连时，插线面板可能早就晾在屏幕上了（那时还没事件可收），补几枪
        for (int i = 1; i <= 4; i++) {
            handler.postDelayed(new Runnable() {
                public void run() {
                    maybeAcceptUsbDialog();
                }
            }, 3000L * i);
        }
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
        if (pkg != null && "com.android.settings".equals(pkg.toString())) {
            // 插数据线后系统弹出的「请选择 USB 的使用方式」，自动选「传输文件」
            maybeAcceptUsbDialog();
        }
    }

    // ---------------- 插线弹窗自动应答 ----------------

    private long usbAcceptAt = 0L;

    /**
     * 这台机器（docomo 版 SO-03J）插上数据线后，系统 Settings 里的
     * UsbModeChooserReceiver 会拉起「请选择 USB 的使用方式」面板，默认停在
     * 「为此设备充电」，adb/MTP 都不可用。该组件属系统应用、无 root 无法禁用，
     * 所以由无障碍在面板出现时替用户点一下「传输文件」。
     * 只在同时能看到「为此设备充电」和「传输文件」两个选项时才动手，避免误点。
     */
    private void maybeAcceptUsbDialog() {
        long now = SystemClock.uptimeMillis();
        if (now - usbAcceptAt < 1200L) {
            return;
        }
        usbAcceptAt = now;
        handler.postDelayed(new Runnable() {
            public void run() {
                try {
                    if (findLabelNode("为此设备充电") != null && findLabelNode("传输文件") != null) {
                        if (clickLabel("传输文件")) {
                            Log.i(TAG, "usb mode chooser -> 传输文件");
                        }
                        return;
                    }
                    // 选中 MTP 后，部分 ROM 还会弹「允许访问设备数据？」（拒绝/允许）
                    // 只在标题、两个按钮同时齐备时才点，避免误点其它权限弹窗
                    if (findLabelNode("允许访问设备数据") != null
                            && findExactLabel("拒绝") != null
                            && findExactLabel("允许") != null) {
                        if (clickExactLabel("允许")) {
                            Log.i(TAG, "mtp permission -> 允许");
                        }
                    }
                } catch (Throwable t) {
                    Log.w(TAG, "maybeAcceptUsbDialog failed", t);
                }
            }
        }, 300L);
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
            // 系统电源菜单是 system 层窗口（Sony Android 8 上是 LegacyGlobalActions），
            // 无障碍事件根本收不到，只能在这个巡检里主动扫窗口把它顶掉
            pollPowerMenu();
            guardExitBar();
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
            pill.setVisibility(pillRequestedVisible ? View.VISIBLE : View.GONE);

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
        for (View v : new View[]{bottomStrip, leftStrip, rightStrip, pill, topStrip, exitBar}) {
            if (v != null) {
                try {
                    wm.removeView(v);
                } catch (Throwable ignored) {
                }
            }
        }
        bottomStrip = leftStrip = rightStrip = pill = topStrip = exitBar = null;
    }

    // ---------------- 系统设置 / 相机 的悬浮控制器 ----------------
    //
    // 这台机器的状态栏和导航栏是整体拿掉的，进了系统设置、或进了全屏相机之后，
    // 屏幕上没有任何"退出去"的按钮。所以由无障碍层盖一条自己的控制条：
    // 左边「‹ 返回」代按系统返回，右边「iPod」直接回桌面（PodClassic 就是桌面）。

    private View exitBar;    /** 浮条刚显示时无障碍拿到的 root 可能还是上一个窗口，给 1.5 秒缓冲再判断 */
    private long exitBarShownAt = 0L;

    private static final String POD_PACKAGE = "io.github.xejohv.podclassicxzs";

    private static final float EXIT_BAR_W_DP = 250f;
    /** 窗口高度（触摸区），里面只放一条 iOS 式细横条 */
    private static final float EXIT_BAR_H_DP = 44f;
    /** iOS home indicator 那条横条的宽/高 */
    private static final float EXIT_CAPSULE_W_DP = 132f;
    private static final float EXIT_CAPSULE_H_DP = 5f;
    /** 上滑多远算"退出去" */
    private static final float EXIT_SWIPE_DP = 40f;
    /**
     * 系统设置页/彩蛋页：真正贴到屏幕下沿。
     * dumpsys 实测：无障碍窗口坐标空间高 2082px，屏幕可见只有 1920px，
     * BOTTOM 原点是 2082，所以 y 要给 +162px（≈48dp）窗口底才落在 1920 的屏底；
     * 给 0 会悬空 48dp（就是之前"没贴底"的原因），给负值会被推出屏幕
     */
    private static final float EXIT_BAR_MARGIN_LOW_DP = 48f;
    /** 相机页：抬到快门（距底 24~84dp）和手势提示条（112~150dp）之上 */
    private static final float EXIT_BAR_MARGIN_HIGH_DP = 210f;
    /** 三秒没碰它就淡下去，别一直压着画面 */
    private static final long EXIT_BAR_FADE_MS = 3000L;

    private final Runnable fadeExitBar = new Runnable() {
        @Override
        public void run() {
            if (exitBar != null) {
                // 三秒没碰它就淡下去（仍在原位，随时一拖就走）
                exitBar.animate().alpha(0.82f).setDuration(400L).start();
            }
        }
    };

    /**
     * 显示悬浮控制器（幂等，重复调用不会叠出第二条）
     *
     * @param raised true = 抬到画面下部（相机页用，避开快门）；false = 贴底（系统设置页用）
     */
    public static void showExitBar(boolean raised) {
        GestureService s = instance;
        if (s == null) {
            // 服务刚好在重连（例如刚装完包/进程重启），先记下来，连上以后立刻补显示
            Log.w(TAG, "showExitBar: service not connected, pending raised=" + raised);
            pendingExitBar = raised ? 2 : 1;
            return;
        }
        pendingExitBar = 0;
        s.showExitBarInternal(raised);
    }

    /**
     * 底部那条"home indicator"细横线（pill）的显隐。
     * 全屏看视频时它既不能点、又占着画面下沿，直接收掉。
     */
    public static void setPillVisible(boolean visible) {
        pillRequestedVisible = visible;
        GestureService s = instance;
        if (s == null) {
            return;
        }
        s.setPillVisibleInternal(visible);
    }

    /** 服务没连上时先记下目标状态，install() 建好 pill 后按它来 */
    private static boolean pillRequestedVisible = true;

    private void setPillVisibleInternal(final boolean visible) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (pill == null) {
                    return;
                }
                try {
                    pill.setVisibility(visible ? View.VISIBLE : View.GONE);
                } catch (Throwable ignored) {
                }
            }
        });
    }

    /** 无障碍服务当前是否已连接（悬浮条、边缘手势都靠它） */
    public static boolean isConnected() {
        return instance != null;
    }

    /** 收起悬浮控制器 */
    public static void hideExitBar() {
        pendingExitBar = 0;
        GestureService s = instance;
        if (s == null) {
            return;
        }
        s.hideExitBarInternal();
    }

    /** 服务未连接时暂存的"要显示悬浮控制器"请求：0 无、1 贴底、2 抬起 */
    private static int pendingExitBar = 0;

    /** 当前浮条是否是"抬起"位置，切页时如果位置要求变了要重建一次 */
    private boolean exitBarRaised = false;

    private void showExitBarInternal(final boolean raised) {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (wm == null) {
                    return;
                }
                // 已经显示着，但新页面要求的位置不同（比如从「附加程序」抬起 → 相机贴底），重建一次
                if (exitBar != null) {
                    if (exitBarRaised == raised) {
                        return;
                    }
                    try {
                        wm.removeView(exitBar);
                    } catch (Throwable ignored) {
                    }
                    exitBar = null;
                }
                try {
                    exitBar = buildExitHandle();
                    exitBarRaised = raised;
                    int barH = dp(EXIT_BAR_H_DP);
                    int margin = dp(raised ? EXIT_BAR_MARGIN_HIGH_DP : EXIT_BAR_MARGIN_LOW_DP);
                    WindowManager.LayoutParams lp = params(dp(EXIT_BAR_W_DP), barH,
                            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL, true);
                    // BOTTOM + 正值 y = 从底边向上抬，这台机器实测就是这么走的
                    lp.y = margin;
                    wm.addView(exitBar, lp);
                    exitBarShownAt = SystemClock.elapsedRealtime();
                    handler.removeCallbacks(fadeExitBar);
                    handler.postDelayed(fadeExitBar, EXIT_BAR_FADE_MS);
                    Log.i(TAG, "exit bar shown, raised=" + raised);
                } catch (Throwable t) {
                    exitBar = null;
                    Log.w(TAG, "showExitBar failed", t);
                }
            }
        });
    }

    private void hideExitBarInternal() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (exitBar == null) {
                    return;
                }
                try {
                    if (wm != null) {
                        wm.removeView(exitBar);
                    }
                } catch (Throwable ignored) {
                }
                exitBar = null;
                handler.removeCallbacks(fadeExitBar);
                Log.i(TAG, "exit bar hidden");
            }
        });
    }

    /**
     * 悬浮退出把手：一块贴底的毛玻璃胶囊，里面只有"上滑"的暗示图形，不放任何文字。
     * 交互照搬接电话的滑动接听——按住往上拖过阈值就退出去（系统设置/相机按返回，本应用内回主菜单）。
     */
    private View buildExitHandle() {
        return new ExitHandle(this);
    }

    private class ExitHandle extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path icon = new Path();
        private final float barW;
        private final float barH;
        private final float threshold;
        private float downRawY = 0f;
        private float dragPx = 0f;
        private boolean tracking = false;

        ExitHandle(Context c) {
            super(c);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            barW = dp(EXIT_CAPSULE_W_DP);
            barH = dp(EXIT_CAPSULE_H_DP);
            // 拖动阈值：跟 iOS 一样，往上轻轻一推就走
            threshold = dp(EXIT_SWIPE_DP);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float w = getWidth();
            final float h = getHeight();
            final float progress = Math.min(1f, dragPx / threshold);
            final float lift = dragPx * 0.62f;
            final float centerX = w / 2f;
            final float centerY = h - dp(10f) - lift;
            final float left = centerX - barW / 2f;
            final float right = centerX + barW / 2f;
            final float rad = barH / 2f;

            // 1) 一层很轻的影：让这条横条在白色页面上也分得出来
            paint.setShader(null);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb((int) (64 + progress * 26), 16, 20, 26));
            canvas.drawRoundRect(left - dp(1f), centerY - rad + dp(1.1f),
                    right + dp(1f), centerY + rad + dp(1.6f), rad + dp(1f), rad + dp(1f), paint);

            // 2) 主横条：iOS home indicator 那种中性灰，不是黑也不是纯白，
            //    暗画面（相机预览）和亮页面（设置、列表）上都读得出来。
            paint.setColor(Color.argb((int) (206 + progress * 44), 154, 161, 170));
            canvas.drawRoundRect(left, centerY - rad, right, centerY + rad, rad, rad, paint);

            // 3) 上沿一丝浅高光，给一点玻璃感，但不刺眼
            paint.setColor(Color.argb((int) (56 + progress * 54), 255, 255, 255));
            canvas.drawRoundRect(left + dp(7f), centerY - rad + dp(0.7f),
                    right - dp(7f), centerY - rad + dp(1.7f), dp(0.6f), dp(0.6f), paint);
        }

        /** 玻璃质感：顶部接近透明的高光，中间略灰，底部再压一层亮，做出厚度 */
        private Shader glassFor(float h, float progress) {
            int a0 = (int) (238 + progress * 12);
            int a1 = (int) (150 + progress * 40);
            int a2 = (int) (196 + progress * 30);
            return new LinearGradient(
                    0f, 0f, 0f, h,
                    new int[]{
                            Color.argb(a0, 255, 255, 255),
                            Color.argb(a1, 246, 248, 250),
                            Color.argb(a2, 226, 232, 238)
                    },
                    new float[]{0f, 0.46f, 1f},
                    Shader.TileMode.CLAMP
            );
        }

        /** 液面高光：一团柔和的径向白，落在胶囊左上方 */
        private Shader glowFor(float gx, float gy, float r) {
            return new RadialGradient(
                    gx, gy, r,
                    new int[]{Color.argb(120, 255, 255, 255), Color.argb(0, 255, 255, 255)},
                    new float[]{0f, 1f},
                    Shader.TileMode.CLAMP
            );
        }


        @Override
        public boolean onTouchEvent(MotionEvent e) {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    tracking = true;
                    downRawY = e.getY();
                    dragPx = 0f;
                    animate().cancel();
                    setAlpha(1f);
                    invalidate();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    if (!tracking) {
                        return true;
                    }
                    dragPx = Math.max(0f, downRawY - e.getY());
                    invalidate();
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!tracking) {
                        return true;
                    }
                    tracking = false;
                    if (dragPx >= threshold) {
                        dragPx = 0f;
                        invalidate();
                        exitFromHandle();
                    } else {
                        dragPx = 0f;
                        invalidate();
                        handler.removeCallbacks(fadeExitBar);
                        handler.postDelayed(fadeExitBar, EXIT_BAR_FADE_MS);
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    /** 拖动越过阈值：退出去 */
    private void exitFromHandle() {
        buzz();
        String pkg = activePackage();
        // 在本应用内（比如 iPod 彩蛋页）全局返回不总是生效，直接调本体的"回主菜单"，一定能退出来
        if (POD_PACKAGE.equals(pkg)) {
            try {
                io.github.xejohv.podclassicxzs.base.Core.INSTANCE.home();
            } catch (Throwable ignored) {
            }
            hideExitBarInternal();
            return;
        }
        // 原系统设置里退出来：直接把 PodClassic 唤起并回到主菜单，
        // 这样下次进设置仍要老老实实点 6 次关于本机
        if (pkg != null && pkg.startsWith("com.android.settings")) {
            try {
                Intent home = new Intent();
                home.setClassName(POD_PACKAGE, POD_PACKAGE + ".activity.MainActivity");
                home.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                home.putExtra("pod_go_home", true);
                startActivity(home);
                hideExitBarInternal();
                return;
            } catch (Throwable ignored) {
            }
        }
        try {
            performGlobalAction(GLOBAL_ACTION_BACK);
        } catch (Throwable ignored) {
        }
    }

    /** 当前前台窗口属于哪个应用；拿不到就返回 null */
    private String activePackage() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) {
                return root.getPackageName().toString();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
    /** 悬浮控制器只该活在系统设置和本应用里，跑到别处就收掉，别一直挂在屏幕上 */
    private void guardExitBar() {
        if (exitBar == null) {
            return;
        }
        if (SystemClock.elapsedRealtime() - exitBarShownAt < 1500L) {
            return;
        }
        String pkg;
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root == null || root.getPackageName() == null) {
                return;
            }
            pkg = root.getPackageName().toString();
        } catch (Throwable t) {
            return;
        }
        if ("com.android.settings".equals(pkg)
                || pkg.startsWith("com.sonymobile")
                || pkg.startsWith("com.sonyericsson")
                || SELF_PKG.equals(pkg)) {
            return;
        }
        hideExitBarInternal();
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
                        if (dx >= dp(SIDE_SWIPE_MIN_DP) && Math.abs(dy) <= dp(SIDE_SWIPE_MAX_SLOPE_DP)) {
                            performGlobalAction(GLOBAL_ACTION_BACK);
                        }
                    } else {
                        if (-dx >= dp(SIDE_SWIPE_MIN_DP) && Math.abs(dy) <= dp(SIDE_SWIPE_MAX_SLOPE_DP)) {
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

    /**
     * 主动扫窗口找系统电源菜单：屏幕上只要出现「关机」/「重新启动」标签，
     * 就把 PodClassic 自己的菜单盖上来。
     *
     * 注意：这里**不能**先按返回把系统菜单关掉 —— 关掉以后「关机 / 重新启动」
     * 这两个按钮就没了，后面 clickLabel 找不到节点，点下去等于空点，用户看到的是
     * "菜单没了但手机没重启"。所以系统菜单要留在下面当"执行层"，我们的菜单只做外壳。
     * 自己的菜单里也有「关机」字样，但 findLabelNode / clickLabel 会跳过本应用自己的窗口，不会自激。
     */
    private void pollPowerMenu() {
        if (System.currentTimeMillis() - lastPowerMenuAt < 1200L) {
            return;
        }
        AccessibilityNodeInfo node = findLabelNode("关机");
        if (node == null) {
            node = findLabelNode("重新启动");
        }
        if (node == null) {
            return;
        }
        lastPowerMenuAt = System.currentTimeMillis();
        openPodPowerMenu();
    }

    private void openPodPowerMenu() {
        showPowerOverlay();
    }

    // ------------------------------------------------------------------
    // PodClassic 自己的电源菜单：做成无障碍浮层，盖在索尼那张系统电源菜单之上。
    // 为什么要盖在系统菜单上而不是把它关掉：本机没 root，"关机/重启"只能靠点
    // 系统菜单里的按钮来执行；一旦把系统菜单按返回关掉，按钮节点就没了，点下去等于空点。
    // 所以系统菜单留在下面当"执行层"，这层只负责长相和两行按钮。
    // ------------------------------------------------------------------

    private View powerOverlay;

    private static final float POWER_CARD_W_DP = 268f;
    private static final float POWER_CARD_H_DP = 156f;
    private static final float POWER_ROW_DP = 58f;
    private static final float POWER_HEAD_DP = 50f;

    private void showPowerOverlay() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (powerOverlay != null || wm == null) {
                    return;
                }
                try {
                    powerOverlay = new PowerOverlay(GestureService.this);
                    wm.addView(powerOverlay,
                            params(WindowManager.LayoutParams.MATCH_PARENT,
                                    WindowManager.LayoutParams.MATCH_PARENT,
                                    Gravity.TOP | Gravity.LEFT, true));
                } catch (Throwable t) {
                    powerOverlay = null;
                    Log.w(TAG, "showPowerOverlay failed", t);
                }
            }
        });
    }

    private void hidePowerOverlay() {
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (powerOverlay == null) {
                    return;
                }
                try {
                    wm.removeView(powerOverlay);
                } catch (Throwable ignored) {
                }
                powerOverlay = null;
            }
        });
    }

    private class PowerOverlay extends View {
        private final Paint pp = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF card = new RectF();
        private float cardL, cardT, cardW, rowH;
        private int pressedRow = -1;
        private boolean done = false;

        PowerOverlay(Context c) {
            super(c);
            setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            cardW = dp(POWER_CARD_W_DP);
            cardL = (w - cardW) / 2f;
            cardT = h * 0.32f;
            rowH = dp(POWER_ROW_DP);
            int cardH = (int) (dp(POWER_HEAD_DP) + rowH * 2 + dp(10f));
            card.set(cardL, cardT, cardL + cardW, cardT + cardH);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            final float w = getWidth();
            final float h = getHeight();
            final int ink = Colors.INSTANCE.getText();
            final int body = Colors.INSTANCE.getColor_primary();
            final int panel = Colors.INSTANCE.getLcd_panel();
            // 线色跟着文字走（半透明黑 / 半透明白），而不是用固定的 divider：
            // divider 是给白面板调的灰色，压在机身色卡片上往往和底色同亮度，直接消失。
            final int hairline = Color.argb(56, Color.red(ink), Color.green(ink), Color.blue(ink));
            final int hairlineSoft = Color.argb(36, Color.red(ink), Color.green(ink), Color.blue(ink));

            pp.setShader(null);
            pp.setStyle(Paint.Style.FILL);
            // 遮罩：这一版回到"近黑"。
            // 试过机身色压暗（金桔深橄榄 / 暗黑深灰），好看但 alpha 只到 238 时
            // 下层系统电源菜单仍会透出七八个点，出现两层菜单叠在一起的观感；
            // 压到能完全盖住就已经接近黑。所以干脆用中性的近黑 + 极淡机身色，
            // 保证"只看到我们这一层菜单"，同时留一点点机身色的温度而不是纯 #000。
            // 遮罩：机身色压到 30% 亮度 + 完全不透明。
            // 之前栽过两次：一次用机身色但只压 20% 且 alpha 238，下层系统电源菜单的
            // 白色卡片会透出来七八个点，两层菜单叠一起；一次为了盖住干脆用中性近黑，
            // 结果和金桔/冰蓝这些机身色完全不对付。
            // 现在实测验证：alpha 给到 255 就能彻底盖住（层级问题不在颜色上），
            // 所以可以既保留机身色调、又保证干净。金桔=深棕，暖银=深暖灰，冰蓝=深蓝灰。
            final int veil = Color.argb(255, Color.red(Colors.INSTANCE.bodyDarkened(0.30f)),
                    Color.green(Colors.INSTANCE.bodyDarkened(0.30f)),
                    Color.blue(Colors.INSTANCE.bodyDarkened(0.30f)));
            canvas.drawColor(veil);
            // 卡片周围再聚一层柔和的暗角，视线才落在卡片上
            pp.setShader(new RadialGradient(w / 2f, card.centerY(), h * 0.66f,
                    Color.argb(84, 0, 0, 0), Color.argb(0, 0, 0, 0), Shader.TileMode.CLAMP));
            canvas.drawRect(0f, 0f, w, h, pp);
            pp.setShader(null);

            // 卡片落影
            pp.setColor(Color.argb(96, 0, 0, 0));
            canvas.drawRoundRect(cardL + dp(2f), cardT + dp(5f), card.left + cardW + dp(2f),
                    card.bottom + dp(7f), dp(12f), dp(12f), pp);

            // 卡片底色跟随机身配色，取"亮阶"背景色：
            // 暖银=米白、冰蓝=浅蓝白、金桔=淡黄、暗黑=深灰，都比机身主色亮一档。
            // 用亮阶而不是主色，一是更像 iPod 那块浅色菜单面板，
            // 二是暗黑主题下主色(#2C2C2C)会和黑遮罩糊成一片，亮阶(#3C3C3C)才有边界。
            pp.setColor(panel);
            canvas.drawRoundRect(card, dp(12f), dp(12f), pp);
            // 上缘高光：机身主色本身就是比亮阶更饱和的一档，拿来当一条细边
            pp.setColor(body);
            canvas.drawRect(cardL + dp(3f), cardT + dp(1f),
                    card.left + cardW - dp(3f), cardT + dp(2f), pp);
            // 描边
            pp.setStyle(Paint.Style.STROKE);
            pp.setStrokeWidth(dp(1f));
            pp.setColor(hairline);
            canvas.drawRoundRect(card, dp(12f), dp(12f), pp);
            pp.setStyle(Paint.Style.FILL);

            // 标题
            pp.setTypeface(Typeface.DEFAULT_BOLD);
            pp.setTextAlign(Paint.Align.LEFT);
            pp.setTextSize(dp(17f));
            pp.setColor(ink);
            canvas.drawText("电源", cardL + dp(22f), cardT + dp(33f), pp);

            // 标题下分隔线
            pp.setColor(hairline);
            canvas.drawRect(cardL + dp(1f), cardT + dp(POWER_HEAD_DP),
                    card.left + cardW - dp(1f), cardT + dp(POWER_HEAD_DP) + dp(1f), pp);

            // 两行
            float rowTop = cardT + dp(POWER_HEAD_DP) + dp(1f);
            String[] rows = {"关机", "重新启动"};
            for (int i = 0; i < rows.length; i++) {
                float rt = rowTop + i * rowH;
                if (i == pressedRow) {
                    // 按下行沿用列表的深色选中条（原版 iPod 的选中效果），字反过来用亮色
                    pp.setShader(new LinearGradient(0f, rt, 0f, rt + rowH,
                            Colors.INSTANCE.getIpod_selected_top(),
                            Colors.INSTANCE.getIpod_selected_bottom(), Shader.TileMode.CLAMP));
                    canvas.drawRect(cardL + dp(1f), rt, card.left + cardW - dp(1f), rt + rowH, pp);
                    pp.setShader(null);
                    pp.setTypeface(Typeface.DEFAULT_BOLD);
                    pp.setTextSize(dp(20f));
                    pp.setColor(Colors.INSTANCE.getSelected_text());
                    canvas.drawText(rows[i], cardL + dp(26f), rt + rowH / 2f + dp(7f), pp);
                } else {
                    pp.setTypeface(Typeface.DEFAULT_BOLD);
                    pp.setTextSize(dp(20f));
                    pp.setColor(ink);
                    canvas.drawText(rows[i], cardL + dp(26f), rt + rowH / 2f + dp(7f), pp);
                }
                if (i == 0) {
                    pp.setShader(null);
                    pp.setColor(hairlineSoft);
                    canvas.drawRect(cardL + dp(20f), rt + rowH,
                            card.left + cardW - dp(20f), rt + rowH + dp(1f), pp);
                }
            }
            // 卡片右下的极淡指示：点空白处取消
            pp.setShader(null);
            pp.setTextSize(dp(11f));
            pp.setColor(Colors.INSTANCE.getText_secondary());
            pp.setTextAlign(Paint.Align.RIGHT);
            canvas.drawText("点空白取消", card.left + cardW - dp(16f), card.bottom - dp(9f), pp);
            pp.setTextAlign(Paint.Align.LEFT);

            // 顶部那条细进度条不再需要，这里留空
        }

        @Override
        public boolean onTouchEvent(MotionEvent e) {
            if (done) {
                return true;
            }
            float rowTop = cardT + dp(POWER_HEAD_DP) + dp(1f);
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    if (e.getY() >= rowTop && e.getY() < rowTop + rowH * 2
                            && e.getX() >= cardL && e.getX() <= card.left + cardW) {
                        pressedRow = (int) ((e.getY() - rowTop) / rowH);
                        invalidate();
                    }
                    return true;
                case MotionEvent.ACTION_MOVE:
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (pressedRow >= 0) {
                        int row = pressedRow;
                        pressedRow = -1;
                        invalidate();
                        execRow(row);
                    } else {
                        done = true;
                        hidePowerOverlay();
                        // 取消：把下面那张系统菜单也按返回关掉
                        try {
                            performGlobalAction(GLOBAL_ACTION_BACK);
                        } catch (Throwable ignored) {
                        }
                    }
                    return true;
                default:
                    return true;
            }
        }
    }

    /** 点一行：让无障碍去点系统菜单里的同名按钮；点不到就给用户让出系统菜单 */
    private void execRow(int row) {
        buzz();
        String label = row == 0 ? "关机" : "重新启动";
        boolean ok = clickLabel(label);
        hidePowerOverlay();
        Log.i(TAG, "power exec " + label + " ok=" + ok);
        if (!ok) {
            // clickLabel 自己已经失败：不要假装成功，露出系统菜单让用户手动点
            hideExitBarInternal();
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
                if (root == null || isSelfWindow(root)) {
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

    /** 文字必须完全相等（避免 "允许" 命中 "允许访问设备数据" 这类标题） */
    private AccessibilityNodeInfo findExactLabel(String label) {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows == null || windows.isEmpty()) {
                return null;
            }
            for (AccessibilityWindowInfo w : windows) {
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null || isSelfWindow(root)) {
                    continue;
                }
                List<AccessibilityNodeInfo> nodes = root.findAccessibilityNodeInfosByText(label);
                if (nodes == null || nodes.isEmpty()) {
                    continue;
                }
                for (AccessibilityNodeInfo n : nodes) {
                    CharSequence t = n.getText();
                    CharSequence d = n.getContentDescription();
                    boolean hit = (t != null && t.toString().trim().equals(label))
                            || (d != null && d.toString().trim().equals(label));
                    if (!hit) {
                        continue;
                    }
                    Rect r = new Rect();
                    n.getBoundsInScreen(r);
                    if (r.width() > 0 && r.height() > 0) {
                        return n;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "findExactLabel failed: " + label, e);
        }
        return null;
    }

    private boolean clickExactLabel(String label) {
        AccessibilityNodeInfo n = findExactLabel(label);
        if (n == null) {
            return false;
        }
        try {
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
        } catch (Exception e) {
            Log.w(TAG, "clickExactLabel failed: " + label, e);
        }
        return false;
    }

    private boolean clickLabel(String label) {
        try {
            List<AccessibilityWindowInfo> windows = getWindows();
            if (windows == null || windows.isEmpty()) {
                return false;
            }
            for (AccessibilityWindowInfo w : windows) {
                AccessibilityNodeInfo root = w.getRoot();
                if (root == null || isSelfWindow(root)) {
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

    /** 这个窗口是不是 PodClassic 自己的（自己的菜单里也有"关机"字样，必须排除） */
    private boolean isSelfWindow(AccessibilityNodeInfo root) {
        CharSequence pkg = root.getPackageName();
        return pkg != null && SELF_PKG.equals(pkg.toString());
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



