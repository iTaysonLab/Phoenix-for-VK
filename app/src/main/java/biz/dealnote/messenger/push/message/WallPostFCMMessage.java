package biz.dealnote.messenger.push.message;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;

import com.google.firebase.messaging.RemoteMessage;

import java.util.Map;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import biz.dealnote.messenger.Extra;
import biz.dealnote.messenger.R;
import biz.dealnote.messenger.activity.MainActivity;
import biz.dealnote.messenger.link.VkLinkParser;
import biz.dealnote.messenger.link.types.AbsLink;
import biz.dealnote.messenger.link.types.WallPostLink;
import biz.dealnote.messenger.longpoll.AppNotificationChannels;
import biz.dealnote.messenger.longpoll.NotificationHelper;
import biz.dealnote.messenger.model.Owner;
import biz.dealnote.messenger.place.PlaceFactory;
import biz.dealnote.messenger.push.NotificationScheduler;
import biz.dealnote.messenger.push.OwnerInfo;
import biz.dealnote.messenger.settings.Settings;
import biz.dealnote.messenger.util.PersistentLogger;
import biz.dealnote.messenger.util.Utils;

import static biz.dealnote.messenger.push.NotificationUtils.configOtherPushNotification;

public class WallPostFCMMessage {

    private static final String TAG = WallPostFCMMessage.class.getSimpleName();

    //from_id=175895893, first_name=Руслан, from=376771982493, text=Тест push-уведомлений, type=wall_post, place=wall25651989_2509, collapse_key=wall_post, last_name=Колбаса

    private int from_id;
    //public String first_name;
    //public String last_name;
    //public long from;
    private String text;
    //public String type;
    private String place;

    public static WallPostFCMMessage fromRemoteMessage(@NonNull RemoteMessage remote) {
        WallPostFCMMessage message = new WallPostFCMMessage();
        Map<String, String> data = remote.getData();
        message.from_id = Integer.parseInt(remote.getData().get("from_id"));
        //message.first_name = bundle.getString("first_name");
        //message.last_name = bundle.getString("last_name");

        //if(bundle.containsKey("from")){
        //    message.from = Long.parseLong(bundle.getString("from"));
        //}

        message.text = data.get("text");
        //message.type = bundle.getString("type");
        message.place = data.get("place");
        return message;
    }

    public void nofify(final Context context, int accountId){
        if (!Settings.get()
                .notifications()
                .isNewPostOnOwnWallNotifEnabled()) {
            return;
        }

        Context app = context.getApplicationContext();
        OwnerInfo.getRx(app, accountId, from_id)
                .subscribeOn(NotificationScheduler.INSTANCE)
                .subscribe(ownerInfo -> notifyImpl(app, ownerInfo.getOwner(), ownerInfo.getAvatar()), throwable -> {/*ignore*/});
    }

    private void notifyImpl(Context context, @NonNull Owner owner, Bitmap avatar){
        String url = "vk.com/" + place;

        AbsLink link = VkLinkParser.parse(url);
        if(link == null || !(link instanceof WallPostLink)){
            PersistentLogger.logThrowable("Push issues", new Exception("Unknown place: " + place));
            return;
        }

        WallPostLink wallPostLink = (WallPostLink) link;

        final NotificationManager nManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (Utils.hasOreo()){
            nManager.createNotificationChannel(AppNotificationChannels.getNewPostChannel(context));
        }

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, AppNotificationChannels.NEW_POST_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notify_statusbar)
                .setLargeIcon(avatar)
                .setContentTitle(owner.getFullName())
                .setContentText(context.getString(R.string.published_post_on_your_wall))
                .setSubText(text)
                .setAutoCancel(true);

        builder.setPriority(NotificationCompat.PRIORITY_HIGH);

        int aid = Settings.get()
                .accounts()
                .getCurrent();

        Intent intent = new Intent(context, MainActivity.class);
        intent.putExtra(Extra.PLACE, PlaceFactory.getPostPreviewPlace(aid, wallPostLink.postId, wallPostLink.ownerId));
        intent.setAction(MainActivity.ACTION_OPEN_PLACE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        PendingIntent contentIntent = PendingIntent.getActivity(context, wallPostLink.postId, intent, PendingIntent.FLAG_CANCEL_CURRENT);
        builder.setContentIntent(contentIntent);
        Notification notification = builder.build();

        configOtherPushNotification(notification);
        nManager.notify(place, NotificationHelper.NOTIFICATION_WALL_POST_ID, notification);
    }
}
