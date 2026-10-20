package com.example.volumeswipe;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;

/**
 * 系统语音识别引擎（Android SpeechRecognizer）。
 * 需要手机里存在 RecognitionService；国内 ROM 往往没有，此时会自动改用 Vosk 离线引擎。
 */
public class VoiceTrigger {

    public interface Listener {
        void onHeard(String text);

        void onCommand(int cmd, String text);

        void onError(String msg);

        /** 实时音量 0~100 */
        void onLevel(int level);

        void onListenStart();

        void onListenIdle();
    }

    private final Context ctx;
    private final Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private SpeechRecognizer sr;
    private boolean running;
    private long lastTriggerTime = 0;

    private int cooldownMs() {
        return Math.max(800, ctx.getSharedPreferences("cfg", 0).getInt("gap", 2000));
    }

    private final Runnable restartTask = new Runnable() {
        @Override
        public void run() {
            listen();
        }
    };

    public VoiceTrigger(Context ctx, Listener listener) {
        this.ctx = ctx.getApplicationContext();
        this.listener = listener;
    }

    /** 返回 false 表示本机没有可用的识别服务 */
    public boolean start() {
        if (running) {
            return true;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            listener.onError("本机没有可用的语音识别服务");
            return false;
        }
        running = true;
        listen();
        return true;
    }

    public void stop() {
        running = false;
        handler.removeCallbacks(restartTask);
        if (sr != null) {
            try {
                sr.stopListening();
                sr.cancel();
                sr.destroy();
            } catch (Exception ignored) {
            }
            sr = null;
        }
    }

    private void restart(long delay) {
        if (!running) {
            return;
        }
        handler.removeCallbacks(restartTask);
        handler.postDelayed(restartTask, delay);
    }

    private void listen() {
        if (!running) {
            return;
        }
        if (sr == null) {
            try {
                sr = SpeechRecognizer.createSpeechRecognizer(ctx);
            } catch (Exception e) {
                listener.onError("创建语音识别失败：" + e.getMessage());
                running = false;
                return;
            }
            sr.setRecognitionListener(new RecognitionListener() {
                @Override
                public void onReadyForSpeech(Bundle params) {
                }

                @Override
                public void onBeginningOfSpeech() {
                    listener.onListenStart();
                }

                @Override
                public void onRmsChanged(float rmsdB) {
                    int level = (int) ((rmsdB + 2f) * 10f);
                    if (level < 0) {
                        level = 0;
                    }
                    if (level > 100) {
                        level = 100;
                    }
                    listener.onLevel(level);
                }

                @Override
                public void onBufferReceived(byte[] buffer) {
                }

                @Override
                public void onEndOfSpeech() {
                }

                @Override
                public void onError(int error) {
                    listener.onListenIdle();
                    if (error != 5 && error != 7) {
                        listener.onError("识别错误 " + error + "（" + describe(error) + "）");
                    }
                    restart(error == 5 ? 300 : 500);
                }

                @Override
                public void onResults(Bundle results) {
                    handle(results);
                    listener.onListenIdle();
                    restart(150);
                }

                @Override
                public void onPartialResults(Bundle partialResults) {
                }

                @Override
                public void onEvent(int eventType, Bundle params) {
                }
            });
        }

        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN");
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.getPackageName());

        try {
            sr.startListening(i);
        } catch (Exception e) {
            restart(800);
        }
    }

    private void handle(Bundle results) {
        ArrayList<String> list = results != null
                ? results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                : null;
        if (list == null || list.isEmpty()) {
            return;
        }

        String first = list.get(0);
        listener.onHeard(first == null ? "" : first);

        long now = System.currentTimeMillis();
        if (now - lastTriggerTime < cooldownMs()) {
            return;
        }

        for (String s : list) {
            int cmd = Commands.match(s);
            if (cmd != Commands.CMD_NONE) {
                lastTriggerTime = now;
                listener.onCommand(cmd, s);
                return;
            }
        }
    }

    /** SpeechRecognizer 错误码转中文 */
    public static String describe(int code) {
        switch (code) {
            case 1: return "网络超时";
            case 2: return "网络错误";
            case 3: return "录音出错（麦克风被占用？）";
            case 4: return "服务器错误";
            case 5: return "客户端取消";
            case 6: return "没听到声音";
            case 7: return "听清了但没匹配";
            case 8: return "识别器忙";
            case 9: return "没有录音权限";
            case 10: return "请求过于频繁";
            case 11: return "服务端断开";
            case 12: return "不支持该语言";
            case 13: return "语言模型不可用";
            default: return "未知";
        }
    }
}
