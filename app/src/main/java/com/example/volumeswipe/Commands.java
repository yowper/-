package com.example.volumeswipe;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * 语音命令的定义与关键词匹配。
 */
public final class Commands {

    public static final int CMD_NONE = 0;
    public static final int CMD_SKIP_AD = 1;    // 跳过广告：等 N 秒 → 上滑
    public static final int CMD_NEXT = 2;       // 下一集 / 上翻：直接上滑
    public static final int CMD_PREV = 3;       // 上一集 / 下翻：直接下滑
    public static final int CMD_PAGE_DOWN = 4;  // 下页：点 (85%, 85%)
    public static final int CMD_PAGE_UP = 5;    // 上页：点 (15%, 85%)
    public static final int CMD_RESUME = 6;     // 旧号（已并入 CMD_TOGGLE_PLAY，保留兼容）
    public static final int CMD_OPEN = 7;       // 打开某 App
    public static final int CMD_EXIT = 8;       // 小白再见：彻底退出
    public static final int CMD_VOL_UP = 9;     // 声音大一点 / 大：媒体音量 +
    public static final int CMD_VOL_DOWN = 10;  // 声音小一点 / 小：媒体音量 -
    public static final int CMD_EXIT_LIVE = 11; // 退出直播：先左划，再上拉
    public static final int CMD_DOUBAO_ON = 12;  // 打开豆包：勾上「使用豆包语音输入法」
    public static final int CMD_DOUBAO_OFF = 13; // 关闭豆包：去掉那个勾
    public static final int CMD_TEST_SECONDS = 14; // 测试读秒：探测下方到底能不能读到读秒文字
    public static final int CMD_TOGGLE_PLAY = 15;  // 停 / 放 / 继续 / 播放 / 继续播放：点 (50%,20%)
    public static final int CMD_BRIGHT_UP = 16;    // 亮：屏幕亮一点
    public static final int CMD_BRIGHT_DOWN = 17;  // 暗：屏幕暗一点
    public static final int CMD_DOUBAO_TOGGLE = 18; // 「豆包」：勾着就关、没勾就开

    private static final String[] KW_EXIT = {
            "小白再见", "再见小白", "小白拜拜"
    };
    /** 「大」在短句里也算（用户要求：声音大一点 → 大） */
    private static final String[] KW_VOL_UP = {
            "声音大", "音量大", "大点声", "大声点", "调大", "大一点", "大点"
    };
    /** 「小」在短句里也算（用户要求：声音小一点 → 小） */
    private static final String[] KW_VOL_DOWN = {
            "声音小", "音量小", "小点声", "小声点", "调小", "小一点", "小点"
    };
    /** 亮：屏幕亮度 +（需要「修改系统设置」权限） */
    private static final String[] KW_BRIGHT_UP = {
            "亮一点", "亮点", "亮些", "调亮", "屏幕亮", "变亮", "亮起来", "亮"
    };
    /** 暗：屏幕亮度 - */
    private static final String[] KW_BRIGHT_DOWN = {
            "暗一点", "暗点", "暗些", "调暗", "屏幕暗", "变暗", "暗下来", "暗"
    };
    private static final String[] KW_EXIT_LIVE = {
            // 用户要求简单化：只说「直播」也算退出直播
            "退出直播", "推出直播", "退出直拨", "直播"
    };
    private static final String[] KW_PREV = {
            "上一集", "上一部", "上一步", "下翻", "向下翻",
            // 同音字容错：级/季/期/节 都读作“ji”
            "上一级", "上一季", "上一期", "上一节", "上集"
    };
    private static final String[] KW_NEXT = {
            "下一集", "下一部", "换一集", "上翻", "向上翻",
            "下一级", "下一季", "下一期", "下一节", "下集"
    };
    private static final String[] KW_PAGE_UP = {
            "上页", "翻上页", "上一页"
    };
    private static final String[] KW_PAGE_DOWN = {
            "下页", "翻下页", "下一页"
    };
    private static final String[] KW_SKIP_AD = {
            // 用户要求简单化：只说「广告」也算退出广告。
            // （「广告」在视频里偶尔会误触发，但用户希望命令越短越好）
            "退出广告", "跳过广告", "推出广告", "退出广告片", "退出光告",
            "关掉广告", "跳过这段广告", "广告"
    };
    /** 停 / 放 / 继续 / 播放 / 继续播放：用户要求合成同一条命令 = 点 (50%,20%) */
    private static final String[] KW_TOGGLE_PLAY = {
            "暂停", "停下", "停一下", "停", "放着", "放一下", "放",
            "恢复播放", "继续播放", "播放", "继续看", "接着看", "接着播", "继续播",
            "开始播放", "接着放", "继续", "继续放"
    };
    private static final String[] KW_RESUME = {
            "恢复播放", "继续播放", "播放", "继续看", "接着看", "接着播", "继续播",
            "开始播放", "接着放", "继续"
    };
    private static final String[] KW_DOUBAO_ON = {
            "打开豆包", "开启豆包", "启用豆包", "用豆包", "豆包打开", "豆包开启"
    };
    private static final String[] KW_DOUBAO_OFF = {
            "关闭豆包", "关掉豆包", "关豆包", "不用豆包", "豆包关闭", "退出豆包", "去掉豆包"
    };
    private static final String[] KW_TEST_SECONDS = {
            "测试读秒", "读秒测试", "读秒探测", "探测读秒", "测试下方"
    };

    private Commands() {
    }

