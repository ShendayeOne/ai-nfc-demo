package com.example.nfcreader;

import android.app.Activity;
import android.nfc.FormatException;
import android.nfc.NdefMessage;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.Ndef;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.IOException;
import java.util.Objects;

/**
 * 基于 Reader Mode 的轻量 NFC Reader。
 * start/stop 必须在主线程调用，并建议分别放在 Activity.onResume/onPause。
 */
public final class NfcReader {
    private static final String LOG_TAG = "NfcReader";
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final NdefParser parser = new NdefParser();

    private volatile NfcReaderCallback activeCallback;
    private Activity activeActivity;

    /** 返回设备不支持、NFC 已关闭或可读取三种状态。 */
    public NfcAvailability getAvailability(Activity activity) {
        Objects.requireNonNull(activity, "activity");
        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(activity);
        if (adapter == null) {
            return NfcAvailability.UNSUPPORTED;
        }
        return adapter.isEnabled() ? NfcAvailability.AVAILABLE : NfcAvailability.DISABLED;
    }

    /**
     * 开始读取并返回当前设备能力。AVAILABLE 时才会真正启用 Reader Mode。
     */
    public NfcAvailability start(Activity activity, NfcReaderCallback callback) {
        requireMainThread();
        Objects.requireNonNull(activity, "activity");
        Objects.requireNonNull(callback, "callback");

        stopActiveReaderIfNeeded();
        NfcAvailability availability = getAvailability(activity);
        if (availability != NfcAvailability.AVAILABLE) {
            return availability;
        }

        NfcAdapter adapter = NfcAdapter.getDefaultAdapter(activity);
        activeActivity = activity;
        activeCallback = callback;
        int flags = NfcAdapter.FLAG_READER_NFC_A
                | NfcAdapter.FLAG_READER_NFC_B
                | NfcAdapter.FLAG_READER_NFC_F
                | NfcAdapter.FLAG_READER_NFC_V
                | NfcAdapter.FLAG_READER_NO_PLATFORM_SOUNDS;
        try {
            adapter.enableReaderMode(activity, this::readTag, flags, null);
            Log.i(LOG_TAG, "NFC Reader Mode 已启用");
        } catch (IllegalStateException | SecurityException exception) {
            activeActivity = null;
            activeCallback = null;
            Log.w(LOG_TAG, "启用 NFC Reader Mode 失败", exception);
            mainHandler.post(() -> callback.onError(NfcReadError.READ_FAILED));
        }
        return NfcAvailability.AVAILABLE;
    }

    /** 停止读取并释放对 Activity 的引用。 */
    public void stop(Activity activity) {
        requireMainThread();
        Objects.requireNonNull(activity, "activity");
        if (activeActivity != activity) {
            return;
        }
        stopActiveReaderIfNeeded();
    }

    private void readTag(Tag tag) {
        Ndef ndef = Ndef.get(tag);
        if (ndef == null) {
            dispatchError(NfcReadError.NOT_NDEF);
            return;
        }

        try {
            // 先消费系统发现标签时缓存的 NDEF；没有缓存时再连接标签执行 NFC I/O。
            NdefMessage message = ndef.getCachedNdefMessage();
            if (message == null) {
                ndef.connect();
                message = ndef.getNdefMessage();
            }
            dispatchResult(parser.parse(message));
        } catch (NdefParser.ParseException exception) {
            Log.w(LOG_TAG, "NDEF 内容无法解析，error=" + exception.getError());
            dispatchError(exception.getError());
        } catch (IOException | FormatException | SecurityException | IllegalStateException exception) {
            Log.w(LOG_TAG, "读取 NFC 标签失败", exception);
            dispatchError(NfcReadError.READ_FAILED);
        } finally {
            try {
                if (ndef.isConnected()) {
                    ndef.close();
                }
            } catch (IOException | SecurityException exception) {
                // 读取结果已经确定，关闭连接失败不覆盖原结果。
                Log.d(LOG_TAG, "关闭 NFC 标签连接时忽略异常", exception);
            }
        }
    }

    private void dispatchResult(NfcReadResult result) {
        NfcReaderCallback callback = activeCallback;
        if (callback != null) {
            // 只记录类型，不输出可能包含业务或隐私数据的标签内容。
            Log.i(LOG_TAG, "NDEF 解析成功，type=" + result.getType());
            mainHandler.post(() -> {
                if (callback == activeCallback) {
                    callback.onResult(result);
                }
            });
        }
    }

    private void dispatchError(NfcReadError error) {
        NfcReaderCallback callback = activeCallback;
        if (callback != null) {
            mainHandler.post(() -> {
                if (callback == activeCallback) {
                    callback.onError(error);
                }
            });
        }
    }

    private void stopActiveReaderIfNeeded() {
        Activity activity = activeActivity;
        activeCallback = null;
        activeActivity = null;
        if (activity != null) {
            NfcAdapter adapter = NfcAdapter.getDefaultAdapter(activity);
            if (adapter != null) {
                try {
                    adapter.disableReaderMode(activity);
                    Log.i(LOG_TAG, "NFC Reader Mode 已停止");
                } catch (IllegalStateException | SecurityException ignored) {
                    // Activity 状态变化时仍确保内部引用已经释放。
                }
            }
        }
    }

    private static void requireMainThread() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            throw new IllegalStateException("NfcReader.start/stop must be called on the main thread");
        }
    }
}
