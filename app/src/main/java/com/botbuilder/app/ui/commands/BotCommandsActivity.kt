package com.botbuilder.app.ui.commands

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.botbuilder.app.R
import com.botbuilder.app.data.local.AppDatabase
import com.botbuilder.app.data.local.BotCommand
import com.botbuilder.app.data.local.SecureStore
import com.botbuilder.app.data.repository.BotRepository
import com.botbuilder.app.databinding.ActivityBotCommandsBinding
import com.botbuilder.app.databinding.ItemCommandRowBinding
import com.botbuilder.app.databinding.SheetEditCommandBinding
import com.botbuilder.app.ui.MainActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Screen that manages the commands shown in Telegram's native "/" popup menu
 *  (the same UI BotFather uses for /newbot, /deletebot, etc.). */
class BotCommandsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBotCommandsBinding
    private lateinit var db: AppDatabase
    private lateinit var repository: BotRepository
    private lateinit var adapter: CommandAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBotCommandsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        db = AppDatabase.getInstance(applicationContext)
        repository = BotRepository(db, SecureStore(applicationContext))

        binding.topBar.textTopBarLabel.text = "Commands"
        binding.topBar.imageAvatar.setOnClickListener {
            startActivity(Intent(this, com.botbuilder.app.ui.profile.ProfileActivity::class.java))
        }

        adapter = CommandAdapter(
            onEdit = { cmd -> openSheet(cmd) },
            onDelete = { cmd -> deleteCommand(cmd) }
        )
        binding.recyclerCommands.layoutManager = LinearLayoutManager(this)
        binding.recyclerCommands.adapter = adapter

        binding.buttonNewCommand.setOnClickListener { openSheet(null) }

        binding.bottomNav.selectedItemId = R.id.nav_more
        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_dashboard -> { startActivity(Intent(this, MainActivity::class.java)); finish(); true }
                R.id.nav_replies -> {
                    startActivity(Intent(this, com.botbuilder.app.ui.replies.AutoRepliesActivity::class.java)); true
                }
                R.id.nav_ai -> {
                    startActivity(Intent(this, com.botbuilder.app.ui.ai.AiSettingsActivity::class.java)); true
                }
                R.id.nav_more -> true
                else -> false
            }
        }

        lifecycleScope.launch {
            db.botCommandDao().observeAll().collectLatest { commands ->
                adapter.submitList(commands)
                binding.textEmpty.visibility = if (commands.isEmpty()) View.VISIBLE else View.GONE
                binding.recyclerCommands.visibility = if (commands.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    private fun syncCommands() {
        binding.textSyncStatus.text = "Syncing…"
        lifecycleScope.launch {
            val result = repository.syncCommandsToTelegram()
            binding.textSyncStatus.text = if (result.isSuccess)
                "Synced! Reopen your bot's chat in Telegram to see the updated menu."
            else
                "Couldn't sync: ${result.exceptionOrNull()?.message}"
        }
    }

    private fun openSheet(existing: BotCommand?) {
        val sheetBinding = SheetEditCommandBinding.inflate(LayoutInflater.from(this))
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(sheetBinding.root)

        sheetBinding.sheetTitle.text = if (existing == null) "Add Command" else "Edit Command"
        sheetBinding.inputCommand.setText(existing?.command ?: "")
        sheetBinding.inputDescription.setText(existing?.description ?: "")
        sheetBinding.inputAnswer.setText(existing?.answer ?: "")

        sheetBinding.buttonCloseSheet.setOnClickListener { dialog.dismiss() }
        sheetBinding.buttonCancel.setOnClickListener { dialog.dismiss() }
        sheetBinding.buttonSaveCommand.setOnClickListener {
            var command = sheetBinding.inputCommand.text.toString().trim().lowercase().removePrefix("/")
            command = command.replace(Regex("[^a-z0-9_]"), "")
            val description = sheetBinding.inputDescription.text.toString().trim()
            val answer = sheetBinding.inputAnswer.text.toString().trim()

            if (command.isEmpty() || description.isEmpty() || answer.isEmpty()) {
                sheetBinding.tilCommand.error = if (command.isEmpty()) " " else null
                sheetBinding.tilDescription.error = if (description.isEmpty()) " " else null
                sheetBinding.tilAnswer.error = if (answer.isEmpty()) " " else null
                return@setOnClickListener
            }

            val cmd = BotCommand(
                id = existing?.id ?: 0,
                command = command,
                description = description,
                answer = answer
            )
            lifecycleScope.launch {
                db.botCommandDao().upsert(cmd)
                dialog.dismiss()
                syncCommands()
            }
        }

        dialog.show()
    }

    private fun deleteCommand(cmd: BotCommand) {
        AlertDialog.Builder(this)
            .setTitle("Delete this command?")
            .setMessage("\"/${cmd.command}\" will be removed and the / menu will update automatically.")
            .setPositiveButton("Delete") { _, _ ->
                lifecycleScope.launch {
                    db.botCommandDao().delete(cmd)
                    syncCommands()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}

private class CommandAdapter(
    private val onEdit: (BotCommand) -> Unit,
    private val onDelete: (BotCommand) -> Unit
) : RecyclerView.Adapter<CommandAdapter.ViewHolder>() {

    private var items: List<BotCommand> = emptyList()
    fun submitList(newItems: List<BotCommand>) { items = newItems; notifyDataSetChanged() }

    class ViewHolder(val binding: ItemCommandRowBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemCommandRowBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val cmd = items[position]
        holder.binding.textCommandName.text = "/${cmd.command}"
        holder.binding.textCommandDescription.text = cmd.description
        holder.binding.buttonEditCommand.setOnClickListener { onEdit(cmd) }
        holder.binding.root.setOnLongClickListener { onDelete(cmd); true }

        // Divider is the second child of item_command_row.xml's root — hide it after the last row
        if (holder.binding.root.childCount > 1) {
            holder.binding.root.getChildAt(1).visibility = if (position == items.size - 1) View.INVISIBLE else View.VISIBLE
        }
    }

    override fun getItemCount(): Int = items.size
}
