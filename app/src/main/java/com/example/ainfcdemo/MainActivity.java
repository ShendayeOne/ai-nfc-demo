package com.example.ainfcdemo;

import android.content.Context;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.nfcreader.NfcAvailability;
import com.example.nfcreader.NfcReadError;
import com.example.nfcreader.NfcReadResult;
import com.example.nfcreader.NfcReader;
import com.example.nfcreader.NfcReaderCallback;
import com.example.nfcreader.NfcSimulator;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.text.DateFormat;
import java.util.Date;

/** NFC Reader 的单页演示入口，负责演示/真实模式切换和结果展示。 */
public class MainActivity extends AppCompatActivity {
    private static final long SIMULATION_DELAY_MS = 450L;
    private static final long SUCCESS_VIBRATION_MS = 70L;
    private static final String DEMO_LANGUAGE = "zh";

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final NfcReader nfcReader = new NfcReader();

    private SwitchMaterial demoSwitch;
    private MaterialCardView simulationCard;
    private MaterialButton simulateTextButton;
    private MaterialButton simulateUrlButton;
    private MaterialButton simulateAllButton;
    private ImageView stateIcon;
    private TextView heroTitle;
    private TextView heroDescription;
    private TextView resultType;
    private TextView resultTextContent;
    private TextView resultUrlContent;
    private TextView resultSource;
    private TextView resultStatus;
    private TextView resultTime;
    private boolean resumed;
    private Runnable pendingSimulation;

