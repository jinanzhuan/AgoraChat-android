package io.agora.chatdemo.chat;

import static io.agora.chat.uikit.menu.EaseChatType.SINGLE_CHAT;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.text.Editable;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.RelativeLayout;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import io.agora.chat.ChatClient;
import io.agora.chat.ChatMessage;
import io.agora.chat.ChatRoom;
import io.agora.chat.CustomMessageBody;
import io.agora.chat.LocationMessageBody;
import io.agora.chat.TextMessageBody;
import io.agora.chat.uikit.chat.EaseChatFragment;
import io.agora.chat.uikit.chat.adapter.EaseMessageAdapter;
import io.agora.chat.uikit.chat.interfaces.IChatTopExtendMenu;
import io.agora.chat.uikit.chat.widget.EaseChatMessageListLayout;
import io.agora.chat.uikit.chat.widget.EaseChatMultiSelectView;
import io.agora.chat.uikit.constants.EaseConstant;
import io.agora.chat.uikit.menu.EaseChatType;
import io.agora.chat.uikit.menu.EasePopupWindowHelper;
import io.agora.chat.uikit.menu.MenuItemBean;
import io.agora.chat.uikit.utils.EaseUtils;
import io.agora.chat.uikit.widget.EaseTitleBar;
import io.agora.chatdemo.DemoHelper;
import io.agora.chatdemo.R;
import io.agora.chatdemo.chat.adapter.CustomMessageAdapter;
import io.agora.chatdemo.chat.viewmodel.ChatViewModel;
import io.agora.chatdemo.general.constant.DemoConstant;
import io.agora.chatdemo.general.dialog.AlertDialog;
import io.agora.chatdemo.general.enums.Status;
import io.agora.chatdemo.general.interfaces.TranslationListener;
import io.agora.chatdemo.general.livedatas.EaseEvent;
import io.agora.chatdemo.general.livedatas.LiveDataBus;
import io.agora.chatdemo.general.permission.PermissionCompat;
import io.agora.chatdemo.general.permission.PermissionsManager;
import io.agora.chatdemo.general.utils.RecyclerViewUtils;
import io.agora.chatdemo.group.GroupHelper;
import io.agora.chatdemo.group.model.MemberAttributeBean;
import io.agora.chatdemo.group.viewmodel.GroupDetailViewModel;
import io.agora.chatdemo.me.LanguageActivity;
import io.agora.chatdemo.me.TranslationHelper;
import io.agora.chatdemo.me.TranslationSettingsActivity;
import io.agora.util.EMLog;

