package com.example.unilink.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.Toast
import com.example.unilink.databinding.DialogPremiumPurchaseBinding
import com.example.unilink.repository.UserRepository
import com.google.android.material.bottomsheet.BottomSheetDialog

object PremiumDialogHelper {

    fun showPremiumPurchaseDialog(context: Context, onSuccess: (() -> Unit)? = null) {
        val dialog = BottomSheetDialog(context)
        val binding = DialogPremiumPurchaseBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(binding.root)

        val userRepository = UserRepository()

        // Toggle between Payment Step and Verification Step
        binding.btnNextStep.setOnClickListener {
            binding.llPaymentStep.visibility = View.GONE
            binding.llVerificationStep.visibility = View.VISIBLE
        }

        binding.btnBackToPay.setOnClickListener {
            binding.llVerificationStep.visibility = View.GONE
            binding.llPaymentStep.visibility = View.VISIBLE
        }

        // Apply Promo Code
        binding.btnApplyPromo.setOnClickListener {
            val code = binding.etPromoCode.text.toString().trim()
            if (code.isNotEmpty()) {
                userRepository.redeemPremiumCode(code) { success, msg ->
                    Toast.makeText(context, msg ?: if (success) "Code applied!" else "Failed", Toast.LENGTH_LONG).show()
                    if (success) {
                        dialog.dismiss()
                        onSuccess?.invoke()
                    }
                }
            } else {
                Toast.makeText(context, "Please enter a promo code", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnHaveCode.setOnClickListener {
            binding.llPromoArea.visibility = View.VISIBLE
            binding.etPromoCode.requestFocus()
        }

        // Submit Verification
        binding.btnSubmitVerification.setOnClickListener {
            val txId = binding.etTransactionId.text.toString().trim()
            if (txId.isEmpty()) {
                Toast.makeText(context, "Please enter Transaction ID", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            userRepository.activatePremium { success ->
                if (success) {
                    Toast.makeText(context, "✨ Payment Verified! UniLink Pro Activated!", Toast.LENGTH_LONG).show()
                    dialog.dismiss()
                    onSuccess?.invoke()
                } else {
                    Toast.makeText(context, "Verification failed. Try again.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.btnUploadScreenshot.setOnClickListener {
            Toast.makeText(context, "Screenshot attached successfully!", Toast.LENGTH_SHORT).show()
        }

        dialog.show()
    }
}
