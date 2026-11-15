"""
Generates a printable, thesis-defense-ready PDF version of the
FSL Research & Defense Guide at FSL_Research_Defense_Guide.pdf.
"""

from pathlib import Path
from reportlab.lib import colors
from reportlab.lib.pagesizes import letter
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import inch
from reportlab.graphics.shapes import Drawing, Rect, String, Line, Polygon
from reportlab.platypus import (
    CondPageBreak,
    KeepTogether,
    PageBreak,
    Paragraph,
    SimpleDocTemplate,
    Spacer,
    Table,
    TableStyle,
)

NAVY = colors.HexColor("#0B1F33")
TEAL = colors.HexColor("#0E7490")
TEAL_LIGHT = colors.HexColor("#ECFEFF")
SLATE = colors.HexColor("#334155")
MUTED = colors.HexColor("#64748B")
BORDER = colors.HexColor("#CBD5E1")
ROW_ALT = colors.HexColor("#F8FAFC")
CALLOUT_BG = colors.HexColor("#F0FDFA")
Q_BG = colors.HexColor("#F1F5F9")


def _draw_arrow(drawing: Drawing, x1: float, y1: float, x2: float, y2: float, color=TEAL):
    drawing.add(Line(x1, y1, x2, y2, strokeColor=color, strokeWidth=1.4))
    if abs(x2 - x1) >= abs(y2 - y1):
        # Horizontal right or left
        s = 1 if x2 >= x1 else -1
        drawing.add(Polygon([x2, y2, x2 - 5 * s, y2 + 3, x2 - 5 * s, y2 - 3], fillColor=color, strokeColor=color))
    else:
        # Vertical down or up
        s = 1 if y2 >= y1 else -1
        drawing.add(Polygon([x2, y2, x2 - 3, y2 - 5 * s, x2 + 3, y2 - 5 * s], fillColor=color, strokeColor=color))


def _make_box(drawing: Drawing, x: float, y: float, w: float, h: float, title: str, subtitle: str = ""):
    drawing.add(
        Rect(x, y, w, h, rx=5, ry=5, fillColor=TEAL_LIGHT, strokeColor=TEAL, strokeWidth=1.1)
    )
    if subtitle:
        drawing.add(String(x + w / 2, y + h * 0.58, title, fontName="Helvetica-Bold", fontSize=7.2, fillColor=NAVY, textAnchor="middle"))
        drawing.add(String(x + w / 2, y + h * 0.25, subtitle, fontName="Helvetica", fontSize=6.3, fillColor=SLATE, textAnchor="middle"))
    else:
        drawing.add(String(x + w / 2, y + h * 0.40, title, fontName="Helvetica-Bold", fontSize=7.2, fillColor=NAVY, textAnchor="middle"))


def build_offline_pipeline_diagram() -> Drawing:
    w, h = 516, 128
    d = Drawing(w, h)
    bw, bh = 112, 38
    # Row 1 (Left -> Right)
    r1_y = 78
    xs = [4, 136, 268, 400]
    _make_box(d, xs[0], r1_y, bw, bh, "1. FSL_Dataset", "2,130 .MOV Clips (105 Signs)")
    _make_box(d, xs[1], r1_y, bw, bh, "2. extract_top20_fast.py", "MediaPipe Hand + Pose")
    _make_box(d, xs[2], r1_y, bw, bh, "3. Normalizer & Resample", "Wrist Origin + 30x159")
    _make_box(d, xs[3], r1_y, bw, bh, "4. FSL_Features", "2,130 x (30, 159) .npy")
    for i in range(3):
        _draw_arrow(d, xs[i] + bw, r1_y + bh / 2, xs[i + 1], r1_y + bh / 2)

    # Connector down from box 4 to row 2 right-most box
    r2_y = 14
    _draw_arrow(d, xs[3] + bw / 2, r1_y, xs[3] + bw / 2, r2_y + bh)

    # Row 2 (Right -> Left)
    _make_box(d, xs[3], r2_y, bw, bh, "5. SuperRobust Training", "7 Augmentations, 100 Epochs")
    _make_box(d, xs[2], r2_y, bw, bh, "6. FSLTemporalClassifier", "1D-CNN + BiLSTM + Attn")
    _make_box(d, xs[1], r2_y, bw, bh, "7. ONNX Export", "opset 14 (2.93 MB)")
    _make_box(d, xs[0], r2_y, bw, bh, "8. Android App Assets", "fsl_model.onnx + vocab.json")
    for i in [3, 2, 1]:
        _draw_arrow(d, xs[i], r2_y + bh / 2, xs[i - 1] + bw, r2_y + bh / 2)

    return d


