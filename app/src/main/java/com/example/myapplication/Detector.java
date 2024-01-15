package com.example.myapplication;

import android.content.Context;
import android.graphics.Bitmap;
import android.media.FaceDetector.Face;
import android.os.SystemClock;
import org.tensorflow.lite.DataType;
import org.tensorflow.lite.Interpreter;
import org.tensorflow.lite.support.common.FileUtil;
import org.tensorflow.lite.support.common.ops.CastOp;
import org.tensorflow.lite.support.common.ops.NormalizeOp;
import org.tensorflow.lite.support.image.ImageProcessor;
import org.tensorflow.lite.support.image.TensorImage;
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import static java.lang.Math.max;
import static java.lang.Math.min;

public class Detector {
    private Context context;
    private String modelPath;
    private String labelPath;
    private DetectorListener detectorListener;
    private Interpreter interpreter;
    private List<String> labels;
    private int tensorWidth;
    private int tensorHeight;
    private int numChannel;
    private int numElements;
    private ImageProcessor imageProcessor;

    public Detector(Context context, String modelPath, String labelPath, DetectorListener detectorListener) {
        this.context = context;
        this.modelPath = modelPath;
        this.labelPath = labelPath;
        this.detectorListener = detectorListener;
        this.interpreter = null;
        this.labels = new ArrayList<>();
        this.tensorWidth = 0;
        this.tensorHeight = 0;
        this.numChannel = 0;
        this.numElements = 0;
        this.imageProcessor = new ImageProcessor.Builder()
                .add(new NormalizeOp(INPUT_MEAN, INPUT_STANDARD_DEVIATION))
                .add(new CastOp(INPUT_IMAGE_TYPE))
                .build();
    }

    public void setup() {
        try {
            ByteBuffer model = FileUtil.loadMappedFile(context, modelPath);
            Interpreter.Options options = new Interpreter.Options();
            options.setNumThreads(4);
            interpreter = new Interpreter(model, options);
            int[] inputShape = interpreter.getInputTensor(0).shape();
            int[] outputShape = interpreter.getOutputTensor(0).shape();
            tensorWidth = inputShape[1];
            tensorHeight = inputShape[2];
            numChannel = outputShape[1];
            numElements = outputShape[2];
            InputStream inputStream = context.getAssets().open(labelPath);
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line = reader.readLine();
            while (line != null && !line.equals("")) {
                labels.add(line);
                line = reader.readLine();
            }
            reader.close();
            inputStream.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void clear() {
        if (interpreter != null) {
            interpreter.close();
            interpreter = null;
        }
    }

    public void detect(Bitmap frame) {
        if (interpreter == null) return;
        if (tensorWidth == 0) return;
        if (tensorHeight == 0) return;
        if (numChannel == 0) return;
        if (numElements == 0) return;
        long inferenceTime = SystemClock.uptimeMillis();
        Bitmap resizedBitmap = Bitmap.createScaledBitmap(frame, tensorWidth, tensorHeight, false);
        TensorImage tensorImage = new TensorImage(DataType.FLOAT32);
        tensorImage.load(resizedBitmap);
        TensorImage processedImage = imageProcessor.process(tensorImage);
        ByteBuffer imageBuffer = processedImage.getBuffer();
        TensorBuffer output = TensorBuffer.createFixedSize(new int[]{1, numChannel, numElements}, OUTPUT_IMAGE_TYPE);
        interpreter.run(imageBuffer, output.getBuffer());
        List<BoundingBox> bestBoxes = bestBox(output.getFloatArray());
        if (bestBoxes == null) {
            detectorListener.onEmptyDetect();
            return;
        }
        inferenceTime = SystemClock.uptimeMillis() - inferenceTime;
        detectorListener.onDetect(bestBoxes, inferenceTime);
    }

    private List<BoundingBox> bestBox(float[] array) {
        List<BoundingBox> boundingBoxes = new ArrayList<>();
        for (int c = 0; c < numElements; c++) {
            List<Float> confidences = new ArrayList<>();
            for (int i = 4; i < numChannel; i++) {
                confidences.add(array[c + numElements * i]);
            }
            float cnf = Collections.max(confidences);
            if (cnf > CONFIDENCE_THRESHOLD) {
                int cls = confidences.indexOf(cnf);
                String clsName = labels.get(cls);
                float cx = array[c];
                float cy = array[c + numElements];
                float w = array[c + numElements * 2];
                float h = array[c + numElements * 3];
                float x1 = cx - (w / 2F);
                float y1 = cy - (h / 2F);
                float x2 = cx + (w / 2F);
                float y2 = cy + (h / 2F);
                if (x1 < 0F || x1 > 1F) continue;
                if (y1 < 0F || y1 > 1F) continue;
                if (x2 < 0F || x2 > 1F) continue;
                if (y2 < 0F || y2 > 1F) continue;
                boundingBoxes.add(new BoundingBox(x1, y1, x2, y2, cx, cy, w, h, cnf, cls, clsName));
            }
        }
        if (boundingBoxes.isEmpty()) return null;
        return applyNMS(boundingBoxes);
    }

    private List<BoundingBox> applyNMS(List<BoundingBox> boxes) {
        List<BoundingBox> sortedBoxes = new ArrayList<>(boxes);
        sortedBoxes.sort((box1, box2) -> Float.compare(box2.getCnf(), box1.getCnf()));
        List<BoundingBox> selectedBoxes = new ArrayList<>();
        while (!sortedBoxes.isEmpty()) {
            BoundingBox first = sortedBoxes.get(0);
            selectedBoxes.add(first);
            sortedBoxes.remove(first);
            Iterator<BoundingBox> iterator = sortedBoxes.iterator();
            while (iterator.hasNext()) {
                BoundingBox nextBox = iterator.next();
                float iou = calculateIoU(first, nextBox);
                if (iou >= IOU_THRESHOLD) {
                    iterator.remove();
                }
            }
        }
        return selectedBoxes;
    }

    private float calculateIoU(BoundingBox box1, BoundingBox box2) {
        float x1 = max(box1.getX1(), box2.getX1());
        float y1 = max(box1.getY1(), box2.getY1());
        float x2 = min(box1.getX2(), box2.getX2());
        float y2 = min(box1.getY2(), box2.getY2());
        float intersectionArea = max(0F, x2 - x1) * max(0F, y2 - y1);
        float box1Area = box1.getW() * box1.getH();
        float box2Area = box2.getW() * box2.getH();
        return intersectionArea / (box1Area + box2Area - intersectionArea);
    }

    public interface DetectorListener {
        void onEmptyDetect();
        void onDetect(List<BoundingBox> boundingBoxes, long inferenceTime);
    }

    private static final float INPUT_MEAN = 0f;
    private static final float INPUT_STANDARD_DEVIATION = 255f;
    private static final DataType INPUT_IMAGE_TYPE = DataType.FLOAT32;
    private static final DataType OUTPUT_IMAGE_TYPE = DataType.FLOAT32;
    private static final float CONFIDENCE_THRESHOLD = 0.75F;
    private static final float IOU_THRESHOLD = 0.5F;
}


