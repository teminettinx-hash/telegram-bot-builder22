package com.botbuilder.app.ui.profile

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings as AndroidSettings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.botbuilder.app.BotBuilderApp
import com.botbuilder.app.R
import com.botbuilder.app.billing.PlanLimits
import com.botbuilder.app.billing.currentTier
import com.botbuilder.app.data.local.AppDatabase
import com.botbuilder.app.data.local.SecureStore
import com.botbuilder.app.data.supabase.SupabaseAuthManager
import com.botbuilder.app.service.BotPollingService
import com.botbuilder.app.ui.MainActivity
import com.botbuilder.app.ui.ai.AiSettingsActivity
import com.botbuilder.app.ui.auth.AuthActivity
import com.botbuilder.app.ui.replies.AutoRepliesActivity
import com.botbuilder.app.ui.settings.SettingsActivity
import com.botbuilder.app.ui.plans.PlansActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.gson.GsonBuilder
import kotlinx.coroutines.launch
import java.io.File
import java.util.Calendar

class ProfileActivity : AppCompatActivity() {

    private lateinit var secureStore: SecureStore
    private lateinit var db: AppDatabase
    private lateinit var authManager: SupabaseAuthManager

    private lateinit var avatarInitials: TextView
    private lateinit var nameText: TextView
    private lateinit var subtitleText: TextView
    private lateinit var planBadge: TextView
    private lateinit var usageNumbersText: TextView
    private lateinit var usageBar: ProgressBar
    private lateinit var resetsText: TextView
    private lateinit var themeChipText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        secureStore = SecureStore(applicationContext)
        db = AppDatabase.getInstance(applicationContext)
        authManager = SupabaseAuthManager(secureStore)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(color(R.color.md_background))
        }

        root.addView(buildTopBar())

        val scrollContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(8))
        }
        scrollContent.addView(buildHeroSection())
        scrollContent.addView(buildPlanCard())
        scrollContent.addView(buildAccountSection())
        scrollContent.addView(buildSupportSection())
        scrollContent.addView(buildFooter())

        val scroll = ScrollView(this).apply { addView(scrollContent) }
        root.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        root.addView(buildBottomNav())

        setContentView(root)
        refreshPlanCard()
    }

    override fun onResume() {
        super.onResume()
        refreshPlanCard()
    }

    // ---------- Sections ----------

    private fun buildTopBar(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64))
            setBackgroundColor(color(R.color.md_surface_container_lowest))
            elevation = dp(2).toFloat()
            setPadding(dp(16), 0, dp(16), 0)

            val logo = android.widget.ImageView(this@ProfileActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(28), dp(28))
                setImageResource(R.drawable.ic_smart_toy)
                imageTintList = ColorStateList.valueOf(color(R.color.md_primary))
            }
            val title = TextView(this@ProfileActivity).apply {
                text = "Bot Builder"
                textSize = 20f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(color(R.color.md_on_surface))
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    .also { it.marginStart = dp(12) }
            }
            // Note: the Stitch mockup's top bar label literally says "Dashboard" on every
            // screen including Profile — that looks like a copy/paste artifact in the design
            // file rather than an intentional choice, so this one label is corrected to the
            // screen it's actually on. Everything else follows the file as given.
            val pageLabel = TextView(this@ProfileActivity).apply {
                text = "Profile"
                textSize = 14f
                setTextColor(color(R.color.md_on_surface_variant))
                setPadding(0, 0, dp(8), 0)
            }
            val avatar = android.widget.ImageView(this@ProfileActivity).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setBackgroundResource(R.drawable.bg_circle_primary)
                setImageResource(R.drawable.ic_person)
                imageTintList = ColorStateList.valueOf(color(R.color.md_on_primary))
            }

            addView(logo)
            addView(title)
            addView(pageLabel)
            addView(avatar)
        }
    }

    private fun buildHeroSection(): LinearLayout {
        val name = secureStore.supabaseUserName ?: "Your Name"
        val initials = name.trim().split(" ").filter { it.isNotEmpty() }
            .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(24), dp(16), dp(24))

            val avatarStack = android.widget.FrameLayout(this@ProfileActivity)
            val avatarCircle = TextView(this@ProfileActivity).apply {
                layoutParams = android.widget.FrameLayout.LayoutParams(dp(96), dp(96))
                setBackgroundResource(R.drawable.bg_circle_primary)
                gravity = Gravity.CENTER
                textSize = 32f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(color(R.color.md_on_primary))
                elevation = dp(3).toFloat()
            }
            avatarInitials = avatarCircle
            avatarCircle.text = initials

            val editBadge = android.widget.ImageView(this@ProfileActivity).apply {
                val size = dp(32)
                layoutParams = android.widget.FrameLayout.LayoutParams(size, size).also {
                    it.gravity = Gravity.BOTTOM or Gravity.END
                }
                setBackgroundResource(R.drawable.bg_circle_secondary_container)
                setPadding(dp(7), dp(7), dp(7), dp(7))
                setImageResource(R.drawable.ic_edit)
                imageTintList = ColorStateList.valueOf(color(R.color.md_primary))
                elevation = dp(3).toFloat()
                isClickable = true
                setOnClickListener { showEditNameDialog() }
            }

            avatarStack.addView(avatarCircle)
            avatarStack.addView(editBadge)
            addView(avatarStack)

            nameText = TextView(this@ProfileActivity).apply {
                text = name
                textSize = 24f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(color(R.color.md_on_surface))
                setPadding(0, dp(12), 0, 0)
            }
            addView(nameText)

            subtitleText = TextView(this@ProfileActivity).apply {
                text = subtitleForAccount()
                textSize = 14f
                setTextColor(color(R.color.md_on_surface_variant))
            }
            addView(subtitleText)
        }
    }

    private fun subtitleForAccount(): String {
        secureStore.supabaseUserEmail?.let { return it }
        secureStore.botUsername?.let { return "@$it" }
        return "Not connected"
    }

    private fun buildPlanCard(): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_bordered)
            setPadding(dp(16), dp(16), dp(16), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.setMargins(dp(16), 0, dp(16), dp(24)) }
        }

        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val planColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        planColumn.addView(TextView(this).apply {
            text = "CURRENT PLAN"
            textSize = 11f
            letterSpacing = 0.08f
            setTextColor(color(R.color.md_on_surface_variant))
        })
        planBadge = TextView(this).apply {
            textSize = 13f
            setTypeface(typeface, Typeface.BOLD)
            setBackgroundResource(R.drawable.bg_badge_plan)
            setTextColor(color(R.color.md_on_tertiary_container))
            setPadding(dp(12), dp(6), dp(12), dp(6))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .also { it.topMargin = dp(6) }
        }
        planColumn.addView(planBadge)
        topRow.addView(planColumn)

        val manageButton = MaterialButton(this).apply {
            text = "Manage Plan"
            isAllCaps = false
            textSize = 14f
            cornerRadius = dp(20)
            backgroundTintList = ColorStateList.valueOf(color(R.color.md_primary))
            setTextColor(color(R.color.md_on_primary))
            setOnClickListener { startActivity(Intent(this@ProfileActivity, PlansActivity::class.java)) }
        }
        topRow.addView(manageButton)
        card.addView(topRow)

        val usageBlock = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(16), 0, 0)
        }
        val usageRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        usageRow.addView(TextView(this).apply {
            text = "Message Usage"
            textSize = 14f
            setTextColor(color(R.color.md_on_surface_variant))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        usageNumbersText = TextView(this).apply {
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color(R.color.md_on_surface))
        }
        usageRow.addView(usageNumbersText)
        usageBlock.addView(usageRow)

        usageBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8)).also { it.topMargin = dp(8) }
            max = 100
            progressTintList = ColorStateList.valueOf(color(R.color.md_primary))
            progressBackgroundTintList = ColorStateList.valueOf(color(R.color.md_surface_container_highest))
        }
        usageBlock.addView(usageBar)

        resetsText = TextView(this).apply {
            textSize = 11f
            setTextColor(color(R.color.md_on_surface_variant))
            setPadding(0, dp(6), 0, 0)
        }
        usageBlock.addView(resetsText)

        card.addView(usageBlock)
        return card
    }

    private fun refreshPlanCard() {
        val tier = secureStore.currentTier()
        planBadge.text = "${tier.displayName.uppercase()} PLAN"

        val cap = PlanLimits.maxMessagesPerMonth(tier)
        val used = secureStore.messagesThisMonth
        val capLabel = if (cap == Int.MAX_VALUE) "Unlimited" else "%,d".format(cap)
        usageNumbersText.text = if (cap == Int.MAX_VALUE) "%,d / Unlimited".format(used) else "%,d / %s".format(used, capLabel)
        usageBar.progress = if (cap == Int.MAX_VALUE || cap == 0) 0 else ((used.toFloat() / cap) * 100).toInt().coerceIn(0, 100)

        val cal = Calendar.getInstance()
        val daysLeft = cal.getActualMaximum(Calendar.DAY_OF_MONTH) - cal.get(Calendar.DAY_OF_MONTH) + 1
        resetsText.text = "Resets in $daysLeft day${if (daysLeft == 1) "" else "s"}"
    }

    private fun buildAccountSection(): LinearLayout {
        val (outer, card) = sectionContainer("ACCOUNT SETTINGS")

        card.addView(settingsRow(R.drawable.ic_smart_toy, color(R.color.md_primary), "Bot Settings") {
            startActivity(Intent(this, SettingsActivity::class.java))
        })
        card.addView(settingsRow(R.drawable.ic_notifications, color(R.color.md_primary), "Notification Preferences") {
            openNotificationSettings()
        })
        card.addView(settingsRow(R.drawable.ic_file_download, color(R.color.md_primary), "Export My Data") {
            exportData()
        })

        themeChipText = TextView(this)
        val themeRow = settingsRowWithTrailingChip(
            R.drawable.ic_light_mode, color(R.color.md_primary), "App Theme", themeChipText
        ) { showThemePicker() }
        card.addView(themeRow)
        updateThemeChipLabel()

        return outer
    }

    private fun buildSupportSection(): LinearLayout {
        val (outer, card) = sectionContainer("SUPPORT")

        card.addView(settingsRow(R.drawable.ic_help, color(R.color.md_secondary), "Help & FAQ") { showHelpDialog() })
        card.addView(settingsRow(R.drawable.ic_mail, color(R.color.md_secondary), "Contact Support") { contactSupport() })
        card.addView(settingsRow(R.drawable.ic_thumb_up, color(R.color.md_secondary), "Rate Bot Builder") { openPlayStoreListing() })

        val linksRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, dp(12), 0, 0)
        }
        linksRow.addView(linkText("Privacy Policy") { Toast.makeText(this@ProfileActivity, "Add your Privacy Policy URL in ProfileActivity", Toast.LENGTH_SHORT).show() })
        linksRow.addView(linkText("Terms of Service") { Toast.makeText(this@ProfileActivity, "Add your Terms of Service URL in ProfileActivity", Toast.LENGTH_SHORT).show() }.also { it.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { lp -> lp.marginStart = dp(24) } })
        outer.addView(linksRow)

        return outer
    }

    private fun buildFooter(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(16), dp(24), dp(16), dp(24))

            val disconnectButton = MaterialButton(this@ProfileActivity).apply {
                text = "DISCONNECT BOT"
                isAllCaps = true
                textSize = 14f
                cornerRadius = dp(24)
                backgroundTintList = ColorStateList.valueOf(color(R.color.md_error_container))
                setTextColor(color(R.color.md_on_error_container))
                icon = androidx.core.content.ContextCompat.getDrawable(this@ProfileActivity, R.drawable.ic_logout)
                iconTint = ColorStateList.valueOf(color(R.color.md_on_error_container))
                setOnClickListener { confirmDisconnect() }
            }
            addView(disconnectButton, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)))

            // Not in the original mockup (which predates account sign-in existing at all) —
            // added because there's now a real account system and it needs a way to sign out.
            val logOutText = TextView(this@ProfileActivity).apply {
                text = "Log Out of Account"
                textSize = 13f
                setTextColor(color(R.color.md_on_surface_variant))
                setPadding(0, dp(12), 0, 0)
                isClickable = true
                setOnClickListener { confirmLogOut() }
            }
            addView(logOutText)

            val versionText = TextView(this@ProfileActivity).apply {
                text = appVersionLabel()
                textSize = 11f
                setTextColor(color(R.color.md_outline))
                setPadding(0, dp(16), 0, 0)
            }
            addView(versionText)
        }
    }

    private fun buildBottomNav(): BottomNavigationView {
        return BottomNavigationView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64))
            setBackgroundColor(color(R.color.md_surface_container_lowest))
            elevation = dp(8).toFloat()
            inflateMenu(R.menu.bottom_nav_menu)
            itemIconTintList = androidx.core.content.ContextCompat.getColorStateList(this@ProfileActivity, R.color.bottom_nav_item_color)
            itemTextColor = androidx.core.content.ContextCompat.getColorStateList(this@ProfileActivity, R.color.bottom_nav_item_color)
            labelVisibilityMode = com.google.android.material.bottomnavigation.LabelVisibilityMode.LABEL_VISIBILITY_LABELED
            // Profile is reached via the avatar, not a nav tab — the design highlights
            // "More" as active while here, since Profile lives under that section.
            selectedItemId = R.id.nav_more
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_dashboard -> { startActivity(Intent(this@ProfileActivity, MainActivity::class.java)); false }
                    R.id.nav_replies -> { startActivity(Intent(this@ProfileActivity, AutoRepliesActivity::class.java)); false }
                    R.id.nav_ai -> { startActivity(Intent(this@ProfileActivity, AiSettingsActivity::class.java)); false }
                    R.id.nav_more -> true
                    else -> false
                }
            }
        }
    }

    // ---------- Row builders ----------

    private fun sectionContainer(headerText: String): Pair<LinearLayout, LinearLayout> {
        val outer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), 0, dp(16), dp(24))
        }
        outer.addView(TextView(this).apply {
            text = headerText
            textSize = 13f
            letterSpacing = 0.06f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(color(R.color.md_on_surface_variant))
            setPadding(dp(4), 0, 0, dp(8))
        })
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundResource(R.drawable.bg_card_bordered)
        }
        outer.addView(card)
        return outer to card
    }

    private fun settingsRow(iconRes: Int, iconTint: Int, label: String, onClick: () -> Unit): LinearLayout {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(14), dp(16), dp(14))
            isClickable = true
            isFocusable = true
            val outValue = android.util.TypedValue()
            theme.resolveAttribute(android.R.attr.selectableItemBackground, outValue, true)
            setBackgroundResource(outValue.resourceId)
            setOnClickListener { onClick() }
        }
        row.addView(android.widget.ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(24), dp(24))
            setImageResource(iconRes)
            imageTintList = ColorStateList.valueOf(iconTint)
        })
        row.addView(TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(color(R.color.md_on_surface))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).also { it.marginStart = dp(16) }
        })
        row.addView(android.widget.ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(20), dp(20))
            setImageResource(R.drawable.ic_chevron_right)
            imageTintList = ColorStateList.valueOf(color(R.color.md_outline))
        })
        return row
    }

    private fun settingsRowWithTrailingChip(iconRes: Int, iconTint: Int, label: String, chipTextView: TextView, onClick: () -> Unit): LinearLayout {
        val row = settingsRow(iconRes, iconTint, label, onClick)
        // Insert the chip before the chevron (last child)
        val chevron = row.getChildAt(row.childCount - 1)
        row.removeView(chevron)
        chipTextView.apply {
            textSize = 13f
            setTextColor(color(R.color.md_primary))
            setBackgroundResource(R.drawable.bg_chip_secondary)
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        row.addView(chipTextView, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).also { it.marginEnd = dp(8) })
        row.addView(chevron)
        return row
    }

    private fun linkText(label: String, onClick: () -> Unit): TextView {
        return TextView(this).apply {
            text = label
            textSize = 12f
            setTextColor(color(R.color.md_on_surface_variant))
            isClickable = true
            setOnClickListener { onClick() }
        }
    }

    // ---------- Actions ----------

    private fun showEditNameDialog() {
        val input = EditText(this).apply {
            setText(secureStore.supabaseUserName ?: "")
            hint = "Your name"
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        AlertDialog.Builder(this)
            .setTitle("Edit your name")
            .setView(input)
            .setPositiveButton("Save") { _, _ ->
                val newName = input.text.toString().trim()
                if (newName.isEmpty()) return@setPositiveButton
                if (!authManager.isSignedIn()) {
                    Toast.makeText(this, "Sign in to save a name", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                lifecycleScope.launch {
                    val result = authManager.updateName(newName)
                    result.onSuccess {
                        nameText.text = newName
                        avatarInitials.text = newName.trim().split(" ").filter { it.isNotEmpty() }
                            .take(2).joinToString("") { it.first().uppercase() }.ifEmpty { "?" }
                        Toast.makeText(this@ProfileActivity, "Name updated", Toast.LENGTH_SHORT).show()
                    }.onFailure {
                        Toast.makeText(this@ProfileActivity, it.message ?: "Couldn't update name", Toast.LENGTH_LONG).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openNotificationSettings() {
        val intent = Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(AndroidSettings.EXTRA_APP_PACKAGE, packageName)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Couldn't open notification settings", Toast.LENGTH_SHORT).show()
        }
    }

    private fun exportData(): Unit {
        lifecycleScope.launch {
            try {
                val replies = db.replyRuleDao().getAllOnce()
                val commands = db.botCommandDao().getAllOnce()

                val exportMap = linkedMapOf(
                    "exportedAt" to java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.US).format(java.util.Date()),
                    "botUsername" to (secureStore.botUsername ?: ""),
                    "autoReplies" to replies.map {
                        mapOf(
                            "label" to it.label, "keywords" to it.keywords, "answer" to it.answer,
                            "matchType" to it.matchType.name, "caseSensitive" to it.caseSensitive
                        )
                    },
                    "botCommands" to commands.map {
                        mapOf("command" to it.command, "description" to it.description, "answer" to it.answer)
                    }
                )

                val json = GsonBuilder().setPrettyPrinting().create().toJson(exportMap)

                val exportsDir = File(cacheDir, "exports").also { it.mkdirs() }
                val file = File(exportsDir, "bot-builder-export.json")
                file.writeText(json)

                val uri: Uri = FileProvider.getUriForFile(this@ProfileActivity, "$packageName.fileprovider", file)
                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Export Bot Builder data"))
            } catch (e: Exception) {
                Toast.makeText(this@ProfileActivity, "Export failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun updateThemeChipLabel() {
        themeChipText.text = when (secureStore.themeMode) {
            "light" -> "☀ Light"
            "dark" -> "🌙 Dark"
            else -> "System"
        }
    }

    private fun showThemePicker() {
        val options = arrayOf("Light", "Dark", "Match system")
        val values = arrayOf("light", "dark", "system")
        val current = values.indexOf(secureStore.themeMode).coerceAtLeast(2)

        AlertDialog.Builder(this)
            .setTitle("App Theme")
            .setSingleChoiceItems(options, current) { dialog, which ->
                secureStore.themeMode = values[which]
                BotBuilderApp.applyThemeMode(values[which])
                dialog.dismiss()
                recreate()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showHelpDialog() {
        AlertDialog.Builder(this)
            .setTitle("Help & FAQ")
            .setMessage(
                "• Connect your bot's token in Settings, then tap Start Bot on the Dashboard.\n\n" +
                "• Auto Replies answer based on keywords you set. AI Settings adds a smart fallback for anything that doesn't match.\n\n" +
                "• The \"/\" command menu in Telegram is powered by Bot Commands, and syncs automatically when you save.\n\n" +
                "• Broadcast messages everyone who has ever messaged your bot — that's a Telegram rule, not a limitation of this app.\n\n" +
                "• Usage limits are shown here and in Plans & Usage, and reset on the 1st of each month."
            )
            .setPositiveButton("Got it", null)
            .show()
    }

    private fun contactSupport() {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:")
            putExtra(Intent.EXTRA_EMAIL, arrayOf("support@example.com"))
            putExtra(Intent.EXTRA_SUBJECT, "Bot Builder Support")
            putExtra(Intent.EXTRA_TEXT, "\n\n---\n${appVersionLabel()}\nAndroid ${android.os.Build.VERSION.RELEASE}")
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "No email app found — update the support address in ProfileActivity", Toast.LENGTH_LONG).show()
        }
    }

    private fun openPlayStoreListing() {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
        } catch (e: Exception) {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
        }
    }

    private fun confirmDisconnect() {
        AlertDialog.Builder(this)
            .setTitle("Disconnect this bot?")
            .setMessage("This stops the bot and removes the saved token from this device. You'll need to paste it again to reconnect.")
            .setPositiveButton("Disconnect") { _, _ ->
                stopService(Intent(this, BotPollingService::class.java))
                secureStore.clearBotToken()
                secureStore.botUsername = null
                Toast.makeText(this, "Bot disconnected", Toast.LENGTH_SHORT).show()
                subtitleText.text = subtitleForAccount()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmLogOut() {
        AlertDialog.Builder(this)
            .setTitle("Log out?")
            .setMessage("You'll need to sign in again to sync your settings. Your bot keeps working either way — this only signs out of your Bot Builder account.")
            .setPositiveButton("Log Out") { _, _ ->
                authManager.signOut()
                startActivity(Intent(this, AuthActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun appVersionLabel(): String {
        return try {
            val info = packageManager.getPackageInfo(packageName, 0)
            val code = if (android.os.Build.VERSION.SDK_INT >= 28) info.longVersionCode else @Suppress("DEPRECATION") info.versionCode.toLong()
            "Bot Builder v${info.versionName} ($code)"
        } catch (e: Exception) {
            "Bot Builder"
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun color(resId: Int): Int = androidx.core.content.ContextCompat.getColor(this, resId)
}
