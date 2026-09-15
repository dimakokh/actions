package com.sendformat.noformat;

import android.util.Log;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;

public class NoFormatPlugin {

    public static final String TAG = "NoFormat";
    public static volatile boolean skipNextFormat = false;

    private static void log(String msg) {
        Log.d(TAG, msg);
    }

    public static void start() {
        log("start() called");
        try {
            ClassLoader cl = getAppClassLoader();
            if (cl == null) {
                log("classloader == null");
                return;
            }
            log("classloader = " + cl);
            hookSetItemOptions(cl);
            log("started OK");
        } catch (Throwable t) {
            log("start error: " + t);
            t.printStackTrace();
        }
    }

    public static void stop() {
        skipNextFormat = false;
        log("stopped");
    }

    private static ClassLoader getAppClassLoader() {
        try {
            Class<?> cls = Class.forName("org.telegram.messenger.ApplicationLoader");
            Object ctx = cls.getField("applicationContext").get(null);
            if (ctx != null) {
                return ctx.getClass().getClassLoader();
            }
            log("applicationContext == null");
        } catch (Throwable t) {
            log("getAppClassLoader error: " + t);
        }
        return NoFormatPlugin.class.getClassLoader();
    }

    private static void hookSetItemOptions(ClassLoader cl) {
        try {
            final Class<?> itemOptionsClass = XposedHelpers.findClass(
                    "org.telegram.ui.Components.ItemOptions", cl);
            log("ItemOptions class found: " + itemOptionsClass);

            XposedHelpers.findAndHookMethod(
                    "org.telegram.ui.MessageSendPreview",
                    cl,
                    "setItemOptions",
                    itemOptionsClass,
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            log("setItemOptions called!");
                            try {
                                Object options = param.args[0];
                                if (options == null) {
                                    log("options == null");
                                    return;
                                }
                                addItem(options);
                            } catch (Throwable t) {
                                log("afterHook error: " + t);
                            }
                        }
                    });
            log("hook installed on setItemOptions");
        } catch (Throwable t) {
            log("hookSetItemOptions error: " + t);
            t.printStackTrace();
        }
    }

    private static void addItem(Object options) {
        try {
            Runnable click = new Runnable() {
                @Override
                public void run() {
                    skipNextFormat = true;
                    log("skip flag ON");
                }
            };

            Class<?> cls = options.getClass();
            log("options class = " + cls.getName());

            java.lang.reflect.Method best = null;
            int bestScore = -1;
            for (java.lang.reflect.Method m : cls.getMethods()) {
                if (!m.getName().equals("add")) continue;
                Class<?>[] p = m.getParameterTypes();
                log("add candidate: " + m + " params=" + p.length);
                if (p.length < 4) continue;
                if (p[1] != CharSequence.class) continue;
                int score = p.length;
                if (score > bestScore) {
                    bestScore = score;
                    best = m;
                }
            }

            if (best == null) {
                log("add(...) not found");
                return;
            }

            int n = best.getParameterTypes().length;
            log("chosen add arity=" + n);
            Object[] args;
            if (n == 5) {
                args = new Object[]{0, "Отправить без форматирования", 0, 0, click};
            } else if (n == 4) {
                args = new Object[]{0, "Отправить без форматирования", 0, click};
            } else {
                log("unsupported add arity=" + n);
                return;
            }
            best.invoke(options, args);
            log("item added OK (arity=" + n + ")");
        } catch (Throwable t) {
            log("addItem error: " + t);
            t.printStackTrace();
        }
    }
}
