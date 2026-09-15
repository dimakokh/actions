package com.sendformat.noformat;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class NoFormatPlugin {

    public static final String TAG = "NoFormatPlugin";
    public static volatile boolean skipNextFormat = false;

    public static void start() {
        try {
            ClassLoader cl = getAppClassLoader();
            if (cl == null) {
                XposedBridge.log(TAG + ": classloader == null");
                return;
            }
            hookSetItemOptions(cl);
            XposedBridge.log(TAG + ": started");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": start error: " + t);
        }
    }

    public static void stop() {
        skipNextFormat = false;
        XposedBridge.log(TAG + ": stopped");
    }

    private static ClassLoader getAppClassLoader() {
        try {
            Class<?> cls = Class.forName("org.telegram.messenger.ApplicationLoader");
            Object ctx = cls.getField("applicationContext").get(null);
            if (ctx != null) {
                return ctx.getClass().getClassLoader();
            }
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": getAppClassLoader: " + t);
        }
        return NoFormatPlugin.class.getClassLoader();
    }

    private static void hookSetItemOptions(ClassLoader cl) {
        try {
            Class<?> itemOptionsClass = XposedHelpers.findClass(
                    "org.telegram.ui.Components.ItemOptions", cl);

            XposedHelpers.findAndHookMethod(
                    "org.telegram.ui.MessageSendPreview",
                    cl,
                    "setItemOptions",
                    itemOptionsClass,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            try {
                                Object options = param.args[0];
                                if (options == null) return;
                                addItem(options);
                            } catch (Throwable t) {
                                XposedBridge.log(TAG + ": afterHook: " + t);
                            }
                        }
                    });
            XposedBridge.log(TAG + ": hook installed on setItemOptions");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hookSetItemOptions error: " + t);
        }
    }

    private static void addItem(Object options) {
        try {
            Runnable click = new Runnable() {
                @Override
                public void run() {
                    skipNextFormat = true;
                    XposedBridge.log(TAG + ": skip flag ON");
                }
            };

            // Ищем подходящую перегрузку add(...)
            Class<?> cls = options.getClass();
            java.lang.reflect.Method best = null;
            int bestScore = -1;
            for (java.lang.reflect.Method m : cls.getMethods()) {
                if (!m.getName().equals("add")) continue;
                Class<?>[] p = m.getParameterTypes();
                if (p.length < 4) continue;
                if (p[1] != CharSequence.class) continue;
                int score = p.length;
                if (score > bestScore) {
                    bestScore = score;
                    best = m;
                }
            }

            if (best == null) {
                XposedBridge.log(TAG + ": add(...) not found");
                return;
            }

            int n = best.getParameterTypes().length;
            Object[] args;
            if (n == 5) {
                args = new Object[]{0, "Отправить без форматирования", 0, 0, click};
            } else if (n == 4) {
                args = new Object[]{0, "Отправить без форматирования", 0, click};
            } else {
                XposedBridge.log(TAG + ": unsupported add arity=" + n);
                return;
            }
            best.invoke(options, args);
            XposedBridge.log(TAG + ": item added (arity=" + n + ")");
        } catch (Throwable t) {
            XposedBridge.log(TAG + ": addItem error: " + t);
        }
    }
}
