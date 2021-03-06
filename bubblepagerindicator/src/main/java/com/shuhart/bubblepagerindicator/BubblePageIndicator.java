package com.shuhart.bubblepagerindicator;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Style;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.view.animation.AccelerateDecelerateInterpolator;

import static android.graphics.Paint.ANTI_ALIAS_FLAG;

public class BubblePageIndicator extends MotionIndicator implements ViewPager.OnPageChangeListener {
    private static final long ANIMATION_TIME = 300;
    private static final int SWIPE_RIGHT = 1000;
    private static final int SWIPE_LEFT = 1001;

    private static final int ANIMATE_SHIFT_LEFT = 2000;
    private static final int ANIMATE_SHIFT_RIGHT = 2001;
    private static final int ANIMATE_IDLE = 2002;

    private static final int DEFAULT_ON_SURFACE_COUNT = 5;
    private static final int DEFAULT_RISING_COUNT = 2;

    private static final float ADD_RADIUS_DEFAULT = 1;

    private int onSurfaceCount = DEFAULT_ON_SURFACE_COUNT;
    private int risingCount = DEFAULT_RISING_COUNT;
    private int surfaceStart;
    private int surfaceEnd = onSurfaceCount - 1;
    private float radius;
    private float marginBetweenCircles;
    private final Paint paintPageFill = new Paint(ANTI_ALIAS_FLAG);
    private final Paint paintFill = new Paint(ANTI_ALIAS_FLAG);
    private int scrollState;
    private float addRadius = ADD_RADIUS_DEFAULT;

    private ViewPagerProvider pagerProvider;
    private ValueAnimator translationAnim;

    private int startX = Integer.MIN_VALUE;
    private float offset;

    private int swipeDirection;
    private int animationState = ANIMATE_IDLE;

    public BubblePageIndicator(Context context) {
        this(context, null);
    }

    public BubblePageIndicator(Context context, AttributeSet attrs) {
        this(context, attrs, R.attr.BubblePageIndicatorDefaultStyle);
    }

    public BubblePageIndicator(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        if (isInEditMode()) return;

        //Load defaults from resources
        final Resources res = getResources();
        final int defaultPageColor = ContextCompat.getColor(context, R.color.default_bubble_indicator_page_color);
        final int defaultFillColor = ContextCompat.getColor(context, R.color.default_bubble_indicator_fill_color);
        final float defaultRadius = res.getDimension(R.dimen.default_bubble_indicator_radius);
        final boolean defaultCentered = res.getBoolean(R.bool.default_bubble_indicator_centered);

        //Retrieve styles attributes
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.BubblePageIndicator, defStyle, 0);

        paintPageFill.setStyle(Style.FILL);
        paintPageFill.setColor(a.getColor(R.styleable.BubblePageIndicator_pageColor, defaultPageColor));
        paintFill.setStyle(Style.FILL);
        paintFill.setColor(a.getColor(R.styleable.BubblePageIndicator_fillColor, defaultFillColor));
        radius = a.getDimension(R.styleable.BubblePageIndicator_radius, defaultRadius);
        marginBetweenCircles = a.getDimension(R.styleable.BubblePageIndicator_marginBetweenCircles, radius);

