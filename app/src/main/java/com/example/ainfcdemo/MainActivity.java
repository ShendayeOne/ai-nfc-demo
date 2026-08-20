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
import androidx.core.content.ContextCompat;
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
    /** 模拟标签靠近的延迟，制造“正在读取”的演示节奏，不是真实 NFC 行为。 */
    private static final long SIMULATION_DELAY_MS = 450L;
    /** 读取成功时的短振动时长，仅用于触觉反馈确认。 */
    private static final long SUCCESS_VIBRATION_MS = 70L;
    /** 模拟 Text Record 使用的语言码，仅影响 payload 中语言码字段。 */
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
    /** 尚未执行的模拟任务；非空表示正在“读取中”，模式切换或离开页面时需要取消。 */
    private Runnable pendingSimulation;

    /** 真实 NFC 读取回调；NfcReader 已保证回调在主线程，可直接更新 UI。 */
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

    /** 初始化页面：Edge-to-Edge、安全区、控件绑定，并默认进入演示模式。 */
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

    /** 页面可见时刷新真实模式状态：从设置页返回后 NFC 开关可能已变化，需要重新 start。 */
    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        updateReaderState();
    }

    /** 页面不可见时停止 NFC 读取并复位未完成的模拟任务。 */
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

    /** 配置 Edge-to-Edge 安全区，保证状态栏、标题栏和滚动内容在各机型上不被系统栏遮挡。 */
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

    /** 绑定布局中的全部控件引用，集中管理避免 findViewById 散落。 */
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

    /** 绑定开关与三个模拟按钮的点击事件。 */
    private void bindActions() {
        demoSwitch.setOnCheckedChangeListener((button, checked) -> renderMode());
        simulateTextButton.setOnClickListener(view -> simulateText());
        simulateUrlButton.setOnClickListener(view -> simulateUrl());
        simulateAllButton.setOnClickListener(view -> simulateAll());
    }

    /**
     * 根据演示模式开关重渲染页面：
     * 演示模式展示模拟卡片并停止真实读取；真实模式隐藏模拟卡片并启动 Reader Mode。
     */
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

    /**
     * 仅在真实模式且页面已恢复时启动 Reader Mode，并按设备 NFC 能力更新图标、描述和状态；
     * onCreate 早于 bindViews 完成时 demoSwitch 可能为 null，需要先判空。
     */
    private void updateReaderState() {
        if (demoSwitch == null || demoSwitch.isChecked() || !resumed) {
            return;
        }

        NfcAvailability availability = nfcReader.start(this, readerCallback);
        switch (availability) {
            case AVAILABLE:
                stateIcon.setImageResource(R.drawable.ic_nfc);
                heroDescription.setText(R.string.nfc_ready_description);
                setResultStatus(R.string.status_waiting, R.color.waiting);
                break;
            case DISABLED:
                stateIcon.setImageResource(R.drawable.ic_status_waiting);
                heroDescription.setText(R.string.nfc_disabled_description);
                setResultStatus(R.string.status_nfc_disabled, R.color.error);
                break;
            case UNSUPPORTED:
            default:
                stateIcon.setImageResource(R.drawable.ic_status_waiting);
                heroDescription.setText(R.string.nfc_unsupported_description);
                setResultStatus(R.string.status_nfc_unsupported, R.color.error);
                break;
        }
    }

    /** 模拟标准 RTD_TEXT 标签读取。 */
    private void simulateText() {
        startSimulation(() -> NfcSimulator.simulateText(
                DEMO_LANGUAGE,
                getString(R.string.demo_text_content)
        ));
    }

    /** 模拟标准 RTD_URI 标签读取。 */
    private void simulateUrl() {
        startSimulation(() -> NfcSimulator.simulateUri(getString(R.string.demo_url_content)));
    }

    /** 模拟同一条 NDEF Message 同时包含 Text 与 URI 的标签读取。 */
    private void simulateAll() {
        startSimulation(() -> NfcSimulator.simulateTextAndUri(
                DEMO_LANGUAGE,
                getString(R.string.demo_text_content),
                getString(R.string.demo_url_content)
        ));
    }

    /**
     * 执行一次模拟读取：先展示“读取中”状态并禁用按钮防重复点击，
     * 延迟后走与真实读取相同的解析链路生成结果。
     * 模拟数据构造失败时按格式异常展示，不让异常抛到 UI 层。
     */
    private void startSimulation(ResultFactory factory) {
        cancelPendingSimulation();
        setSimulationButtonsEnabled(false);
        stateIcon.setImageResource(R.drawable.ic_status_waiting);
        heroDescription.setText(R.string.simulating_description);
        setResultStatus(R.string.status_reading, R.color.primary);

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

    /**
     * 展示读取成功结果：振动反馈、成功图标、类型标签和内容。
     * source 区分结果来自真实读取还是模拟输入。
     */
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
        resultTextContent.setText(R.string.result_placeholder);
        resultUrlContent.setText(R.string.result_placeholder);
        if (result.hasText()) {
            resultTextContent.setText(displayContent(result.getTextContent()));
        }
        // Text 与 URI 可以同时存在，必须独立判断，避免组合结果遗漏 URL。
        if (result.hasUrl()) {
            resultUrlContent.setText(displayContent(result.getUrlContent()));
        }
        resultSource.setText(source);
        setResultStatus(R.string.status_success, R.color.success);
        resultTime.setText(DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date()));
    }

    /** 展示读取失败结果：占位内容、按当前模式标记来源，并把错误枚举映射为红色提示文案。 */
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
        resultStatus.setTextColor(ContextCompat.getColor(this, R.color.error));
        resultTime.setText(DateFormat.getTimeInstance(DateFormat.MEDIUM).format(new Date()));
    }

    /** 把 nfc-reader 的稳定错误枚举映射为用户可见文案，文案统一放在 strings.xml。 */
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

    /** 把结果区全部复位为占位符，用于模式切换或初始进入页面。 */
    private void resetResult() {
        resultType.setText(R.string.result_placeholder);
        resultTextContent.setText(R.string.result_placeholder);
        resultUrlContent.setText(R.string.result_placeholder);
        resultSource.setText(R.string.result_placeholder);
        setResultStatus(R.string.status_waiting, R.color.waiting);
        resultTime.setText(R.string.result_placeholder);
    }

    /** 状态文字同时更新语义色，让等待、成功和异常无需细读也能区分。 */
    private void setResultStatus(int textRes, int colorRes) {
        resultStatus.setText(textRes);
        resultStatus.setTextColor(ContextCompat.getColor(this, colorRes));
    }

    /** 统一启用/禁用三个模拟按钮，避免模拟期间重复触发。 */
    private void setSimulationButtonsEnabled(boolean enabled) {
        simulateTextButton.setEnabled(enabled);
        simulateUrlButton.setEnabled(enabled);
        simulateAllButton.setEnabled(enabled);
    }

    /** 空字符串内容显示为“（空内容）”，避免用户误以为没有读到。 */
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

    /** 取消尚未执行的模拟任务，防止模式切换或页面销毁后回调继续执行。 */
    private void cancelPendingSimulation() {
        if (pendingSimulation != null) {
            mainHandler.removeCallbacks(pendingSimulation);
            pendingSimulation = null;
        }
    }

    /** 模拟结果工厂：三种模拟按钮各自提供构造 NfcReadResult 的方式。 */
    private interface ResultFactory {
        NfcReadResult create();
    }
}
