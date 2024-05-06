package com.example.androidusbcamera;

import android.content.Context;
import androidx.appcompat.app.AlertDialog;

public class Alert {
    // 確認用
    public static void alert(String msg, Context context) {
        new AlertDialog.Builder(context)
                .setTitle("タイトル")
                .setMessage(msg)
                .setPositiveButton("Yes", (_a, _b) -> {})
                .setNegativeButton("No", (_a, _b) -> {})
                .show();
    }
}