    private final NfcReaderCallback readerCallback = new NfcReaderCallback() {
        @Override
        public void onResult(NfcReadResult result) {
            showSuccess(result, getString(R.string.source_real));
        }

        @Override
        public void onError(NfcReadError error) {
            showReadError(error);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        applySystemInsets();
        bindViews();
        bindActions();

        demoSwitch.setChecked(true);
        renderMode();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        updateReaderState();
    }

    @Override
    protected void onPause() {
        resumed = false;
        // 模拟反馈尚未完成就离开页面时主动复位，避免返回后按钮永久禁用或 UI 停在“读取中”。
        boolean simulationWasPending = pendingSimulation != null;
        cancelPendingSimulation();
        if (simulationWasPending) {
            setSimulationButtonsEnabled(true);
            stateIcon.setImageResource(R.drawable.ic_nfc);
            heroDescription.setText(R.string.hero_demo_description);
            resetResult();
        }
        nfcReader.stop(this);
        super.onPause();
    }

    private void applySystemInsets() {
        View root = findViewById(R.id.main);
        View statusBarInset = findViewById(R.id.view_status_bar_inset);
        View toolbar = findViewById(R.id.layout_toolbar);
        View contentScroll = findViewById(R.id.content_scroll);
        int horizontalPadding = getResources().getDimensionPixelSize(R.dimen.screen_horizontal_padding);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets systemBars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            int statusBarTop = windowInsets.getInsets(WindowInsetsCompat.Type.statusBars()).top;
            int navigationBarBottom = windowInsets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom;

            // 复用公司基座的安全区原则：顶部纯占位、标题固定、滚动内容只承接左右和底部 Insets。
            statusBarInset.setPadding(systemBars.left, statusBarTop, systemBars.right, 0);
            toolbar.setPadding(
                    horizontalPadding + systemBars.left,
                    0,
                    horizontalPadding + systemBars.right,
                    0
            );
            contentScroll.setPadding(
                    horizontalPadding + systemBars.left,
                    0,
                    horizontalPadding + systemBars.right,
                    navigationBarBottom
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    private void bindViews() {
        demoSwitch = findViewById(R.id.switch_demo);
        simulationCard = findViewById(R.id.card_simulation);
        simulateTextButton = findViewById(R.id.button_simulate_text);
        simulateUrlButton = findViewById(R.id.button_simulate_url);
        simulateAllButton = findViewById(R.id.button_simulate_all);
        stateIcon = findViewById(R.id.image_nfc_state);
        heroTitle = findViewById(R.id.text_hero_title);
        heroDescription = findViewById(R.id.text_hero_description);
        resultType = findViewById(R.id.text_result_type);
        resultTextContent = findViewById(R.id.text_result_text_content);
        resultUrlContent = findViewById(R.id.text_result_url_content);
        resultSource = findViewById(R.id.text_result_source);
        resultStatus = findViewById(R.id.text_result_status);
        resultTime = findViewById(R.id.text_result_time);
    }

    private void bindActions() {
        demoSwitch.setOnCheckedChangeListener((button, checked) -> renderMode());
        simulateTextButton.setOnClickListener(view -> simulateText());
        simulateUrlButton.setOnClickListener(view -> simulateUrl());
        simulateAllButton.setOnClickListener(view -> simulateAll());
    }

    private void renderMode() {
        cancelPendingSimulation();
        boolean demoMode = demoSwitch.isChecked();
        simulationCard.setVisibility(demoMode ? View.VISIBLE : View.GONE);
        setSimulationButtonsEnabled(true);
        resetResult();

        if (demoMode) {
            nfcReader.stop(this);
            stateIcon.setImageResource(R.drawable.ic_nfc);
            heroTitle.setText(R.string.hero_demo_title);
            heroDescription.setText(R.string.hero_demo_description);
        } else {
            heroTitle.setText(R.string.hero_real_title);
            updateReaderState();
        }
    }

    private void updateReaderState() {
        if (demoSwitch == null || demoSwitch.isChecked() || !resumed) {
            return;
        }

        NfcAvailability availability = nfcReader.start(this, readerCallback);
        switch (availability) {
            case AVAILABLE:
                stateIcon.setImageResource(R.drawable.ic_nfc);
                heroDescription.setText(R.string.nfc_ready_description);
                resultStatus.setText(R.string.status_waiting);
                break;
            case DISABLED:
                stateIcon.setImageResource(R.drawable.ic_status_waiting);
                heroDescription.setText(R.string.nfc_disabled_description);
                resultStatus.setText(R.string.status_nfc_disabled);
                break;
            case UNSUPPORTED:
            default:
                stateIcon.setImageResource(R.drawable.ic_status_waiting);
                heroDescription.setText(R.string.nfc_unsupported_description);
                resultStatus.setText(R.string.status_nfc_unsupported);
                break;
        }
    }

    private void simulateText() {
        startSimulation(() -> NfcSimulator.simulateText(
                DEMO_LANGUAGE,
                getString(R.string.demo_text_content)
        ));
    }

    private void simulateUrl() {
        startSimulation(() -> NfcSimulator.simulateUri(getString(R.string.demo_url_content)));
    }

    private void simulateAll() {
        startSimulation(() -> NfcSimulator.simulateTextAndUri(
                DEMO_LANGUAGE,
                getString(R.string.demo_text_content),
                getString(R.string.demo_url_content)
        ));
    }

    private void startSimulation(ResultFactory factory) {
        cancelPendingSimulation();
        setSimulationButtonsEnabled(false);
        stateIcon.setImageResource(R.drawable.ic_status_waiting);
        heroDescription.setText(R.string.simulating_description);
        resultStatus.setText(R.string.status_reading);

        pendingSimulation = () -> {
            pendingSimulation = null;
            try {
                showSuccess(factory.create(), getString(R.string.source_simulated));
            } catch (IllegalArgumentException exception) {
                showReadError(NfcReadError.MALFORMED_DATA);
            } finally {
                setSimulationButtonsEnabled(true);
            }
        };
        mainHandler.postDelayed(pendingSimulation, SIMULATION_DELAY_MS);
    }

    private void showSuccess(NfcReadResult result, String source) {
        vibrateOnSuccess();
        stateIcon.setImageResource(R.drawable.ic_status_success);
        heroDescription.setText(R.string.read_success_description);
        switch (result.getType()) {
            case TEXT:
                resultType.setText(R.string.result_type_text);
                break;
            case URL:
                resultType.setText(R.string.result_type_url);
                break;
            case TEXT_AND_URL:
            default:
                resultType.setText(R.string.result_type_text_and_url);
                break;
        }
        resultTextContent.setText(result.hasText()
                ? displayContent(result.getTextContent())
                : getString(R.string.result_placeholder));
        resultUrlContent.setText(result.hasUrl()
                ? displayContent(result.getUrlContent())
                : getString(R.string.result_placeholder));
        resultSource.setText(source);
        resultStatus.setText(R.string.status_success);
        resultTime.setText(DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date()));
    }

    private void showReadError(NfcReadError error) {
        stateIcon.setImageResource(R.drawable.ic_status_waiting);
        heroDescription.setText(R.string.read_error_description);
        resultType.setText(R.string.result_placeholder);
        resultTextContent.setText(R.string.result_placeholder);
        resultUrlContent.setText(R.string.result_placeholder);
        resultSource.setText(demoSwitch.isChecked()
                ? R.string.source_simulated
                : R.string.source_real);
        resultStatus.setText(errorMessage(error));
        resultTime.setText(DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date()));
    }

    private String errorMessage(NfcReadError error) {
        switch (error) {
            case NOT_NDEF:
                return getString(R.string.error_not_ndef);
            case EMPTY_MESSAGE:
                return getString(R.string.error_empty_message);
            case UNSUPPORTED_RECORD:
                return getString(R.string.error_unsupported_record);
            case MALFORMED_DATA:
                return getString(R.string.error_malformed_data);
            case READ_FAILED:
            default:
                return getString(R.string.error_read_failed);
        }
    }

    private void resetResult() {
        resultType.setText(R.string.result_placeholder);
        resultTextContent.setText(R.string.result_placeholder);
        resultUrlContent.setText(R.string.result_placeholder);
        resultSource.setText(R.string.result_placeholder);
        resultStatus.setText(R.string.status_waiting);
        resultTime.setText(R.string.result_placeholder);
    }

    private void setSimulationButtonsEnabled(boolean enabled) {
        simulateTextButton.setEnabled(enabled);
        simulateUrlButton.setEnabled(enabled);
        simulateAllButton.setEnabled(enabled);
    }

    private String displayContent(String content) {
        return content.isEmpty() ? getString(R.string.result_empty_content) : content;
    }

    /** 成功时提供一次短促触觉反馈，用户不看屏幕也能确认标签已经读到。 */
    @SuppressWarnings("deprecation")
    private void vibrateOnSuccess() {
        Vibrator vibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator == null || !vibrator.hasVibrator()) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(
                    SUCCESS_VIBRATION_MS,
                    VibrationEffect.DEFAULT_AMPLITUDE
            ));
        } else {
            vibrator.vibrate(SUCCESS_VIBRATION_MS);
        }
    }

    private void cancelPendingSimulation() {
        if (pendingSimulation != null) {
            mainHandler.removeCallbacks(pendingSimulation);
            pendingSimulation = null;
        }
    }

    private interface ResultFactory {
        NfcReadResult create();
    }
}
