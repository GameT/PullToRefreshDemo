package com.example.administrator.myapplication;

import android.content.Context;
import android.support.v4.widget.SwipeRefreshLayout;
import android.util.AttributeSet;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.animation.RotateAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.Scroller;
import android.widget.TextView;

/**
 * Created by Administrator on 2016/11/13.
 */

public class RefreshableView extends LinearLayout implements View.OnTouchListener {


    /**
     * 下拉刷新的监听器，使用下拉刷新的地方应该注册此监听器来获取刷新回调。
     */
    public interface PullToRefreshListener {

        /**
         * 刷新时会去回调此方法，在方法内编写具体的刷新逻辑。
         */
        void onRefresh();

    }

    public final static String TAG = "RefreshableView";

    /**
     * 下拉状态
     */
    public static final int STATUS_PULL_TO_REFRESH = 0;

    /**
     * 释放立即刷新状态
     */
    public static final int STATUS_RELEASE_TO_REFRESH = 1;

    /**
     * 正在刷新状态
     */
    public static final int STATUS_REFRESHING = 2;

    /**
     * 刷新完成或未刷新状态
     */
    public static final int STATUS_REFRESH_FINISHED = 3;

    /**
     * 下拉头部回滚的速度
     */
    public static final int SCROLL_TIME = 2000;

    /**
     * 下拉头的View
     */
    private View header;

    /**
     * 需要去下拉刷新的ListView
     */
    private ListView listView;

    /**
     * 刷新时显示的进度条
     */
    private ProgressBar progressBar;

    /**
     * 指示下拉和释放的箭头
     */
    private ImageView arrow;

    /**
     * 下拉头的高度
     */
    private int hideHeaderHeight;

    /**
     * 当前处理什么状态，可选值有STATUS_PULL_TO_REFRESH, STATUS_RELEASE_TO_REFRESH,
     * STATUS_REFRESHING 和 STATUS_REFRESH_FINISHED
     */
    private int currentStatus = STATUS_REFRESH_FINISHED;

    /**
     * 记录上一次的状态是什么，避免进行重复操作
     */
    private int lastStatus = currentStatus;

    /**
     * 手指按下时的屏幕纵坐标
     */
    private float yDown;

    /**
     * 在被判定为滚动之前用户手指可以移动的最大值。
     */
    private int touchSlop;

    /**
     * 是否已加载过一次layout，这里onLayout中的初始化只需加载一次
     */
    private boolean loadOnce;

    /**
     * 当前是否可以下拉，只有ListView滚动到头的时候才允许下拉
     */
    private boolean ableToPull;

    private PullToRefreshListener mListener;

    private float mLastY;

    private Scroller mScroller;

    public RefreshableView(Context context, AttributeSet attrs) {
        super(context, attrs);
        //添加下拉头布局
        header = LayoutInflater.from(context).inflate(R.layout.pull_to_refresh, null, true);
        arrow = (ImageView) header.findViewById(R.id.arrow);
        progressBar = (ProgressBar) header.findViewById(R.id.progress_bar);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        setOrientation(VERTICAL);
        addView(header, 0);

        mScroller = new Scroller(context);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (changed && !loadOnce) {
            //下拉头向上偏移，隐藏下拉头
            hideHeaderHeight = -header.getHeight();
            ((MarginLayoutParams) header.getLayoutParams()).topMargin = hideHeaderHeight;
            listView = (ListView) getChildAt(1);
            listView.setOnTouchListener(this);
            loadOnce = true;
        }
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        isAbleToPull(motionEvent);
        if (ableToPull) {
            switch (motionEvent.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    yDown = motionEvent.getRawY();
                    mLastY = motionEvent.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    Log.e(TAG, "getScrollY" + getScrollY());
                    Log.e(TAG, "-(" + motionEvent.getRawY() + "-" + mLastY + ")=" + (-(int) (motionEvent.getRawY() - mLastY)));
                    //手指上滑不处理事件
                    if (motionEvent.getRawY() - yDown <= 0 && getScrollY() == 0) {
                        mLastY = motionEvent.getRawY();
                        return false;
                    }
                    //小于最小滑动距离
                    if (motionEvent.getRawY() - yDown < touchSlop) {
                        mLastY = motionEvent.getRawY();
                        return false;
                    }
                    //手指是下滑并且下拉头完全隐藏执行下滑
                    if (motionEvent.getRawY() - yDown > 0 && getScrollY() <= 0) {
                        //滑动距离大于下拉头高度松手可刷新
                        if (getScrollY() < hideHeaderHeight) {
                            currentStatus = STATUS_RELEASE_TO_REFRESH;
                        } else {
                            currentStatus = STATUS_PULL_TO_REFRESH;
                        }
                        scrollBy(0, -(int) (motionEvent.getRawY() - mLastY));
                    }
                    mLastY = motionEvent.getRawY();
                    break;
                case MotionEvent.ACTION_UP:
                default:
                    if (currentStatus == STATUS_PULL_TO_REFRESH) {
                        //隐藏下拉头
                        smoothScrollBy();
                    }
                    if (currentStatus == STATUS_RELEASE_TO_REFRESH) {
                        //刷新
                        currentStatus = STATUS_REFRESHING;
                        updateHeaderView();
                        onRefresh();
                    }
                    break;
            }
            // 时刻记得更新下拉头中的信息
            if (currentStatus == STATUS_PULL_TO_REFRESH
                    || currentStatus == STATUS_RELEASE_TO_REFRESH) {
                updateHeaderView();
                // 当前正处于下拉或释放状态，要让ListView失去焦点，否则被点击的那一项会一直处于选中状态
                listView.setPressed(false);
                listView.setFocusable(false);
                listView.setFocusableInTouchMode(false);
                lastStatus = currentStatus;
                // 当前正处于下拉或释放状态，通过返回true屏蔽掉ListView的滚动事件
                return true;
            }
        }
        return false;
    }

