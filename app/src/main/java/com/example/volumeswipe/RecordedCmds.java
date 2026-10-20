package com.example.volumeswipe;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 录制模式：用户自己录下来的一串「点 / 划」动作，绑到一个语音命令名上。
 * 之后只要听到这个名字，就自动把这串动作重放一遍（多次点击 / 多次划动）。
 *
 * 存在 SharedPreferences "cfg" 里：
 * <pre>
 *   rec_names = "点赞,快进"                              —— 录过的命令名（逗号分隔）
 *   rec_点赞  = "tap:80:50:0;swipe:50:75:50:25:300:200"  —— 这个名字对应的动作
 * </pre>
 *
 * 一步的格式（坐标都是屏幕百分比 0~100，延时单位毫秒）：
 * <pre>
 *   tap:X:Y:delay                    点 (X%,Y%)，动手前先等 delay 毫秒
 *   swipe:X1:Y1:X2:Y2:dur:delay      从 (X1%,Y1%) 划到 (X2%,Y2%)，划 dur 毫秒，前等 delay
 * </pre>
 *
 * 解析结果统一成了 int[7]：{type, x1, y1, x2, y2, dur, delay}
 */
public final class RecordedCmds {

    public static final int TYPE_TAP = 0;
    public static final int TYPE_SWIPE = 1;

    private static final String KEY_NAMES = "rec_names";
    private static final String PREFIX = "rec_";

    /** 名字缓存（WakeEngine.idleVocab() 是静态方法，拿不到 Context，只能走缓存） */
    private static volatile String[] cache = new String[0];

    private RecordedCmds() {
    }

    private static SharedPreferences sp(Context ctx) {
        return ctx.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    /** 重新读一遍名字列表（服务启动 / 录完 / 删完都要调一次） */
    public static void reload(Context ctx) {
        try {
            cache = readNames(ctx);
        } catch (Throwable ignored) {
            cache = new String[0];
        }
    }

    /** 缓存里的名字（静态，给词表用） */
    public static String[] names() {
        return cache;
    }

    public static String[] readNames(Context ctx) {
        String s = sp(ctx).getString(KEY_NAMES, "");
        if (s == null || s.length() == 0) {
            return new String[0];
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String one : s.split(",")) {
            String n = Commands.normalize(one);
            if (n.length() > 0) {
                set.add(n);
            }
        }
        return set.toArray(new String[0]);
    }

    private static void writeNames(Context ctx, String[] names) {
        StringBuilder sb = new StringBuilder();
        for (String n : names) {
            if (sb.length() > 0) {
                sb.append(',');
            }
            sb.append(n);
        }
        sp(ctx).edit().putString(KEY_NAMES, sb.toString()).apply();
        cache = names;
    }

    /**
     * 听到的这句话是不是某个录制的命令名：
     * 先找完全相同的（最准），再找包含的（例如听到「点赞点赞」）。
     * 返回命中的录制名，没有就返回 null。
     */
    public static String find(Context ctx, String heard) {
        String t = Commands.normalize(heard);
        if (t.length() == 0) {
            return null;
        }
        String[] names = readNames(ctx);
        for (String n : names) {
            if (n.equals(t)) {
                return n;
            }
        }
        // 用汉字个数最多的那个先比，避免「赞」这种短名字抢走「点赞」
        String best = null;
        for (String n : names) {
            if (n.length() >= 2 && t.contains(n)) {
                if (best == null || n.length() > best.length()) {
                    best = n;
                }
            }
        }
        return best;
    }

    /** 这个名字的动作序列 */
    public static List<int[]> steps(Context ctx, String name) {
        List<int[]> out = new ArrayList<>();
        String s = sp(ctx).getString(PREFIX + Commands.normalize(name), "");
        if (s == null || s.length() == 0) {
            return out;
        }
        for (String one : s.split(";")) {
            int[] st = parseStep(one);
            if (st != null) {
                out.add(st);
            }
        }
        return out;
    }

    private static int[] parseStep(String one) {
        try {
            String[] f = one.split(":");
            if (f.length >= 4 && "tap".equals(f[0])) {
                return new int[]{TYPE_TAP,
                        clampPct(Integer.parseInt(f[1])), clampPct(Integer.parseInt(f[2])),
                        0, 0, 0, Math.max(0, Integer.parseInt(f[3]))};
            }
            if (f.length >= 7 && "swipe".equals(f[0])) {
                return new int[]{TYPE_SWIPE,
                        clampPct(Integer.parseInt(f[1])), clampPct(Integer.parseInt(f[2])),
                        clampPct(Integer.parseInt(f[3])), clampPct(Integer.parseInt(f[4])),
                        clamp(Math.max(60, Integer.parseInt(f[5])), 60, 3000),
                        Math.max(0, Integer.parseInt(f[6]))};
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static int clampPct(int v) {
        return clamp(v, 0, 100);
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    /** 保存一个录制好的命令（steps 为空也允许：等于把这个名字删掉） */
    public static void save(Context ctx, String name, List<int[]> steps) {
        String n = Commands.normalize(name);
        if (n.length() == 0) {
            return;
        }
        StringBuilder sb = new StringBuilder();
        for (int[] st : steps) {
            if (sb.length() > 0) {
                sb.append(';');
            }
            if (st[0] == TYPE_TAP) {
                sb.append("tap:").append(clampPct(st[1])).append(':').append(clampPct(st[2]))
                        .append(':').append(Math.max(0, st[6]));
            } else {
                sb.append("swipe:").append(clampPct(st[1])).append(':').append(clampPct(st[2]))
                        .append(':').append(clampPct(st[3])).append(':').append(clampPct(st[4]))
                        .append(':').append(clamp(st[5], 60, 3000))
                        .append(':').append(Math.max(0, st[6]));
            }
        }
        sp(ctx).edit().putString(PREFIX + n, sb.toString()).apply();

        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String one : readNames(ctx)) {
            if (!one.equals(n)) {
                set.add(one);
            }
        }
        set.add(n);
        writeNames(ctx, set.toArray(new String[0]));
    }

    /** 删掉一个录制命令 */
    public static void remove(Context ctx, String name) {
        String n = Commands.normalize(name);
        sp(ctx).edit().remove(PREFIX + n).apply();
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String one : readNames(ctx)) {
            if (!one.equals(n)) {
                set.add(one);
            }
        }
        writeNames(ctx, set.toArray(new String[0]));
    }

    /** 一句话描述（列表 / 日志用） */
    public static String describe(Context ctx, String name) {
        List<int[]> steps = steps(ctx, name);
        int tap = 0;
        int sw = 0;
        int ms = 0;
        for (int[] st : steps) {
            if (st[0] == TYPE_TAP) {
                tap++;
            } else {
                sw++;
            }
            ms += st[6] + (st[0] == TYPE_TAP ? 150 : st[5] + 150);
        }
        StringBuilder sb = new StringBuilder();
        sb.append(steps.size()).append(" 步");
        if (tap > 0 || sw > 0) {
            sb.append("（点 ").append(tap);
            if (sw > 0) {
                sb.append(" / 划 ").append(sw);
            }
            sb.append("）");
        }
        sb.append("，约 ").append(ms / 1000.0).append(" 秒");
        return sb.toString();
    }
}
