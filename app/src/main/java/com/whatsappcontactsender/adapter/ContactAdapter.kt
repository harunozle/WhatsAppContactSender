package com.whatsappcontactsender.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.whatsappcontactsender.R
import com.whatsappcontactsender.model.Contact

class ContactAdapter(
    private val contacts: List<Contact>,
    private val onWhatsAppClick: (Contact) -> Unit
) : RecyclerView.Adapter<ContactAdapter.ContactViewHolder>() {

    class ContactViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val phoneNumberText: TextView = itemView.findViewById(R.id.phoneNumberText)
        val nameText: TextView = itemView.findViewById(R.id.nameText)
        val statusText: TextView = itemView.findViewById(R.id.statusText)
        val whatsappButton: ImageButton = itemView.findViewById(R.id.whatsappButton)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ContactViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_contact, parent, false)
        return ContactViewHolder(view)
    }

    override fun onBindViewHolder(holder: ContactViewHolder, position: Int) {
        val contact = contacts[position]
        
        holder.phoneNumberText.text = contact.phoneNumber
        holder.nameText.text = contact.name
        holder.statusText.text = "Bilinmeyen numara"
        
        // WhatsApp butonuna tıklandığında
        holder.whatsappButton.setOnClickListener {
            onWhatsAppClick(contact)
            
            // Tıklama animasyonu
            holder.whatsappButton.animate()
                .scaleX(0.9f)
                .scaleY(0.9f)
                .setDuration(100)
                .withEndAction {
                    holder.whatsappButton.animate()
                        .scaleX(1.0f)
                        .scaleY(1.0f)
                        .setDuration(100)
                        .start()
                }
                .start()
        }
    }

    override fun getItemCount(): Int = contacts.size
}