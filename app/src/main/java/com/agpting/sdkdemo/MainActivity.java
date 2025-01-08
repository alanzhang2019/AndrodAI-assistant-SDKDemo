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
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;

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

    // 添加唤醒状态指示器
    private TextView wakeupStatusText;
    private ProgressBar wakeupProgressBar;

    private static final int PERMISSION_REQUEST_CODE = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // 1. 初始化基本 UI 组件
        initializeEssentialViews();
        
        // 2. 检查权限并初始化 VoiceAssist
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_REQUEST_CODE);
        } else {
            initializeAll();
        }
    }

    private void initializeAll() {
        // 1. 初始化 VoiceAssist
        initializeVoiceAssist();
        
        // 2. 初始化其他 UI 组件
        initializeComponents();
        
        // 3. 设置按钮点击事件
        setupClickListeners();
    }

    private void initializeComponents() {
        // 初始化对话列表
        conversationRecyclerView = findViewById(R.id.conversationRecyclerView);
        conversationAdapter = new ConversationAdapter(new ArrayList<>());
        conversationRecyclerView.setAdapter(conversationAdapter);
        conversationRecyclerView.setLayoutManager(new LinearLayoutManager(this));

        // 初始化输入相关组件
        inputEditText = findViewById(R.id.inputEditText);
        sendButton = findViewById(R.id.sendButton);

        // 初始化参数控制相关组件
        tvMicGain = findViewById(R.id.tvMicGain);
        tvAudioGain = findViewById(R.id.tvAudioGain);
        tvSensitivity = findViewById(R.id.tvSensitivity);
        tvSampleRate = findViewById(R.id.tvSampleRate);

        // 更新参数显示
        updateParameterDisplay();
    }

    private void setupClickListeners() {
        // 设置发送按钮点击事件
        sendButton.setOnClickListener(v -> {
            String text = inputEditText.getText().toString().trim();
            if (!text.isEmpty()) {
                handleUserInput(text);
                inputEditText.setText("");
            }
        });

        // 麦克风增益调节按钮
        findViewById(R.id.btnMicGainDecrease).setOnClickListener(v -> {
            if (micGain > MIN_GAIN) {
                micGain = Math.max(MIN_GAIN, micGain - GAIN_STEP);
                updateParameterDisplay();
                updateWakeupParameters();
            }
        });

        findViewById(R.id.btnMicGainIncrease).setOnClickListener(v -> {
            if (micGain < MAX_GAIN) {
                micGain = Math.min(MAX_GAIN, micGain + GAIN_STEP);
                updateParameterDisplay();
                updateWakeupParameters();
            }
        });

        // 音频增益调节按钮
        findViewById(R.id.btnAudioGainDecrease).setOnClickListener(v -> {
            if (audioGain > MIN_GAIN) {
                audioGain = Math.max(MIN_GAIN, audioGain - GAIN_STEP);
                updateParameterDisplay();
                updateWakeupParameters();
            }
        });

        findViewById(R.id.btnAudioGainIncrease).setOnClickListener(v -> {
            if (audioGain < MAX_GAIN) {
                audioGain = Math.min(MAX_GAIN, audioGain + GAIN_STEP);
                updateParameterDisplay();
                updateWakeupParameters();
            }
        });

        // 灵敏度调节按钮
        findViewById(R.id.btnSensitivityDecrease).setOnClickListener(v -> {
            if (sensitivity > MIN_SENSITIVITY) {
                sensitivity = Math.max(MIN_SENSITIVITY, sensitivity - SENSITIVITY_STEP);
                updateParameterDisplay();
                updateWakeupParameters();
            }
        });

        findViewById(R.id.btnSensitivityIncrease).setOnClickListener(v -> {
            if (sensitivity < MAX_SENSITIVITY) {
                sensitivity = Math.min(MAX_SENSITIVITY, sensitivity + SENSITIVITY_STEP);
                updateParameterDisplay();
                updateWakeupParameters();
            }
        });

        // 采样率调节按钮
        findViewById(R.id.btnSampleRateDecrease).setOnClickListener(v -> {
            if (sampleRate > MIN_SAMPLE_RATE) {
                sampleRate = Math.max(MIN_SAMPLE_RATE, sampleRate - SAMPLE_RATE_STEP);
                updateParameterDisplay();
                updateWakeupParameters();
            }
        });

        findViewById(R.id.btnSampleRateIncrease).setOnClickListener(v -> {
            if (sampleRate < MAX_SAMPLE_RATE) {
                sampleRate = Math.min(MAX_SAMPLE_RATE, sampleRate + SAMPLE_RATE_STEP);
                updateParameterDisplay();
                updateWakeupParameters();
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                initializeAll();
            } else {
                Toast.makeText(this, "需要录音权限才能使用语音功能", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void initializeVoiceAssist() {
        try {
            voiceAssist = new VoiceAssist(this);
            setupVoiceAssistCallback();
            
            updateWakeupStatus(false, "正在初始化语音助手...");
            
            boolean initSuccess = voiceAssist.initializeComponents();
            if (!initSuccess) {
                Log.e(TAG, "VoiceAssist initialization failed");
                updateWakeupStatus(false, "语音助手初始化失败");
                showError("语音助手初始化失败，请检查网络连接");
                return;
            }
            
            voiceAssist.setCurrentLocation(currentLocation);
            updateWakeupStatus(false, "正在准备唤醒系统...");
            voiceAssist.startWakeupDetection();
        } catch (Exception e) {
            Log.e(TAG, "Error initializing VoiceAssist", e);
            updateWakeupStatus(false, "语音助手初始化失败");
            showError("语音助手初始化失败: " + e.getMessage());
        }
    }

    private void initializeEssentialViews() {
        // 只初始化立即需要的视图
        statusText = findViewById(R.id.statusText);
        progressBar = findViewById(R.id.progressBar);
        
        // 添加唤醒状态指示器初始化
        wakeupStatusText = findViewById(R.id.wakeupStatusText);
        wakeupProgressBar = findViewById(R.id.wakeupProgressBar);
        
        // 显示初始状态
        updateWakeupStatus(false, "正在初始化唤醒系统...");
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
        if (voiceAssist != null) {
            voiceAssist.handleUserInput(text);
        }
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

    private void setupVoiceAssistCallback() {
        voiceAssist.setCallback(new VoiceAssist.VoiceAssistCallback() {
            @Override
            public void onWakeupSystemReady() {
                Log.d(TAG, "onWakeupSystemReady callback received");
                runOnUiThread(() -> {
                    updateWakeupStatus(true, "唤醒系统已就绪");
                    if (wakeupProgressBar != null) {
                        wakeupProgressBar.setVisibility(View.GONE);
                    }
                });
            }

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
                    updateWakeupStatus(false, "已唤醒");  // 改回显示唤醒状态
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
            // 使用新的LocationListener实现
            LocationListener locationListener = new LocationListener() {
                @Override
                public void onLocationChanged(@NonNull Location location) {
                    updateLocationInfo(location);
                }
            };

            // 使用新的请求位置更新方法
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12及以上版本
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    10000,  // 10 seconds
                    10f,    // 10 meters
                    Executors.newSingleThreadExecutor(),
                    locationListener
                );
            } else {
                // 较旧版本
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    10000,
                    10f,
                    locationListener
                );
            }
            
            // 获取最后已知位置
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                locationManager.getCurrentLocation(
                    LocationManager.GPS_PROVIDER,
                    null,
                    Executors.newSingleThreadExecutor(),
                    location -> {
                        if (location != null) {
                            updateLocationInfo(location);
                        }
                    }
                );
            } else {
                Location lastLocation = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                if (lastLocation != null) {
                    updateLocationInfo(lastLocation);
                }
            }
        }
    }

    // 添加新的辅助方法处理位置信息更新
    private void updateLocationInfo(Location location) {
        try {
            Geocoder geocoder = new Geocoder(this, Locale.getDefault());
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Android 13及以上版本使用新的异步API
                geocoder.getFromLocation(
                    location.getLatitude(),
                    location.getLongitude(),
                    1,
                    addresses -> {
                        if (!addresses.isEmpty()) {
                            processAddress(addresses.get(0));
                        }
                    }
                );
            } else {
                // 较旧版本使用同步API
                List<Address> addresses = geocoder.getFromLocation(
                    location.getLatitude(),
                    location.getLongitude(),
                    1
                );
                if (!addresses.isEmpty()) {
                    processAddress(addresses.get(0));
                }
            }
        } catch (IOException e) {
            Log.e(TAG, "Error getting address from location", e);
        }
    }

    private void processAddress(Address address) {
        String locationStr = String.format("%s%s%s",
            address.getAdminArea() != null ? address.getAdminArea() : "",
            address.getLocality() != null ? address.getLocality() : "",
            address.getSubLocality() != null ? address.getSubLocality() : ""
        );
        currentLocation = locationStr;
        if (voiceAssist != null) {
            voiceAssist.setCurrentLocation(locationStr);
        }
        Log.d(TAG, "Location updated: " + locationStr);
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
        if (tvMicGain != null) {
            tvMicGain.setText(String.format("麦克风增益: %.1f", micGain));
        }
        if (tvAudioGain != null) {
            tvAudioGain.setText(String.format("音频增益: %.1f", audioGain));
        }
        if (tvSensitivity != null) {
            tvSensitivity.setText(String.format("灵敏度: %.2f", sensitivity));
        }
        if (tvSampleRate != null) {
            tvSampleRate.setText(String.format("采样率: %d", sampleRate));
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        synchronized (parameterLock) {
            if (!isAdjustingParameters && voiceAssist != null) {
                if (!voiceAssist.isWakeupEnabled()) {
                    updateWakeupStatus(false, "正在恢复唤醒系统...");
                    voiceAssist.startWakeupDetection();
                }
                // 移除这里的状态更新，让回调来处理
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

                // 使用用户调整的参数值
                voiceAssist.configureWakeupParameters(
                    micGain,         // 使用当前麦克风增益值
                    audioGain,       // 使用当前音频增益值
                    sensitivity,     // 使用当前灵敏度值
                    sampleRate,      // 使用当前采样率值
                    true,           // 保持降噪启用
                    0.12f,          // 保持默认降噪阈值
                    3               // 保持默认平滑帧数
                );
                
                Log.d(TAG, String.format("Updating parameters - MicGain: %.1f, AudioGain: %.1f, " +
                    "Sensitivity: %.2f, SampleRate: %d", micGain, audioGain, sensitivity, sampleRate));

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

    // 添加更新唤醒状态的辅助方法
    private void updateWakeupStatus(boolean isReady, String message) {
        Log.d(TAG, "updateWakeupStatus called: isReady=" + isReady + ", message=" + message);
        runOnUiThread(() -> {
            if (wakeupStatusText != null) {
                Log.d(TAG, "Setting wakeup status text to: " + message);
                wakeupStatusText.setText(message);
                int color = isReady ? R.color.green : R.color.gray;
                wakeupStatusText.setTextColor(getResources().getColor(color));
            }
            if (wakeupProgressBar != null) {
                wakeupProgressBar.setVisibility(isReady ? View.GONE : View.VISIBLE);
            }
        });
    }
}
