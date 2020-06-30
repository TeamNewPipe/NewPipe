package org.schabi.newpipe;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;

import com.caverock.androidsvg.SVG;
import com.caverock.androidsvg.SVGParseException;
import com.nostra13.universalimageloader.core.decode.BaseImageDecoder;
import com.nostra13.universalimageloader.core.decode.ImageDecodingInfo;
import com.nostra13.universalimageloader.utils.IoUtils;
import com.nostra13.universalimageloader.utils.L;

import java.io.IOException;
import java.io.InputStream;

public class ImageDecoder extends BaseImageDecoder {

    public ImageDecoder(final boolean loggingEnabled) {
        super(loggingEnabled);
    }

    @Override
    public Bitmap decode(final ImageDecodingInfo decodingInfo) throws IOException {
        if (decodingInfo.getOriginalImageUri().endsWith("svg")) {
            Bitmap decodedBitmap;
            ImageFileInfo imageInfo;

            InputStream imageStream = getImageStream(decodingInfo);
            if (imageStream == null) {
                L.e(ERROR_NO_IMAGE_STREAM, decodingInfo.getImageKey());
                return null;
            }
            try {
                imageInfo = defineImageSizeAndRotation(imageStream, decodingInfo);
                imageStream = resetStream(imageStream, decodingInfo);
                BitmapFactory.Options decodingOptions = prepareDecodingOptions(imageInfo.imageSize,
                        decodingInfo);
                decodedBitmap = decodeSVG(imageStream, decodingOptions);
            } finally {
                IoUtils.closeSilently(imageStream);
            }

            if (decodedBitmap == null) {
                L.e(ERROR_CANT_DECODE_IMAGE, decodingInfo.getImageKey());
            } else {
                decodedBitmap = considerExactScaleAndOrientatiton(decodedBitmap, decodingInfo,
                        imageInfo.exif.rotation, imageInfo.exif.flipHorizontal);
            }
            return decodedBitmap;
        } else {
            return super.decode(decodingInfo);
        }
    }

    private Bitmap decodeSVG(final InputStream imageStream,
                             final BitmapFactory.Options decodingOptions) {
        SVG svg;
        try {
            svg = SVG.getFromInputStream(imageStream);
        } catch (SVGParseException e) {
            return null;
        }
        int width = decodingOptions.outWidth != 0 ? decodingOptions.outWidth : 200;
        int height = decodingOptions.outHeight != 0 ? decodingOptions.outHeight : 200;
        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.WHITE);
        svg.renderToCanvas(canvas);
        return bitmap;
    }
}
