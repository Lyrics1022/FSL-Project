import os
from reportlab.lib.pagesizes import letter
from reportlab.platypus import (
    SimpleDocTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
)
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib import colors

def generate_pdf(output_path):
    # Printable area: 8.5 x 11 inches with 28 pt margins -> width = 556 pt, height = 736 pt
    doc = SimpleDocTemplate(
        output_path,
        pagesize=letter,
        leftMargin=28,
        rightMargin=28,
        topMargin=24,
        bottomMargin=24,
    )

    styles = getSampleStyleSheet()

    # Typography & spacing tailored for a single-page official form layout
    title_style = ParagraphStyle(
        'SectionTitle',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=9.5,
        leading=11.5,
        textColor=colors.black,
        spaceAfter=1,
    )

    subtitle_style = ParagraphStyle(
        'SectionSubtitle',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=7.5,
        leading=9.5,
        textColor=colors.black,
        spaceAfter=3,
    )

    th_style = ParagraphStyle(
        'TableHeader',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=7,
        leading=8.5,
        alignment=0,
        textColor=colors.black,
    )

    th_center_style = ParagraphStyle(
        'TableHeaderCenter',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=7,
        leading=8.5,
        alignment=1,
        textColor=colors.black,
    )

    td_style = ParagraphStyle(
        'TableCell',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=6.5,
        leading=7.8,
        textColor=colors.black,
    )

    td_center_style = ParagraphStyle(
        'TableCellCenter',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=6.5,
        leading=7.8,
        alignment=1,
        textColor=colors.black,
    )

    box_p_style = ParagraphStyle(
        'BoxParagraph',
        parent=styles['Normal'],
        fontName='Helvetica',
        fontSize=6.5,
        leading=8,
        textColor=colors.black,
        spaceAfter=2,
    )

    box_bold_style = ParagraphStyle(
        'BoxBold',
        parent=styles['Normal'],
        fontName='Helvetica-Bold',
        fontSize=6.8,
        leading=8.5,
        textColor=colors.black,
        spaceAfter=1,
    )

    story = []

    # ==========================================
    # PART V
    # ==========================================
    story.append(Paragraph("PART V. ISSUES, RISKS, AND CORRECTIVE ACTIONS", title_style))
    story.append(Paragraph("Identify problems that may affect the system's development schedule, quality, or completion.", subtitle_style))

    # Total width: 556 pt = 140 + 116 + 170 + 65 + 65
    part_v_data = [
        [
            Paragraph("<b>Identified<br/>Issue / Risk</b>", th_style),
            Paragraph("<b>Effect on the<br/>Project</b>", th_style),
            Paragraph("<b>Planned<br/>Corrective Action</b>", th_style),
            Paragraph("<b>Responsible<br/>Member(s)</b>", th_style),
            Paragraph("<b>Target<br/>Resolution Date</b>", th_style),
        ],
        [
            Paragraph("<b>Limited and Uneven Video Dataset for Translation Accuracy</b><br/><i>(SRS Section 2.5.3: System accuracy relies directly on the quality, size, and diversity of recorded training examples)</i>", td_style),
            Paragraph("If our collection of video samples lacks variety across different signers, hand sizes, and lighting, the app may struggle to recognize signs correctly when tested by new users or in dim room lighting.", td_style),
            Paragraph("1. Record additional practice video samples with diverse volunteer signers under varying room lighting.<br/>2. Use software techniques to create mirrored and slightly angled variations of current clips.<br/>3. Train the app to focus strictly on overall hand shape and motion rather than background details.", td_style),
            Paragraph("Payanay, R.", td_style),
            Paragraph("Nov 10, 2026", td_style),
        ],
        [
            Paragraph("<b>Expanding Vocabulary to Complete the Predefined Word List</b><br/><i>(SRS Feature 1 & 2: The system must recognize and display the full predefined set of supported FSL categories)</i>", td_style),
            Paragraph("While the app successfully translates the first 20 core words (such as 'Hello', 'Thank You', 'Yes', and 'No'), the remaining everyday categories (family, calendar, numbers, food) still need to be integrated into the app.", td_style),
            Paragraph("1. Finish processing the remaining recorded gesture categories from our library.<br/>2. Update the offline app so it recognizes the full vocabulary list.<br/>3. Add illustrated step-by-step guides in the app's Sign Guide for every newly added word.", td_style),
            Paragraph("Bautista, L.", td_style),
            Paragraph("Nov 17, 2026", td_style),
        ],
        [
            Paragraph("<b>Handling Unclear Signs with Clear On-Screen Guidance</b><br/><i>(SRS FR-3.1.3.4 & Safety Requirement 5.2.1: The system must display a helpful notice when a gesture is not recognized instead of guessing or freezing)</i>", td_style),
            Paragraph("If a user performs a sign too quickly, stands partially out of camera view, or makes an unsupported gesture, the app could show the wrong word or confuse the user.", td_style),
            Paragraph("1. Set the app to only show a translation when it is confident in the match.<br/>2. When a gesture is unclear, display a clear message: <i>'Sign not recognized. Please position your hands in view and try again.'</i><br/>3. Keep the camera display running smoothly so the app never freezes or crashes.", td_style),
            Paragraph("Narvaez, J.Z.", td_style),
            Paragraph("Nov 21, 2026", td_style),
        ],
        [
            Paragraph("<b>Simple System Maintenance and Sign Database Updates</b><br/><i>(SRS Feature 3: The system must allow the team to update the sign list and recognition file over time)</i>", td_style),
            Paragraph("As our team records new words and improves the app, updating the app on user devices could become difficult without a clear update procedure.", td_style),
            Paragraph("1. Establish an easy, one-step process to load new sign updates and words into the app.<br/>2. Prepare a clear developer guide detailing how to add new words, meanings, and pictures to the app in the future.", td_style),
            Paragraph("Payanay, R.<br/>Bautista, L.", td_style),
            Paragraph("Nov 26, 2026", td_style),
        ],
    ]

    table_v = Table(part_v_data, colWidths=[140, 116, 170, 65, 65])
    table_v.setStyle(TableStyle([
        ('BOX', (0,0), (-1,-1), 0.8, colors.black),
        ('INNERGRID', (0,0), (-1,-1), 0.5, colors.black),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('TOPPADDING', (0,0), (-1,-1), 2),
        ('BOTTOMPADDING', (0,0), (-1,-1), 2),
        ('LEFTPADDING', (0,0), (-1,-1), 3),
        ('RIGHTPADDING', (0,0), (-1,-1), 3),
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#F5F5F5')),
    ]))

    story.append(table_v)
    story.append(Spacer(1, 6))

    # ==========================================
    # PART VI
    # ==========================================
    story.append(Paragraph("PART VI. NEXT DEVELOPMENT TARGETS", title_style))
    story.append(Paragraph("Identify the specific tasks that the group commits to accomplishing during the next development period.", subtitle_style))

    # Total width: 556 pt = 36 + 145 + 175 + 75 + 65 + 60
    part_vi_data = [
        [
            Paragraph("<b>Priority</b>", th_center_style),
            Paragraph("<b>Next Task /<br/>Deliverable</b>", th_style),
            Paragraph("<b>Expected<br/>Output</b>", th_style),
            Paragraph("<b>Responsible<br/>Member(s)</b>", th_style),
            Paragraph("<b>Target<br/>Date</b>", th_center_style),
            Paragraph("<b>Expected Task<br/>Completion (%)</b>", th_center_style),
        ],
        [
            Paragraph("1", td_center_style),
            Paragraph("<b>Expand Vocabulary to Full Predefined Word List</b><br/><i>(SRS Feature 1: FR-3.1.3.2)</i><br/>Train the offline model using the complete set of recorded FSL categories (family, calendar, numbers, greetings, food) and update the app's word list.", td_style),
            Paragraph("The app will recognize and translate the complete predefined set of everyday signs instead of just the initial 20 words.", td_style),
            Paragraph("Payanay, R.", td_style),
            Paragraph("Nov 17, 2026", td_center_style),
            Paragraph("<b>70%</b>", td_center_style),
        ],
        [
            Paragraph("2", td_center_style),
            Paragraph("<b>Refine Detection Timing & Unrecognized Sign Prompts</b><br/><i>(SRS Feature 1: FR-3.1.3.3 & FR-3.1.3.4)</i><br/>Optimize camera capture response time and implement clear on-screen notifications whenever a sign is not recognized or performed outside camera view.", td_style),
            Paragraph("Quick, reliable translation response with a helpful on-screen message (<i>'Sign not recognized, please try again'</i>) instead of guessing or freezing.", td_style),
            Paragraph("Bautista, L.", td_style),
            Paragraph("Nov 24, 2026", td_center_style),
            Paragraph("<b>80%</b>", td_center_style),
        ],
        [
            Paragraph("3", td_center_style),
            Paragraph("<b>Administrator Sign Database & Model Maintenance Module</b><br/><i>(SRS Feature 3: FR-3.3.3.1 & FR-3.3.3.2)</i><br/>Implement the maintenance function that allows the developer/administrator to add, edit, or remove sign labels and swap in newly trained model files without rebuilding the entire app.", td_style),
            Paragraph("A secure admin maintenance screen to manage the local sign dictionary and load updated AI model files smoothly.", td_style),
            Paragraph("Payanay, R.<br/>Narvaez, J.Z.", td_style),
            Paragraph("Dec 01, 2026", td_center_style),
            Paragraph("<b>90%</b>", td_center_style),
        ],
        [
            Paragraph("4", td_center_style),
            Paragraph("<b>Complete Sign Reference Library & Final User Testing</b><br/><i>(SRS Feature 2 & Section 5.4)</i><br/>Complete all illustrated guides in the Sign Reference Library, conduct reliability testing on multiple devices, and evaluate usability with target users.", td_style),
            Paragraph("A complete, fully offline Android app with an accessible sign dictionary, zero crashes, and verified user test results.", td_style),
            Paragraph("Payanay, R.<br/>Bautista, L.<br/>Narvaez, J.Z.", td_style),
            Paragraph("Dec 08, 2026", td_center_style),
            Paragraph("<b>100%</b>", td_center_style),
        ],
    ]

    table_vi = Table(part_vi_data, colWidths=[36, 145, 175, 75, 65, 60])
    table_vi.setStyle(TableStyle([
        ('BOX', (0,0), (-1,-1), 0.8, colors.black),
        ('INNERGRID', (0,0), (-1,-1), 0.5, colors.black),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('TOPPADDING', (0,0), (-1,-1), 2),
        ('BOTTOMPADDING', (0,0), (-1,-1), 2),
        ('LEFTPADDING', (0,0), (-1,-1), 3),
        ('RIGHTPADDING', (0,0), (-1,-1), 3),
        ('BACKGROUND', (0,0), (-1,0), colors.HexColor('#F5F5F5')),
    ]))

    story.append(table_vi)
    story.append(Spacer(1, 6))

    # ==========================================
    # Required milestone action plan
    # ==========================================
    story.append(Paragraph("<b>Required milestone action plan:</b> Describe how your group will reach or maintain the 60% project completion milestone.", title_style))

    box_content = [
        Paragraph("<b>How our group reached the 60% completion milestone:</b>", box_bold_style),
        Paragraph("Our group achieved the <b>60% milestone</b> by building a complete, working mobile application that successfully connects the smartphone camera, the offline AI translation engine, and an intuitive user interface:", box_p_style),
        Paragraph("• <b>Working Hand Detection & Offline Translation:</b> The phone's camera detects hand positions and upper-body movements in real time on the screen. The system operates 100% offline directly on the mobile device, requiring no internet connection. The initial AI translation model is fully functional and successfully translates the first 20 essential everyday Filipino signs (such as <i>'Hello'</i>, <i>'Thank You'</i>, <i>'Mother'</i>, <i>'Father'</i>, <i>'Yes'</i>, and <i>'No'</i>).", box_p_style),
        Paragraph("• <b>User-Friendly Mobile App Interface:</b> A live camera screen displays a real-time skeletal hand outline so users can easily see that their hands are in proper view. An easy-to-follow flow guides the user (Ready → Countdown → Record Sign → Display Translation Result), accompanied by an on-screen accuracy meter, voice audio pronunciation, and an illustrated in-app <b>Sign Guide</b> showing beginner-friendly hand diagrams.", box_p_style),
        Paragraph("<b>How our group will maintain this 60% milestone:</b>", box_bold_style),
        Paragraph("• <b>Testing on Physical Devices:</b> Continually test every new update on actual Android smartphones to guarantee that the camera remains smooth and the app does not freeze.<br/>• <b>Protecting Existing Features:</b> Ensure that the current 20 working words and camera functions continue to operate without errors before introducing new vocabulary.<br/>• <b>Safe Version Backups:</b> Maintain working backups of the app and dataset so the group always has a stable, presentation-ready version available.", box_p_style),
    ]

    action_box_table = Table([[box_content]], colWidths=[556])
    action_box_table.setStyle(TableStyle([
        ('BOX', (0,0), (-1,-1), 0.8, colors.black),
        ('VALIGN', (0,0), (-1,-1), 'TOP'),
        ('TOPPADDING', (0,0), (-1,-1), 4),
        ('BOTTOMPADDING', (0,0), (-1,-1), 4),
        ('LEFTPADDING', (0,0), (-1,-1), 6),
        ('RIGHTPADDING', (0,0), (-1,-1), 6),
    ]))

    story.append(action_box_table)

    doc.build(story)
    print(f"PDF generated successfully at: {output_path}")

if __name__ == "__main__":
    out_pdf = "/home/zeto/Desktop/FSL-Project/FSL_Milestone_Report_Part_V_and_VI.pdf"
    generate_pdf(out_pdf)
