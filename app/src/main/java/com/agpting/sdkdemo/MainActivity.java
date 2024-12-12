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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化权限管理器
        permissionManager = new PermissionManager(this);
        permissionManager.checkAndRequestPermissions();

        // 初始化UI组件
        initializeViews();
        // 初始化VoiceAssist
        initializeVoiceAssist();
        // 初始化对话列表
        initializeConversationRecyclerView();

        // 初始化其他组件
        statusText = findViewById(R.id.statusText);
        progressBar = findViewById(R.id.progressBar);
    }

    private void initializeViews() {
        intentTextView = findViewById(R.id.intentTextView);
        conversationRecyclerView = findViewById(R.id.conversationRecyclerView);
        inputEditText = findViewById(R.id.inputEditText);
        sendButton = findViewById(R.id.sendButton);

        sendButton.setOnClickListener(v -> {
            String input = inputEditText.getText().toString().trim();
            if (!input.isEmpty()) {
                handleUserInput(input);
                inputEditText.setText("");
            }
        });
    }

    private void initializeVoiceAssist() {
        // 直接创建VoiceAssist实例，不需要传入deviceId
        voiceAssist = new VoiceAssist(this);
        setVoiceAssistCallback();

        boolean initSuccess = voiceAssist.initializeComponents();
        if (!initSuccess) {
            Log.e(TAG, "VoiceAssist initialization failed");
            showError("语音助手初始化失败，请检查网络连接");
            return;
        }
        voiceAssist.setCurrentLocation(currentLocation);
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
            voiceAssist.close();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        permissionManager.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    private void setVoiceAssistCallback() {
        if (voiceAssist != null) {
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
            });
        }
    }

    private void updateUIState(SpeechState state) {
        runOnUiThread(() -> {
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
            // 只解析一次 JSON
            JSONObject json = new JSONObject(intentResult);
            String intent = json.getString("type");
            JSONObject data = json.optJSONObject("data");  // 使用 optJSONObject 代替 getJSONObject

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
                            // 更新位置信息
                            if (voiceAssist != null) {
                                voiceAssist.setCurrentLocation(currentLocation);
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
}
