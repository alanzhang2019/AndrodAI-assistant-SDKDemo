package com.agpting.sdkdemo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.LinearInterpolator;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class AIAnimationView extends View {

    private Paint circlePaint;
    private Paint wavePaint;
    private Paint effectPaint;
    private float circleRadius;
    private float baseRadius;
    private ValueAnimator breathAnimator;
    private List<WavePoint> wavePoints;
    private Random random;
    private boolean isSpeaking;
    private Path wavePath;

    private int state;
    private static final int STATE_IDLE = 0;
    private static final int STATE_LISTENING = 1;
    private static final int STATE_THINKING = 2;
    private static final int STATE_SPEAKING = 3;

    private static final int POINT_COUNT = 8;
    private static final float MAX_AMPLITUDE = 30f;

    public AIAnimationView(Context context) {
        super(context);
        init();
    }

    public AIAnimationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public AIAnimationView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(Color.BLUE);

        wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        wavePaint.setColor(Color.WHITE);
        wavePaint.setStyle(Paint.Style.STROKE);
        wavePaint.setStrokeWidth(4f);

        effectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        effectPaint.setStyle(Paint.Style.STROKE);
        effectPaint.setStrokeWidth(4f);

        random = new Random();
        wavePoints = new ArrayList<>();
        wavePath = new Path();

        state = STATE_IDLE;
        isSpeaking = false;

        setupBreathAnimation();
        setupWavePoints();
    }

    private void setupBreathAnimation() {
        breathAnimator = ValueAnimator.ofFloat(0.95f, 1.05f);
        breathAnimator.setDuration(1500);
        breathAnimator.setRepeatCount(ValueAnimator.INFINITE);
        breathAnimator.setRepeatMode(ValueAnimator.REVERSE);
        breathAnimator.setInterpolator(new LinearInterpolator());
        breathAnimator.addUpdateListener(animation -> {
            float scale = (float) animation.getAnimatedValue();
            circleRadius = baseRadius * scale;
            invalidate();
        });
        breathAnimator.start();
    }

    private void setupWavePoints() {
        for (int i = 0; i < POINT_COUNT; i++) {
            wavePoints.add(new WavePoint());
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        baseRadius = Math.min(w, h) / 2f - MAX_AMPLITUDE - 20;
        circleRadius = baseRadius;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int centerX = getWidth() / 2;
        int centerY = getHeight() / 2;

        // 绘制呼吸效果的中心圆
        canvas.drawCircle(centerX, centerY, circleRadius, circlePaint);

        // 根据状态绘制不同效果
        switch (state) {
            case STATE_IDLE:
                // 仅显示呼吸效果
                break;
            case STATE_LISTENING:
                drawListeningEffect(canvas, centerX, centerY);
                break;
            case STATE_THINKING:
                drawThinkingEffect(canvas, centerX, centerY);
                break;
            case STATE_SPEAKING:
                if (isSpeaking) {
                    drawWaveform(canvas, centerX, centerY);
                }
                break;
        }

        invalidate();
    }

    private void drawWaveform(Canvas canvas, int centerX, int centerY) {
        wavePath.reset();
        float angleStep = 360f / POINT_COUNT;

        for (int i = 0; i < POINT_COUNT; i++) {
            WavePoint point = wavePoints.get(i);
            point.update();

            float angle = i * angleStep;
            float x = centerX + (float) Math.cos(Math.toRadians(angle)) * (circleRadius + point.amplitude);
            float y = centerY + (float) Math.sin(Math.toRadians(angle)) * (circleRadius + point.amplitude);

            if (i == 0) {
                wavePath.moveTo(x, y);
            } else {
                wavePath.lineTo(x, y);
            }
        }

        wavePath.close();
        canvas.drawPath(wavePath, wavePaint);
    }

    private void drawListeningEffect(Canvas canvas, int centerX, int centerY) {
        effectPaint.setColor(Color.GREEN);
        canvas.drawCircle(centerX, centerY, circleRadius + 20, effectPaint);
    }

    private void drawThinkingEffect(Canvas canvas, int centerX, int centerY) {
        effectPaint.setColor(Color.YELLOW);
        float angle = (System.currentTimeMillis() / 10) % 360;
        float x = centerX + (float) Math.cos(Math.toRadians(angle)) * (circleRadius + 30);
        float y = centerY + (float) Math.sin(Math.toRadians(angle)) * (circleRadius + 30);
        canvas.drawCircle(x, y, 10, effectPaint);
    }

    public void setIdleState() {
        state = STATE_IDLE;
        isSpeaking = false;
    }

    public void setListeningState() {
        state = STATE_LISTENING;
        isSpeaking = false;
    }

    public void setThinkingState() {
        state = STATE_THINKING;
        isSpeaking = false;
    }

    public void setSpeakingState() {
        state = STATE_SPEAKING;
        isSpeaking = true;
    }

    public void startSpeakingAnimation() {
        isSpeaking = true;
    }

    public void stopSpeakingAnimation() {
        isSpeaking = false;
    }

    private class WavePoint {
        float amplitude;
        float speed;

        WavePoint() {
            reset();
        }

        void reset() {
            amplitude = random.nextFloat() * MAX_AMPLITUDE;
            speed = random.nextFloat() * 2 + 1;
        }

        void update() {
            amplitude += speed;
            if (amplitude > MAX_AMPLITUDE || amplitude < 0) {
                speed = -speed;
            }
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (breathAnimator != null) {
            breathAnimator.cancel();
        }
    }
}