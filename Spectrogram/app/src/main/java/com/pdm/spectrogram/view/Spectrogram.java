package com.pdm.spectrogram.view;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.view.View;

/**
 * 通常普清音频的采样率为44.1KHZ,安卓处理音频输出都会进行重采样压缩成44.1KHZ，也就是如果要听高清音频，手机肯定是不行滴
 * 我们这里做的是8分频fft，8分频是为了取低频数据,因为fft处理后的数据呈线性变换，间隔都是一样的，如果间隔太大，会导致很多频段的低频数据取不到
 * Author:pdm on 2016/3/15
 * Email:aiyh0202@163.com
 * CSDN:http://blog.csdn.net/aiyh0202
 * GitHub:https://github.com/flyingfishes
 */
public class Spectrogram extends View {
    private static final String TAG = "Spectrogram";
    //圆周率
    private static final double PI = 3.14159265f;
    // 每次取8K数据，因为需要8分频（每次采样1024个点）1024*8，8分频是为了取低频数据
    public static final int SAMPLING_TOTAL = 8192;
    private static final int FFT_SIZE = 1024; // 进行两次1024个数据的FFT
    //中间显示的段数，这里取31段展示
    private static final int SPECTROGRAM_COUNT = 31;
    //这里代表最高电频（最多的格子数）
    private static final int ROW_LOCAL_COUNT = 32;
    /**
     * 高频与低频的分界位置
     */
    private static final int LowFreqDividing = 14;
    /**
     * 纵坐标分布数组
     */
    private double[] row_local_table = new double[ROW_LOCAL_COUNT];

    public void setBitspersample(int bitspersample) {
        this.bitspersample = bitspersample;
    }

    private int bitspersample = 16;//这里默认为16位

    private double bits;//音频编码长度存储的最大10进制的值
    /**
     * 两个柱形之间的间隔
     */
    private static final int XINTERVAL = 10;
    private static final int YINTERVAL = 8;

    private Canvas canvas = new Canvas();
    private Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float LineViewWidth = 0;
    private float LineViewHeight = 0;
    //不分频的实部和虚部
    private double[] first_fft_real = new double[FFT_SIZE];
    private double[] first_fft_imag = new double[FFT_SIZE];
    //分频后的实部和虚部
    private double[] second_fft_real = new double[FFT_SIZE];
    private double[] second_fft_imag = new double[FFT_SIZE];
    //绘制频谱的实部和虚部
    private double[] real = new double[SPECTROGRAM_COUNT];
    private double[] imag = new double[SPECTROGRAM_COUNT];

    // 落差效果，记录最高点的坐标
    private int[] top_local = new int[ROW_LOCAL_COUNT]; // 绿色点的坐标
    /**
     * 达到最大时,等到该数达到定值时才开始下落top_local_count[i] = 0的时候，处于最高点，top_local_count[i]会一直
     * 叠加到10，最高点方格才会下落
     */
    private int[] top_local_count = new int[ROW_LOCAL_COUNT];
    //下落延时次数
    private static final int DELAY = 10;

    /**
     * 方格显示方式
     */
    public static final int GRID_TYPE_SHOW = 1;
    /**
     * 波形显示方式
     */
    public static final int WAVE_TYPE_SHOW = 2;
    /**
     * 没有显示
     */
    public static final int NONE_TYPE_SHOW = 3;

    private int show_type = GRID_TYPE_SHOW;

    /**
     * true=有信号,false=无信号
     */
    private boolean Signaled = false;

    private int[] data = new int[SAMPLING_TOTAL];

    private static final int INVALIDATE = 121212;
    private int sepColor, textColor, topColor;
    private int sepAlpha, textAlpha;
    private double[] sampleratePoint;//显示频点
    private int[] loc;//取31组频率
    private double step = 0;

    public int getTextAlpha() {
        return textAlpha;
    }

    public void setTextAlpha(int textAlpha) {
        this.textAlpha = textAlpha;
    }

