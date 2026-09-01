package com.google.android.material.bottomsheet;

import android.app.Dialog;
import android.os.Bundle;
import androidx.fragment.app.DialogFragment;

public class BottomSheetDialogFragment extends DialogFragment {
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new BottomSheetDialog(getContext());
    }
}
