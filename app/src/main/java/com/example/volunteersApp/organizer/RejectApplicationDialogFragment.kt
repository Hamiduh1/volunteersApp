package com.example.volunteersApp.organizer // Or your preferred package for dialogs

import android.app.Dialog
import android.os.Bundle
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
//import androidx.compose.ui.semantics.text
//import androidx.compose.ui.test.cancel
import androidx.fragment.app.DialogFragment
import com.example.volunteersApp.R // Assuming you have a layout for the dialog
import kotlin.text.isNotEmpty
import kotlin.text.trim

/**
 * A DialogFragment for entering the reason for rejecting an application.
 */
class RejectApplicationDialogFragment : DialogFragment() {

    interface RejectionReasonListener {
        fun onRejectionReasonEntered(reason: String)
    }

    private var listener: RejectionReasonListener? = null

    companion object {
        const val TAG = "RejectApplicationDialog"

        fun newInstance(): RejectApplicationDialogFragment {
            return RejectApplicationDialogFragment()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        // Attempt to set the listener from the target fragment or parent activity
        // This is a common pattern, but you could also pass the listener via a setter or constructor argument
        // if using this dialog from various places.
        if (targetFragment is RejectionReasonListener) {
            listener = targetFragment as RejectionReasonListener
        } else if (activity is RejectionReasonListener) {
            listener = activity as RejectionReasonListener
        } else {
            // Fallback: If no listener is set via targetFragment or activity,
            // you might want to consider how the result is passed.
            // For now, we'll proceed, but the listener might be null.
            // throw ClassCastException("Calling fragment or activity must implement RejectionReasonListener")
        }


        val builder = AlertDialog.Builder(requireActivity())
        val inflater = requireActivity().layoutInflater
        val dialogView = inflater.inflate(R.layout.dialog_reject_reason, null) // You'll need to create this layout

        val editTextReason = dialogView.findViewById<EditText>(R.id.editTextRejectionReason)

        builder.setView(dialogView)
            .setTitle("Reason for Rejection")
            .setPositiveButton("Submit") { _, _ ->
                val reason = editTextReason.text.toString().trim()
                if (reason.isNotEmpty()) {
                    listener?.onRejectionReasonEntered(reason)
                } else {
                    // Optionally handle empty reason, e.g., show a Toast or just submit empty
                    listener?.onRejectionReasonEntered("") // Or handle as an error
                }
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.cancel()
            }

        return builder.create()
    }

    fun setRejectionReasonListener(listener: RejectionReasonListener) {
        this.listener = listener
    }

    override fun onDetach() {
        super.onDetach()
        listener = null // Avoid memory leaks
    }
}