        a.recycle();
    }

    public void setAddRadius(float value) {
        addRadius = value;
        invalidate();
    }

    public void setOnSurfaceCount(int onSurfaceCount) {
        this.onSurfaceCount = onSurfaceCount;
        invalidate();
    }

    public void setRisingCount(int risingCount) {
        this.risingCount = risingCount;
        invalidate();
    }

    public void setPageColor(int pageColor) {
        paintPageFill.setColor(pageColor);
        invalidate();
    }

    public int getPageColor() {
        return paintPageFill.getColor();
    }

    public void setFillColor(int fillColor) {
        paintFill.setColor(fillColor);
        invalidate();
    }

    public int getFillColor() {
        return paintFill.getColor();
    }

    public void setRadius(float radius) {
        this.radius = radius;
        invalidate();
    }

    public void setMarginBetweenCircles(float margin) {
        this.marginBetweenCircles = margin;
    }

    public float getRadius() {
        return radius;
    }

    @Override
    protected int getRealCount() {
        return pagerProvider.getRealCount();
    }

    @Override
    public int getPaddingLeft() {
        return (int) Math.max(super.getPaddingLeft(), marginBetweenCircles);
    }

    @Override
    public int getPaddingRight() {
        return (int) Math.max(super.getPaddingRight(), marginBetweenCircles);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        if (viewPager == null) {
            return;
        }

        final int count = getRealCount();
        if (count == 0 || count == 1) {
            return;
        }

        int shortPaddingBefore = getPaddingTop();
        final float shortOffset = shortPaddingBefore + radius + 1;

        //Draw stroked circles
        drawStrokedCircles(canvas, count, shortOffset, startX);

        //Draw the filled circle according to the current scroll
        drawFilledCircle(canvas, shortOffset, startX);
    }

    private void drawStrokedCircles(Canvas canvas, int count, float shortOffset, float startX) {
        // Only paint fill if not completely transparent
        if (paintPageFill.getAlpha() == 0) {
            return;
        }
        float dX;
        for (int iLoop = 0; iLoop < count; iLoop++) {
            dX = startX + iLoop * (radius * 2 + marginBetweenCircles);

            if (dX < 0 || dX > getWidth()) continue;

            float scaledRadius = getScaledRadius(radius, iLoop);

            canvas.drawCircle(dX, shortOffset, scaledRadius, paintPageFill);
        }
    }

    private float getScaledRadius(float radius, int position) {
        // circles to the left of the surface
        if (position < surfaceStart) {
            float add = ((surfaceStart - position == 1) ? addRadius : 0);
            // swipe left
            if (swipeDirection == SWIPE_LEFT && animationState == ANIMATE_SHIFT_LEFT) {
                float finalRadius = radius / (2 << (surfaceStart - position - 1)) + add;
                float currentRadius = radius / (2 << (surfaceStart - position - 1)) * 2 + ((surfaceStart - position - 1 == 1) ? 1 : 0);
                return currentRadius - (1 - offset) * (currentRadius - finalRadius);
            } else if (swipeDirection == SWIPE_RIGHT && animationState == ANIMATE_SHIFT_RIGHT) { // swipe right
                float finalRadius = radius / (2 << (surfaceStart - position - 1)) + add;
                float currentRadius = radius / (2 << (surfaceStart - position));
                return currentRadius + (1 - offset) * (finalRadius - currentRadius);
            } else {
                return radius / (2 << (surfaceStart - position - 1)) + add;
            }
        } else if (position > surfaceEnd) { // circles to the right of the surface
            float add = ((position - surfaceEnd == 1) ? addRadius : 0);
            // swipe left
            if (swipeDirection == SWIPE_LEFT && animationState == ANIMATE_SHIFT_LEFT) {
                float finalRadius = radius / (2 << (position - surfaceEnd)) * 2 + add;
                float currentRadius = radius / (2 << (position - surfaceEnd));
                return currentRadius + (1 - offset) * (finalRadius - currentRadius);
            } else if (swipeDirection == SWIPE_RIGHT && animationState == ANIMATE_SHIFT_RIGHT) { // swipe right
                float finalRadius = radius / (2 << (position - surfaceEnd - 1)) + add;
                return finalRadius + offset * finalRadius;
            } else {
                return radius / (2 << (position - surfaceEnd - 1)) + add;
            }
        } else if (position == currentPage) {
            // swipe left
            if (swipeDirection == SWIPE_LEFT && animationState == ANIMATE_SHIFT_LEFT) {
                float finalRadius = radius + addRadius;
                float currentRadius = radius / 2 + addRadius;
                return currentRadius + (1 - offset) * (finalRadius - currentRadius);
            } else if (swipeDirection == SWIPE_RIGHT && animationState == ANIMATE_SHIFT_RIGHT) { // swipe right
                float finalRadius = radius + addRadius;
                float currentRadius = radius / 2 + addRadius;
                return currentRadius + (1 - offset) * (finalRadius - currentRadius);
            } else {
                return radius + addRadius;
            }
        }
        return radius;
    }

    private void drawFilledCircle(Canvas canvas, float shortOffset, float startX) {
        float dX;
        float dY;
        float cx = currentPage * (radius * 2 + marginBetweenCircles);
        dX = startX + cx;
        dY = shortOffset;
        canvas.drawCircle(dX, dY, getScaledRadius(radius, currentPage), paintFill);
    }

    public void setViewPager(ViewPager view, ViewPagerProvider pagerProvider) {
        this.pagerProvider = pagerProvider;
        if (viewPager == view) {
            return;
        }
        if (viewPager != null) {
            viewPager.removeOnPageChangeListener(this);
        }
        if (view.getAdapter() == null) {
            throw new IllegalStateException("ViewPager does not have adapter instance.");
        }
        viewPager = view;
        viewPager.addOnPageChangeListener(this);
        invalidate();
    }

    public void setViewPager(ViewPager view, ViewPagerProvider pagerProvider, int initialPosition) {
        setViewPager(view, pagerProvider);
        initialPosition = pagerProvider.getRealPosition(initialPosition);
        setCurrentItem(initialPosition);
    }

    public void setCurrentItem(int item) {
        if (viewPager == null) {
            throw new IllegalStateException("ViewPager has not been bound.");
        }
        if (item < 0 || item > pagerProvider.getRealCount()) {
            return;
        }
        viewPager.setCurrentItem(item);
        currentPage = item;
        invalidate();
    }

    public void notifyDataSetChanged() {
        invalidate();
    }

    @Override
    public void onPageScrollStateChanged(int state) {
        scrollState = state;
    }

    @Override
    public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
        if (position == currentPage) {
            if (positionOffset >= 0.5 && currentPage + 1 < pagerProvider.getRealCount()) {
                swipeDirection = SWIPE_LEFT;
                currentPage += 1;
                if (currentPage > surfaceEnd) {
                    animationState = ANIMATE_SHIFT_LEFT;
                    correctSurface();
                    invalidate();
                    animateShifting(startX, (int) (startX - (marginBetweenCircles + radius * 2)));
                } else {
                    animationState = ANIMATE_IDLE;
                    invalidate();
                }
            }
        } else if (position < currentPage) {
            if (positionOffset <= 0.5) {
                swipeDirection = SWIPE_RIGHT;
                currentPage = position;
                if (currentPage < surfaceStart) {
                    animationState = ANIMATE_SHIFT_RIGHT;
                    correctSurface();
                    invalidate();
                    animateShifting(startX, (int) (startX + (marginBetweenCircles + radius * 2)));
                } else {
                    animationState = ANIMATE_IDLE;
                    invalidate();
                }
            }
        }
    }

    private void animateShifting(final int from, final int to) {
        if (translationAnim != null && translationAnim.isRunning()) translationAnim.end();
        translationAnim = ValueAnimator.ofInt(from, to);
        translationAnim.setDuration(ANIMATION_TIME);
        translationAnim.setInterpolator(new AccelerateDecelerateInterpolator());
        translationAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator valueAnimator) {
                int val = (Integer) valueAnimator.getAnimatedValue();
                offset = (val - to) * 1f / (from - to);
                startX = val;
                invalidate();
            }
        });
        translationAnim.addListener(new AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animator) {
                animationState = ANIMATE_IDLE;
                startX = to;
                offset = 0;
                invalidate();
            }
        });
        translationAnim.start();
    }

    private void correctSurface() {
        if (currentPage > surfaceEnd) {
            surfaceEnd = currentPage;
            surfaceStart = surfaceEnd - (onSurfaceCount - 1);
        } else if (currentPage < surfaceStart) {
            surfaceStart = currentPage;
            surfaceEnd = surfaceStart + (onSurfaceCount - 1);
        }
    }

    @Override
    public void onPageSelected(int position) {
        if (scrollState == ViewPager.SCROLL_STATE_IDLE) {
            position = pagerProvider.getRealPosition(position);
            currentPage = position;
            correctSurface();
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(measureWidth(widthMeasureSpec), measureHeight(heightMeasureSpec));
        measureStartX();
    }

    private void measureStartX() {
        if (startX == Integer.MIN_VALUE) {
            if (pagerProvider.getRealCount() <= onSurfaceCount) {
                startX = (int) (getPaddingLeft() + radius);
            } else {
                startX = (int) (getPaddingLeft() + radius * 4 + marginBetweenCircles * 2);
            }
        }
    }

    private int measureWidth(int measureSpec) {
        int result;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if ((specMode == MeasureSpec.EXACTLY) || (viewPager == null)) {
            //We were told how big to be
            result = specSize;
        } else {
            //Calculate the width according the views count
            result = calculateExactWidth();
            //Respect AT_MOST value if that was what is called for by measureSpec
            if (specMode == MeasureSpec.AT_MOST) {
                result = Math.min(result, specSize);
            }
        }
        return result;
    }

    private int calculateExactWidth() {
        int count = pagerProvider.getRealCount();
        int maxCount = onSurfaceCount + risingCount * 2;
        int diff = maxCount - count;
        float width;
        if (diff <= 1) {
            width = getPaddingLeft() + getPaddingRight()
                    + (maxCount * 2 * radius) + (maxCount - 1) * marginBetweenCircles;
        } else {
            width = getPaddingLeft() + getPaddingRight()
                    + (count * 2 * radius) + (count - 1) * marginBetweenCircles;
            if (count > onSurfaceCount) {
                width += radius * 2 + marginBetweenCircles * 2;
            }
        }
        return (int) width;
    }

    private int measureHeight(int measureSpec) {
        int result;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        if (specMode == MeasureSpec.EXACTLY) {
            //We were told how big to be
            result = specSize;
        } else {
            //Measure the height
            result = (int) (2 * (radius + addRadius) + getPaddingTop() + getPaddingBottom());
            //Respect AT_MOST value if that was what is called for by measureSpec
            if (specMode == MeasureSpec.AT_MOST) {
                result = Math.min(result, specSize);
            }
        }
        return result;
    }
}
