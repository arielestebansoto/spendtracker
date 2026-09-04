package com.arielsoto.spendtracker.ocr;

import com.google.cloud.vision.v1.*;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.protobuf.ByteString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@EnableConfigurationProperties(OcrProperties.class)
public class VisionOcrService {

    private static final Logger log = LoggerFactory.getLogger(VisionOcrService.class);

    private final ImageAnnotatorClient visionClient;

    public VisionOcrService(OcrProperties properties) throws IOException {
        GoogleCredentials credentials = GoogleCredentials
            .fromStream(new FileInputStream(properties.credentialsPath()))
            .createScoped(List.of("https://www.googleapis.com/auth/cloud-vision"));

        ImageAnnotatorSettings settings = ImageAnnotatorSettings.newBuilder()
            .setCredentialsProvider(() -> credentials)
            .build();

        this.visionClient = ImageAnnotatorClient.create(settings);
    }

    public OcrResult extractText(byte[] imageBytes, String contentType) {
        try {
            Image image = Image.newBuilder()
                .setContent(ByteString.copyFrom(imageBytes))
                .build();

            return annotateImage(image);
        } catch (Exception e) {
            log.error("OCR extraction failed", e);
            return new OcrResult("", 0f, List.of());
        }
    }

    public OcrResult extractText(String gcsUri) {
        try {
            ImageSource imageSource = ImageSource.newBuilder()
                .setGcsImageUri(gcsUri)
                .build();

            Image image = Image.newBuilder()
                .setSource(imageSource)
                .build();

            return annotateImage(image);
        } catch (Exception e) {
            log.error("OCR extraction from GCS failed", e);
            return new OcrResult("", 0f, List.of());
        }
    }

    private OcrResult annotateImage(Image image) {
        Feature feature = Feature.newBuilder()
            .setType(Feature.Type.DOCUMENT_TEXT_DETECTION)
            .build();

        AnnotateImageRequest request = AnnotateImageRequest.newBuilder()
            .addFeatures(feature)
            .setImage(image)
            .build();

        BatchAnnotateImagesResponse batchResponse = visionClient
            .batchAnnotateImages(List.of(request));

        AnnotateImageResponse response = batchResponse.getResponses(0);

        if (response.hasError()) {
            log.error("Vision API error: {}", response.getError().getMessage());
            return new OcrResult("", 0f, List.of());
        }

        return parseResponse(response);
    }

    private OcrResult parseResponse(AnnotateImageResponse response) {
        TextAnnotation textAnnotation = response.getFullTextAnnotation();

        if (textAnnotation == null) {
            return new OcrResult("", 0f, List.of());
        }

        String rawText = textAnnotation.getText();
        float confidence = textAnnotation.getPagesCount() > 0
            ? textAnnotation.getPages(0).getConfidence()
            : 0f;

        List<OcrResult.TextBlock> blocks = new ArrayList<>();
        for (Page page : textAnnotation.getPagesList()) {
            for (Block block : page.getBlocksList()) {
                List<OcrResult.Symbol> symbols = block.getParagraphsList().stream()
                    .flatMap(p -> p.getWordsList().stream())
                    .flatMap(w -> w.getSymbolsList().stream())
                    .map(s -> new OcrResult.Symbol(s.getText(), s.getConfidence()))
                    .collect(Collectors.toList());

                String blockText = block.getParagraphsList().stream()
                    .flatMap(p -> p.getWordsList().stream())
                    .flatMap(w -> w.getSymbolsList().stream())
                    .map(Symbol::getText)
                    .collect(Collectors.joining());

                blocks.add(new OcrResult.TextBlock(
                    blockText,
                    block.getConfidence(),
                    symbols
                ));
            }
        }

        return new OcrResult(rawText, confidence, blocks);
    }
}
