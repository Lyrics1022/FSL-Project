package com.example.sign_language_translator

import android.app.Activity
import android.app.Dialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat

/**
 * Interactive full-screen Sign Language Library modal dialog.
 * Displays all 105 active FSL vocabulary items with category filtering,
 * animated hand placement blueprint diagrams, directional motion arrows,
 * step-by-step instructions, and audio pronunciation.
 */
class VocabLibraryDialog(
    private val activity: Activity,
    private val onSpeak: (String) -> Unit
) {

    private val density = activity.resources.displayMetrics.density
    private fun dp(v: Float): Int = (v * density).toInt()

    fun show() {
        val dialog = Dialog(activity, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_vocab_library)

        dialog.window?.let { win ->
            win.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            win.setBackgroundDrawable(ColorDrawable(Color.parseColor("#071320")))
        }

        // View references
        val viewLibraryList = dialog.findViewById<View>(R.id.viewLibraryList)
        val viewSignDetail = dialog.findViewById<View>(R.id.viewSignDetail)
        val btnCloseLibrary = dialog.findViewById<ImageButton>(R.id.btnCloseLibrary)
        val signsListContainer = dialog.findViewById<LinearLayout>(R.id.signsListContainer)

        val chipAll = dialog.findViewById<TextView>(R.id.chipAll)
        val chipGreetings = dialog.findViewById<TextView>(R.id.chipGreetings)
        val chipSurvival = dialog.findViewById<TextView>(R.id.chipSurvival)
        val chipNumbers = dialog.findViewById<TextView>(R.id.chipNumbers)
        val chipFamily = dialog.findViewById<TextView>(R.id.chipFamily)
        val chipCalendar = dialog.findViewById<TextView>(R.id.chipCalendar)
        val chipRelationships = dialog.findViewById<TextView>(R.id.chipRelationships)
        val chipColors = dialog.findViewById<TextView>(R.id.chipColors)
        val chipFoodDrink = dialog.findViewById<TextView>(R.id.chipFoodDrink)

        // Detail View references
        val tvDetailTitle = dialog.findViewById<TextView>(R.id.tvDetailTitle)
        val tvDetailCategory = dialog.findViewById<TextView>(R.id.tvDetailCategory)
        val tvDetailHandShape = dialog.findViewById<TextView>(R.id.tvDetailHandShape)
        val tvDetailStep1 = dialog.findViewById<TextView>(R.id.tvDetailStep1)
        val tvDetailStep2 = dialog.findViewById<TextView>(R.id.tvDetailStep2)
        val tvDetailStep3 = dialog.findViewById<TextView>(R.id.tvDetailStep3)
        val tvDetailStep4 = dialog.findViewById<TextView>(R.id.tvDetailStep4)
        val tvDetailProTip = dialog.findViewById<TextView>(R.id.tvDetailProTip)
        val signDiagramView = dialog.findViewById<SignDiagramView>(R.id.signDiagramView)
        val scrollSignDetail = dialog.findViewById<ScrollView>(R.id.scrollSignDetail)
        val btnDetailSpeak = dialog.findViewById<ImageButton>(R.id.btnDetailSpeak)
        val btnBackToLibrary = dialog.findViewById<ImageButton>(R.id.btnBackToLibrary)
        val btnTrySign = dialog.findViewById<Button>(R.id.btnTrySign)

        var selectedCategory: String = "ALL"
        var activeSign: FslSignItem? = null

        // Dismiss library
        btnCloseLibrary.setOnClickListener {
            dialog.dismiss()
        }

        fun showDetail(item: FslSignItem) {
            activeSign = item
            tvDetailTitle.text = item.name
            tvDetailCategory.text = "${item.category}  •  ${if (item.isTwoHanded) "2 HANDS" else "1 HAND"}"
            tvDetailHandShape.text = "${item.handShape}  •  ${item.placement}"
            tvDetailStep1.text = item.step1
            tvDetailStep2.text = item.step2
            tvDetailStep3.text = item.step3
            tvDetailStep4.text = item.step4
            tvDetailProTip.text = item.proTip

            signDiagramView.setSign(item)

            viewLibraryList.visibility = View.GONE
            viewSignDetail.visibility = View.VISIBLE
            scrollSignDetail?.scrollTo(0, 0)
            scrollSignDetail?.post { scrollSignDetail.scrollTo(0, 0) }
        }

        btnBackToLibrary.setOnClickListener {
            viewSignDetail.visibility = View.GONE
            viewLibraryList.visibility = View.VISIBLE
        }

        btnDetailSpeak.setOnClickListener {
            activeSign?.let { item ->
                btnDetailSpeak.animate().scaleX(0.85f).scaleY(0.85f).setDuration(70).withEndAction {
                    btnDetailSpeak.animate().scaleX(1f).scaleY(1f).setDuration(100).start()
                }.start()
                onSpeak(item.name)
            }
        }

        btnTrySign.setOnClickListener {
            activeSign?.let { item ->
                Toast.makeText(activity, "Camera ready for: ${item.name}", Toast.LENGTH_SHORT).show()
            }
            dialog.dismiss()
        }

        fun populateList(category: String) {
            signsListContainer.removeAllViews()
            val filtered = if (category == "ALL") {
                FslVocabularyData.items
            } else {
                FslVocabularyData.items.filter { it.category.contains(category, ignoreCase = true) }
            }

            for (item in filtered) {
                val card = buildSignCard(item) {
                    showDetail(item)
                }
                signsListContainer.addView(card)
            }
        }

        fun updateChips(activeChip: TextView, categoryKey: String) {
            selectedCategory = categoryKey
            val allChips = listOf(
                chipAll, chipGreetings, chipSurvival, chipNumbers,
                chipFamily, chipCalendar, chipRelationships, chipColors, chipFoodDrink
            )
            for (c in allChips) {
                if (c == activeChip) {
                    c.setBackgroundResource(R.drawable.bg_chip_recording)
                    c.setTextColor(Color.parseColor("#00D2C4"))
                    c.setTypeface(null, Typeface.BOLD)
                } else {
                    c.setBackgroundResource(R.drawable.bg_chip_language)
                    c.setTextColor(Color.parseColor("#94A3B8"))
                    c.setTypeface(null, Typeface.NORMAL)
                }
            }
            populateList(categoryKey)
        }

        chipAll.setOnClickListener { updateChips(chipAll, "ALL") }
        chipGreetings.setOnClickListener { updateChips(chipGreetings, "GREETINGS") }
        chipSurvival.setOnClickListener { updateChips(chipSurvival, "SURVIVAL") }
        chipNumbers.setOnClickListener { updateChips(chipNumbers, "NUMBERS") }
        chipFamily.setOnClickListener { updateChips(chipFamily, "FAMILY") }
        chipCalendar.setOnClickListener { updateChips(chipCalendar, "CALENDAR") }
        chipRelationships.setOnClickListener { updateChips(chipRelationships, "RELATIONSHIPS") }
        chipColors.setOnClickListener { updateChips(chipColors, "COLORS") }
        chipFoodDrink.setOnClickListener { updateChips(chipFoodDrink, "FOOD") }

        // Initial population
        populateList("ALL")

        dialog.show()
    }

    private fun buildSignCard(item: FslSignItem, onClick: () -> Unit): View {
        val card = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.bg_card)
            setPadding(dp(16f), dp(14f), dp(16f), dp(14f))
            isClickable = true
            isFocusable = true
            val outValue = android.util.TypedValue()
            activity.theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            foreground = ContextCompat.getDrawable(activity, outValue.resourceId)

            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(10f)
            }
            layoutParams = params

            setOnClickListener { onClick() }
        }

        // 1. Number Badge Circle
        val badgeNumber = TextView(activity).apply {
            text = "${item.id}"
            textSize = 13f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#00D2C4"))

            val size = dp(34f)
            val badgeParams = LinearLayout.LayoutParams(size, size).apply {
                marginEnd = dp(14f)
            }
            layoutParams = badgeParams

            val bgCircle = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#0B2238"))
                setStroke(dp(1.5f), Color.parseColor("#00D2C4"))
            }
            background = bgCircle
        }
        card.addView(badgeNumber)

        // 2. Middle Text Column: Name + Handedness Tag + Summary
        val middleColumn = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }

        // Title Row: Sign Name + Handedness Tag
        val titleRow = LinearLayout(activity).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val tvName = TextView(activity).apply {
            text = item.name
            textSize = 16f
            typeface = Typeface.create("sans-serif-bold", Typeface.BOLD)
            setTextColor(Color.parseColor("#FFFFFF"))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }
        titleRow.addView(tvName)

        val tvHandTag = TextView(activity).apply {
            text = if (item.isTwoHanded) "2 HANDS" else "1 HAND"
            textSize = 9.5f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            val textColor = if (item.isTwoHanded) Color.parseColor("#10B981") else Color.parseColor("#38BDF8")
            setTextColor(textColor)

            val tagBg = GradientDrawable().apply {
                cornerRadius = dp(4f).toFloat()
                setColor(Color.parseColor("#0D263E"))
            }
            background = tagBg
            setPadding(dp(6f), dp(2f), dp(6f), dp(2f))

            val tagParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = dp(8f)
            }
            layoutParams = tagParams
        }
        titleRow.addView(tvHandTag)

        middleColumn.addView(titleRow)

        // Arrow motion label
        val tvArrow = TextView(activity).apply {
            text = "${item.arrowLabel}  •  ${item.placement}"
            textSize = 11.5f
            setTextColor(Color.parseColor("#94A3B8"))
            setPadding(0, dp(3f), 0, 0)
        }
        middleColumn.addView(tvArrow)

        card.addView(middleColumn)

        // 3. Right Chevron Indicator (›)
        val tvChevron = TextView(activity).apply {
            text = "›"
            textSize = 24f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setTextColor(Color.parseColor("#475569"))
            setPadding(dp(8f), 0, 0, 0)
        }
        card.addView(tvChevron)

        return card
    }
}
