package com.example.volumeswipe;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity {

    private TextView tvStatus;
    private CheckBox cbDoubao;
    private SharedPreferences sp;
    private boolean syncingDoubao = false;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());

    /** 语音说了「打开豆包 / 关闭豆包」之后，设置里那个勾也要跟着变 */
    private final Runnable syncDoubaoTask = new Runnable() {
        @Override
        public void run() {
            syncDoubaoCheck();
            ui.postDelayed(this, 800);
        }
    };

    private void syncDoubaoCheck() {
        if (cbDoubao == null || sp == null) {
            return;
        }
        boolean want = sp.getBoolean("useDoubao", true);
        if (cbDoubao.isChecked() != want) {
            syncingDoubao = true;
            try {
                cbDoubao.setChecked(want);
            } finally {
                syncingDoubao = false;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        try {
            initUi();
        } catch (Throwable t) {
            showCrash(t);
        }
    }

    private void initUi() {
        setContentView(R.layout.activity_main);

        sp = getSharedPreferences("cfg", MODE_PRIVATE);
        tvStatus = findViewById(R.id.tvStatus);
        // 唤醒/监听不再提供开关（界面已去掉那行），永远开启
        sp.edit().putBoolean("voiceOn", true).apply();

        // 录音 + 通知栏权限一起要（Android 13+ 不给通知权限，常驻通知根本不显示）
        askAudioPermission();

        // ① 后台运行
        findViewById(R.id.btnBattery).setOnClickListener(v -> {
            try {
                Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
                } catch (Exception ignored) {
                }
            }
        });

        // ② 悬浮窗权限
        findViewById(R.id.btnOverlayPerm).setOnClickListener(v -> {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                toast("打不开授权页，请手动到设置里授予「显示在其他应用上层」");
            }
        });

        // ③ 无障碍
        findViewById(R.id.btnOpen).setOnClickListener(v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)));

        // ④ 常驻
        findViewById(R.id.btnKeep).setOnClickListener(v -> {
            askAudioPermission();
            KeepService.start(this);
            toast("已请求常驻，看通知栏那条「运行中」");
            tvStatus.postDelayed(this::refreshStatus, 800);
        });

        // 摇一摇开关
        CheckBox cbShake = findViewById(R.id.cbShake);
        cbShake.setChecked(sp.getBoolean("shakeOn", true));
        cbShake.setOnCheckedChangeListener((b, checked) -> {
            sp.edit().putBoolean("shakeOn", checked).apply();
            KeepService.applyVoiceSetting(this);
            toast(checked ? "摇一摇已开启" : "摇一摇已关闭");
        });

        // 唤醒动作：什么都不做（不静音、不点屏幕）

        // 豆包语音输入法开关（默认勾选）
        cbDoubao = findViewById(R.id.cbDoubao);
        cbDoubao.setChecked(sp.getBoolean("useDoubao", true));
        cbDoubao.setOnCheckedChangeListener((b, checked) -> {
            if (syncingDoubao) {
                return; // 程序自己同步过来的勾，不当作手动操作
            }
            sp.edit().putBoolean("useDoubao", checked).apply();
            askAudioPermission();
            // 服务可能已被 ✕ 退出杀掉：不在就直接拉起来，再切词表
            KeepService.applyVoiceSetting(this);
            toast(checked ? "豆包输入法参与识别（推荐）" : "不用豆包：不用唤醒词，直接说命令");
        });

        // ⚙ 参数
        findViewById(R.id.btnSettings).setOnClickListener(v -> showSettings());

        // ☀ 允许修改系统设置（亮/暗命令需要）
        findViewById(R.id.btnWrite).setOnClickListener(v -> {
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS);
                i.setData(Uri.parse("package:" + getPackageName()));
                startActivity(i);
                toast("把「允许修改系统设置」打开，然后返回");
            } catch (Exception e) {
                try {
                    startActivity(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS));
                    toast("把「允许修改系统设置」打开，然后返回");
                } catch (Exception e2) {
                    toast("打不开系统设置页，请手动到 设置 → 应用 → 手机助手 → 修改系统设置");
                }
            }
        });

        // 📋 最近日志：通知栏看不全时用这个看（排错用）
        findViewById(R.id.btnLog).setOnClickListener(v -> {
            refreshStatusInner();
            String log = KeepService.recentLog();
            showTextDialog("最近日志（最近 5 条）",
                    log.length() > 0 ? log
                            : "还没有记录。\n\n先点 ④ 开启后台常驻服务，再说一句「嗨小白」试试。");
        });

        // ▶ 测试Mic：弹出编辑框 + 豆包键盘，你点一下键盘上的 Mic，程序记住它的位置
        findViewById(R.id.btnTestMic).setOnClickListener(v -> {
            AutoService svc = AutoService.get();
            if (svc == null) {
                toast("请先在第 ③ 步里打开无障碍服务");
                return;
            }
            svc.startMicTest();
        });

        // ⑤ 退出
        findViewById(R.id.btnExit).setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("退出")
                .setMessage("退出：命令条消失、语音停止、通知消失。\n"
                        + "不会去改系统设置里的无障碍开关（下次打开 App 自动恢复）。")
                .setPositiveButton("退出", (d, w) -> doExit())
                .setNegativeButton("取消", null)
                .show());

        // 打开 App = 恢复运行
        sp.edit().putBoolean("enabled", true).apply();
        KeepService.start(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 打开 App = 恢复运行：服务被杀（含 ✕ 退出后重进）就自动拉起
        if (sp != null && sp.getBoolean("enabled", true) && !KeepService.alive) {
            KeepService.start(this);
        }
        syncDoubaoCheck();
        ui.removeCallbacks(syncDoubaoTask);
        ui.postDelayed(syncDoubaoTask, 800);
        refreshStatus();
    }

    @Override
    protected void onPause() {
        super.onPause();
        ui.removeCallbacks(syncDoubaoTask);
    }

    private void showSettings() {
        View v = getLayoutInflater().inflate(R.layout.dialog_settings, null);
        final EditText etDur = v.findViewById(R.id.dlgDur);
        final EditText etGap = v.findViewById(R.id.dlgGap);
        final EditText etMicX = v.findViewById(R.id.dlgMicX);
        final EditText etMicY = v.findViewById(R.id.dlgMicY);
        final EditText etListen = v.findViewById(R.id.dlgListen);
        final EditText etMicDelay = v.findViewById(R.id.dlgMicDelay);

        etDur.setText(String.valueOf(sp.getInt("dur", 300)));
        etGap.setText(String.valueOf(sp.getInt("gap", 2000)));
        etMicX.setText(String.valueOf(sp.getInt("micx", 22)));
        etMicY.setText(String.valueOf(sp.getInt("micy", 66)));
        etListen.setText(String.valueOf(sp.getInt("listenms", 3000) / 1000));
        etMicDelay.setText(String.valueOf(sp.getInt("micdelay", 200)));

        new AlertDialog.Builder(this)
                .setTitle("参数设置")
                .setView(v)
                .setPositiveButton("保存", (d, which) -> {
                    int durMs = clamp(parseInt(etDur.getText().toString(), 300), 50, 5000);
                    int gapMs = clamp(parseInt(etGap.getText().toString(), 2000), 800, 10000);
                    int micX = clamp(parseInt(etMicX.getText().toString(), 22), 0, 100);
                    int micY = clamp(parseInt(etMicY.getText().toString(), 66), 0, 100);
                    int listenSec = clamp(parseInt(etListen.getText().toString(), 3), 3, 20);
                    int micDelayMs = clamp(parseInt(etMicDelay.getText().toString(), 200), 0, 3000);

                    sp.edit()
                            .putInt("dur", durMs)
                            .putInt("gap", gapMs)
                            .putInt("micx", micX)
                            .putInt("micy", micY)
                            .putInt("listenms", listenSec * 1000)
                            .putInt("micdelay", micDelayMs)
                            .apply();

                    toast("已保存");
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void doExit() {
        sp.edit().putBoolean("enabled", false).apply();
        AutoService svc = AutoService.get();
        if (svc != null) {
            svc.shutdownAll();
        }
        KeepService.stop(this);
        finishAffinity();
    }

    private void refreshStatus() {
        try {
            refreshStatusInner();
        } catch (Throwable t) {
            if (tvStatus != null) {
                tvStatus.setText("状态刷新出错：" + t);
            }
        }
    }

    private void refreshStatusInner() {
        boolean acc = isServiceEnabled();
        boolean keep = KeepService.alive;
        boolean ignored = isIgnoringBattery();
        boolean canOverlay = canDrawOverlays();
        boolean shakeOn = sp.getBoolean("shakeOn", true);
        boolean doubao = sp.getBoolean("useDoubao", true);

        String ver = "";
        try {
            ver = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception e2) {
        }
        String s = "版本：" + ver + "（v20）\n"
                + "无障碍服务：" + (acc ? "已开启 ✓" : "未开启 ✗  请点 ③") + "\n"
                + "后台常驻服务：" + (keep ? "运行中 ✓" : "未运行 ✗  请点 ④") + "\n"
                + "后台运行权限：" + (ignored ? "已允许 ✓" : "未允许 ✗  请点 ①") + "\n"
                + "悬浮窗权限：" + (canOverlay ? "已授予 ✓" : "未授予 ✗  请点 ②") + "\n"
                + "摇一摇：" + (shakeOn ? "已开启 ✓" : "已关闭") + "\n"
                + "唤醒动作：什么都不做（不静音、不点屏幕）\n"
                + "修改系统设置（亮/暗）：" + (Settings.System.canWrite(this)
                        ? "已允许 ✓" : "未允许 ✗  请点 ☀") + "\n"
                + "语音监听：开启 ✓\n"
                + "豆包输入法：" + (doubao ? "使用 ✓" : "不用（纯本地识别）") + "\n"
                + "语音状态：" + KeepService.voiceState + "\n"
                + "上次听到：" + (KeepService.lastHeard.length() > 0
                        ? KeepService.lastHeard : "（无）") + "\n"
                + (acc ? "\n摇一摇或说「嗨小白」即可触发。" : "");
        // 最近发生了什么（通知栏只能看到几行，这里能看到全部）
        String log = KeepService.recentLog();
        if (log.length() > 0) {
            s = s + "\n\n— 最近日志 —\n" + log;
        }
        tvStatus.setText(s);
    }

    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT < 23) {
            return true;
        }
        return Settings.canDrawOverlays(this);
    }

    private void askAudioPermission() {
        java.util.ArrayList<String> need = new java.util.ArrayList<>();
        if (Build.VERSION.SDK_INT >= 23
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            need.add(Manifest.permission.RECORD_AUDIO);
        }
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission("android.permission.POST_NOTIFICATIONS")
                != PackageManager.PERMISSION_GRANTED) {
            need.add("android.permission.POST_NOTIFICATIONS");
        }
        if (!need.isEmpty()) {
            requestPermissions(need.toArray(new String[0]), 101);
        }
    }

    private int clamp(int v, int lo, int hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private int parseInt(String s, int def) {
        try {
            return (int) Math.round(Double.parseDouble(s.trim()));
        } catch (Exception e) {
            return def;
        }
    }

    private double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private boolean isServiceEnabled() {
        String enabled = Settings.Secure.getString(
                getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
        if (enabled == null) {
            return false;
        }
        ComponentName cn = new ComponentName(this, AutoService.class);
        return enabled.contains(cn.flattenToString())
                || enabled.contains(cn.flattenToShortString());
    }

    private void toast(String s) {
        try {
            Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
        } catch (Exception ignored) {
        }
    }

    private boolean isIgnoringBattery() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm != null && pm.isIgnoringBatteryOptimizations(getPackageName());
    }

    private void showTextDialog(String title, String msg) {
        android.widget.ScrollView sv = new android.widget.ScrollView(this);
        TextView tv = new TextView(this);
        tv.setPadding(36, 48, 36, 36);
        tv.setTextSize(13f);
        tv.setTextIsSelectable(true);
        tv.setText(msg);
        sv.addView(tv);
        new AlertDialog.Builder(this)
                .setTitle(title)
                .setView(sv)
                .setPositiveButton("关闭", null)
                .show();
    }

    private void showCrash(Throwable t) {
        try {
            java.io.StringWriter sw = new java.io.StringWriter();
            t.printStackTrace(new java.io.PrintWriter(sw));
            String msg = sw.toString();
            if (msg.length() > 2500) {
                msg = msg.substring(0, 2500);
            }

            android.widget.ScrollView sv = new android.widget.ScrollView(this);
            TextView tv = new TextView(this);
            tv.setPadding(36, 72, 36, 36);
            tv.setTextSize(12f);
            tv.setText("启动出错，把这段截图发我：\n\n" + msg);
            sv.addView(tv);
            setContentView(sv);
        } catch (Throwable ignored) {
        }
    }
}
