package com.example.volumeswipe;

import android.app.Activity;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

/**
 * 输入法语音识别的「兜底界面」。
 *
 * 正常路径是 AutoService 里的悬浮窗（不切 App）；
 * 只有当悬浮窗被 ROM 拒绝时，才会回退到这里。
 *
 * 两者的关闭规则必须一致：
 *   命中命令       → 250ms 后关闭
 *   有字但没命中   → 1.2 秒后关闭
 *   完全没字       → listenms（默认 8 秒）后关闭
 *   点 ✕          → 立刻关闭
 */
public class VoiceInputActivity extends Activity {

    private EditText et;
    private SharedPreferences sp;
    private final Handler h = new Handler(Looper.getMainLooper());

    private String lastMatched = "";
    private Runnable matchTask;
    private Runnable closeTask;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            try {
                if (et != null && et.getText() != null) {
                    String t = Commands.normalize(et.getText().toString());
                    if (t.length() > 0) {
                        tryMatch();
                    }
                }
            } catch (Exception ignored) {
            }
            ui.postDelayed(this, 3000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_voice_input);

        KeepService.pauseWake();

        sp = getSharedPreferences("cfg", MODE_PRIVATE);
        et = findViewById(R.id.voiceEdit);

        findViewById(R.id.btnClose).setOnClickListener(v -> finish());
        findViewById(R.id.btnPickMic).setOnClickListener(v -> {
            AutoService svc = AutoService.get();
            if (svc != null) {
                svc.startPickMic();
            }
        });

        et.requestFocus();
        getWindow().setSoftInputMode(
                android.view.WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE
                        | android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        et.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                scheduleMatch();
            }
        });

        // 键盘弹出后自动点麦克风
        et.postDelayed(new Runnable() {
            @Override
            public void run() {
                tapMicButton();
            }
        }, 900);

        // 最长等待：无条件关闭
        final int listenMs = Math.max(3000, sp.getInt("listenms", 5000));
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, listenMs);

        ui.postDelayed(tick, 3000);
    }

    private void tapMicButton() {
        // 没勾豆包时不该点输入法麦克风（识别全部由本地引擎做）
        if (sp != null && !sp.getBoolean("useDoubao", true)) {
            return;
        }
        AutoService svc = AutoService.get();
        if (svc == null) {
            return;
        }
        String msg = svc.tryTapImeMic();
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
    }

    private void scheduleMatch() {
        if (matchTask != null) {
            h.removeCallbacks(matchTask);
        }
        matchTask = new Runnable() {
            @Override
            public void run() {
                tryMatch();
            }
        };
        h.postDelayed(matchTask, 350);
    }

    private void tryMatch() {
        String text = et.getText() == null ? "" : et.getText().toString();
        String t = Commands.normalize(text);
        if (t.length() == 0 || t.equals(lastMatched)) {
            return;
        }

        int cmd = Commands.match(t);
        if (cmd == Commands.CMD_NONE) {
            android.widget.Toast.makeText(this, "没匹配到命令：" + t,
                    android.widget.Toast.LENGTH_SHORT).show();
            // 有字但没命中：1.2 秒后自己关
            final String snapshot = t;
            if (closeTask != null) {
                h.removeCallbacks(closeTask);
            }
            closeTask = new Runnable() {
                @Override
                public void run() {
                    String cur = et.getText() == null ? "" : Commands.normalize(et.getText().toString());
                    if (cur.equals(snapshot)) {
                        finish();
                    }
                }
            };
            h.postDelayed(closeTask, 1200);
            return;
        }

        lastMatched = t;
        execute(cmd, t);
        android.widget.Toast.makeText(this, "执行：" + t,
                android.widget.Toast.LENGTH_SHORT).show();
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();
            }
        }, 250);
    }

    private void execute(int cmd, String text) {
        AutoService svc = AutoService.get();
        if (svc == null) {
            return;
        }
        // 统一走 AutoService 的命令分发，新命令不用重复写
        svc.executeCommand(cmd, text);
        if (cmd == Commands.CMD_EXIT) {
            finishAffinity();
        }
    }

    private String extractAppName(String text) {
        String t = Commands.normalize(text);
        String[] prefixes = {"打开", "启动"};
        for (String p : prefixes) {
            int i = t.indexOf(p);
            if (i >= 0) {
                String name = t.substring(i + p.length());
                if (name.length() > 0) {
                    return name;
                }
            }
        }
        return "";
    }

    private void adjustMediaVolume(boolean up) {
        try {
            android.media.AudioManager am =
                    (android.media.AudioManager) getSystemService(AUDIO_SERVICE);
            if (am != null) {
                am.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC,
                        up ? android.media.AudioManager.ADJUST_RAISE
                                : android.media.AudioManager.ADJUST_LOWER,
                        android.media.AudioManager.FLAG_SHOW_UI);
            }
        } catch (Exception ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacks(tick);
        super.onDestroy();
        KeepService.resumeWake();
    }

    @Override
    protected void onPause() {
        super.onPause();
        InputMethodManager imm =
                (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(et.getWindowToken(), 0);
        }
    }
}
