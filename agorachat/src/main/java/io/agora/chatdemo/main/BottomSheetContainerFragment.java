package io.agora.chatdemo.main;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import io.agora.chatdemo.base.BaseContainChildBottomSheetFragment;
import io.agora.chatdemo.group.GroupCreateFragment;

public class BottomSheetContainerFragment extends BaseContainChildBottomSheetFragment {

    @NonNull
    @Override
    protected Fragment getChildFragment() {
        return new GroupCreateFragment();
    }

    @Override
    protected void initListener() {
        super.initListener();
        baseBinding.titlebar.getRightText().setOnClickListener(v -> {
                    if (!currentChild.onTitlebarRightTextViewClick()) {
                        hide();
                    }
                }
        );
    }
}