def build_live_pipeline_diagram() -> Drawing:
    w, h = 516, 128
    d = Drawing(w, h)
    bw, bh = 112, 38
    r1_y = 78
    xs = [4, 136, 268, 400]
    _make_box(d, xs[0], r1_y, bw, bh, "1. CameraX Stream", "RGBA_8888 + Rotation")
    _make_box(d, xs[1], r1_y, bw, bh, "2. MediaPipe Tasks", "2 Hands + 11 Pose Joints")
    _make_box(d, xs[2], r1_y, bw, bh, "3. Spatial Normalization", "159 Floats + HUD Box")
    _make_box(d, xs[3], r1_y, bw, bh, "4. RAM frameBuffer", "Up to 2.6s (No Video Saved)")
    for i in range(3):
        _draw_arrow(d, xs[i] + bw, r1_y + bh / 2, xs[i + 1], r1_y + bh / 2)

    r2_y = 14
    _draw_arrow(d, xs[3] + bw / 2, r1_y, xs[3] + bw / 2, r2_y + bh)

    _make_box(d, xs[3], r2_y, bw, bh, "5. Active Window Trim", "handEnergy > 0.05f")
    _make_box(d, xs[2], r2_y, bw, bh, "6. Linear Resampling", "30 x 159 = 4,770 Floats")
    _make_box(d, xs[1], r2_y, bw, bh, "7. Dual ONNX Inference", "Normal vs. Mirrored Seq")
    _make_box(d, xs[0], r2_y, bw, bh, "8. Softmax + TTS Output", "Word + Confidence + Speech")
    for i in [3, 2, 1]:
        _draw_arrow(d, xs[i], r2_y + bh / 2, xs[i - 1] + bw, r2_y + bh / 2)

    return d


def _add_page_furniture(canvas, doc):
    canvas.saveState()
    canvas.setStrokeColor(BORDER)
    canvas.setLineWidth(0.6)
    canvas.line(48, letter[1] - 36, letter[0] - 48, letter[1] - 36)
    canvas.line(48, 36, letter[0] - 48, 36)

    canvas.setFont("Helvetica", 8)
    canvas.setFillColor(MUTED)
    canvas.drawString(48, letter[1] - 28, "Filipino Sign Language (FSL) Translator — 105-Sign Research & Defense Guide")
    canvas.drawRightString(letter[0] - 48, 24, f"Page {doc.page}")
    canvas.drawString(48, 24, "Offline Edge AI Architecture: MediaPipe + 1D-CNN + BiLSTM + ONNX Runtime")
    canvas.restoreState()


