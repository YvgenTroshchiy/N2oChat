package com.troshchiy.n2ochat.ui

import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.troshchiy.n2ochat.Message
import com.troshchiy.n2ochat.databinding.ListItemMessageBinding
import com.troshchiy.n2ochat.inflater
import java.util.*


class ChatAdapter() : RecyclerView.Adapter<ChatAdapter.BindingHolder>() {

    private val data = LinkedList<Message>()

    var books: List<Message> = LinkedList()
        set(value) {
            data.clear()
            data.addAll(value)
            notifyDataSetChanged()
        }

    override fun getItemCount(): Int = data.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        BindingHolder(ListItemMessageBinding.inflate(parent.context.inflater(), parent, false))

    override fun onBindViewHolder(holder: BindingHolder, position: Int) {
        holder.bind(data[position])
    }

    inner class BindingHolder(private val binding: ListItemMessageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(message: Message) {
            binding.message = message
            binding.executePendingBindings()
        }
    }
}