    //刷新结束后调用的方法
    public void finishRefresh() {
        currentStatus = STATUS_REFRESH_FINISHED;
        smoothScrollBy();
    }

    /**
     * 更新下拉头中的信息。
     */
    private void updateHeaderView() {
        if (lastStatus != currentStatus) {
            if (currentStatus == STATUS_PULL_TO_REFRESH) {
                arrow.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                rotateArrow();
            } else if (currentStatus == STATUS_RELEASE_TO_REFRESH) {
                arrow.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                rotateArrow();
            } else if (currentStatus == STATUS_REFRESHING) {
                progressBar.setVisibility(View.VISIBLE);
                arrow.clearAnimation();
                arrow.setVisibility(View.GONE);
            }
        }
    }

    /**
     * 根据当前的状态来旋转箭头。
     */
    private void rotateArrow() {
        float pivotX = arrow.getWidth() / 2f;
        float pivotY = arrow.getHeight() / 2f;
        float fromDegrees = 0f;
        float toDegrees = 0f;
        if (currentStatus == STATUS_PULL_TO_REFRESH) {
            fromDegrees = 180f;
            toDegrees = 360f;
        } else if (currentStatus == STATUS_RELEASE_TO_REFRESH) {
            fromDegrees = 0f;
            toDegrees = 180f;
        }
        RotateAnimation animation = new RotateAnimation(fromDegrees, toDegrees, pivotX, pivotY);
        animation.setDuration(100);
        animation.setFillAfter(true);
        arrow.startAnimation(animation);
    }


    public void isAbleToPull(MotionEvent motionEvent) {
        View fristChiler = listView.getChildAt(0);
        if (fristChiler != null) {
            //如果ListView滑动到顶部可以下拉下拉头
            if (listView.getFirstVisiblePosition() == 0 && fristChiler.getTop() == 0) {
                if (!ableToPull) {
                    yDown = motionEvent.getRawY();
                }
                ableToPull = true;
            } else {
                ableToPull = false;
            }
        } else {
            //ListView为空，也可以下拉下拉头
            ableToPull = true;
        }
    }

    //设置刷新事件监听
    public void setRefreshListener(PullToRefreshListener l) {
        this.mListener = l;
    }

    //刷新方法
    public void onRefresh() {
//        smoothScrollBy( 0 , (-getScrollY()) + hideHeaderHeight);
        if (mListener != null) {
            mListener.onRefresh();
        }
    }

    public void smoothScrollBy() {
        mScroller.startScroll(0, getScrollY(), 0, -getScrollY(), SCROLL_TIME);
        invalidate();
    }

    @Override
    public void computeScroll() {
        if (mScroller.computeScrollOffset()) {
            scrollTo(mScroller.getCurrX(), mScroller.getCurrY());
            Log.e(TAG, "getScrollY" + getScrollY() + "**topmargin" + ((MarginLayoutParams) header.getLayoutParams()).topMargin);
            postInvalidate();
        }
    }
}
