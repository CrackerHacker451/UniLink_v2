package com.example.unilink.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import com.example.unilink.databinding.BottomSheetProPassBinding
import com.example.unilink.repository.UserRepository
import com.example.unilink.utils.HapticUtils
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class ProPassBottomSheet(
    private val onSubscriptionActivated: () -> Unit = {}
) : BottomSheetDialogFragment() {

    private var _binding: BottomSheetProPassBinding? = null
    private val binding get() = _binding!!

    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private var isAnnualSelected = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetProPassBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        selectPlan(isAnnual = true)

        binding.cardMonthly.setOnClickListener {
            HapticUtils.playSelection(it)
            selectPlan(isAnnual = false)
        }

        binding.cardAnnual.setOnClickListener {
            HapticUtils.playSelection(it)
            selectPlan(isAnnual = true)
        }

        binding.btnRedeem.setOnClickListener {
            val code = binding.etPromoCode.text.toString().trim().uppercase()
            if (code.isNotEmpty()) {
                UserRepository().redeemPremiumCode(code) { success, msg ->
                    if (success) {
                        Toast.makeText(context, msg ?: "Pro Pass Activated! 👑", Toast.LENGTH_LONG).show()
                        onSubscriptionActivated()
                        dismiss()
                    } else {
                        Toast.makeText(context, msg ?: "Invalid code", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(context, "Please enter a valid promo code (e.g. UNILINKPRO)", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnSubscribe.setOnClickListener {
            val planName = if (isAnnualSelected) "Annual Pro Pass (₹299/yr)" else "Monthly Pro Pass (₹49/mo)"
            activateProPass(planName)
        }
    }

    private fun selectPlan(isAnnual: Boolean) {
        isAnnualSelected = isAnnual
        if (isAnnual) {
            binding.cardAnnual.strokeWidth = 6
            binding.cardMonthly.strokeWidth = 2
        } else {
            binding.cardMonthly.strokeWidth = 6
            binding.cardAnnual.strokeWidth = 2
        }
    }

    private fun activateProPass(planName: String) {
        val uid = auth.currentUser?.uid ?: return
        HapticUtils.playSuccess(binding.btnSubscribe)

        val updates = hashMapOf<String, Any>(
            "isPremium" to true,
            "isVip" to true,
            "premium" to true,
            "vip" to true,
            "vipLevel" to 3,
            "uniCoins" to FieldValue.increment(500),
            "badges" to FieldValue.arrayUnion("Pro Crown 👑", "VIP Student ⭐")
        )

        db.collection("users").document(uid).update(updates).addOnSuccessListener {
            context?.let { ctx ->
                Toast.makeText(ctx, "🎉 $planName Activated! +500 UniCoins 👑", Toast.LENGTH_LONG).show()
            }
            onSubscriptionActivated()
            dismiss()
        }.addOnFailureListener {
            context?.let { ctx ->
                Toast.makeText(ctx, "Subscription failed. Please try again.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
