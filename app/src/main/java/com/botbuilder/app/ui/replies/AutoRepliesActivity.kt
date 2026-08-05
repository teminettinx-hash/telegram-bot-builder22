package com.botbuilder.app.ui.replies

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.botbuilder.app.R
import com.botbuilder.app.billing.PlanLimits
import com.botbuilder.app.billing.currentTier
import com.botbuilder.app.data.local.AppDatabase
import com.botbuilder.app.data.local.MatchType
import com.botbuilder.app.data.local.ReplyRule
import com.botbuilder.app.data.local.SecureStore
import com.botbuilder.app.databinding.ActivityAutoRepliesBinding
import com.botbuilder.app.databinding.ItemKeywordChipBinding
import com.botbuilder.app.databinding.ItemReplyCardBinding
import com.botbuilder.app.databinding.SheetEditReplyBinding
import com.botbuilder.app.ui.MainActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class AutoRepliesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAutoRepliesBinding
    private lateinit var db: AppDatabase
    private lateinit var secureStore: SecureStore
    private lateinit var adapter: ReplyAdapter

    private val matchTypeOptions = listOf("Exact Match", "Contains", "Starts With", "Ends With")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAutoRepliesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        db = AppDatabase.getInstance(applicationContext)
        secureStore = SecureStore(applicationContext)

        binding.topBar.textTopBarLabel.text = "Replies"
        binding.topBar.imageAvatar.setOnClickListener {
            startActivity(Intent(this, MainActivity::class.java))
        }

        adapter = ReplyAdapter(
            onClick = { rule -> openSheet(rule) },
            onMore = { rule -> openSheet(rule) }
        )
        binding.recyclerReplies.layoutManager = LinearLayoutManager(this)
        binding.recyclerReplies.adapter = adapter

        binding.fabAdd.setOnClickListener { checkCapThenAdd() }

        binding.bottomNav.selectedItemId = R.id.nav_replies
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> { startActivity(Intent(this, MainActivity::class.java)); finish(); true }
                R.id.nav_replies -> true
                R.id.nav_ai -> {
                    startActivity(Intent(this, com.botbuilder.app.ui.ai.AiSettingsActivity::class.java)); true
                }
                R.id.nav_more -> {
                    startActivity(Intent(this, com.botbuilder.app.ui.settings.SettingsActivity::class.java)); true
                }
                else -> false
            }
        }

        lifecycleScope.launch {
            db.replyRuleDao().observeAll().collectLatest { rules ->
                adapter.submitList(rules)
                binding.textCount.text = "${rules.size} Active"
                binding.textEmpty.visibility = if (rules.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
                binding.recyclerReplies.visibility = if (rules.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            }
        }
    }

    private fun checkCapThenAdd() {
        lifecycleScope.launch {
            val tier = secureStore.currentTier()
            val cap = PlanLimits.maxReplyRules(tier)
            val count = db.replyRuleDao().count()
            if (count >= cap) {
                showUpgradeDialog(
                    "Auto-reply limit reached",
                    "The ${tier.displayName} plan allows up to $cap auto-replies. Upgrade to add more."
                )
            } else {
                openSheet(null)
            }
        }
    }

    private fun showUpgradeDialog(title: String, message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("See plans") { _, _ ->
                startActivity(Intent(this, com.botbuilder.app.ui.plans.PlansActivity::class.java))
            }
            .setNegativeButton("Not now", null)
            .show()
    }

    private fun openSheet(existing: ReplyRule?) {
        val sheetBinding = SheetEditReplyBinding.inflate(LayoutInflater.from(this))
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.sheetTitle.text = if (existing == null) "Add Auto Reply" else "Edit Auto Reply"
        sheetBinding.inputLabel.setText(existing?.label ?: "")
        sheetBinding.inputKeywords.setText(existing?.keywordList()?.joinToString(", ") ?: "")
        sheetBinding.inputAnswer.setText(existing?.answer ?: "")
        sheetBinding.switchCaseSensitive.isChecked = existing?.caseSensitive ?: false

        val spinnerAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, matchTypeOptions)
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        sheetBinding.spinnerMatchType.adapter = spinnerAdapter
        sheetBinding.spinnerMatchType.setSelection(
            when (existing?.matchType) {
                MatchType.EXACT -> 0
                MatchType.STARTS_WITH -> 2
                MatchType.ENDS_WITH -> 3
                else -> 1
            }
        )

        sheetBinding.buttonCloseSheet.setOnClickListener { dialog.dismiss() }
        sheetBinding.buttonCancel.setOnClickListener { dialog.dismiss() }
        sheetBinding.buttonSave.setOnClickListener {
            val label = sheetBinding.inputLabel.text.toString().trim()
            val keywords = sheetBinding.inputKeywords.text.toString()
                .split(",").map { it.trim() }.filter { it.isNotEmpty() }.joinToString("|")
            val answer = sheetBinding.inputAnswer.text.toString().trim()

            if (label.isEmpty() || keywords.isEmpty() || answer.isEmpty()) {
                Toast.makeText(this, "Fill in label, at least one keyword, and an answer", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val matchType = when (sheetBinding.spinnerMatchType.selectedItemPosition) {
                0 -> MatchType.EXACT
                2 -> MatchType.STARTS_WITH
                3 -> MatchType.ENDS_WITH
                else -> MatchType.CONTAINS
            }

            val rule = ReplyRule(
                id = existing?.id ?: 0,
                label = label,
                keywords = keywords,
                answer = answer,
                matchType = matchType,
                caseSensitive = sheetBinding.switchCaseSensitive.isChecked,
                priority = existing?.priority ?: 0
            )
            lifecycleScope.launch {
                db.replyRuleDao().upsert(rule)
                dialog.dismiss()
            }
        }

        dialog.show()
    }
}

private class ReplyAdapter(
    private val onClick: (ReplyRule) -> Unit,
    private val onMore: (ReplyRule) -> Unit
) : RecyclerView.Adapter<ReplyAdapter.ViewHolder>() {

    private var items: List<ReplyRule> = emptyList()
    fun submitList(newItems: List<ReplyRule>) { items = newItems; notifyDataSetChanged() }

    class ViewHolder(val binding: ItemReplyCardBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemReplyCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val rule = items[position]
        holder.binding.textLabel.text = rule.label
        holder.binding.textMatchType.text = when (rule.matchType) {
            MatchType.EXACT -> "Exact Match"
            MatchType.STARTS_WITH -> "Starts With"
            MatchType.ENDS_WITH -> "Ends With"
            else -> "Contains"
        }
        holder.binding.textAnswerPreview.text = "\"${rule.answer}\""

        holder.binding.chipContainer.removeAllViews()
        rule.keywordList().forEach { keyword ->
            val chip = ItemKeywordChipBinding.inflate(
                LayoutInflater.from(holder.binding.root.context), holder.binding.chipContainer, false
            )
            chip.root.text = keyword
            holder.binding.chipContainer.addView(chip.root)
        }

        holder.binding.root.setOnClickListener { onClick(rule) }
        holder.binding.buttonMore.setOnClickListener { onMore(rule) }
    }

    override fun getItemCount(): Int = items.size
}