    /** 本地引擎命令模式的词表：所有命令关键词 + 打开/启动（不含唤醒词，那部分由 WakeEngine 拼） */
    public static String[] COMMAND_VOCAB() {
        Set<String> set = new LinkedHashSet<>();
        String[][] all = {
                KW_EXIT, KW_PAGE_UP, KW_PAGE_DOWN, KW_SKIP_AD, KW_EXIT_LIVE,
                KW_PREV, KW_NEXT, KW_VOL_UP, KW_VOL_DOWN,
                KW_BRIGHT_UP, KW_BRIGHT_DOWN,
                KW_DOUBAO_ON, KW_DOUBAO_OFF, KW_TEST_SECONDS, KW_TOGGLE_PLAY
        };
        for (String[] arr : all) {
            for (String k : arr) {
                set.add(k);
            }
        }
        set.add("打开");
        set.add("启动");
        set.add("豆包");
        // 「打开抖音」「打开红果」… 这些 App 名字必须提前写进词表，
        // 否则 Vosk 压根听不懂这几个名字
        for (String s : Apps.OPEN_PHRASES) {
            set.add(s);
        }
        for (String s : Apps.BARE_NAMES) {
            set.add(s);
        }
        return set.toArray(new String[0]);
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        // 只保留汉字、字母、数字：标点、空格、引号、emoji 全部丢弃
        StringBuilder sb = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch >= 0x4E00 && ch <= 0x9FFF) {
                sb.append(ch);
            } else if (ch >= 'a' && ch <= 'z') {
                sb.append(ch);
            } else if (ch >= 'A' && ch <= 'Z') {
                sb.append(ch);
            } else if (ch >= '0' && ch <= '9') {
                sb.append(ch);
            }
        }
        return sb.toString();
    }

    /** 返回命中的命令，CMD_NONE 表示没命中 */
    public static int match(String text) {
        String t = normalize(text);
        if (t.length() == 0) {
            return CMD_NONE;
        }

        // 退出指令放最前面，优先级最高
        if (hit(t, KW_EXIT)) {
            return CMD_EXIT;
        }

        // 豆包开关：必须放在「打开/启动」兑底之前，否则「打开豆包」会被当成开 App
        // 0) 「打开豆包应用」= 打开豆包这个 App（要在「打开豆包」之前判）
        if (Apps.doubaoApp(t)) {
            return CMD_OPEN;
        }
        // 1) 明确说「关」的（先判，免得「不用豆包」被「用豆包」抢走）
        if (hit(t, KW_DOUBAO_OFF)) {
            return CMD_DOUBAO_OFF;
        }
        // 2) 明确说「开」的
        if (hit(t, KW_DOUBAO_ON)) {
            return CMD_DOUBAO_ON;
        }
        // 3) 只喊一声「打开」（就这两个字）→ 当打开豆包；豆包本来开着的话，
        //    executeCommand 里会当成“已打开”忽略
        if (t.equals("打开")) {
            return CMD_DOUBAO_ON;
        }
        // 4) 只喊一声「豆包」（没说明确开关）→ 切换：
        //    勾上了「使用豆包语音输入法」就关，没勾就开（用户要求）
        if (t.contains("豆包")) {
            return CMD_DOUBAO_TOGGLE;
        }
        if (hit(t, KW_TEST_SECONDS)) {
            return CMD_TEST_SECONDS;
        }

        // 先判“页”，避免被“上一/下一”抢走
        if (hit(t, KW_PAGE_UP)) {
            return CMD_PAGE_UP;
        }
        if (hit(t, KW_PAGE_DOWN)) {
            return CMD_PAGE_DOWN;
        }
        if (hit(t, KW_EXIT_LIVE)) {
            return CMD_EXIT_LIVE;
        }
        if (hit(t, KW_SKIP_AD)) {
            return CMD_SKIP_AD;
        }
        if (hit(t, KW_PREV)) {
            return CMD_PREV;
        }
        if (hit(t, KW_NEXT)) {
            return CMD_NEXT;
        }
        // 兜底：识别成“下几集”“下一鸡”这种，只要含“下”且长度短，也当下一集
        if (t.length() <= 4 && t.contains("下")
                && (t.contains("集") || t.contains("级") || t.contains("季") || t.contains("期"))) {
            return CMD_NEXT;
        }
        if (t.length() <= 4 && t.contains("上")
                && (t.contains("集") || t.contains("级") || t.contains("季") || t.contains("期"))) {
            return CMD_PREV;
        }
        // 亮度：先说「亮/暗」（防被“大/小”抢走）；单字「亮」「暗」也认
        if (hit(t, KW_BRIGHT_UP) || (t.length() <= 4 && t.contains("亮"))) {
            return CMD_BRIGHT_UP;
        }
        if (hit(t, KW_BRIGHT_DOWN) || (t.length() <= 4 && t.contains("暗"))) {
            return CMD_BRIGHT_DOWN;
        }
        // 音量：长说法直接命中；用户要求「大」「小」这两个单字也行（限短句）
        if (hit(t, KW_VOL_UP) || (t.length() <= 4 && t.contains("大"))) {
            return CMD_VOL_UP;
        }
        if (hit(t, KW_VOL_DOWN) || (t.length() <= 4 && t.contains("小"))) {
            return CMD_VOL_DOWN;
        }
        // 停 / 放 / 继续 / 播放 / 继续播放：用户要求合成【同一条命令】= (50%,20%) 点一下
        if (hit(t, KW_TOGGLE_PLAY)) {
            return CMD_TOGGLE_PLAY;
        }
        // 用户要求：直接喊 App 名字就打开（「红果」=打开红果，「抖音」=打开抖音）
        if (Apps.isAppName(t)) {
            return CMD_OPEN;
        }
        if (t.contains("打开") || t.contains("启动")) {
            return CMD_OPEN;
        }


        return CMD_NONE;
    }

    private static boolean hit(String text, String[] keys) {
        for (String k : keys) {
            if (text.contains(k)) {
                return true;
            }
        }
        return false;
    }
}
