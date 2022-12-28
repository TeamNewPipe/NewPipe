package org.schabi.newpipe.util;

import android.graphics.Rect;
import android.text.Layout;

import androidx.annotation.IntDef;
import androidx.annotation.IntRange;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import org.schabi.newpipe.views.NewPipeTextView;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.text.BreakIterator;
import java.util.Collections;
import java.util.Iterator;
import java.util.Set;
import java.util.WeakHashMap;

/* our home-grown cache holding the metrics for drawing the ellipsis and/or a ellipsis mask */
public final class EllipsizeParams {
    /* weak references to LayoutTickets post-service which may be gc'ed or recycled */
    private static Set<LayoutTicket> oldLayoutTickets = Collections.newSetFromMap(
            new WeakHashMap<>());

    public static void recycle(final LayoutTicket ticket) {
        if (ticket != null) {
            oldLayoutTickets.add(ticket);
        }
    }

    @Nullable
    public static LayoutTicket popOldLayoutTickets() {
        final Iterator<LayoutTicket> it = oldLayoutTickets.iterator();
        if (it.hasNext()) {
            final LayoutTicket ticket = it.next();
            it.remove();
            return ticket;
        }
        return null;
    }

    private EllipsizeParams() {
        // no impl pls
    }

    public static final boolean DEBUG = android.os.Debug.isDebuggerConnected();


    /*//////////////////////////////////////////////////////////////////////////
    // Ellipsizing companions
    //////////////////////////////////////////////////////////////////////////*/