public class CustomChatFragment extends EaseChatFragment {
    private static final int REQUEST_CODE_STORAGE_PICTURE = 111;
    private static final int REQUEST_CODE_STORAGE_VIDEO = 112;
    private static final int REQUEST_CODE_STORAGE_FILE = 113;
    private boolean isFirstMeasure = true;
    private GroupDetailViewModel groupDetailViewModel;
    private ChatViewModel viewModel;
    private AlertDialog translationDialog;
    private int translationType;
    private ChatMessage translationMsg;
    private ActivityResultLauncher<Intent> launcher;
    private boolean isDestroy = false;
    private final ActivityResultLauncher<String[]> requestImagePermission = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions()
            , result -> onRequestResult(result, REQUEST_CODE_STORAGE_PICTURE));
    private final ActivityResultLauncher<String[]> requestVideoPermission = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions()
            , result -> onRequestResult(result, REQUEST_CODE_STORAGE_VIDEO));
    private final ActivityResultLauncher<String[]> requestFilePermission = registerForActivityResult(new ActivityResultContracts.RequestMultiplePermissions()
            , result -> onRequestResult(result, REQUEST_CODE_STORAGE_FILE));

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        launcher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        boolean enable = DemoHelper.getInstance().getModel().getDemandTranslationEnable();
                        if (enable && !TextUtils.isEmpty(getPreferredLanguageCode())){
                            translationMessage(translationMsg,getPreferredLanguageCode());
                        }
                    }
                }
        );
    }

    @Override
    public void initData() {
        super.initData();
        groupDetailViewModel = new ViewModelProvider((AppCompatActivity)mContext).get(GroupDetailViewModel.class);
        groupDetailViewModel.getFetchMemberAttributesObservable().observe(this,response ->{
            if(response == null || isDestroy) {
                return;
            }
            if(response.status == Status.SUCCESS) {
                chatLayout.getChatMessageListLayout().refreshMessages();
            }
        });
        viewModel = new ViewModelProvider(this).get(ChatViewModel.class);
        viewModel.getTranslationObservable().observe(this,response ->{
            if(response == null || isDestroy) {
                return;
            }
            if(response.status == Status.SUCCESS) {
                chatLayout.getChatMessageListLayout().refreshMessages();
            }else {
                EMLog.e("translationMessage","onError: " + response.errorCode + " - " + response.getMessage());
            }
        });

        LiveDataBus.get().with(DemoConstant.GROUP_MEMBER_ATTRIBUTE_CHANGE, EaseEvent.class).observe(getViewLifecycleOwner(), event -> {
            if(event == null || isDestroy) {
                return;
            }
            chatLayout.getChatMessageListLayout().refreshMessages();
        });
        LiveDataBus.get().with(DemoConstant.MESSAGE_CHANGE_CHANGE, EaseEvent.class).observe(getViewLifecycleOwner(), event -> {
            if(event == null || isDestroy) {
                return;
            }
            if(event.isMessageChange()) {
                chatLayout.getChatMessageListLayout().refreshMessages();
            }
        });
        LiveDataBus.get().with(DemoConstant.EVENT_CHAT_MODEL_TO_NORMAL, EaseEvent.class).observe(this, event -> {
            if(event == null || isDestroy) {
                return;
            }
            if(event.type == EaseEvent.TYPE.NOTIFY && TextUtils.isEmpty(event.message)) {
                IChatTopExtendMenu chatTopExtendMenu = chatLayout.getChatInputMenu().getChatTopExtendMenu();
                if(chatTopExtendMenu instanceof EaseChatMultiSelectView) {
                    ((EaseChatMultiSelectView) chatTopExtendMenu).dismissSelectView(null);
                }
                titleBar.setVisibility(View.GONE);
            }
        });
    }

    @Override
    public void initListener() {
        super.initListener();
        listenerRecyclerViewItemFinishLayout();
        EditText editText = chatLayout.getChatInputMenu().getPrimaryMenu().getEditText();
        if (editText != null){
            editText.setOnKeyListener(new View.OnKeyListener() {
                @Override
                public boolean onKey(View v, int keyCode, KeyEvent event) {
                    return removePickAt(v,keyCode,event);
                }
            });
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {

                }

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    if(!chatLayout.getChatMessageListLayout().isGroupChat()) {
                        return;
                    }
                    if(count == 1 && "@".equals(String.valueOf(s.charAt(start)))){
                        Bundle bundle = new Bundle();
                        bundle.putString(EaseConstant.EXTRA_CONVERSATION_ID, conversationId);
                        PickAtUserDialogFragment fragment = new PickAtUserDialogFragment();
                        fragment.setPickAtSelectListener(username -> {
                            chatLayout.inputAtUsername(username,false);
                        });
                        fragment.setArguments(bundle);
                        if (getActivity() != null){
                            fragment.show(getActivity().getSupportFragmentManager(), "pick_at_user");
                            if (getActivity() != null){
                                new Handler().postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                                        editText.requestFocus();
                                        imm.toggleSoftInput(InputMethodManager.SHOW_FORCED, 0);
                                    }
                                },200);
                            }
                        }
                    }
                }

                @Override
                public void afterTextChanged(Editable editable) {
                    setPickAtContentStyle(editable);
                }
            });
        }
    }


    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

    }

    @Override
    public void initView() {
        super.initView();
        MenuItemBean menuItemBean = new MenuItemBean(0, R.id.action_chat_report, 99, getResources().getString(R.string.ease_action_report));
        menuItemBean.setResourceId(R.drawable.chat_item_menu_report);
        MenuItemBean menuTranslationBean = new MenuItemBean(0, R.id.action_chat_translation,88, getResources().getString(R.string.ease_action_translation));
        menuTranslationBean.setResourceId(R.drawable.chat_item_menu_translation);
        MenuItemBean menuReTranslationBean = new MenuItemBean(0, R.id.action_chat_re_translation,111, getResources().getString(R.string.ease_action_re_translation));
        menuReTranslationBean.setResourceId(R.drawable.chat_item_menu_translation);
        chatLayout.getMenuHelper().addItemMenu(menuItemBean);
        chatLayout.getMenuHelper().addItemMenu(menuTranslationBean);
        chatLayout.getMenuHelper().addItemMenu(menuReTranslationBean);
        chatLayout.setPresenter(new ChatCustomPresenter());

        EaseMessageAdapter adapter = chatLayout.getChatMessageListLayout().getMessageAdapter();
        if (adapter instanceof CustomMessageAdapter){
            ((CustomMessageAdapter)adapter).setTranslationListener(new TranslationListener() {
                @Override
                public void onTranslationRetry(ChatMessage message,String languageCode) {
                    if (message.getBody() instanceof TextMessageBody){
                        translationMessage(message,languageCode);
                    }
                }
            });
        }
    }

    @Override
    public void onPreMenu(EasePopupWindowHelper helper, ChatMessage message) {
        super.onPreMenu(helper, message);

        if (TextUtils.equals(message.getFrom(), ChatClient.getInstance().getCurrentUser())
                || message.getBody() instanceof LocationMessageBody
                || message.getBody() instanceof CustomMessageBody
                || message.status() != ChatMessage.Status.SUCCESS) {
            helper.findItemVisible(R.id.action_chat_report, false);
        } else {
            helper.findItemVisible(R.id.action_chat_report, true);
        }

        boolean isRecallNote = message.getBooleanAttribute(DemoConstant.MESSAGE_TYPE_RECALL, false);
        if (isRecallNote) {
            helper.setAllItemsVisible(false);
            helper.showHeaderView(false);
            helper.findItemVisible(R.id.action_chat_delete, true);
        }

        if (message.getBody() instanceof TextMessageBody) {
            if (((TextMessageBody) message.getBody()).getTranslations().size() > 0) {
                helper.findItemVisible(R.id.action_chat_translation, false);
                helper.findItemVisible(R.id.action_chat_re_translation, true);
            } else {
                helper.findItemVisible(R.id.action_chat_translation, true);
                helper.findItemVisible(R.id.action_chat_re_translation, false);
            }
        } else {
            helper.findItemVisible(R.id.action_chat_translation, false);
            helper.findItemVisible(R.id.action_chat_re_translation, false);
        }
    }

    @Override
    public boolean onMenuItemClick(MenuItemBean item, ChatMessage message) {
        switch (item.getItemId()){
            case R.id.action_chat_report:
                if (message.status() == ChatMessage.Status.SUCCESS)
                    ChatReportActivity.actionStart(getActivity(),message.getMsgId());
                break;
            case R.id.action_chat_select:
                showSelectModelTitle();
                LiveDataBus.get().with(DemoConstant.EVENT_CHAT_MODEL_TO_SELECT).postValue(EaseEvent.create(DemoConstant.EVENT_CHAT_MODEL_TO_SELECT, EaseEvent.TYPE.NOTIFY));
                break;
            case R.id.action_chat_translation:
            case R.id.action_chat_re_translation:
                translationMsg = message;
                if (!TextUtils.isEmpty(getPreferredLanguageCode())){
                    boolean enable = DemoHelper.getInstance().getModel().getDemandTranslationEnable();
                    if (enable){
                        translationMessage(message,getPreferredLanguageCode());
                        break;
                    }else {
                        translationType = DemoConstant.TRANSLATION_DEMAND_ENABLE;
                    }
                }else {
                    translationType = DemoConstant.TRANSLATION_NO_LANGUAGE;
                }
                showTranslationDialog();
                break;
        }
        return super.onMenuItemClick(item, message);
    }

    @Override
    public boolean onChatExtendMenuItemClick(View view, int itemId) {
        switch (itemId) {
            case R.id.extend_item_take_picture:
                if (!PermissionsManager.getInstance().hasPermission(mContext, Manifest.permission.CAMERA)) {
                    PermissionsManager.getInstance().requestPermissionsIfNecessaryForResult(mContext
                            , new String[]{Manifest.permission.CAMERA}, null);
                    return true;
                }
                break;
            case R.id.extend_item_picture:
                if(!PermissionCompat.checkMediaPermission(mContext, requestImagePermission, Manifest.permission.READ_MEDIA_IMAGES)) {
                    return true;
                }
                break;
            case R.id.extend_item_video:
                if(!PermissionCompat.checkMediaPermission(mContext, requestVideoPermission, Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.CAMERA)) {
                    return true;
                }
                break;
            case R.id.extend_item_file:
                if(!PermissionCompat.checkMediaPermission(mContext, requestFilePermission, Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)) {
                    return true;
                }
                break;
        }
        return super.onChatExtendMenuItemClick(view, itemId);
    }

    private void onRequestResult(Map<String, Boolean> result, int requestCode) {
        if(result != null && result.size() > 0) {
            for (Map.Entry<String, Boolean> entry : result.entrySet()) {
                EMLog.e("chat", "onRequestResult: " + entry.getKey() + "  " + entry.getValue());
            }
            if(PermissionCompat.getMediaAccess(mContext) != PermissionCompat.StorageAccess.Denied) {
                if(requestCode == REQUEST_CODE_STORAGE_PICTURE) {
                    selectPicFromLocal();
                }else if(requestCode == REQUEST_CODE_STORAGE_VIDEO) {
                    selectVideoFromLocal();
                }else if(requestCode == REQUEST_CODE_STORAGE_FILE) {
                    selectFileFromLocal();
                }
            }
        }
    }

    private void showSelectModelTitle() {
        titleBar.setVisibility(View.VISIBLE);
        titleBar.setDisplayHomeAsUpEnabled(false);
        titleBar.setTitlePosition(EaseTitleBar.TitlePosition.Left);
        titleBar.setRightTitle(getString(R.string.ease_cancel));
        titleBar.getRightText().setTextColor(ContextCompat.getColor(mContext, R.color.color_action_text));
        titleBar.getIcon().setVisibility(View.VISIBLE);
        titleBar.getLeftLayout().setVisibility(View.GONE);
        ViewParent parent = titleBar.getTitle().getParent();
        if(parent instanceof ViewGroup) {
            ViewGroup.LayoutParams params = ((ViewGroup) parent).getLayoutParams();
            if(params instanceof RelativeLayout.LayoutParams) {
                ((RelativeLayout.LayoutParams) params).leftMargin = (int) EaseUtils.dip2px(mContext, 12);
            }
        }
        titleBar.setOnRightClickListener(new EaseTitleBar.OnRightClickListener() {
            @Override
            public void onRightClick(View view) {
                LiveDataBus.get().with(DemoConstant.EVENT_CHAT_MODEL_TO_NORMAL).postValue(EaseEvent.create(DemoConstant.EVENT_CHAT_MODEL_TO_NORMAL, EaseEvent.TYPE.NOTIFY));
            }
        });
        if(chatType != SINGLE_CHAT) {
            boolean hasProvided = DemoHelper.getInstance().setGroupInfo(mContext, conversationId, titleBar.getTitle(), titleBar.getIcon());
            if(!hasProvided) {
                setGroupInfo();
            }
        } else {
            DemoHelper.getInstance().getUsersManager().setUserInfo(mContext, conversationId, titleBar.getTitle(), titleBar.getIcon());
            titleBar.getTitle().setVisibility(View.INVISIBLE);
            titleBar.getSubTitle().setVisibility(View.INVISIBLE);
        }
    }

    private void setGroupInfo() {
        String title = "";
        if(chatType == EaseChatType.GROUP_CHAT) {
            title = GroupHelper.getGroupName(conversationId);
            titleBar.getIcon().setImageResource(R.drawable.icon);
        }else if(chatType == EaseChatType.CHATROOM) {
            titleBar.getIcon().setImageResource(R.drawable.icon);
            ChatRoom room = ChatClient.getInstance().chatroomManager().getChatRoom(conversationId);
            if(room == null) {
                return;
            }
            title =  TextUtils.isEmpty(room.getName()) ? conversationId : room.getName();
        }
        titleBar.getTitle().setText(title);
    }

    private void listenerRecyclerViewItemFinishLayout() {
        if (chatLayout == null || chatType.getChatType() != EaseConstant.CHATTYPE_GROUP) {
            return;
        }
        EaseChatMessageListLayout chatMessageListLayout = chatLayout.getChatMessageListLayout();
        if (chatMessageListLayout == null || chatMessageListLayout.getChildCount() <= 0) {
            return;
        }
        View swipeView = chatMessageListLayout.getChildAt(0);
        if (!(swipeView instanceof SwipeRefreshLayout)) {
            return;
        }
        if (((SwipeRefreshLayout) swipeView).getChildCount() <= 0) {
            return;
        }
        RecyclerView recyclerView = null;
        for (int i = 0; i < ((SwipeRefreshLayout) swipeView).getChildCount(); i++) {
            View child = ((SwipeRefreshLayout) swipeView).getChildAt(i);
            if (child instanceof RecyclerView) {
                recyclerView = (RecyclerView) child;
                break;
            }
        }
        if (recyclerView == null || chatMessageListLayout.getMessageAdapter() == null) {
            return;
        }
        EaseMessageAdapter messageAdapter = chatMessageListLayout.getMessageAdapter();
        RecyclerView finalRecyclerView = recyclerView;
        recyclerView.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (isFirstMeasure && finalRecyclerView.getLayoutManager() != null && messageAdapter.getData() != null
                    && ((LinearLayoutManager) finalRecyclerView.getLayoutManager()).findLastVisibleItemPosition() == messageAdapter.getData().size() - 1) {
                isFirstMeasure = false;
                int[] positionArray = RecyclerViewUtils.rangeMeasurement(finalRecyclerView);
                getGroupUserInfo(positionArray[0], positionArray[1]);
            }
        });
        recyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    int[] positionArray = RecyclerViewUtils.rangeMeasurement(recyclerView);
                    getGroupUserInfo(positionArray[0], positionArray[1]);
                }
            }
        });
    }

    public void getGroupUserInfo(int start, int end) {
        if (start <= end && end >= 0 && chatType.getChatType() == DemoConstant.CHATTYPE_GROUP) {
            Set<String> nameSet = new HashSet<>();
            for (int i = start; i <= end; i++) {
                ChatMessage message = chatLayout.getChatMessageListLayout().getMessageAdapter().getItem(i);
                if (message != null && !TextUtils.isEmpty(message.getFrom())) {
                    nameSet.add(message.getFrom());
                }
            }
            Iterator<String> iterator = nameSet.iterator();
            while (iterator.hasNext()) {
                String userId = iterator.next();
                MemberAttributeBean bean = DemoHelper.getInstance().getMemberAttribute(conversationId, userId);
                if (bean != null) {
                    iterator.remove();
                }
            }
            if (nameSet.isEmpty()) {
                return;
            }
            List<String> userIds = new ArrayList<>(nameSet);
            groupDetailViewModel.fetchGroupMemberAttribute(conversationId, userIds);
        }
    }

    @Override
    public void onModifyMessageSuccess(ChatMessage messageModified) {
        super.onModifyMessageSuccess(messageModified);
        //refresh conversation
        EaseEvent event = EaseEvent.create(DemoConstant.MESSAGE_CHANGE_RECEIVE, EaseEvent.TYPE.MESSAGE);
        LiveDataBus.get().with(DemoConstant.MESSAGE_CHANGE_CHANGE).postValue(event);
    }


    private void translationMessage(ChatMessage message,String language){
        List<String> list = new ArrayList<>();
        list.add(language);
        viewModel.translationMessage(message,list);
    }

    @Override
    public void addMsgAttrsBeforeSend(ChatMessage message) {
        super.addMsgAttrsBeforeSend(message);
        String[] autoLanguage = TranslationHelper.getLanguageByType(DemoConstant.TRANSLATION_TYPE_AUTO, conversationId);
        if (!TextUtils.isEmpty(autoLanguage[0])){
            translationMessage(message,autoLanguage[0]);
        }
    }

    private void setPickAtContentStyle(Editable editable){
        Pattern pattern = Pattern.compile("@([^\\s]+)");
        Matcher matcher = pattern.matcher(editable);
        while (matcher.find()) {
            int start = matcher.start();
            int end = matcher.end();
            editable.setSpan(
                    new ForegroundColorSpan(
                            getResources().getColor(io.agora.chat.uikit.R.color.color_conversation_title)
                    ), start, end, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    private boolean removePickAt(View v, int keyCode, KeyEvent event){
        if (keyCode == KeyEvent.KEYCODE_DEL && event.getAction() == KeyEvent.ACTION_DOWN && v instanceof EditText) {
            int selectionStart = ((EditText)v).getSelectionStart();
            int selectionEnd = ((EditText)v).getSelectionEnd();
            SpannableStringBuilder text = (SpannableStringBuilder) ((EditText)v).getText();
            ForegroundColorSpan[] spans = text.getSpans(0, text.length(), ForegroundColorSpan.class);
            for (ForegroundColorSpan span : spans) {
                int spanStart = text.getSpanStart(span);
                int spanEnd = text.getSpanEnd(span);
                if (selectionStart >= spanStart && selectionEnd <= spanEnd) {
                    if (spanStart != -1 && spanEnd != -1){
                        text.delete(spanStart+1, spanEnd);
                    }
                }
            }
        }
        return false;
    }

    private void showTranslationDialog(){
        if (translationType == 0){ return;}
        translationDialog = new AlertDialog.Builder(mContext)
                .setContentView(R.layout.dialog_auto_translation)
                .setText(R.id.tv_content,
                        translationType == DemoConstant.TRANSLATION_NO_LANGUAGE ?
                                getString(R.string.translation_auto_about_info)
                              : getString(R.string.translation_unable)
                )
                .setText(R.id.btn_ok, getString(R.string.translation_setting))
                .setLayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                .setGravity(Gravity.CENTER)
                .setCancelable(true)
                .setOnClickListener(R.id.btn_ok, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        Intent starter;
                        if (translationType == DemoConstant.TRANSLATION_NO_LANGUAGE){
                            starter = new Intent(mContext, LanguageActivity.class);
                            starter.putExtra(DemoConstant.TRANSLATION_TYPE, DemoConstant.TRANSLATION_TYPE_MESSAGE);
                            starter.putExtra(DemoConstant.TRANSLATION_SELECT_MAX_COUNT, 1);
                        }else {
                            starter = new Intent(mContext, TranslationSettingsActivity.class);
                        }
                        launcher.launch(starter);
                        translationDialog.dismiss();
                    }
                }).setOnClickListener(R.id.btn_cancel, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        translationDialog.dismiss();
                    }
                }).create();
        translationDialog.show();
    }

    private String getPreferredLanguageCode(){
        String[] language = TranslationHelper.getLanguageByType(DemoConstant.TRANSLATION_TYPE_MESSAGE, "");
        return language[0];
    }

    @Override
    public void onResume() {
        super.onResume();
        isDestroy = false;
    }

    @Override
    public void onPause() {
        super.onPause();
        if(mContext != null && mContext.isFinishing()) {
            isDestroy = true;
        }
    }
}
