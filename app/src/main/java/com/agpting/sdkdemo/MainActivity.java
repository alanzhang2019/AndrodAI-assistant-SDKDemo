package com.agpting.sdkdemo;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.SharedPreferences;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.agpting.sdk.KwordRecognizer;
import com.agpting.sdk.SpeechState;
import com.agpting.sdk.VoiceAssist;
import com.agpting.sdk.IntentRecognizer;  // 导入 IntentRecognizer 类

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.Timer;
import java.util.TimerTask;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";
    private static final String LOG_TAG = "MainActivity";
    private String currentLocation = "附近";  // 默认位置
    private VoiceAssist voiceAssist;
    private TextView intentTextView;
    private RecyclerView conversationRecyclerView;
    private ConversationAdapter conversationAdapter;
    private EditText inputEditText;
    private Button sendButton;
    private PermissionManager permissionManager;
    private Timer recognitionTimer;
    private boolean isTimerRunning = false;
    private Runnable timerRunnable;
    private Handler timerHandler = new Handler();
    private static final int TIMER_DELAY = 6000;
    private TextView statusText;
    private ProgressBar progressBar;

    // 添加参数控制相关的 TextView
    private TextView tvMicGain;
    private TextView tvAudioGain;
    private TextView tvSensitivity;
    private TextView tvSampleRate;

    // 定义参数的默认值
    private float micGain = 1.0f;
    private float audioGain = 1.0f;
    private float sensitivity = 0.5f;
    private int sampleRate = 16000;
    
    // 定义参数的范围限制
    private static final float MIN_GAIN = 0.1f;
    private static final float MAX_GAIN = 2.0f;
    private static final float MIN_SENSITIVITY = 0.3f;
    private static final float MAX_SENSITIVITY = 0.7f;
    private static final int MIN_SAMPLE_RATE = 8000;
    private static final int MAX_SAMPLE_RATE = 48000;
    
    // 定义调节步进值
    private static final float GAIN_STEP = 0.1f;
    private static final float SENSITIVITY_STEP = 0.05f;  // 更精细的灵敏度调节
    private static final int SAMPLE_RATE_STEP = 8000;    // 常用采样率间隔

    // 添加参数调整状态锁
    private volatile boolean isAdjustingParameters = false;
    private final Object parameterLock = new Object();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化权限管理器
        permissionManager = new PermissionManager(this);
        
        // 初始化UI组件 - 移到最前面
        initializeViews();
        
        // 其他初始化
        permissionManager.checkAndRequestPermissions();
        initializeVoiceAssist();
        initializeConversationRecyclerView();
        initializeLocationServices();
        setupWakeupControls();
    }

    private void initializeViews() {
        // 基本UI组件
        statusText = findViewById(R.id.statusText);
        progressBar = findViewById(R.id.progressBar);
        intentTextView = findViewById(R.id.intentTextView);
        conversationRecyclerView = findViewById(R.id.conversationRecyclerView);
        inputEditText = findViewById(R.id.inputEditText);
        sendButton = findViewById(R.id.sendButton);

        // 参数控制相关的TextView
        tvMicGain = findViewById(R.id.tvMicGain);
        tvAudioGain = findViewById(R.id.tvAudioGain);
        tvSensitivity = findViewById(R.id.tvSensitivity);
        tvSampleRate = findViewById(R.id.tvSampleRate);

        // 初始状态设置
        if (statusText != null) statusText.setText("空闲");
        if (progressBar != null) progressBar.setVisibility(View.INVISIBLE);
        if (intentTextView != null) intentTextView.setText("意图: 未识别");

        sendButton.setOnClickListener(v -> {
            String input = inputEditText.getText().toString().trim();
            if (!input.isEmpty()) {
                handleUserInput(input);
                inputEditText.setText("");
            }
        });
    }

    private void initializeVoiceAssist() {
        voiceAssist = new VoiceAssist(this);
        setupVoiceAssistCallback();
        
        boolean initSuccess = voiceAssist.initializeComponents();
        if (!initSuccess) {
            Log.e(TAG, "VoiceAssist initialization failed");
            showError("语音助手初始化失败，请检查网络连接");
            return;
        }
        voiceAssist.setCurrentLocation(currentLocation);
        
        // 启动唤醒检测
        voiceAssist.startWakeupDetection();
    }

    private void initializeConversationRecyclerView() {
        conversationAdapter = new ConversationAdapter(new ArrayList<>());
        conversationRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        conversationRecyclerView.setAdapter(conversationAdapter);
    }

    private void handleUserInput(String text) {
        // 显示用户输入
        addConversationItem(text, ConversationItem.TYPE_USER);
        // 处理用户输入
        voiceAssist.handleUserInput(text);
    }

    private void addConversationItem(String message, int type) {
        if (message == null || message.trim().isEmpty()) {
            Log.w(TAG, "Attempted to add empty message");
            return;
        }
        ConversationItem item = new ConversationItem(message, type);
        conversationAdapter.addItem(item);
        conversationRecyclerView.scrollToPosition(conversationAdapter.getItemCount() - 1);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (voiceAssist != null) {
            voiceAssist.stopWakeupDetection();
            voiceAssist.close();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void setupVoiceAssistCallback() {
        voiceAssist.setCallback(new VoiceAssist.VoiceAssistCallback() {
            @Override
            public void onStateChanged(SpeechState newState) {
                runOnUiThread(() -> {
                    updateUIState(newState);
                    if (newState == SpeechState.THINKING) {
                        startTimer();
                    } else {
                        stopTimer();
                    }
                });
            }

            @Override
            public void onSpeechRecognized(String text) {
                runOnUiThread(() -> {
                    if ("No_Match".equals(text)) {
                        stopTimer();
                    } else {
                        addConversationItem(text, ConversationItem.TYPE_USER);
                        startTimer();
                    }
                });
            }

            @Override
            public void onChatCompleted(String response) {
                runOnUiThread(() -> {
                    stopTimer();
                    Log.d(TAG, "Chat response received: " + response);
                    addConversationItem(response, ConversationItem.TYPE_ASSISTANT);
                    conversationRecyclerView.scrollToPosition(conversationAdapter.getItemCount() - 1);
                });
            }

            @Override
            public void onResult(String result) {
                runOnUiThread(() -> {
                    stopTimer();
                    Log.d(TAG, "Result received: " + result);
                    addConversationItem(result, ConversationItem.TYPE_ASSISTANT);
                    conversationRecyclerView.scrollToPosition(conversationAdapter.getItemCount() - 1);
                });
            }

            @Override
            public void onIntentRecognized(String intent) {
                runOnUiThread(() -> {
                    try {
                        // 解析意图JSON
                        JSONObject intentJson = new JSONObject(intent);
                        String intentType = intentJson.optString("type", "");
                        double confidence = intentJson.optDouble("confidence", 0.0);

                        // 更新UI显示意图
                        if (intentTextView != null) {
                            String displayText = String.format("意图: %s (置信度: %.2f)", intentType, confidence);
                            intentTextView.setText(displayText);
                        }

                        // 直接显示原始 JSON，避免格式化可能带来的问题
                        addConversationItem(intent, ConversationItem.TYPE_ASSISTANT);

                    } catch (JSONException e) {
                        Log.e(TAG, "Error parsing intent: " + e.getMessage());
                        // 只有在完全无法解析 JSON 时才显示错误
                        if (intent == null || intent.trim().isEmpty()) {
                            showError("意图解析失败");
                        }
                    }
                });
            }

            @Override
            public void onSpeechSynthesized() {
                runOnUiThread(() -> updateUIState(SpeechState.SPEAKING));
            }

            @Override
            public void onDialogueEnded() {
                runOnUiThread(() -> {
                    updateUIState(SpeechState.IDLE);
                });
            }

            @Override
            public void onError(String errorMessage) {
                runOnUiThread(() -> showError(errorMessage));
            }

            @Override
            public void onInterrupted() {
                runOnUiThread(() -> {
                    updateUIState(SpeechState.IDLE);
                    showToast("语音被打断");
                });
            }

            @Override
            public void onWakeup() {
                Log.d(TAG, "Wake-up detected!");
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "唤醒成功!", Toast.LENGTH_SHORT).show();
                });
            }
        });
    }

    private void updateUIState(SpeechState state) {
        runOnUiThread(() -> {
            if (statusText == null || progressBar == null) {
                Log.e(TAG, "UI components not initialized");
                return;
            }

            switch (state) {
                case IDLE:
                    statusText.setText("空闲");
                    progressBar.setVisibility(View.INVISIBLE);
                    break;
                case SPEECH_RECOGNIZING:
                    statusText.setText("正在听...");
                    progressBar.setVisibility(View.VISIBLE);
                    break;
                case THINKING:
                    statusText.setText("思考中...");
                    progressBar.setVisibility(View.VISIBLE);
                    break;
                case SPEAKING:
                    statusText.setText("说话中...");
                    progressBar.setVisibility(View.VISIBLE);
                    break;
            }
        });
    }

    private void startTimer() {
        if (!isTimerRunning) {
            isTimerRunning = true;
            timerRunnable = () -> {
                showToast("请稍等...");
                if (isTimerRunning) {
                    timerHandler.postDelayed(timerRunnable, TIMER_DELAY);
                }
            };
            timerHandler.postDelayed(timerRunnable, TIMER_DELAY);
        }
    }

    private void stopTimer() {
        isTimerRunning = false;
        if (timerHandler != null && timerRunnable != null) {
            timerHandler.removeCallbacks(timerRunnable);
        }
    }

    private void showError(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, "Error: " + message, Toast.LENGTH_SHORT).show();
        });
    }

    private void showToast(String message) {
        runOnUiThread(() -> {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        });
    }

    private void handleIntentResult(String intentResult) {
        try {
            JSONObject json = new JSONObject(intentResult);
            String intent = json.getString("type");
            JSONObject data = json.optJSONObject("data");
            
            // 添加 order 意图的处理
            if (intent.equals("order")) {
                String command = data != null ? data.optString("command", "unknown") : "unknown";
                String message = String.format("执行命令：%s", command);
                addConversationItem(message, ConversationItem.TYPE_ASSISTANT);
                // TODO: 实现实际的命令执行逻辑
                return;
            }
            
            // 显示意图识别的 JSON 结果
            String formattedJson = json.toString(2);
            addConversationItem(formattedJson, ConversationItem.TYPE_ASSISTANT);
            
            // 只有天气和其他意图需要进一步处理
            if (intent.equals("weather") || intent.equals("other")) {
                if (voiceAssist != null) {
                    String userQuery = data != null ? data.optString("text", "") : "";
                    processChatModel(userQuery);
                }
            }
            // 其他特定意图（导航、电话、音乐、打开、退出）不需要额外处理
            
        } catch (JSONException e) {
            Log.e(TAG, "Error parsing intent result: " + e.getMessage());
            // 只在真正的解析错误时显示错误信息
            if (intentResult == null || intentResult.isEmpty()) {
                showError("意图解析失败");
            }
        }
    }

    // 确保 processChatModel 方法正确实现
    private void processChatModel(String text) {
        if (voiceAssist != null && !text.isEmpty()) {
            Log.d(TAG, "Processing chat model with input: " + text);
            try {
                voiceAssist.handleUserInput(text);
            } catch (Exception e) {
                Log.e(TAG, "Chat model error: " + e.getMessage());
            }
        }
    }

    void initializeLocationServices() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                10000,  // 10 seconds
                10,     // 10 meters
                new LocationListener() {
                    @Override
                    public void onLocationChanged(Location location) {
                        // 使用 Geocoder 将经纬度转换为地址
                        try {
                            Geocoder geocoder = new Geocoder(MainActivity.this, Locale.getDefault());
                            List<Address> addresses = geocoder.getFromLocation(
                                location.getLatitude(),
                                location.getLongitude(),
                                1
                            );
                            if (!addresses.isEmpty()) {
                                Address address = addresses.get(0);
                                // 构建地址字符串
                                String locationStr = String.format("%s%s%s",
                                    address.getAdminArea(),
                                    address.getLocality(),
                                    address.getSubLocality()
                                );
                                currentLocation = locationStr;
                                if (voiceAssist != null) {
                                    voiceAssist.setCurrentLocation(locationStr);
                                }
                                Log.d(TAG, "Location updated: " + locationStr);
                            }
                        } catch (IOException e) {
                            Log.e(TAG, "Error getting address from location", e);
                        }
                    }

                    @Override
                    public void onStatusChanged(String provider, int status, Bundle extras) {}

                    @Override
                    public void onProviderEnabled(String provider) {}

                    @Override
                    public void onProviderDisabled(String provider) {}
                }
            );
            
            // 立即尝试获取最后已知位置
            try {
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation != null) {
                    Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                    List<Address> addresses = geocoder.getFromLocation(
                        lastLocation.getLatitude(),
                        lastLocation.getLongitude(),
                        1
                    );
                    if (!addresses.isEmpty()) {
                        Address address = addresses.get(0);
                        String locationStr = String.format("%s%s%s",
                            address.getAdminArea(),
                            address.getLocality(),
                            address.getSubLocality()
                        );
                        currentLocation = locationStr;
                        if (voiceAssist != null) {
                            voiceAssist.setCurrentLocation(locationStr);
                        }
                        Log.d(TAG, "Initial location set: " + locationStr);
                    }
                }
            } catch (SecurityException | IOException e) {
                Log.e(TAG, "Error getting initial location", e);
            }
        }
    }

    // 添加音乐处理方法
    private void handleMusic(String title, String singer) {
        try {
            String message;
            if (!title.isEmpty() && !singer.isEmpty()) {
                message = String.format("正在播放：%s - %s", title, singer);
            } else if (!title.isEmpty()) {
                message = String.format("正在播放：%s", title);
            } else if (!singer.isEmpty()) {
                message = String.format("正在播放 %s 的音乐", singer);
            } else {
                message = "正在播放音乐";
            }
            
            addConversationItem(message, ConversationItem.TYPE_ASSISTANT);
            showToast(message);
            
            // TODO: 实现实际的音乐播放逻辑
            Log.d(TAG, "Music request: " + message);
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling music: " + e.getMessage());
            showError("播放音乐时出错");
        }
    }

    // 修改导航处理方法
    private void handleNavigation(JSONObject data) {
        try {
            String address = data.optString("address", "");
            if (address.isEmpty()) {
                String response = "抱歉，我需要知道您去哪里";
                addConversationItem(response, ConversationItem.TYPE_ASSISTANT);
                return;
            }
            
            String message = String.format("正在为您导航到：%s", address);
            addConversationItem(message, ConversationItem.TYPE_ASSISTANT);
            
            // TODO: 实现实际的导航逻辑
            Log.d(TAG, "Navigation request to: " + address);
            
        } catch (Exception e) {
            Log.e(TAG, "Error handling navigation: " + e.getMessage());
            showError("导航请求处理出错");
        }
    }

    private void setupWakeupControls() {
        Handler debounceHandler = new Handler(Looper.getMainLooper());
        Runnable updateParametersRunnable = new Runnable() {
            @Override
            public void run() {
                updateWakeupParameters();
            }
        };

        // 麦克风增益控制
        findViewById(R.id.btnMicGainIncrease).setOnClickListener(v -> {
            if (micGain < MAX_GAIN) {
                micGain = Math.min(MAX_GAIN, micGain + GAIN_STEP);
                updateParameterDisplay();
                debounceHandler.removeCallbacks(updateParametersRunnable);
                debounceHandler.postDelayed(updateParametersRunnable, 300);
            }
        });

        findViewById(R.id.btnMicGainDecrease).setOnClickListener(v -> {
            if (micGain > MIN_GAIN) {
                micGain = Math.max(MIN_GAIN, micGain - GAIN_STEP);
                updateParameterDisplay();
                debounceHandler.removeCallbacks(updateParametersRunnable);
                debounceHandler.postDelayed(updateParametersRunnable, 300);
            }
        });

        // 音频增益控制
        findViewById(R.id.btnAudioGainIncrease).setOnClickListener(v -> {
            if (audioGain < MAX_GAIN) {
                audioGain = Math.min(MAX_GAIN, audioGain + GAIN_STEP);
                updateParameterDisplay();
                debounceHandler.removeCallbacks(updateParametersRunnable);
                debounceHandler.postDelayed(updateParametersRunnable, 300);
            }
        });

        findViewById(R.id.btnAudioGainDecrease).setOnClickListener(v -> {
            if (audioGain > MIN_GAIN) {
                audioGain = Math.max(MIN_GAIN, audioGain - GAIN_STEP);
                updateParameterDisplay();
                debounceHandler.removeCallbacks(updateParametersRunnable);
                debounceHandler.postDelayed(updateParametersRunnable, 300);
            }
        });

        // 灵敏度控制
        findViewById(R.id.btnSensitivityIncrease).setOnClickListener(v -> {
            if (sensitivity < MAX_SENSITIVITY) {
                sensitivity = Math.min(MAX_SENSITIVITY, sensitivity + SENSITIVITY_STEP);
                updateParameterDisplay();
                debounceHandler.removeCallbacks(updateParametersRunnable);
                debounceHandler.postDelayed(updateParametersRunnable, 300);
            }
        });

        findViewById(R.id.btnSensitivityDecrease).setOnClickListener(v -> {
            if (sensitivity > MIN_SENSITIVITY) {
                sensitivity = Math.max(MIN_SENSITIVITY, sensitivity - SENSITIVITY_STEP);
                updateParameterDisplay();
                debounceHandler.removeCallbacks(updateParametersRunnable);
                debounceHandler.postDelayed(updateParametersRunnable, 300);
            }
        });

        // 采样率控制
        findViewById(R.id.btnSampleRateIncrease).setOnClickListener(v -> {
            if (sampleRate < MAX_SAMPLE_RATE) {
                sampleRate = Math.min(MAX_SAMPLE_RATE, sampleRate + SAMPLE_RATE_STEP);
                updateParameterDisplay();
                debounceHandler.removeCallbacks(updateParametersRunnable);
                debounceHandler.postDelayed(updateParametersRunnable, 300);
            }
        });

        findViewById(R.id.btnSampleRateDecrease).setOnClickListener(v -> {
            if (sampleRate > MIN_SAMPLE_RATE) {
                sampleRate = Math.max(MIN_SAMPLE_RATE, sampleRate - SAMPLE_RATE_STEP);
                updateParameterDisplay();
                debounceHandler.removeCallbacks(updateParametersRunnable);
                debounceHandler.postDelayed(updateParametersRunnable, 300);
            }
        });
    }

    private void updateParameterDisplay() {
        tvMicGain.setText(String.format("麦克风增益: %.1f", micGain));
        tvAudioGain.setText(String.format("音频增益: %.1f", audioGain));
        tvSensitivity.setText(String.format("灵敏度: %.2f", sensitivity));
        tvSampleRate.setText(String.format("采样率: %d", sampleRate));
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 检查是否正在调整参数
        synchronized (parameterLock) {
            if (!isAdjustingParameters && voiceAssist != null) {
                voiceAssist.startWakeupDetection();
            }
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        // 检查是否正在调整参数
        synchronized (parameterLock) {
            if (!isAdjustingParameters && voiceAssist != null) {
                voiceAssist.stopWakeupDetection();
            }
        }
    }

    private void updateWakeupParameters() {
        if (voiceAssist == null) return;

        synchronized (parameterLock) {
            if (isAdjustingParameters) {
                Log.d(TAG, "Parameters are being adjusted, skipping this update");
                return;
            }
            isAdjustingParameters = true;
        }

        new Thread(() -> {
            try {
                voiceAssist.stopWakeupDetection();
                Thread.sleep(200);

                runOnUiThread(() -> updateUIState(SpeechState.IDLE));
                Thread.sleep(100);

                // 配置新参数，包括降噪设置
                voiceAssist.configureWakeupParameters(
                    2.0f,    // 麦克风增益
                    2.0f,    // 音频增益
                    0.6f,    // 灵敏度
                    16000,   // 采样率
                    true,    // 启用降噪
                    0.12f,   // 降噪阈值（降低以提高灵敏度）
                    3        // 平滑帧数（减少以提高响应速度）
                );
                
                Thread.sleep(300);

                runOnUiThread(() -> {
                    try {
                        updateUIState(SpeechState.IDLE);
                        stopTimer();
                        voiceAssist.startWakeupDetection();
                    } catch (Exception e) {
                        Log.e(TAG, "Error restarting wakeup detection: " + e.getMessage());
                        showToast("重启唤醒检测失败");
                    }
                });

            } catch (Exception e) {
                Log.e(TAG, "Error updating parameters: " + e.getMessage());
                runOnUiThread(() -> {
                    showToast("参数更新失败");
                    updateUIState(SpeechState.IDLE);
                });
            } finally {
                synchronized (parameterLock) {
                    isAdjustingParameters = false;
                }
            }
        }).start();
    }
}