    public int getSepAlpha() {
        return sepAlpha;
    }

    public void setSepAlpha(int sepAlpha) {
        this.sepAlpha = sepAlpha;
    }

    @SuppressLint("HandlerLeak")
    private Handler mHandler = new Handler() {
        public void handleMessage(Message message) {
            switch (message.what) {
                case INVALIDATE:
                    invalidate();
                    break;
            }
        }
    };

    public Spectrogram(Context context, AttributeSet attrs) {
        super(context, attrs);
        // TODO Auto-generated private static final intructor stub
        for (int i = 0; i < FFT_SIZE; i++) {
            first_fft_real[i] = 0;
            first_fft_imag[i] = 0;
            second_fft_real[i] = 0;
            second_fft_imag[i] = 0;
        }
        for (int i = 0; i < SPECTROGRAM_COUNT; i++) {
            real[i] = 0;
            imag[i] = 0;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // TODO Auto-generated method stub
        super.onDraw(canvas);
        this.canvas = canvas;
        LineViewWidth = this.getWidth() - XINTERVAL;
        LineViewHeight = this.getHeight() - YINTERVAL;
        //频谱颜色
        sepColor = Color.rgb(63, 81, 181);
        //字体颜色
        textColor = Color.rgb(63, 81, 181);
        //最高点方格颜色
        topColor = Color.rgb(51, 181, 229);
        // 设置透明度(有真实信号加深)0-250
        setSepAlpha(250);
        setTextAlpha(250);
        // 构建纵坐标的值;bits = 16位数转十进制的最大值,这里面的值是定值，只需要算一次
        if (step == 0) {
            row_local_table[0] = 5.0;
            bits = Math.pow(2.0, bitspersample - 1) - 1;
            step = Math.pow(bits / row_local_table[0], 1.0 / ROW_LOCAL_COUNT);// x的y次幂
            for (int i = 1; i < ROW_LOCAL_COUNT; i++) {
                row_local_table[i] = row_local_table[i - 1] * step;
            }
        }
        //显示频谱
        if (show_type == GRID_TYPE_SHOW) {
            // 格子的频谱(绘制横坐标)
            drawSpectrogramAxis();
            //绘制纵坐标方格
            drawGridTypeSpectrogram(real, imag);
        } else if (show_type == WAVE_TYPE_SHOW) {
            // 显示波形图
            drawWave();
        } else if (show_type == NONE_TYPE_SHOW) {

        }
    }

    /**
     * 设置显示方式
     *
     * @param type
     */
    public void setShowType(int type) {
        show_type = type;
        invalidate();
    }

    /**
     * 更改显示方式
     */
    public void changeShowType() {
        if (show_type == GRID_TYPE_SHOW) {
            show_type = WAVE_TYPE_SHOW;
        } else if (show_type == WAVE_TYPE_SHOW) {
            show_type = GRID_TYPE_SHOW;
        }
        // 刷新
        invalidate();
    }

    /**
     * 绘制频率坐标
     */
    private void drawSpectrogramAxis() {
        //这里对应的值是30HZ-20KHZ，中间的值成对数关系，即sampleratePoint
        String[] X_axis = {"20Hz", "46Hz", "69Hz", "105Hz", "160Hz", "244Hz",
                "371Hz", "565Hz", "859Hz", "1.30KHz", "1.98KHz", "3.02KHz",
                "4.60KHz", "7.00KHz", "10.6KHz", "20KHz"};
        float x_step = LineViewWidth / SPECTROGRAM_COUNT;
        //这里计算的是格子的宽度
        float width = x_step - XINTERVAL;
        // 横坐标(Hz)
        mPaint.setColor(textColor);
        mPaint.setAlpha(getTextAlpha());
        mPaint.setTextSize(15f);
        for (int i = 0; i < X_axis.length; i++) {
            float textWidth = mPaint.measureText(X_axis[i]);
            //这里是为了计算格子跟字的宽度差，用来确定字的位置,确保字跟方格中心在一条直线
            float xBad = (width - textWidth) / 2;
            float x = XINTERVAL + 2 * i * x_step + xBad;
            //获取文字上坡度(为负数)和下坡度的高度
            Paint.FontMetrics font = mPaint.getFontMetrics();
            float y = -(font.ascent + font.descent) / 2;
            canvas.drawText(X_axis[i], x, LineViewHeight - YINTERVAL/2 + y, mPaint);
        }

    }
    /**
     * 柱形频谱：方格方式显示
     *
     * @param real
     * @param imag
     */
    private void drawGridTypeSpectrogram(double real[], double imag[]) {
        double model;
        int[] local = new int[ROW_LOCAL_COUNT];
        //计算绘制频谱格子的宽度
        float x_step = LineViewWidth / SPECTROGRAM_COUNT;
        //格子的高度
        float y_step = LineViewHeight / ROW_LOCAL_COUNT;
        canvas.save();
        canvas.translate(0, -10);
        for (int i = 0; i < SPECTROGRAM_COUNT; i++) {
            model = 2 * Math.hypot(real[i], imag[i]) / FFT_SIZE;// 计算电频最大值，Math.hypot(x,y)返回sqrt(x2+y2)，最高电频
            for (int k = 1; k < ROW_LOCAL_COUNT; k++) {
                if (model >= row_local_table[k - 1]
                        && model < row_local_table[k]) {
                    local[i] = k - 1;//这里取最高电频所对应的方格数
                    break;
                }
            }
            // 最上面的为0位置，最下面的为31位置,为了方便绘制top方格
            local[i] = ROW_LOCAL_COUNT - local[i];
            // 柱形
            if (Signaled) {
                mPaint.setColor(sepColor);
                mPaint.setAlpha(getSepAlpha());
            } else {
                mPaint.setColor(sepColor);
                mPaint.setAlpha(getSepAlpha());
            }
            float x = XINTERVAL + i * x_step;
            for (int j = ROW_LOCAL_COUNT; j > local[i]; j--) {
                float y = (j - 1) * y_step;
                canvas.drawRect(x, y, x + x_step - XINTERVAL, y + y_step - YINTERVAL,
                        mPaint);// 绘制矩形,左上右下
            }
            // 绿点
            if (Signaled) {
                mPaint.setColor(topColor);
                mPaint.setAlpha(getSepAlpha());
            } else {
                mPaint.setColor(topColor);
                mPaint.setAlpha(getSepAlpha());
            }
            //下面部分是用来显示落差效果的，没有强大的理解能力可能会绕晕，所以我也不做过多注释，看个人天赋吧
            //local[i] < top_local[i]说明最高点改变（local[i]越小，点越高，这里需要注意）
            if (local[i] < top_local[i]) {
                //当进入到这里，说明达到最大电频，这个矩形会停留十次循环的时间才有改变
                top_local[i] = local[i];
                top_local_count[i] = 0;
            } else {
                top_local_count[i]++;
                //这里top_local_count这个是用来记录达到top值的柱体，然后会循环10次开始
                // top_local_count中小于DELAY的top_local都保持不变
                if (top_local_count[i] >= DELAY) {
                    top_local_count[i] = DELAY;
                    //这里控制下降的速度
                    top_local[i] = local[i] > (top_local[i] + 1) ? (top_local[i] + 1) : local[i];
                }
            }
            //y增加则最高位方格下降
            float y = top_local[i] * y_step;
            canvas.drawRect(x, y, x + x_step - XINTERVAL, y + y_step - YINTERVAL, mPaint);// 最高位置的方格
        }
        canvas.restore();
    }

    /**
     * 显示频谱时进行FFT计算
     *
     * @param buf
     * @param samplerate 采样率
     */
    public void spectrogram(int[] buf, double samplerate) {
        first_fft_real[0] = buf[0];
        first_fft_imag[0] = 0;

        second_fft_real[0] = buf[0];
        second_fft_imag[0] = 0;

        for (int i = 0; i < FFT_SIZE; i++) {
            first_fft_real[i] = buf[i];
            first_fft_imag[i] = 0;
            // 八分频(相当于降低了8倍采样率)，这样1024缓存区中的fft频率密度就越大，有利于取低频
            second_fft_real[i] = (buf[i * 8] + buf[i * 8 + 1] + buf[i * 8 + 2]
                    + buf[i * 8 + 3] + buf[i * 8 + 4] + buf[i * 8 + 5]
                    + buf[i * 8 + 6] + buf[i * 8 + 7]) / 8.0;
            second_fft_imag[i] = 0;
        }
        // 高频部分从原始数据取
        fft(first_fft_real, first_fft_imag, FFT_SIZE);

        // 八分频后的1024个数据的FFT,频率间隔为5.512Hz(samplerate / 8)，取低频部分
        fft(second_fft_real, second_fft_imag, FFT_SIZE);
        //这里算出的是每一个频点的坐标，对应横坐标的值，因为是定值，所以只需要算一次
        if (loc == null) {
            loc = new int[SPECTROGRAM_COUNT];
            sampleratePoint = new double[SPECTROGRAM_COUNT];
            for (int i = 0; i < loc.length; i++) {
                //20000表示的最大频点20KHZ,这里的20-20K之间坐标的数据成对数关系,这是音频标准
                double F = Math.pow(20000 / 20, 1.0 / SPECTROGRAM_COUNT);//方法中20为低频起点20HZ，31为段数
                sampleratePoint[i] = 20 * Math.pow(F, i);//乘方，30为低频起点
                //这里的samplerate为采样率(samplerate / (1024 * 8))是8分频后点FFT的点密度
                loc[i] = (int) (sampleratePoint[i] / (samplerate / (1024 * 8)));//估算出每一个频点的位置
            }
        }
        //低频部分
        for (int j = 0; j < LowFreqDividing; j++) {
            int k = loc[j];
            // 低频部分：八分频的数据,取31段，以第14段为分界点，小于为低频部分，大于为高频部分
            // 这里的14是需要取数后分析确定的，确保低频有足够的数可取
            real[j] = second_fft_real[k];
            imag[j] = second_fft_imag[k];
        }
        // 高频部分，高频部分不需要分频
        for (int m = LowFreqDividing; m < loc.length; m++) {
            int k = loc[m];
            real[m] = first_fft_real[k / 8];
            imag[m] = first_fft_imag[k / 8];
        }
    }

    /**
     * 方格方式显示背景
     */
    private void drawGridTypeSpectrogrambg() {
        float x_step = LineViewWidth / SPECTROGRAM_COUNT;
        float y_step = LineViewHeight / ROW_LOCAL_COUNT;

        mPaint.setColor(Color.rgb(0x1f, 0x1f, 0x1f));
        for (int i = 0; i < SPECTROGRAM_COUNT; i++) {
            float x = 25 + i * x_step;
            for (int j = 0; j < ROW_LOCAL_COUNT; j++) {
                float y = j * y_step;
                canvas.drawRect(x, y, x + x_step - XINTERVAL, y + y_step - YINTERVAL,
                        mPaint);
            }
        }
    }

    /**
     * 绘制波形
     */
    private void drawWave() {
        int len = data.length;
        float step = len / LineViewWidth;
        if (step == 0)
            step = 1;
        float prex = 0, prey = 0; // 上一个坐标
        float x, y;

        if (Signaled) {
            mPaint.setColor(sepColor);
        } else {
            mPaint.setColor(sepColor);
        }
        double k = LineViewHeight / 2 / bits;// 采样点音频为16位

        for (int i = 0; i <= LineViewWidth / 2; i++) {
            x = 10 + i * 2;//两个点之间X轴方向的间隔
            if (i * 3 < data.length) {
                y = LineViewHeight / 2
                        - (int) (data[i * 3] * k);
                if (i != 0) {
                    canvas.drawLine(x, y, prex, prey, mPaint);
                }
                prex = x;
                prey = y;
            }
        }

    }

    /**
     * 绘制波形,这里用于实施音频绘制(接收其他设备传过来的音源)
     */
    private void drawWave1() {
        int len = data.length;
        float step = len / LineViewWidth;
        if (step == 0)
            step = 1;

        float prex = 0, prey = 0; // 上一个坐标
        float x = 0, y = 0;

        if (Signaled) {
            mPaint.setColor(Color.RED);
        } else {
            mPaint.setColor(Color.rgb(0x1f, 0x1f, 0x1f));
        }
        double k = LineViewHeight / 2.0 / bits;
        for (int i = 0; i < LineViewWidth; ++i) {
            x = i;

            // 下面是个三点取出并绘制
            // 实际中应该按照采样率来设置间隔
            y = LineViewHeight / 2 - (int) (data[i * 3] * k);

            if (i != 0) {
                canvas.drawLine(x, y, prex, prey, mPaint);
            }
            prex = x;
            prey = y;
        }

    }

    /**
     * 绘制频谱,提供绘制接口，每次传8K数据
     *
     * @param buf      长度为8192
     * @param Signaled 有无信号
     */
    public synchronized void ShowSpectrogram(int[] buf, boolean Signaled,
                                             double samplerate) {
        this.Signaled = Signaled;
        // 绘制网格频谱
        if (show_type == GRID_TYPE_SHOW) {
            spectrogram(buf, samplerate);

        }// 绘制波形频谱
        else if (show_type == WAVE_TYPE_SHOW) {
            data = buf;

        } else if (show_type == NONE_TYPE_SHOW) {

            return;
        }
        // 刷屏
        mHandler.sendMessage(mHandler.obtainMessage(INVALIDATE, ""));
    }

    /**
     * 快速傅立叶变换，将复数 x 变换后仍保存在 x 中(这个算法可以不用理解，直接用)，转成频率轴的数（呈线性分步）
     * 计算出每一个点的信号强度，即电频强度
     *
     * @param real 实部
     * @param imag 虚部
     * @param n    多少个数进行FFT,n必须为2的指数倍数
     * @return
     */
    private int fft(double real[], double imag[], int n) {
        int i, j, l, k, ip;
        int M = 0;
        int le, le2;
        double sR, sI, tR, tI, uR, uI;

        M = (int) (Math.log(n) / Math.log(2));

        // bit reversal sorting
        l = n / 2;
        j = l;
        // 控制反转，排序，从低频到高频
        for (i = 1; i <= n - 2; i++) {
            if (i < j) {
                tR = real[j];
                tI = imag[j];
                real[j] = real[i];
                imag[j] = imag[i];
                real[i] = tR;
                imag[i] = tI;
            }
            k = l;
            while (k <= j) {
                j = j - k;
                k = k / 2;
            }
            j = j + k;
        }
        // For Loops
        for (l = 1; l <= M; l++) { /* loop for ceil{log2(N)} */
            le = (int) Math.pow(2, l);
            le2 = (int) (le / 2);
            uR = 1;
            uI = 0;
            sR = Math.cos(PI / le2);// cos和sin消耗大量的时间，可以用定值
            sI = -Math.sin(PI / le2);
            for (j = 1; j <= le2; j++) { // loop for each sub DFT
                // jm1 = j - 1;
                for (i = j - 1; i <= n - 1; i += le) {// loop for each butterfly
                    ip = i + le2;
                    tR = real[ip] * uR - imag[ip] * uI;
                    tI = real[ip] * uI + imag[ip] * uR;
                    real[ip] = real[i] - tR;
                    imag[ip] = imag[i] - tI;
                    real[i] += tR;
                    imag[i] += tI;
                } // Next i
                tR = uR;
                uR = tR * sR - uI * sI;
                uI = tR * sI + uI * sR;
            } // Next j
        } // Next l

        return 0;
    }

}