    // magic numbers
    public static final int UNSET = -1;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ValueType.BOOLEAN, ValueType.INT, ValueType.FLOAT})
    public @interface ValueType {
        int BOOLEAN = 0;
        int INT = 1;
        int FLOAT = 2;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({Op.NORMAL, Op.ADD, Op.MINUS})
    public @interface Op {
        int NORMAL = 0;  // read: replace
        int ADD = 1;
        int MINUS = 2;
    }

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({Result.OK, Result.NOT_FOUND, Result.NOT_IN_RANGE,
            Result.ERROR, Result.NOT_IMPLEMENTED})
    public @interface Result {  // status codes
        int OK = -200;
        int NOT_FOUND = -404;
        int NOT_IN_RANGE = -416;
        int ERROR = -500;
        int NOT_IMPLEMENTED = -501;
    }

    public static final int HEADER_RESERVED = 0;

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({INITIALIZED, SCHEMA_1, SCHEMA_DEBUG, IS_LTR, SKIP_PREFIX_SPACE, ANIMATING})
    public @interface Flags {
    }

    public static final int FLAGS_START = 0;
    // booleans are stored in FLAGS
    public static final int INITIALIZED = FLAGS_START;
    public static final int SCHEMA_1 = 1 + FLAGS_START;
    public static final int SCHEMA_DEBUG = 2 + FLAGS_START;
    public static final int FALLBACK_LAYOUT = 3 + FLAGS_START;
    // LineMetrics enums
    public static final int IS_LTR = 4 + FLAGS_START;
    public static final int SKIP_PREFIX_SPACE = 5 + FLAGS_START;
    public static final int ELLIPSISING_MASK = 6 + FLAGS_START;
    // animation enums
    public static final int ANIMATING = 7 + FLAGS_START;
    /* keep this to last */
    public static final int FLAGS_END = 8 + FLAGS_START;

    /* reserved a byte to 'smuggle in' crossfadeEllipsis (bits 8-15) */
    public static final int CROSSFADE_ELLIPSIS = 8 + FLAGS_START;

    public static final int FLOAT_INDEX_START = 16 + FLAGS_START; // 15 slots (bits 16-30)

    // highest bit reserved for meta
    public static final int CONTINUATION = Integer.SIZE - 1; // highest bit (31) of int


    public static final int SCHEMA_0_START = 100;
    // LineMetrics enums (SCHEMA 0)
    public static final int LINEMETRICS_START = SCHEMA_0_START;
    /* properties */
    public static final int LINE_NO = 1 + LINEMETRICS_START;
    /* bounds */
    public static final int WIDTH = 2 + LINEMETRICS_START;
    public static final int TOP = 3 + LINEMETRICS_START;
    public static final int BOTTOM = 4 + LINEMETRICS_START;
    /* canvas coordinates */
    public static final int BASELINE = 5 + LINEMETRICS_START;
    public static final int LINEMAX = 6 + LINEMETRICS_START;  // float
    /* text offsets */
    public static final int LINE_START = 7 + LINEMETRICS_START;
    public static final int LINE_END = 8 + LINEMETRICS_START;
    /* keep this to last */
    public static final int LINEMETRICS_END = 9 + LINEMETRICS_START;

    // derived LineMetrics
    public static final int LINEMETRIC_DERIVED_START = 110;
    public static final int LINE_LEFT = 1 + LINEMETRIC_DERIVED_START;
    public static final int LINE_RIGHT = 2 + LINEMETRIC_DERIVED_START;
    public static final int LINEMETRIC_DERIVED_END = 3 + LINEMETRIC_DERIVED_START;

    // extended LineMetrics (computed without equivalent functions in Layout)
    public static final int LINEMETRIC_COMPUTED_START = 120;
    public static final int LINE_REMAINING_SPACE = 1 + LINEMETRIC_COMPUTED_START;
    public static final int LINE_START_EDGE = 2 + LINEMETRIC_COMPUTED_START;
    public static final int LINE_END_EDGE = 3 + LINEMETRIC_COMPUTED_START;
    public static final int LINEMETRIC_COMPUTED_END = 4 + LINEMETRIC_COMPUTED_START;

    public static final int LINEMETRICS_EXT_END = LINEMETRIC_COMPUTED_END;

    public static final int SCHEMA_1_START = 200;
    // EllipsisParams enums (SCHEMA 1)
    public static final int ELLIPSISPARAMS_START = SCHEMA_1_START;
    /* ellipsisBounds */
    public static final int EB_LEFT = 1 + ELLIPSISPARAMS_START;
    public static final int EB_RIGHT = 2 + ELLIPSISPARAMS_START;
    public static final int EB_TOP = 3 + ELLIPSISPARAMS_START;
    public static final int EB_BOTTOM = 4 + ELLIPSISPARAMS_START;
    /* ellipsisBaseline */
    public static final int EB_BASELINE = 5 + ELLIPSISPARAMS_START;
    /* keep this to last */
    public static final int ELLIPSISPARAMS_END = 6 + ELLIPSISPARAMS_START;

    // EllipsisParamsDebug enums (SCHEMA 1-D)
    public static final int ELLIPSISPARAMS_DEBUG_START = SCHEMA_1_START;
    /* lastLineBounds (shared with lastLineEndBounds; overlaps LineMetrics bounds) */
    public static final int LL_WIDTH = 2 + ELLIPSISPARAMS_DEBUG_START;
    public static final int LL_TOP = 3 + ELLIPSISPARAMS_DEBUG_START;
    public static final int LL_BOTTOM = 4 + ELLIPSISPARAMS_DEBUG_START;
    /* ellipsisBaseline */
    public static final int EB_BASELINE_D = 5 + ELLIPSISPARAMS_DEBUG_START;
    // lastLineEndBounds */                      // overlaps LINEMAX
    public static final int LE_EDGE = 6 + ELLIPSISPARAMS_DEBUG_START;
    /* ellipsisBounds */
    public static final int EB_LEFT_D = 1 + ELLIPSISPARAMS_DEBUG_START;
    public static final int EB_RIGHT_D = 7 + ELLIPSISPARAMS_DEBUG_START;
    public static final int EB_TOP_D = LL_TOP;
    public static final int EB_BOTTOM_D = LL_BOTTOM;
    /* keep this to last */
    public static final int ELLIPSISPARAMS_DEBUG_END = 8 + ELLIPSISPARAMS_DEBUG_START;

    public static int[] setupLineMetrics(@Nullable final int[] storage) {
        int[] configStorage = storage;
        final int size = accomodationSize();
        final int storageSize = size + extendedPages(size);
        if (configStorage == null || configStorage.length < storageSize) {
            configStorage = new int[storageSize];
        } else {
            configStorage[0] = 0;
        }
        initializeIndex(configStorage, size);
        setFlag(configStorage, INITIALIZED, true);
        if (EllipsizeParams.DEBUG) {
            setFlag(configStorage, SCHEMA_DEBUG, true);
        }
        return configStorage;
    }

    private static int accomodationSize() {
        // finds the lowest common size that could fit the schemas into one common storage
        return Math.max(getRawIndex(LINEMETRICS_END), getRawIndex(ELLIPSISPARAMS_DEBUG_END));
    }

    private static boolean initializeIndex(@NonNull final int[] params,
                                           @IntRange(from = 1) final int paramsSize) {
        final int indexSize = extendedPages(paramsSize);
        if (indexSize < params.length) {
            for (int i = 0; i < indexSize; i++) {
                params[i] = params[i] | (1 << CONTINUATION);
            }
            return true;
        }
        return false;
    }

    // get the actual index in storage
    private static int getRawIndex(@IntRange(from = FLAGS_START) final int flag) {
        // inclusive of end bounds (<=) in case it's called to compute size
        if (flag <= FLOAT_INDEX_START) {  // could have been FLAGS_END but to cover the use case
            return flag - FLAGS_START;
        } else if (flag <= LINEMETRICS_END) {
            return flag - SCHEMA_0_START;
        } else if (flag <= ELLIPSISPARAMS_DEBUG_END) {
            return flag - SCHEMA_1_START;
        }
        return Result.NOT_IN_RANGE;
    }

    // compute the additional 'pages' (int) of FLAGS required for paramsSize
    // ref: https://stackoverflow.com/a/20090375
    private static int extendedPages(@IntRange(from = 1) final int paramsSize) {
        return indexOffset(paramsSize - 1, false);
    }

    private static int indexOffset(@IntRange(from = FLAGS_START) final int enumIndex,
                                   final boolean isFlag) {
        final int pageSize = Integer.SIZE - 1; // minus the reserved highest bit
        // take also into account size reserved for flags
        final int headerSize = enumIndex + (isFlag ? 1 : getRawIndex(FLOAT_INDEX_START));
        // minus the first 'page' already reserved at int[0]
        return headerSize / pageSize - ((headerSize % pageSize == 0) ? 1 : 0);
    }

    // returns the additional 'pages' (int) used for FLAGS de facto, 0 if just int[0] in params
    private static int extHeader(@NonNull final int[] params) {
        int extendedPages = 0;
        for (int i = 0; i < params.length; i++) {
            // evaluate directly here instead of calling getBit() to avoid infinite loop
            if ((params[i] & (1 << CONTINUATION)) != 0) {
                extendedPages++;
            } else {
                break;
            }
        }
        return extendedPages;
    }

    // compute the 'page' no (int) of flag in params
    private static int indexPage(@NonNull final int[] params,
                                 @IntRange(from = FLAGS_START) final int enumIndex) {
        return indexOffset(enumIndex, true);
    }

    // ref: https://stackoverflow.com/a/12015619
    private static boolean getBit(@NonNull final int[] params,
                                  @IntRange(from = 0) final int index,
                                  @IntRange(from = 0, to = CONTINUATION - 1) final int bit) {
        if (index <= extHeader(params)) {
            return (params[index] & (1 << bit)) != 0;
        }
        return false;
    }

    private static boolean setBit(@NonNull final int[] params,
                                  @IntRange(from = 0) final int index,
                                  @IntRange(from = 0, to = CONTINUATION - 1) final int bit,
                                  final boolean markBit) {
        if (index <= extHeader(params)) {
            if (markBit) {
                params[index] |= (1 << bit);
            } else {
                params[index] &= ~(1 << bit);
            }
            return true;
        }
        return false;
    }

    private static boolean paramValid(@NonNull final int[] params, final int param) {
        return getFlag(params, INITIALIZED) && getRawIndex(param) > HEADER_RESERVED;
    }

    private static boolean setParamInt(@NonNull final int[] params,
                                       final int param, final int value) {
        return paramValid(params, param)
                && setInt(params, getRawIndex(param) + extHeader(params), value);
    }

    private static boolean setParamFloat(@NonNull final int[] params,
                                         final int param, final float value) {
        return paramValid(params, param)
                && setFloat(params, getRawIndex(param) + extHeader(params), value);
    }

    public static float getFloat(@NonNull final int[] params, final int param) {
        if (!paramValid(params, param)) {
            return -1;
        }
        final int index = getRawIndex(param) + extHeader(params);
        if (index < params.length) {
            final boolean isFloat = markedFloat(params, index);
            if (isFloat) {
                return Float.intBitsToFloat(params[index]);
            } else {
                return params[index];
            }
        }
        return -1;
    }

    public static int getInt(@NonNull final int[] params, final int param) {
        if (!paramValid(params, param)) {
            return -1;
        }
        final int index = getRawIndex(param) + extHeader(params);
        if (index < params.length) {
            final boolean isFloat = markedFloat(params, index);
            if (isFloat) {
                return (int) Float.intBitsToFloat(params[index]);
            } else {
                return params[index];
            }
        }
        return -1;
    }

    private static boolean getParam(@NonNull final int[] params, final int param,
                                    @NonNull final TypedValue value) {
        return paramValid(params, param)
                && getValue(params, getRawIndex(param) + extHeader(params), value);
    }

    public static boolean getFlag(@NonNull final int[] params, @Flags final int flag) {
        final int flagIndex = getRawIndex(flag);
        return getBit(params, indexPage(params, flagIndex), flagIndex);
    }

    public static boolean setFlag(@NonNull final int[] params, final int flag,
                                   final boolean b) {
        final int flagIndex = getRawIndex(flag);
        return setBit(params, indexPage(params, flagIndex), flagIndex, b);
    }

    // ref: https://stackoverflow.com/a/46385852
    public static int getByte(@NonNull final int[] params, @Flags final int flag) {
        final int flagIndex = getRawIndex(flag);
        final int index = indexPage(params, flagIndex);
        if (index <= extHeader(params)) {
            return (params[index] >>> flagIndex) & 0xff;
        }
        return -1;
    }

    // ref: https://stackoverflow.com/a/27870983
    public static boolean setByte(@NonNull final int[] params, @Flags final int flag,
                                   @IntRange(from = 0, to = 255) final int value) {
        final int flagIndex = getRawIndex(flag);
        final int index = indexPage(params, flagIndex);
        if (index <= extHeader(params)) {
            params[index] &= ~(0xff << flagIndex);  // clear current bit values
            params[index] |= ((value & 0xff) << flagIndex);
            return true;
        }
        return false;
    }

    private static boolean setValue(@NonNull final int[] params,
                                    @IntRange(from = 0) final int index,
                                    final int value, final boolean isFloat) {
        if (index < params.length) {
            params[index] = value;
            markFloat(params, index, isFloat);
            return true;
        }
        return false;
    }

    private static boolean setInt(@NonNull final int[] params,
                                  @IntRange(from = 0) final int index, final int value) {
        return setValue(params, index, value, false);
    }

    private static boolean setFloat(@NonNull final int[] params,
                                    @IntRange(from = 0) final int index, final float value) {
        return setValue(params, index, Float.floatToRawIntBits(value), true);
    }

    private static boolean getValue(@NonNull final int[] params,
                                    @IntRange(from = 0) final int index,
                                    @NonNull final TypedValue value) {
        if (index < params.length) {
            final boolean isFloat = markedFloat(params, index);
            if (isFloat) {
                value.setValue(Float.intBitsToFloat(params[index]));
            } else {
                value.setValue(params[index]);
            }
            return true;
        }
        return false;
    }

    private static boolean markFloat(@NonNull final int[] params,
                                     @IntRange(from = 0) final int index,
                                     final boolean isFloat) {
        final int extIndex = extHeader(params);
        final int flagIndex = index - 1 - extIndex + getRawIndex(FLOAT_INDEX_START);
        final int page = indexPage(params, flagIndex);
        if (page <= extIndex) {
            setBit(params, page, flagIndex - page * (Integer.SIZE - 1), isFloat);
            return true;
        }
        return false;
    }

    private static boolean markedFloat(@NonNull final int[] params,
                                       @IntRange(from = 0) final int index) {
        final int flagIndex = getRawIndex(FLOAT_INDEX_START) + index - 1 - extHeader(params);
        final int page = indexPage(params, flagIndex);
        if (page <= extHeader(params)) {
            return getBit(params, page, flagIndex - page * (Integer.SIZE - 2));
        }
        return false;
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Vehicles and Tickets
    //////////////////////////////////////////////////////////////////////////*/

    // a convenient type-agnostic transport 'vehicle' to shuffle primitives around
    // without worrying about its type or its type-corresponding method overloads
    // (it's a pity that Java generics doesn't seem to work with primitives wuthout boxing
    // so we're left with duplicating some parts of the code for primitive types overloads)
    private static class TypedValue {
        protected int value;
        protected int type = UNSET;
        protected int op = Op.NORMAL;

        public void setValue(final int v) {
            opValue(v);
            op = Op.NORMAL;
        }

        public void setValue(final float v) {
            opValue(v);
            op = Op.NORMAL;
        }

        public void setValue(final boolean v) {
            value = v ? 1 : 0;
            type = ValueType.BOOLEAN;
            op = Op.NORMAL;
        }

        public void opValue(final int v) {
            switch (op) {
                case Op.MINUS:
                case Op.ADD:
                    if (type == ValueType.FLOAT) {
                        value = Float.floatToRawIntBits(asFloat() + (op == Op.MINUS ? -v : v));
                    } else {
                        value += (op == Op.MINUS ? -v : v);
                        type = ValueType.INT;
                    }
                    break;
                default:
                    value = v;
                    type = ValueType.INT;
                    break;
            }
        }

        public void opValue(final float v) {
            switch (op) {
                case Op.MINUS:
                case Op.ADD:
                    if (type == ValueType.FLOAT) {
                        value = Float.floatToRawIntBits(asFloat() + (op == Op.MINUS ? -v : v));
                    } else {
                        value = Float.floatToRawIntBits(value + (op == Op.MINUS ? -v : v));
                        type = ValueType.FLOAT;
                    }
                    break;
                default:
                    value = Float.floatToRawIntBits(v);
                    type = ValueType.FLOAT;
                    break;
            }
        }

        public int getType() {
            return type;
        }

        public int getRawValue() {
            return value;
        }

        public int asInt() {
            return type == ValueType.FLOAT ? (int) Float.intBitsToFloat(value) : value;
        }

        public float asFloat() {
            return type == ValueType.FLOAT ? Float.intBitsToFloat(value) : (float) value;
        }

        public boolean asBoolean() {
            return value != 0;
        }
    }

    // acts as a temporary holder for g/seting params from/by different 'actors'
    private static class Ticket extends TypedValue {
        private int param = UNSET;
        private int status = UNSET;

        public Ticket setParam(final int p) {
            param = p;
            return this;
        }

        public int getParam() {
            return param;
        }

        public void resolved(final boolean success) {
            status = success ? Result.OK : Result.ERROR;
        }
    }

    // a wrapper class around the backing config storage
    // (since it's such a pain to directly work with the (primitive) int[] in raw)
    protected static class LayoutTicket extends Ticket implements AutoCloseable {
        private WeakReference<Layout> layout;
        private int[] cache;
        private int line = UNSET;
        private boolean cached = false;
        private UpgradeHelper upgrade;
        private BreakIterator wordIterator;

        public void reset() {
            layout = null;
            cache = null;
            line = UNSET;
            cached = false;
            upgrade = null;
        }
        @Override
        public void close() {
            reset();
            recycle(this);
        }
        private BreakIterator getWordInstance() {
            if (wordIterator == null) {
                wordIterator = BreakIterator.getWordInstance();
            }
            return wordIterator;
        }

        public static LayoutTicket from(final Layout l, final int ln) {
            final LayoutTicket ticket = popOldLayoutTickets();
            return (ticket != null ? ticket : new LayoutTicket()).setLayout(l).setLine(ln);
        }

        public LayoutTicket setLayout(final Layout l) {
            layout = new WeakReference<>(l);
            return this;
        }

        public Layout getLayout() {
            return layout == null ? null : layout.get();
        }

        public int[] getCache() {
            return cache;
        }

        public LayoutTicket setLine(@IntRange(from = 0) final int ln) {
            line = ln;
            return this;
        }

        public int getLine() {
            return line;
        }

        public LayoutTicket to(final int[] c) {
            cache = c;
            if (line != UNSET) {
                setParamInt(c, LINE_NO, line);
            }
            return this;
        }

        public LayoutTicket resolve(final int... params) {
            if (cached) {
                resolveFromCache(this, cache, params);
            } else {
                resolveFromLayout(this, cache == null ? null : resolveToCache, params);
            }
            return this;
        }

        public LayoutTicket cached(final boolean c) {
            cached = c;
            return this;
        }

        public LayoutTicket get(final int param) {
            op = Op.NORMAL;
            return resolve(param);
        }

        public LayoutTicket andThen(@Op final int operator, final int param) {
            op = operator;
            return resolve(param);
        }

        // offset the value in relation to text direction, +ve to move forward, -ve backwards
        public LayoutTicket advance(final int offset) {
            if (cache == null) {
                resolved(false);
                return this;
            }
            op = getFlag(cache, IS_LTR) ? Op.ADD : Op.MINUS;
            opValue(offset);
            op = Op.NORMAL;
            return this;
        }

        public LayoutTicket advance(final float offset) {
            if (cache == null) {
                resolved(false);
                return this;
            }
            op = getFlag(cache, IS_LTR) ? Op.ADD : Op.MINUS;
            opValue(offset);
            op = Op.NORMAL;
            return this;
        }

        public LayoutTicket set(final int param, final int value) {
            super.setParam(param);
            super.setValue(value);
            resolveToCache.accept(this);
            return this;
        }

        public LayoutTicket set(final int param, final float value) {
            super.setParam(param);
            super.setValue(value);
            resolveToCache.accept(this);
            return this;
        }

        public LayoutTicket set(final int param, final boolean value) {
            super.setParam(param);
            super.setValue(value);
            resolveToCache.accept(this);
            return this;
        }

        public UpgradeHelper upgrade() {
            if (upgrade == null) {
                upgrade = new UpgradeHelper().from(this).to(this);
                if (cache != null) {
                    setFlag(cache, SCHEMA_1, true);
                }
            }
            return upgrade;
        }
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Resolvers
    //////////////////////////////////////////////////////////////////////////*/

    // resolver 1: from cached line metrics
    private static void resolveFromCache(@NonNull final LayoutTicket t, final int[] cache,
                                         final int... params) {
        if (cache == null) {
            t.resolved(false);
            return;
        }
        for (int i = 0; i < params.length; i++) {
            final int param = params[i];
            t.setParam(param);
            if (param < FLAGS_END) {
                t.setValue(getFlag(cache, param));
                t.resolved(true);
            } else if (param < LINEMETRICS_END) {
                t.resolved(getParam(cache, param, t));
            } else {
                final boolean isLTR = getFlag(cache, IS_LTR);
                final boolean fallback = getFlag(cache, FALLBACK_LAYOUT);
                switch (param) {
                    case LINE_LEFT:
                        if (fallback) {
                            resolveFromLayout(t, null, param);
                        } else {
                            // from AOSP: mWidth - getLineMax(line)if ALIGN_RIGHT or 0
                            if (isLTR) {
                                t.setValue(0);
                            } else {
                                t.get(WIDTH).andThen(Op.MINUS, LINEMAX);
                            }
                        }
                        break;
                    case LINE_RIGHT:
                        if (fallback) {
                            resolveFromLayout(t, null, param);
                        } else {
                            // from AOSP: mWidth if ALIGN_RIGHT or getLineMax(line)
                            getParam(cache, isLTR ? LINEMAX : WIDTH, t);
                        }
                        break;
                    // extended
                    case LINE_REMAINING_SPACE:
                        if (isLTR) {
                            t.get(WIDTH).andThen(Op.MINUS, LINE_RIGHT);
                        } else {
                            t.get(LINE_LEFT);
                        }
                        break;
                    case LINE_START_EDGE:
                        if (isLTR) {
                            t.setValue(0);
                        } else {
                            getParam(cache, WIDTH, t);
                        }
                        break;
                    case LINE_END_EDGE:
                        t.get(isLTR ? LINE_RIGHT : LINE_LEFT);
                        break;
                    default:
                        t.resolved(false);
                        continue;
                }
                t.resolved(true);
            }
        }
    }

    // resolver 2 (fallback): directly from source (by calling respective Layout functions)
    private static void resolveFromLayout(@NonNull final LayoutTicket t,
                                          @Nullable final Consumer<LayoutTicket> resolveTo,
                                          final int... params) {
        final Layout layout = t.getLayout();
        final int line = t.getLine();
        if (layout == null) {
            t.resolved(false);
            return;
        }
        Rect bounds = null;
        for (int i = 0; i < params.length; i++) {
            t.setParam(params[i]);
            switch (t.getParam()) {
                case BASELINE:
                    if (bounds == null) {
                        bounds = new Rect();
                    }
                    t.setValue(layout.getLineBounds(line, bounds));
                    break;
                case WIDTH:
                    if (bounds != null) {
                        t.setValue(bounds.right);
                    } else {
                        t.setValue(layout.getWidth());
                    }
                    break;
                case TOP:
                    if (bounds != null) {
                        t.setValue(bounds.top);
                    } else {
                        t.setValue(layout.getLineTop(line));
                    }
                    break;
                case BOTTOM:
                    if (bounds != null) {
                        t.setValue(bounds.bottom);
                    } else {
                        t.setValue(layout.getLineBottom(line));
                    }
                    break;
                case LINEMAX:
                    t.setValue(layout.getLineMax(line));
                    break;
                case LINE_START:
                    t.setValue(layout.getLineStart(line));
                    break;
                case LINE_END:
                    t.setValue(layout.getLineEnd(line));
                    break;
                case IS_LTR:
                    t.setValue(layout.getParagraphDirection(line) == Layout.DIR_LEFT_TO_RIGHT);
                    break;
                default:
                    t.resolved(false);
                    continue;
            }
            t.resolved(true);
            if (resolveTo != null) {
                resolveTo.accept(t);
            }
        }
    }

    // writer to save line metrics to cache
    private static Consumer<LayoutTicket> resolveToCache = t -> {
        final int[] cache = t.getCache();
        if (cache == null) {
            t.resolved(false);
            return;
        }
        switch (t.getType()) {
            case ValueType.FLOAT:
                setParamFloat(cache, t.getParam(), t.asFloat());
                break;
            case ValueType.INT:
                setParamInt(cache, t.getParam(), t.asInt());
                break;
            case ValueType.BOOLEAN:
                setFlag(cache, t.getParam(), t.asBoolean());
                break;
            default:
                t.resolved(false);
                return;
        }
        t.resolved(true);
    };

    // highly experimental: might not be put into use after all
    private static class UpgradeHelper {
        private int[] tmpStorage;
        private LayoutTicket storage;
        private boolean inPlace = false;
        private int[] reservedIndex;

        public UpgradeHelper from(final LayoutTicket src) {
            storage = src;
            return this;
        }

        public UpgradeHelper to(final LayoutTicket dst) {
            if (dst == storage) {
                inPlace = true;
            } else {
                // not (yet) implemented
                throw new RuntimeException(-Result.NOT_IMPLEMENTED + ": Not Implemented");
            }
            return this;
        }

        private void initializeReservedIndex(final int size) {
            final int storageSize = size + extendedPages(size);
            if (reservedIndex == null || reservedIndex.length < storageSize) {
                reservedIndex = new int[storageSize];
            } else {
                reservedIndex[0] = 0;
            }
            initializeIndex(reservedIndex, size);
            setFlag(reservedIndex, INITIALIZED, true);
        }

        public UpgradeHelper defaultTo(final int ellipsisParam, final int lineMetric) {
            if (!inPlace) {
                // not (yet) implemented
                throw new RuntimeException(-Result.NOT_IMPLEMENTED + ": Not Implemented");
            }
            final int srcPos = getRawIndex(lineMetric);
            final int dstPos = getRawIndex(ellipsisParam);
            if (dstPos >= getRawIndex(LINEMETRICS_END)) {
                // we can safely write the value since there's no overlap
                // just copy from old position to new position since we're upgrading in-place
                storage.getCache()[dstPos] = storage.getCache()[srcPos];
            }
            if (srcPos == dstPos) {
                markReserved(srcPos, true);
            }
            return this;
        }

        private void markReserved(final int pos, final boolean b) {
            if (b && reservedIndex == null) {
                initializeReservedIndex(getRawIndex(LINEMETRICS_END));
            }
            if (reservedIndex != null) {
                setBit(reservedIndex, indexPage(reservedIndex, pos), pos, b);
            }
        }

        public UpgradeHelper set(final int ellipsisParam, final int v) {
            final int dstPos = getRawIndex(ellipsisParam);
            markReserved(dstPos, false);
            storage.set(ellipsisParam, v);
            return this;
        }

        public UpgradeHelper set(final int ellipsisParam, final float v) {
            final int dstPos = getRawIndex(ellipsisParam);
            markReserved(dstPos, false);
            storage.set(ellipsisParam, v);
            return this;
        }
    }


    /*//////////////////////////////////////////////////////////////////////////
    // Ellipsizing stuff
    //////////////////////////////////////////////////////////////////////////*/

    private static boolean shortCircuitsUnsupported(@IntRange(from = 0) final int line,
                                                    @NonNull final Layout layout) {
        // short-circuits are unimplemented for Alignment other than ALIGN_NORMAL
        // as there is simply no use case, fall back gracefully to Layout just in case
        return layout.getParagraphAlignment(line) != Layout.Alignment.ALIGN_NORMAL;
    }

    private static LayoutTicket warmUpLineMetrics(@IntRange(from = 0) final int line,
                                           @NonNull final Layout layout,
                                           @NonNull final int[] metrics) {
        return LayoutTicket.from(layout, line).to(metrics)
                .set(FALLBACK_LAYOUT, shortCircuitsUnsupported(line, layout))
                .resolve(BASELINE, WIDTH, TOP, BOTTOM, LINEMAX, LINE_START, LINE_END, IS_LTR)
                .cached(true);
    }

    // non-breaking space, ellipsis, non-breaking space
    // we surround nbsp at both ends so that we can just offset ELLIPSIS_LEN by 1 for RTL
    public static final char[] ELLIPSIS_CHARS = {'\u00a0', '\u2026', '\u00a0'};
    public static final int ELLIPSIS_LEN = 2;

    /* Our custom ellipsizing strategy implemented as follows: where lines of text don't fit,
     * - append the ellipsis to the end of the last line displayed, if screen estate fits; else
     * - crunch the last bit (word) of the last line to make room for the ellipsis.
     * It's been longstanding (bug) in Android's TextView the internal ellipsizing doesn't work
     * with BufferType.SPANNABLE (necessitated by setMovementMethod() other than null) at all
     * (so we're bound to roll our own). A feature over TextView's internal implementation
     * is that we crunch by word rather than characters (so the user won't be caught by surprise
     * when 'app...' was meant to mean 'approach' upon expanding the text, just as an example).
     * (We defer to BreakIterator for the last 'word' since not every language delimits words
     * by a space (and there we have emojis too).) */
    public static int[] initialize(@NonNull final NewPipeTextView textView,
                                   @Nullable final int[] storage) {
        final Layout layout = textView.getLayout();
        if (layout == null) {
            return storage;
        }
        int ellipsisLine = textView.getDisplayLines() - 1;
        final int[] configStorage = setupLineMetrics(storage);
        try (LayoutTicket lastLine = warmUpLineMetrics(ellipsisLine, layout, configStorage)) {

            // determine screen estate the ellipsis takes
            final float ellipsisWidth = layout.getPaint().measureText(ELLIPSIS_CHARS,
                    lastLine.get(IS_LTR).asBoolean() ? 0
                            : ELLIPSIS_CHARS.length - ELLIPSIS_LEN, ELLIPSIS_LEN);
            final float shortfall = ellipsisWidth - lastLine.get(LINE_REMAINING_SPACE).asFloat();
            int lastChar = lastLine.get(LINE_END).asInt();
            // this would be the reference point to determine if the clipping mask
            // would eventually kick in at the end of the evaluations
            final int origLastChar = lastChar;
            if (shortfall > 0) {
                // find the closest text offset where the ellipsis fits in
                lastChar = layout.getOffsetForHorizontal(ellipsisLine,
                        lastLine.get(LINE_END_EDGE).advance(-shortfall).asFloat());
            }
            // we're safe to assume by now the ellipsis fits in the last line up to lastChar
            // but we go further to crunch by word and do some cleanup / nitpicks
            final int origLineStart = lastLine.get(LINE_START).asInt();
            final BreakIterator wb = lastLine.getWordInstance();
            boolean trailingWhitespacesOnly = lastChar == origLastChar;

            wb.setText(textView.getText().toString());
            int last = wb.preceding(lastChar);
            if (!wb.isBoundary(lastChar) && last != BreakIterator.DONE) {
                lastChar = last;
                trailingWhitespacesOnly = false;
                last = wb.preceding(lastChar);
            }
            // strip whitespaces: the last line might well end with a space and a line return
            // be greedy since we're always prefixing the ellipsis to be drawn with a space
            // and we won't want there to end up having more than one
            while (last != BreakIterator.DONE && Character.isWhitespace(
                    Character.codePointAt(textView.getText(), last))) {
                lastChar = last;
                last = wb.preceding(lastChar);
            }

            // our clipping mask to be, which also positions the ellipsis to be drawn
            // default to zero width bounds to cloak nothing of the text drawn by super.onDraw(),
            // say if we're simply drawing an ellipsis above it when screen estate fits
            final float initialPos = lastLine.get(LINE_END_EDGE).asFloat();
            final boolean debug = lastLine.get(SCHEMA_DEBUG).asBoolean();
            // prepare to write the finalised ellipsis params
            if (debug) {
                lastLine.upgrade().set(LE_EDGE, initialPos)
                        .defaultTo(LL_WIDTH, WIDTH)
                        .defaultTo(LL_TOP, TOP)
                        .defaultTo(LL_BOTTOM, BOTTOM)
                        .defaultTo(EB_BASELINE_D, BASELINE)
                        .defaultTo(EB_TOP_D, TOP)
                        .defaultTo(EB_BOTTOM_D, BOTTOM);
            } else {
                lastLine.upgrade().defaultTo(EB_BASELINE, BASELINE)
                        .defaultTo(EB_TOP, TOP)
                        .defaultTo(EB_BOTTOM, BOTTOM);
            }
            if (lastChar != origLastChar) {
                // whitespaces stripped above also include line break characters (hard returns)
                // opportunistically wrap the ellipsis to the second last line if screen estate fits
                if (lastChar < origLineStart && ellipsisLine > 0) {
                    if (layout.getLineMax(ellipsisLine - 1) + ellipsisWidth
                            // assuming width consistent across lines
                            <= lastLine.get(WIDTH).asInt()) {
                        ellipsisLine -= 1; // nil shortfall
                        lastLine.set(debug ? EB_BASELINE_D : EB_BASELINE,
                                        layout.getLineBaseline(ellipsisLine))
                                .set(IS_LTR, layout.getParagraphDirection(ellipsisLine)
                                        == Layout.DIR_LEFT_TO_RIGHT);
                    } else {
                        // we're not crunching anything above the last line, so never mind
                        // just let the ellipsis start on its own right in the last line
                        lastChar = origLineStart;
                    }
                }
                // finally setting the extent of the clipping mask, we need one after all
                final float ellipsisPos = layout.getPrimaryHorizontal(lastChar);
                if (trailingWhitespacesOnly && lastChar >= origLineStart) {
                    // optimization: we won't need a mask just for whitespaces on the same last line
                    lastLine.set(debug ? EB_LEFT_D : EB_LEFT, ellipsisPos)
                            .set(debug ? EB_RIGHT_D : EB_RIGHT, ellipsisPos);
                } else {
                    lastLine.set(debug ? EB_LEFT_D : EB_LEFT,
                                    lastLine.get(IS_LTR).asBoolean() ? ellipsisPos : initialPos)
                            .set(debug ? EB_RIGHT_D : EB_RIGHT,
                                    lastLine.get(IS_LTR).asBoolean() ? initialPos : ellipsisPos);
                }
            } else {
                lastLine.set(debug ? EB_LEFT_D : EB_LEFT, initialPos)
                        .set(debug ? EB_RIGHT_D : EB_RIGHT, initialPos);
            }
            // nitpick: omit the prefixed space if ellipsis starts on its own line
            if (lastChar == origLineStart) {
                lastLine.set(SKIP_PREFIX_SPACE, true);
            }
        }

        return configStorage;
    }
}
