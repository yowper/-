package com.example.volumeswipe;

import android.graphics.Bitmap;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions;

/**
 * 截图认字（OCR）：用 ML Kit 的中文识别，把截图里那块区域上的字真读出来。
 * 模型是内置在 APK 里的，不联网也能用。
 *
 * 用途：读出视频最下方那行「🔒 10 秒后可继续看下一集」里的秒数，
 * 然后按读到的秒数等它走完再上滑。读不出来时上层会退回像素比对。
 */
public final class Ocr {

    private static TextRecognizer recognizer;
    private static volatile boolean broken;

    private Ocr() {
    }

    public interface Cb {
        /** 识别结束（主线程回调）。读不到字就是空串，不会为 null */
        void onText(String text);
    }

    public static boolean available() {
        return !broken;
    }

    public static void run(Bitmap bmp, final Cb cb) {
        if (bmp == null) {
            cb.onText("");
            return;
        }
        if (broken) {
            cb.onText("");
            return;
        }
        try {
            if (recognizer == null) {
                recognizer = TextRecognition.getClient(
                        new ChineseTextRecognizerOptions.Builder().build());
            }
            recognizer.process(InputImage.fromBitmap(bmp, 0))
                    .addOnSuccessListener(new com.google.android.gms.tasks.OnSuccessListener<Text>() {
                        @Override
                        public void onSuccess(Text result) {
                            String s = "";
                            try {
                                s = result == null ? "" : result.getText();
                            } catch (Throwable ignored) {
                            }
                            cb.onText(s == null ? "" : s);
                        }
                    })
                    .addOnFailureListener(new com.google.android.gms.tasks.OnFailureListener() {
                        @Override
                        public void onFailure(Exception e) {
                            cb.onText("");
                        }
                    });
        } catch (Throwable t) {
            // 依赖缺失 / 模型装不上：直接标记不可用，上层退回像素比对
            broken = true;
            cb.onText("");
        }
    }
}
