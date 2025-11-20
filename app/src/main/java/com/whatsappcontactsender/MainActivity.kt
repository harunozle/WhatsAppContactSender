package com.whatsappcontactsender

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.whatsappcontactsender.databinding.ActivityMainBinding
import com.whatsappcontactsender.model.Contact
import com.whatsappcontactsender.adapter.ContactAdapter

class MainActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityMainBinding
    private lateinit var contactAdapter: ContactAdapter
    private lateinit var contactList: MutableList<Contact>
    
    // Rehber izni talep launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            loadContacts()
        } else {
            Toast.makeText(this, "Rehbere erişim izni gerekli", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // View binding kullan
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupRecyclerView()
        checkPermissionAndLoadContacts()
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.title = "Bilinmeyen Numaralar"
    }
    
    private fun setupRecyclerView() {
        contactList = mutableListOf()
        contactAdapter = ContactAdapter(contactList) { contact ->
            openWhatsApp(contact.phoneNumber)
        }
        
        binding.recyclerView.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = contactAdapter
        }
    }
    
    private fun checkPermissionAndLoadContacts() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) == PackageManager.PERMISSION_GRANTED -> {
                loadContacts()
            }
            
            shouldShowRequestPermissionRationale(Manifest.permission.READ_CONTACTS) -> {
                showPermissionRationale()
            }
            
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
        }
    }
    
    private fun showPermissionRationale() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("İzin Gerekli")
            .setMessage("Bu uygulama rehberinizdeki numaralara erişebilmek için izin gerektirir.")
            .setPositiveButton("İzin Ver") { _, _ ->
                requestPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            }
            .setNegativeButton("İptal", null)
            .show()
    }
    
    private fun loadContacts() {
        try {
            val cursor = contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone._ID
                ),
                null,
                null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            
            val knownNumbers = mutableSetOf<String>()
            
            cursor?.use { c ->
                val numberIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                val nameIndex = c.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                
                while (c.moveToNext()) {
                    val number = c.getString(numberIndex)?.trim()
                    val name = c.getString(nameIndex)
                    
                    if (!number.isNullOrEmpty() && !name.isNullOrEmpty()) {
                        knownNumbers.add(cleanPhoneNumber(number))
                    }
                }
            }
            
            // Tüm çağrı kayıtlarından bilinmeyen numaraları çek
            loadCallLogs(knownNumbers)
            
        } catch (e: Exception) {
            Toast.makeText(this, "Rehber yüklenirken hata: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun loadCallLogs(knownNumbers: Set<String>) {
        try {
            val callLogCursor = contentResolver.query(
                android.provider.CallLog.Calls.CONTENT_URI,
                arrayOf(
                    android.provider.CallLog.Calls.NUMBER,
                    android.provider.CallLog.Calls.DATE,
                    android.provider.CallLog.Calls.TYPE,
                    android.provider.CallLog.Calls.CACHED_NAME
                ),
                null,
                null,
                android.provider.CallLog.Calls.DATE + " DESC"
            )
            
            val unknownNumbers = mutableSetOf<String>()
            val recentCallLogNumbers = mutableSetOf<String>()
            
            callLogCursor?.use { cursor ->
                val numberIndex = cursor.getColumnIndex(android.provider.CallLog.Calls.NUMBER)
                val typeIndex = cursor.getColumnIndex(android.provider.CallLog.Calls.TYPE)
                val cachedNameIndex = cursor.getColumnIndex(android.provider.CallLog.Calls.CACHED_NAME)
                
                while (cursor.moveToNext()) {
                    val number = cursor.getString(numberIndex)?.trim()
                    val callType = cursor.getString(typeIndex)
                    val cachedName = cursor.getString(cachedNameIndex)
                    
                    if (!number.isNullOrEmpty()) {
                        val cleanNumber = cleanPhoneNumber(number)
                        recentCallLogNumbers.add(cleanNumber)
                        
                        // Bilinmeyen numara: rehberde kayıtlı değil VE cache'de isim yok
                        if (!knownNumbers.contains(cleanNumber) && cachedName.isNullOrEmpty()) {
                            unknownNumbers.add(cleanNumber)
                        }
                    }
                }
            }
            
            // Son 50 çağrıdan bilinmeyenleri listele
            contactList.clear()
            val sortedUnknownNumbers = unknownNumbers.toList().sorted()
            
            for (number in sortedUnknownNumbers.take(50)) {
                contactList.add(
                    Contact(
                        id = number,
                        name = "Bilinmeyen Numara",
                        phoneNumber = formatPhoneNumber(number),
                        isUnknown = true
                    )
                )
            }
            
            contactAdapter.notifyDataSetChanged()
            
            if (contactList.isEmpty()) {
                binding.emptyTextView.visibility = android.view.View.VISIBLE
                binding.recyclerView.visibility = android.view.View.GONE
            } else {
                binding.emptyTextView.visibility = android.view.View.GONE
                binding.recyclerView.visibility = android.view.View.VISIBLE
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "Çağrı geçmişi yüklenirken hata: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun cleanPhoneNumber(number: String): String {
        return number.replace("[^\\d+]".toRegex(), "")
                    .replace("+", "")
    }
    
    private fun formatPhoneNumber(number: String): String {
        val clean = cleanPhoneNumber(number)
        return if (clean.startsWith("90")) {
            "+$clean"
        } else {
            "+90$clean"
        }
    }
    
    private fun openWhatsApp(phoneNumber: String) {
        try {
            val cleanNumber = phoneNumber.replace("[^\\d+]".toRegex(), "")
            val whatsappUrl = "https://wa.me/$cleanNumber"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(whatsappUrl))
            
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                // WhatsApp yoksa web versiyonunu aç
                val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://web.whatsapp.com/send?phone=$cleanNumber"))
                startActivity(webIntent)
            }
            
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp açılırken hata oluştu", Toast.LENGTH_SHORT).show()
        }
    }
    
    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_refresh -> {
                loadContacts()
                true
            }
            R.id.action_help -> {
                showHelpDialog()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun showHelpDialog() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Yardım")
            .setMessage("Bu uygulama, rehberinizde kayıtlı olmayan kişilerden gelen çağrıları listeler. WhatsApp butonuna tıklayarak doğrudan WhatsApp üzerinden mesaj gönderebilirsiniz.")
            .setPositiveButton("Tamam", null)
            .show()
    }
}