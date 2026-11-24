package net.qiujuer.lame;

import java.io.Closeable;
import java.security.InvalidParameterException;

/**
 * 64-bit-safe Java wrapper for the bundled Android LAME JNI library.
 * The original wrapper incorrectly treated every negative jlong as an
 * initialization failure. Android's tagged native heap addresses may have
 * the sign bit set, so only zero denotes a null native pointer.
 */
public class Lame implements Closeable {
    public interface LameVersion {
        int MPEG_1 = 1;
        int MPEG_2 = 0;
        int MPEG_2_5 = 2;
    }

    public interface LameModel {
        int STEREO = 0;
        int JOINT_STEREO = 1;
        int MONO = 3;
        int AUTO = 5;
    }

    public interface LameQuality {
        int BEST = 0;
        int NEAR_BEST = 2;
        int GOOD = 5;
        int OK = 7;
        int WORST = 9;
    }

    private long mNativeLame;
    private int mInChannels;

    public Lame(int inSampleRate, int inChannels, int outSampleRate) {
        this(inSampleRate, inChannels, outSampleRate, 32, LameModel.AUTO, LameQuality.OK);
    }

    public Lame(int inSampleRate, int inChannels, int outSampleRate, int outBitrate, int quality) {
        this(inSampleRate, inChannels, outSampleRate, outBitrate, LameModel.AUTO, quality);
    }

    public Lame(int inSampleRate, int inChannels, int outSampleRate,
                int outBitrate, int model, int quality) {
        if (inSampleRate < 8000 || inSampleRate > 48000)
            throw new InvalidParameterException("Input sample rate must be between 8000 and 48000 Hz.");
        if (outSampleRate < 8000 || outSampleRate > inSampleRate)
            throw new InvalidParameterException("Output sample rate must be between 8000 Hz and input sample rate.");
        if (outBitrate > 320 || outBitrate < 8)
            throw new InvalidParameterException("Output bitrate must be between 8 and 320 kbps.");
        if (inChannels > 2 || inChannels < 1)
            throw new InvalidParameterException("Input channels must be 1 or 2.");
        if (model > 5 || model < 0)
            throw new InvalidParameterException("Invalid channel mode.");
        if (quality > 9 || quality < 0)
            throw new InvalidParameterException("Quality must be between 0 and 9.");

        long ptr = nInit(inSampleRate, inChannels, outSampleRate, outBitrate, model, quality);
        // IMPORTANT: native pointers are opaque bit patterns, not signed numbers.
        if (ptr == 0L) throw new RuntimeException("Initialize Lame failed: native encoder returned a null pointer.");
        mInChannels = inChannels;
        mNativeLame = ptr;
    }

    private void checkLame() {
        if (mNativeLame == 0L) throw new RuntimeException("Lame was closed.");
    }

    public int getVersion() { checkLame(); return nGetVersion(mNativeLame); }
    public int getMp3bufferSize() { checkLame(); return mGetMp3bufferSize(mNativeLame); }
    public int getMp3bufferSize(int samples) { checkLame(); return mGetMp3bufferSizeWithSamples(mNativeLame, samples); }

    @Override public void close() {
        checkLame();
        nClose(mNativeLame);
        mNativeLame = 0L;
    }

    public int encodeInterleaved(short[] bufLR, int samples, byte[] outMp3buf) {
        checkLame();
        return nEncodeShortInterleaved(mNativeLame, bufLR, samples, outMp3buf);
    }

    public int encode(short[] bufL, short[] bufR, int samples, byte[] outMp3buf) {
        checkLame();
        return nEncodeShort(mNativeLame, bufL, bufR, samples, outMp3buf);
    }

    public int flush(byte[] outMp3buf) { checkLame(); return nFlush(mNativeLame, outMp3buf); }

    private static native long nInit(int inSampleRate, int inChannels, int outSampleRate, int outBitrate, int model, int quality);
    private static native int nGetVersion(long lamePtr);
    private static native int mGetMp3bufferSize(long lamePtr);
    private static native int mGetMp3bufferSizeWithSamples(long lamePtr, int samples);
    private static native int nEncodeShortInterleaved(long lamePtr, short[] bufLR, int samples, byte[] outMp3buf);
    private static native int nEncodeShort(long lamePtr, short[] bufL, short[] bufR, int samples, byte[] outMp3buf);
    private static native int nFlush(long lamePtr, byte[] outBuf);
    private static native void nClose(long lamePtr);

    static { System.loadLibrary("mp3lame-lib"); }
}
