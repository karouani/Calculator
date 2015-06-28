/*
* Copyright (C) 2014 The Android Open Source Project
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package com.android2.calculator3.view;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Color;
import android.support.v4.view.MotionEventCompat;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.widget.RecyclerView;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import com.android2.calculator3.HistoryAdapter;
import com.android2.calculator3.NumberBaseManager;
import com.android2.calculator3.R;
import com.xlythe.floatingview.AnimationFinishedListener;

import io.codetail.animation.SupportAnimator;
import io.codetail.animation.ViewAnimationUtils;

public class CalculatorPadView extends FrameLayout {
    private VelocityTracker mVelocityTracker;
    private int mTouchSlop;
    private int mMinimumFlingVelocity;
    private int mMaximumFlingVelocity;
    private float mInitialMotion;
    private float mLastMotion;
    private float mLastDelta;
    private float mOffset;
    private final DisplayAnimator mAnimator = new DisplayAnimator(0, 1f);
    private TranslateState mLastState = TranslateState.COLLAPSED;
    private TranslateState mState = TranslateState.COLLAPSED;

    private View mBase;
    private View mOverlay;
    private View mFab;
    private View mTray;

    public CalculatorPadView(Context context) {
        super(context);
        setup();
    }

    public CalculatorPadView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setup();
    }

    public CalculatorPadView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setup();
    }

    public CalculatorPadView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        setup();
    }

    private void setup() {
        ViewConfiguration vc = ViewConfiguration.get(getContext());
        mMinimumFlingVelocity = vc.getScaledMinimumFlingVelocity();
        mMaximumFlingVelocity = vc.getScaledMaximumFlingVelocity();
        mTouchSlop = vc.getScaledTouchSlop();
        mOffset = getResources().getDimensionPixelSize(R.dimen.pad_page_margin);
    }

    public enum TranslateState {
        EXPANDED, COLLAPSED, PARTIAL
    }

    protected void onPageSelected(int position) {
    }

    public TranslateState getState() {
        return mState;
    }

    public boolean isExpanded() {
        return getState() == TranslateState.EXPANDED;
    }

    public boolean isCollapsed() {
        return getState() == TranslateState.COLLAPSED;
    }

    protected View getFab() {
        return mFab;
    }

    protected View getBase() {
        return mBase;
    }

    protected FrameLayout getBaseOverlay() {
        return (FrameLayout) mOverlay;
    }

    protected View getTray() {
        return mTray;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        if (initializeLayout(getState())) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }
    }

    /**
     * Sets up the height / position of the fab and tray
     *
     * Returns true if it requires a relayout
     * */
    protected boolean initializeLayout(TranslateState state) {
        mFab.setTranslationX((mFab.getWidth() - getWidth() / 4) / 2);
        mFab.setTranslationY((mFab.getHeight() - getHeight() / 4) / 2);
        if (state == TranslateState.EXPANDED) {
            mOverlay.setTranslationX(0);
            mFab.setScaleX(1f);
            mFab.setScaleY(1f);
        } else {
            mOverlay.setTranslationX(mOverlay.getWidth() + mOffset);
            mFab.setScaleX(0f);
            mFab.setScaleY(0f);
        }

        int trayHeight = getHeight() / 4;
        Log.d("TEST", "tray height: " + trayHeight);
        if (mTray.getLayoutParams().height != trayHeight) {
            mTray.getLayoutParams().height = trayHeight;
            return true;
        }

        return false;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mBase = getChildAt(0);
        mOverlay = getChildAt(1);
        mFab = getChildAt(2);
        mTray = getChildAt(3);
        mOverlay.setOnTouchListener(new OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return true;
            }
        });
        mTray.setVisibility(View.INVISIBLE);
        mFab.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                View sourceView = mFab;
                boolean reverse = mTray.getVisibility() == View.VISIBLE;
                mTray.setVisibility(View.VISIBLE);

                final SupportAnimator revealAnimator;
                final int[] clearLocation = new int[2];
                sourceView.getLocationInWindow(clearLocation);
                clearLocation[0] += sourceView.getWidth() / 2;
                clearLocation[1] += sourceView.getHeight() / 2;
                final int revealCenterX = clearLocation[0] - mTray.getLeft();
                final int revealCenterY = clearLocation[1] - mTray.getTop();
                final double x1_2 = Math.pow(mTray.getLeft() - revealCenterX, 2);
                final double x2_2 = Math.pow(mTray.getRight() - revealCenterX, 2);
                final double y_2 = Math.pow(mTray.getTop() - revealCenterY, 2);
                final float revealRadius = (float) Math.max(Math.sqrt(x1_2 + y_2), Math.sqrt(x2_2 + y_2));

                float start = reverse ? revealRadius : 0;
                float end = reverse ? 0 : revealRadius;
                revealAnimator =
                        ViewAnimationUtils.createCircularReveal(mTray,
                                revealCenterX, mTray.getHeight() / 2, start, end);
                revealAnimator.setDuration(getResources().getInteger(android.R.integer.config_shortAnimTime));
                if (reverse) {
                    revealAnimator.addListener(new AnimationFinishedListener() {
                        @Override
                        public void onAnimationFinished() {
                            mTray.setVisibility(View.INVISIBLE);
                        }
                    });
                }
                revealAnimator.start();
            }
        });
    }

    @Override
    public void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {
        // no
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        int action = MotionEventCompat.getActionMasked(ev);
        float pos = ev.getRawX();
        boolean intercepted = false;

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                mInitialMotion = pos;
                mLastMotion = pos;
                handleDown();
                break;
            case MotionEvent.ACTION_MOVE:
                // Reset initial motion if the user drags in a different direction suddenly
                if (pos - mInitialMotion / Math.abs(pos - mInitialMotion) != (pos - mLastMotion) / Math.abs(pos - mLastMotion)) {
                    mInitialMotion = mLastMotion;
                }

                float delta = Math.abs(pos - mInitialMotion);
                if (delta > mTouchSlop) {
                    float dx = pos - mInitialMotion;
                    if (dx < 0) {
                        intercepted = getState() == TranslateState.COLLAPSED;
                    } else if (dx > 0) {
                        intercepted = getState() == TranslateState.EXPANDED;
                    }
                }
                mLastMotion = pos;
                break;
        }

        return intercepted;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = MotionEventCompat.getActionMasked(event);

        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.addMovement(event);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
                // Handled in intercept
                break;
            case MotionEvent.ACTION_MOVE:
                handleMove(event);
                break;
            case MotionEvent.ACTION_UP:
                handleUp(event);
                break;
        }

        return true;
    }

    protected void handleDown() {
    }

    protected void handleMove(MotionEvent event) {
        float percent = getCurrentPercent();
        mAnimator.onUpdate(percent);
        mLastDelta = mLastMotion - event.getRawX();
        mLastMotion = event.getRawX();
        setState(TranslateState.PARTIAL);
    }

    protected void handleUp(MotionEvent event) {
        mVelocityTracker.computeCurrentVelocity(1000, mMaximumFlingVelocity);
        if (Math.abs(mVelocityTracker.getXVelocity()) > mMinimumFlingVelocity) {
            // the sign on velocity seems unreliable, so use last delta to determine direction
            if (mLastDelta > 0) {
                expand();
            } else {
                collapse();
            }
        } else {
            if (mLastMotion > getWidth() / 2) {
                expand();
            } else {
                collapse();
            }
        }
        mVelocityTracker.recycle();
        mVelocityTracker = null;
    }
    public void expand() {
        expand(null);
    }

    public void expand(Animator.AnimatorListener listener) {
        DisplayAnimator animator = new DisplayAnimator(getCurrentPercent(), 1f);
        if (listener != null) {
            animator.addListener(listener);
        }
        animator.start();
        setState(TranslateState.EXPANDED);
    }

    public void collapse() {
        collapse(null);
    }

    public void collapse(Animator.AnimatorListener listener) {
        DisplayAnimator animator = new DisplayAnimator(getCurrentPercent(), 0f);
        if (listener != null) {
            animator.addListener(listener);
        }
        animator.start();
        setState(TranslateState.COLLAPSED);
    }

    private void setState(TranslateState state) {
        if (mState != state) {
            mLastState = mState;
            mState = state;

            if (mState == TranslateState.EXPANDED) {
                onPageSelected(0);
            } else if (mState == TranslateState.COLLAPSED) {
                onPageSelected(1);
            }

            if (mState != TranslateState.EXPANDED) {
                Log.d("TEST", "settin' state");
                if (mTray.getVisibility() == View.VISIBLE) {
                    Log.d("TEST", "but da tray is visible. fix ittt");
                    if (android.os.Build.VERSION.SDK_INT >= 15) {
                        mFab.callOnClick();
                    } else {
                        mFab.performClick();
                    }
                }
            }
        }
    }

    protected float getCurrentPercent() {
        float percent = (mInitialMotion - mLastMotion) / getWidth();

        // Start at 100% if open
        if (mState == TranslateState.EXPANDED ||
                (mState == TranslateState.PARTIAL && mLastState == TranslateState.EXPANDED)) {
            percent += 1f;
        }
        percent = Math.min(Math.max(percent, 0f), 1f);
        Log.d("TEST", "percent: "+percent);
        return percent;
    }

    /**
     * An animator that goes from 0 to 100%
     **/
    private class DisplayAnimator extends ValueAnimator {
        public DisplayAnimator(float start, float end) {
            super();
            setFloatValues(start, end);
            addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
                @Override
                public void onAnimationUpdate(ValueAnimator animation) {
                    float percent = (float) animation.getAnimatedValue();
                    onUpdate(percent);
                }
            });
        }

        public void onUpdate(float percent) {
            // Update the drag animation
            View overlay = getChildAt(1);
            overlay.setTranslationX((getWidth() + mOffset) * (1 - percent));

            View fab = getChildAt(2);
            if (percent < 0.3f) {
                fab.setScaleX(0f);
                fab.setScaleY(0f);
            } else {
                fab.setScaleX(percent);
                fab.setScaleY(percent);
            }
        }

        float scale(float percent, float goal) {
            return 1f - percent * (1f - goal);
        }

        int mixColors(float percent, int initColor, int goalColor) {
            int a1 = Color.alpha(goalColor);
            int r1 = Color.red(goalColor);
            int g1 = Color.green(goalColor);
            int b1 = Color.blue(goalColor);

            int a2 = Color.alpha(initColor);
            int r2 = Color.red(initColor);
            int g2 = Color.green(initColor);
            int b2 = Color.blue(initColor);

            percent = Math.min(1, percent);
            percent = Math.max(0, percent);
            float a = a1 * percent + a2 * (1 - percent);
            float r = r1 * percent + r2 * (1 - percent);
            float g = g1 * percent + g2 * (1 - percent);
            float b = b1 * percent + b2 * (1 - percent);

            return Color.argb((int) a, (int) r, (int) g, (int) b);
        }
    }
}