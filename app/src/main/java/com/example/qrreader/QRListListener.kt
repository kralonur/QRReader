package com.example.qrreader

import com.google.mlkit.vision.barcode.Barcode

interface QRListListener {
    fun qrList(list: List<Barcode>)
}