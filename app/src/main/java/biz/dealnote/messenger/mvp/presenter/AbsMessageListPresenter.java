package biz.dealnote.messenger.mvp.presenter;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

import androidx.annotation.CallSuper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import biz.dealnote.messenger.Injection;
import biz.dealnote.messenger.R;
import biz.dealnote.messenger.media.voice.IVoicePlayer;
import biz.dealnote.messenger.media.voice.PrepareException;
import biz.dealnote.messenger.model.LastReadId;
import biz.dealnote.messenger.model.Message;
import biz.dealnote.messenger.model.VoiceMessage;
import biz.dealnote.messenger.mvp.presenter.base.PlaceSupportPresenter;
import biz.dealnote.messenger.mvp.view.IBasicMessageListView;
import biz.dealnote.messenger.util.Lookup;
import biz.dealnote.messenger.util.Optional;
import biz.dealnote.messenger.util.Utils;
import biz.dealnote.mvp.reflect.OnGuiCreated;

import static biz.dealnote.messenger.util.Utils.countOfSelection;
import static biz.dealnote.messenger.util.Utils.getSelected;
import static biz.dealnote.messenger.util.Utils.safeIsEmpty;

/**
 * Created by ruslan.kolbasa on 03.10.2016.
 * phoenix
 */
public abstract class AbsMessageListPresenter<V extends IBasicMessageListView> extends
        PlaceSupportPresenter<V> implements IVoicePlayer.IPlayerStatusListener {

    private final ArrayList<Message> mData;
    private IVoicePlayer mVoicePlayer;
    private Lookup mVoiceMessageLookup;

    AbsMessageListPresenter(int accountId, @Nullable Bundle savedInstanceState) {
        super(accountId, savedInstanceState);

        mData = new ArrayList<>();

        mVoicePlayer = Injection.provideVoicePlayerFactory().createPlayer();
        mVoicePlayer.setCallback(this);
        mVoiceMessageLookup = new Lookup(500);
        mVoiceMessageLookup.setCallback(() -> resolveVoiceMessagePlayingState(true));
    }

    @OnGuiCreated
    private void syncVoiceLookupState(){
        boolean needLookup = mVoicePlayer.isSupposedToPlay() && isGuiReady();

        if(needLookup){
            mVoiceMessageLookup.start();
        } else {
            mVoiceMessageLookup.stop();
        }
    }

    protected final LastReadId lastReadId = new LastReadId(0, 0);

    @OnGuiCreated
    public void resolveListView(){
        if(isGuiReady()) {
            getView().displayMessages(mData, lastReadId);
        }
    }

    @NonNull
    public ArrayList<Message> getData() {
        return mData;
    }

    protected int indexOf(int messageId){
        return Utils.indexOf(mData, messageId);
    }

    @Nullable
    protected Message findById(int messageId){
        return Utils.findById(mData, messageId);
    }

    protected boolean clearSelection(){
        boolean hasChanges = false;
        for(Message message : mData){
            if(message.isSelected()){
                message.setSelected(false);
                hasChanges = true;
            }
        }

        return hasChanges;
    }

    @OnGuiCreated
    protected void resolveActionMode() {
        if (!isGuiReady()) return;

        int selectionCount = countOfSelection(getData());
        if (selectionCount > 0) {
            getView().showActionMode(String.valueOf(selectionCount), selectionCount == 1, selectionCount == 1);
        } else {
            getView().finishActionMode();
        }
    }

    protected void safeNotifyDataChanged() {
        if (isGuiReady()) {
            getView().notifyDataChanged();
        }
    }

    public void fireMessageLongClick(Message message) {
        message.setSelected(!message.isSelected());
        resolveActionMode();
        safeNotifyDataChanged();
    }

    public void fireMessageClick(@NonNull Message message) {
        int actionModeActive = countOfSelection(getData());

        if (actionModeActive > 0) {
            message.setSelected(!message.isSelected());
            resolveActionMode();
            safeNotifyDataChanged();
        } else {
            onMessageClick(message);
        }
    }

    protected void onMessageClick(@NonNull Message message){

    }

    public final void fireActionModeDestroy(){
        onActionModeDestroy();
    }

    @CallSuper
    protected void onActionModeDestroy(){
        if (clearSelection()) {
            safeNotifyDataChanged();
        }
    }

    public final void fireActionModeDeleteClick() {
        onActionModeDeleteClick();
    }

    protected void onActionModeDeleteClick(){

    }

    public final void fireActionModeCopyClick(){
        onActionModeCopyClick();
    }

    protected void onActionModeCopyClick(){
        List<Message> selected = getSelected(getData(), true);
        if (safeIsEmpty(selected)) return;

        String result = "";
        boolean firstTime = true;
        for (Message message : selected) {
            String body = TextUtils.isEmpty(message.getDecryptedBody()) ? message.getBody() : message.getDecryptedBody();
            result = result + (!firstTime ? "\n" : "") + body;
            firstTime = false;
        }

        ClipboardManager clipboard = (ClipboardManager) getApplicationContext()
                .getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clip = ClipData.newPlainText("messages", result);
        clipboard.setPrimaryClip(clip);

        safeShowLongToast(getView(), R.string.copied_to_clipboard);
    }

    public final void fireForwardClick(){
        onActionModeForwardClick();
    }

    protected void onActionModeForwardClick(){

    }

    public void fireVoicePlayButtonClick(int voiceHolderId, int voiceMessageId, @NonNull VoiceMessage voiceMessage){
        try {
            boolean messageChanged = mVoicePlayer.toggle(voiceMessageId, voiceMessage);
            if(messageChanged){
                resolveVoiceMessagePlayingState();
            } else {
                boolean paused = !mVoicePlayer.isSupposedToPlay();
                float progress = mVoicePlayer.getProgress();
                getView().bindVoiceHolderById(voiceHolderId, true, paused, progress, false);
            }
        } catch (PrepareException ignored) {

        }

        syncVoiceLookupState();
    }

    private void resolveVoiceMessagePlayingState(boolean anim){
        if(isGuiReady()){
            Optional<Integer> optionalVoiceMessageId = mVoicePlayer.getPlayingVoiceId();

            if(optionalVoiceMessageId.isEmpty()){
                getView().disableVoicePlaying();
            } else {
                float progress = mVoicePlayer.getProgress();
                boolean paused = !mVoicePlayer.isSupposedToPlay();

                getView().configNowVoiceMessagePlaying(optionalVoiceMessageId.get(), progress, paused, anim);
            }
        }
    }

    private void resolveVoiceMessagePlayingState(){
        resolveVoiceMessagePlayingState(false);
    }

    @Override
    public void onGuiDestroyed() {
        syncVoiceLookupState();
        super.onGuiDestroyed();
    }

    public void fireVoiceHolderCreated(int voiceMessageId, int voiceHolderId) {
        Optional<Integer> currentVoiceId = mVoicePlayer.getPlayingVoiceId();
        boolean play = currentVoiceId.nonEmpty() && currentVoiceId.get() == voiceMessageId;
        boolean paused = play && !mVoicePlayer.isSupposedToPlay();

        getView().bindVoiceHolderById(voiceHolderId, play, paused, mVoicePlayer.getProgress(), false);
    }

    @Override
    public void onPlayerStatusChange(int status) {
        //Optional<Integer> voiceMessageId = mVoicePlayer.getPlayingVoiceId();
    }

    @Override
    public void onDestroyed() {
        super.onDestroyed();
        mVoicePlayer.setCallback(null);
        mVoicePlayer.release();
        mVoicePlayer = null;

        mVoiceMessageLookup.stop();
        mVoiceMessageLookup.setCallback(null);
        mVoiceMessageLookup = null;
    }
}