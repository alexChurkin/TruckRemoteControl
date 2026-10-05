package com.alexchurkin.truckremote.ui.settings

import android.content.Context
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning

/**
 * Google code scanner: no camera permission, the scanning screen is of Google Play services.
 * Its module is downloaded by Play services (requested at the app install, see the manifest);
 * if it isn't there yet, the download is started and [onLoading] is called instead of a silent wait.
 */
object QrScanner {

    fun scan(context: Context, onResult: (String?) -> Unit, onLoading: () -> Unit, onUnavailable: () -> Unit) {
        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build()
        val scanner = GmsBarcodeScanning.getClient(context, options)
        val modules = ModuleInstall.getClient(context)
        modules.areModulesAvailable(scanner)
            .addOnSuccessListener { response ->
                if (response.areModulesAvailable()) {
                    scanner.startScan()
                        .addOnSuccessListener { onResult(it.rawValue) }
                        .addOnFailureListener { onUnavailable() }
                } else {
                    modules.installModules(ModuleInstallRequest.newBuilder().addApi(scanner).build())
                    onLoading()
                }
            }
            .addOnFailureListener { onUnavailable() }
    }
}
