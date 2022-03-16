package com.alexchurkin.truckremote.helpers;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TaskRunner {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final Handler uiHandler = new Handler(Looper.getMainLooper());

    public void executeAsync(Runnable task) {
        executor.execute(task);
    }

    public void postToMainThread(Runnable runnable) {

        uiHandler.post(runnable);
    }
}