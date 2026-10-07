package com.yagay.ysuite.feature.ynfc
import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcA
import com.yagay.YNFC.CardModel
internal class YNfcReaderController(private val activity: Activity) {
    private val adapter = NfcAdapter.getDefaultAdapter(activity)
    private var enabled = false
    fun enable(onCard: (CardModel) -> Unit): Result<Unit> = runCatching {
        if (enabled) return@runCatching
        val nfc = adapter ?: error("NFC unavailable")
        nfc.enableReaderMode(
            activity,
            { tag -> parse(tag)?.let { activity.runOnUiThread { onCard(it) } } },
            NfcAdapter.FLAG_READER_NFC_A or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,
            null,
        )
        enabled = true
    }
    fun disable() {
        if (!enabled) return
        runCatching { adapter?.disableReaderMode(activity) }
        enabled = false
    }
    private fun parse(tag: Tag): CardModel? {
        val uid = tag.id?.joinToString("") { "%02X".format(it) }?.uppercase().orEmpty()
        if (uid.isBlank()) return null
        var sak = "08"
        var atqa = "0400"
        runCatching {
            NfcA.get(tag)?.let {
                sak = "%02X".format(it.sak.toInt() and 0xFF)
                atqa = it.atqa.reversedArray().joinToString("") { b -> "%02X".format(b) }.uppercase()
            }
        }
        return CardModel("Card " + uid.takeLast(4), uid, sak, atqa)
    }
}