def generate_pdf(output_path: Path):
    doc = SimpleDocTemplate(
        str(output_path),
        pagesize=letter,
        leftMargin=48,
        rightMargin=48,
        topMargin=48,
        bottomMargin=46,
    )

    base = getSampleStyleSheet()
    s_title = ParagraphStyle("DocTitle", parent=base["Normal"], fontName="Helvetica-Bold", fontSize=18, leading=22, textColor=NAVY, spaceAfter=4)
    s_subtitle = ParagraphStyle("DocSub", parent=base["Normal"], fontName="Helvetica", fontSize=10, leading=14, textColor=SLATE, spaceAfter=10)
    s_h1 = ParagraphStyle("H1", parent=base["Normal"], fontName="Helvetica-Bold", fontSize=13, leading=17, textColor=NAVY, spaceBefore=12, spaceAfter=6)
    s_h2 = ParagraphStyle("H2", parent=base["Normal"], fontName="Helvetica-Bold", fontSize=10.5, leading=14, textColor=TEAL, spaceBefore=8, spaceAfter=4)
    s_body = ParagraphStyle("Body", parent=base["Normal"], fontName="Helvetica", fontSize=9.3, leading=13.2, textColor=SLATE, spaceAfter=5)
    s_bullet = ParagraphStyle("Bullet", parent=s_body, leftIndent=12, bulletIndent=0, spaceAfter=3.5)
    s_th = ParagraphStyle("TH", parent=base["Normal"], fontName="Helvetica-Bold", fontSize=8.2, leading=10.5, textColor=colors.white)
    s_td = ParagraphStyle("TD", parent=base["Normal"], fontName="Helvetica", fontSize=8.0, leading=10.6, textColor=SLATE)
    s_td_bold = ParagraphStyle("TDB", parent=s_td, fontName="Helvetica-Bold", textColor=NAVY)
    s_callout = ParagraphStyle("Callout", parent=s_body, fontSize=8.8, leading=12.5, textColor=NAVY)
    s_q = ParagraphStyle("Q", parent=base["Normal"], fontName="Helvetica-Bold", fontSize=9.5, leading=13, textColor=NAVY, spaceAfter=3)
    s_a = ParagraphStyle("A", parent=base["Normal"], fontName="Helvetica", fontSize=9.0, leading=12.8, textColor=SLATE, spaceAfter=0)

    def make_table(headers, rows, col_widths):
        data = [[Paragraph(h, s_th) for h in headers]]
        for idx, row in enumerate(rows):
            cells = [Paragraph(row[0], s_td_bold)] + [Paragraph(c, s_td) for c in row[1:]]
            data.append(cells)
        t = Table(data, colWidths=col_widths, repeatRows=1)
        style_cmds = [
            ("BACKGROUND", (0, 0), (-1, 0), NAVY),
            ("TEXTCOLOR", (0, 0), (-1, 0), colors.white),
            ("VALIGN", (0, 0), (-1, -1), "TOP"),
            ("GRID", (0, 0), (-1, -1), 0.4, BORDER),
            ("LEFTPADDING", (0, 0), (-1, -1), 5),
            ("RIGHTPADDING", (0, 0), (-1, -1), 5),
            ("TOPPADDING", (0, 0), (-1, -1), 4),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 4),
        ]
        for r in range(1, len(data)):
            if r % 2 == 0:
                style_cmds.append(("BACKGROUND", (0, r), (-1, r), ROW_ALT))
        t.setStyle(TableStyle(style_cmds))
        return t

    def callout_box(text_html: str):
        tbl = Table([[Paragraph(text_html, s_callout)]], colWidths=[516])
        tbl.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (-1, -1), CALLOUT_BG),
            ("BOX", (0, 0), (-1, -1), 0.8, TEAL),
            ("LEFTPADDING", (0, 0), (-1, -1), 8),
            ("RIGHTPADDING", (0, 0), (-1, -1), 8),
            ("TOPPADDING", (0, 0), (-1, -1), 6),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 6),
        ]))
        return tbl

    def qa_card(q_text: str, a_text: str):
        tbl = Table([[Paragraph(q_text, s_q)], [Paragraph(a_text, s_a)]], colWidths=[516])
        tbl.setStyle(TableStyle([
            ("BACKGROUND", (0, 0), (0, 0), Q_BG),
            ("BACKGROUND", (0, 1), (0, 1), colors.white),
            ("BOX", (0, 0), (-1, -1), 0.5, BORDER),
            ("LINEBELOW", (0, 0), (0, 0), 0.4, BORDER),
            ("LEFTPADDING", (0, 0), (-1, -1), 8),
            ("RIGHTPADDING", (0, 0), (-1, -1), 8),
            ("TOPPADDING", (0, 0), (-1, -1), 5),
            ("BOTTOMPADDING", (0, 0), (-1, -1), 5),
        ]))
        return KeepTogether([tbl, Spacer(1, 7)])

    story = []

    # Header
    story.append(Paragraph("Filipino Sign Language (FSL) Translator", s_title))
    story.append(Paragraph(
        "Complete System Components, Technical &amp; Non-Technical Workflows, and Panel Defense Q&amp;A Guide (105 Active Signs)",
        s_subtitle,
    ))
    story.append(callout_box(
        "<b>Executive Summary:</b> An offline, mobile-first FSL translator supporting <b>105 signs</b> across 8 categories. "
        "It uses <b>MediaPipe Tasks Vision (0.10.14)</b> to convert camera frames into 159-dimensional skeletal keypoint vectors, "
        "normalizes and resamples gestures to <b>30 frames</b>, and runs a PyTorch-trained <b>1D-CNN + BiLSTM + Attention</b> model "
        "exported to <b>ONNX (2.93 MB)</b> via <b>ONNX Runtime Android (1.17.0)</b>—achieving <b>96.95%</b> clean validation accuracy "
        "(<b>97.18%</b> best composite robustness) with zero cloud dependency."
    ))

    # Section 1
    story.append(Paragraph("1. System Components You Should Know", s_h1))
    story.append(Paragraph("A. Android Application Layer (android/app/src/main/)", s_h2))
    android_rows = [
        ["MainActivity.kt", "Controls camera preview, Start/Stop signing button, 2.6s timer, confidence meter, and speech button.", "CameraX 1.4.2 (Preview + ImageAnalysis), State machine (IDLE/RECORDING/PROCESSING/RESULT), Executors, TextToSpeech."],
        ["HandLandmarkTracker.kt", "Finds hands and upper body in live video, records movement in RAM, and prepares the 30-frame sequence.", "MediaPipe HandLandmarker + PoseLandmarker (0.10.14), wrist/shoulder normalization (159 floats), handEnergy trimming, linear resampling."],
        ["LandmarkOverlayView.kt", "Draws the glowing cyan/amber framing box on screen and warns 'Show your hands' if none are detected.", "Custom Android View drawing mirrored bounding box and GuideState pill badge."],
        ["FslClassifier.kt", "Feeds the 30x159 sequence into fsl_model.onnx and converts the top score into an English word.", "onnxruntime-android 1.17.0, dual-hypothesis normal + mirrored inference, softmax confidence, vocab.json lookup."],
        ["FslVocabularyData.kt", "Stores all 105 sign names, categories, tips, and 4-step beginner instructions.", "105 FslSignItem records + 38 HandIllustration enum states aligned 1-to-1 with vocab.json."],
        ["VocabLibraryDialog.kt", "Interactive full-screen sign dictionary with category filter chips and pronunciation playback.", "Dialog inflating dialog_vocab_library.xml with 8 category filters and TextToSpeech trigger."],
        ["SignDiagramView.kt", "Draws monochrome '1. START -> 2. FINISH' hand illustrations for every sign.", "Custom Canvas procedural line-art renderer supporting all 38 HandIllustration postures."],
        ["assets/ &amp; build.gradle.kts", "Packages the offline AI brain and landmarker models inside the APK without compression.", "fsl_model.onnx (2.93 MB), vocab.json (105 classes), hand_landmarker.task, pose_landmarker_lite.task; minSdk 24, compileSdk 37."],
    ]
    story.append(make_table(["Component", "Plain-English Role", "Technical Role"], android_rows, [128, 190, 198]))
    story.append(Spacer(1, 8))

    story.append(Paragraph("B. Offline Python ML &amp; Research Layer (src/, scripts/, models/)", s_h2))
    python_rows = [
        ["FSL_Dataset/ &amp; labels.csv", "Master library of recorded FSL training clips across 105 signs.", "105 sign folders containing 2,130 .MOV clips (18–22 videos per sign)."],
        ["extract_top20_fast.py", "Watches each .MOV clip once and saves lightweight skeleton coordinates.", "8-worker ProcessPoolExecutor + MediaPipe (~45 frames/video) -> (30, 159) .npy in FSL_Features/."],
        ["normalizer.py", "Makes signs look identical regardless of distance or position in the frame.", "SpatialNormalizer (wrist origin + max-dist scale; shoulder-midpoint pose) + TemporalResampler."],
        ["bilstm_classifier.py", "Neural network brain that reads hand shapes and motion forward &amp; backward.", "FSLTemporalClassifier: Conv1d(159->128) + BN + ReLU -> 2-layer BiLSTM(128) -> Attention -> MLP(105)."],
        ["train_phase1_super_model.py", "Trains with realistic camera distortions and exports the mobile ONNX model.", "SuperRobustFSLDataset (7 augmentations), AdamW + CosineAnnealing (100 epochs), ONNX opset 14 parity check."],
        ["protonet.py", "Research module for adding new signs from just a few examples.", "FSLFeatureEmbeddingNet + ProtoNetClassifier (few-shot metric learning)."],
        ["tests/ &amp; ExampleUnitTest.kt", "Automated checks verifying math, tensor shapes, ONNX parity, and 105-sign sync.", "10 pytest unit/integration tests + Android vocabulary alignment unit test."],
    ]
    story.append(make_table(["Component", "Plain-English Role", "Technical Role"], python_rows, [128, 190, 198]))

    # Section 2
    story.append(Paragraph("2. Video Storage & Training Workflow (How Knowledge Is Built)", s_h1))
    story.append(Paragraph(
        "<b>Key distinction for the panel:</b> In the live Android app, <b>user camera videos are never saved to phone storage</b>. "
        "Frames exist only in RAM inside <i>frameBuffer</i> as numeric coordinates during the 2.6-second signing window and are cleared "
        "immediately after translation (<i>frameBuffer.clear()</i>), guaranteeing user privacy and zero storage bloat. "
        "Offline on the development PC, raw training clips are stored in <i>FSL_Dataset/</i> (2,130 .MOV files) and converted once "
        "into compact (30, 159) coordinate arrays inside <i>FSL_Features/</i> for fast training.",
        s_body,
    ))
    story.append(build_offline_pipeline_diagram())
    story.append(Spacer(1, 6))

    # Section 3
    story.append(Paragraph("3. Live App Translation Workflow (Camera -> Word -> Speech)", s_h1))
    story.append(Paragraph("Non-Technical Analogy ('How the App Thinks')", s_h2))
    for b in [
        "<b>1. Eyes (CameraX):</b> Tapping 'Start signing' activates a 2.6-second live camera window.",
        "<b>2. Invisible Motion-Capture Stickers (MediaPipe):</b> Instead of memorizing shirts, skin tone, or room walls, MediaPipe places <b>53 virtual dots</b> on both hands and the upper body.",
        "<b>3. Size & Speed Equalizer (Normalization):</b> Whether you stand close or far, the dots are scaled to a standard size around your wrists and converted into an even <b>30-snapshot flipbook</b>.",
        "<b>4. Gesture Reader (1D-CNN + BiLSTM + Attention):</b> Checks handshape details per frame, reads motion forward and backward in time, and focuses on the most important moments of the sign.",
        "<b>5. Mirror Check & Speech (ONNX + TTS):</b> Evaluates both normal and left/right mirrored versions (for left-handers and selfie cameras), outputs the highest-confidence word from 105 signs, and speaks it aloud.",
    ]:
        story.append(Paragraph(f"• {b}", s_bullet))

    story.append(Spacer(1, 4))
    story.append(build_live_pipeline_diagram())
    story.append(Spacer(1, 6))

    # Section 4
    story.append(Paragraph("4. Deep Dive: MediaPipe, ONNX vs. TensorFlow, BiLSTM, and Output Math", s_h1))
    story.append(Paragraph("A. MediaPipe Tasks Vision & 159-Feature Vector", s_h2))
    story.append(Paragraph(
        "Each frame extracts <b>53 3D landmarks</b> (x, y, z) = <b>159 floats</b>: <b>Left Hand</b> (21x3 = 63 floats, dims 0..62), "
        "<b>Right Hand</b> (21x3 = 63 floats, dims 63..125), and <b>Upper-Body Pose</b> (11 joints x 3 = 33 floats, dims 126..158: "
        "nose, shoulders, elbows, wrists, hips, ears). Each hand is shifted to put the wrist at (0,0,0) and divided by its max joint "
        "distance; pose is centered at the shoulder midpoint. In selfie mode (<i>isFrontCamera=true</i>), <i>HandLandmarkTracker</i> flips "
        "handedness and negates local X so live input matches training orientation. When recording ends, idle frames (<i>handEnergy &lt;= 0.05f</i>) "
        "are trimmed and active frames are linearly interpolated to shape <b>(30, 159) = 4,770 floats</b>.",
        s_body,
    ))

    story.append(Paragraph("B. Why PyTorch + ONNX Runtime (vs. TensorFlow)", s_h2))
    story.append(Paragraph(
        "• <b>MediaPipe Tasks Vision (0.10.14)</b> internally runs Google's pre-packaged TFLite/XNNPACK models (<i>hand_landmarker.task</i>, <i>pose_landmarker_lite.task</i>) strictly to detect joint coordinates.<br/>"
        "• <b>Our Custom Sign Classifier</b> is built and trained in <b>PyTorch</b> (not TensorFlow), then exported via <i>torch.onnx.export (opset 14)</i> to <b>fsl_model.onnx (2.93 MB)</b>.<br/>"
        "• On Android, <b>ONNX Runtime (1.17.0)</b> executes the model completely offline with verified numerical parity (tolerance 1e-4).",
        s_body,
    ))

    story.append(Paragraph("C. 1D-CNN + BiLSTM + Attention Architecture & Dual-Hypothesis Output", s_h2))
    story.append(Paragraph(
        "1. <b>Conv1d(159 -> 128, kernel=3) + BatchNorm1d + ReLU:</b> Captures short 3-frame local finger dynamics and velocity.<br/>"
        "2. <b>2-Layer Bidirectional LSTM (hidden=128):</b> Reads the 30-frame trajectory forward (t=1->30) and backward (t=30->1), yielding 256-dim temporal features per frame.<br/>"
        "3. <b>Temporal Attention Pooling:</b> A learned <i>Linear(256 -> 1) + Softmax</i> layer weights the most informative frames into a single (B, 256) sign summary vector.<br/>"
        "4. <b>Classification Head:</b> <i>Dropout(0.35) -> Linear(256 -> 128) -> ReLU -> Linear(128 -> 105)</i> produces 105 class logits.<br/>"
        "5. <b>Dual-Hypothesis Calibration (FslClassifier.kt):</b> Runs both the normal (30,159) tensor and a hand-swapped X-inverted mirror tensor, applies Softmax to convert logits into 1–100% confidence, selects the higher-confidence result, formats the label from <i>vocab.json</i>, and sends it to the UI & Android <i>TextToSpeech</i>.",
        s_body,
    ))

    # Section 5: Mock Panelist Q&A
    story.append(PageBreak())
    story.append(Paragraph("5. Mock Panelist Questions &amp; Model Answers (12 Defense Q&amp;As)", s_h1))
    story.append(Paragraph(
        "Practice these answers aloud. Every metric and design detail below matches your implemented system.",
        s_subtitle,
    ))

    qas = [
        ("Q1. Why use MediaPipe 3D keypoints instead of training a 3D-CNN or Vision Transformer directly on raw video?",
         "Raw video models learn background clutter, clothing, and lighting, and require large weights (50–300 MB) that lag on phones. "
         "Converting frames into 53 3D keypoints (159 floats/frame) strips away background noise, shrinks our classifier to 2.93 MB, runs in milliseconds offline, and reaches 96.95% clean validation accuracy across 105 signs from 2,130 clips."),
        ("Q2. Why combine 1D-CNN, Bidirectional LSTM, and Attention instead of a simple LSTM or MLP?",
         "An MLP ignores temporal order. The 1D-CNN (kernel=3) extracts local 3-frame joint motion; the 2-layer BiLSTM captures full gesture context forward and backward in time; and Temporal Attention Pooling emphasizes peak gesture frames over transition frames."),
        ("Q3. What is inside the 159-dimensional vector, and how do you handle users standing close or far from the camera?",
         "Each frame holds 63 floats for Left Hand (21x3), 63 for Right Hand (21x3), and 33 for 11 upper-body joints. SpatialNormalizer centers each hand on its wrist (landmark 0) and divides by max joint distance, and centers pose on the shoulder midpoint—making signs scale- and position-invariant."),
        ("Q4. Why resample every sign to 30 frames, and what if someone signs faster or slower than 2.6 seconds?",
         "The BiLSTM expects a fixed (1, 30, 159) input for mobile inference. HandLandmarkTracker trims idle empty-hand frames (handEnergy <= 0.05f) and uses linear interpolation to resample active motion into 30 frames. During training, temporal speed warping (22–38 frames) teaches speed invariance."),
        ("Q5. How does the app handle left-handed signers or front vs. back camera mirroring?",
         "Two safeguards: (1) Training applies 50% random hand-swap + horizontal reflection augmentation (96.71%–97.18% mirror accuracy). (2) At runtime, FslClassifier runs dual-hypothesis inference on both normal and mirrored tensors and picks the higher-confidence prediction."),
        ("Q6. Why PyTorch + ONNX Runtime instead of TensorFlow Lite or a cloud API?",
         "Offline edge inference ensures zero internet latency, zero server cost, and full privacy. PyTorch gave us flexible BiLSTM + Attention training, and exporting to ONNX (opset 14) with ONNX Runtime Android (1.17.0) achieved verified 1e-4 numerical parity in a 2.93 MB model."),
        ("Q7. How did you validate the 105-sign model, and why report Clean, Zero-Pose, and Mirror accuracies?",
         "We used a stratified 80/20 split (1,704 train / 426 validation). Because mobile users may drop torso tracking or sign left-handed, we test Clean Val (96.95%, peak 97.18%), Zero-Pose Val (95.77%, peak 96.24%), and Mirror-Hand Val (96.71%, peak 97.18%)."),
        ("Q8. Which signs were confused in validation, and why?",
         "Only 12 of 426 validation clips were misclassified: (1) identical handshapes at different heights (FATHER vs. IM FINE vs. MOTHER) due to wrist-centered normalization and 50% pose dropout; (2) subtle finger-contact numbers (FOUR/SEVEN, SIX/EIGHT/WEDNESDAY); and (3) fist/circular signs (YES/MONDAY)."),
        ("Q9. Why is ProtoNet (protonet.py) in the repository if the app uses BiLSTM?",
         "ProtoNetClassifier is our few-shot learning research module showing how future custom or regional signs can be enrolled using 3–5 examples via embedding distance, whereas the production 105-sign model uses FSLTemporalClassifier for maximum end-to-end accuracy."),
        ("Q10. How does the app prevent fake predictions when no hands are in front of the camera?",
         "HandLandmarkTracker counts framesWithHands during the 2.6s capture. In MainActivity, if fewer than 2 frames contain hands, ONNX inference is skipped and the UI shows 'No Sign Detected' (0% confidence). Live HUD also turns amber ('Show your hands') whenever hands exit frame."),
        ("Q11. How would you extend this to translate full continuous sentences instead of isolated signs?",
         "Replace the stop-and-classify 2.6s window with a continuous sliding window (prototyped in stream_inference.py), train with CTC or a Transformer decoder for word boundaries, and use confidence thresholding + hysteresis smoothing."),
        ("Q12. What are the current limitations and your top recommendations for future work?",
         "Limitations: (1) isolated single signs only, (2) no facial expression (Face Mesh) non-manual markers yet, and (3) 18–22 clips per class from limited recording setups. Next improvements: add explicit wrist-to-nose/shoulder relative features, expand to 35–40 multi-signer clips per sign, and add a minimum confidence rejection threshold."),
    ]

    for q_text, a_text in qas:
        story.append(qa_card(q_text, a_text))

    doc.build(story, onFirstPage=_add_page_furniture, onLaterPages=_add_page_furniture)


if __name__ == "__main__":
    out = Path("/home/zeto/Desktop/FSL-Project/FSL_Research_Defense_Guide.pdf")
    generate_pdf(out)
    print(f"Generated {out} ({out.stat().st_size / 1024:.1f} KB)")
