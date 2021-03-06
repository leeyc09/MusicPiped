package deep.ryd.rydplayer;

import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.util.ArraySet;
import android.util.Log;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.danikula.videocache.HttpProxyCacheServer;
import com.squareup.picasso.Picasso;
import com.squareup.picasso.Target;

import org.schabi.newpipe.extractor.NewPipe;
import org.schabi.newpipe.extractor.services.youtube.YoutubeService;
import org.schabi.newpipe.extractor.stream.Stream;
import org.schabi.newpipe.extractor.stream.StreamInfo;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

import static java.lang.Math.abs;

public class PlayerService extends Service {

    public MediaPlayer umP;
    public StreamInfo streamInfo;
    public MusicServiceBinder mBinder = new MusicServiceBinder();
    public Activity launch;

    public static PlayerService mainobj;
    public AudioManager audioManager;

    public  int ID=1;
    private String CHANNEL_ID = "player";

    public static final String ACTION_PLAY = "action_play";
    public static final String ACTION_PAUSE = "action_pause";
    public static final String ACTION_REWIND = "action_rewind";
    public static final String ACTION_FAST_FORWARD = "action_fast_foward";
    public static final String ACTION_NEXT = "action_next";
    public static final String ACTION_PREVIOUS = "action_previous";
    public static final String ACTION_STOP = "action_stop";

    public ImageView thumbStore;
    public Bitmap thumbnail;
    public boolean isuMPready=false;
    PendingIntent launchIntent;

    public List<StreamInfo> queue;
    public int currentIndex;

    public DBManager dbManager;
    public MainActivity mainActivity=null;

    public boolean started=false;

    private boolean quitServiceNotif=false;

    public float VOLUME = 1.0f;

    public void control_MP( String action ) {

        if( action.equalsIgnoreCase( ACTION_PLAY ) ) {
            //mediaController.getTransportControls().play();
        } else if( action.equalsIgnoreCase( ACTION_PAUSE ) ) {
            //mediaController.getTransportControls().pause();
        } else if( action.equalsIgnoreCase( ACTION_FAST_FORWARD ) ) {
            //mediaController.getTransportControls().fastForward();
        } else if( action.equalsIgnoreCase( ACTION_REWIND ) ) {
            //mediaController.getTransportControls().rewind();
        } else if( action.equalsIgnoreCase( ACTION_PREVIOUS ) ) {
            //mediaController.getTransportControls().skipToPrevious();
        } else if( action.equalsIgnoreCase( ACTION_NEXT ) ) {
            //mediaController.getTransportControls().skipToNext();
        } else if( action.equalsIgnoreCase( ACTION_STOP ) ) {
            //mediaController.getTransportControls().stop();
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mainobj=this;
        createNotificationChannel();
        Intent intent = new Intent(this, Main2Activity.class);
        intent.putExtra("some data", "txt");

        Random generator = new Random();
        dbManager = new DBManager(this);
        //dbManager = dbManager.open();

        Log.i("ryd"," SERVICE CREATE CALLED ");

        launchIntent=PendingIntent.getActivity(this, generator.nextInt(), intent,PendingIntent.FLAG_UPDATE_CURRENT);
    }

    public void start(){

        buildNotification(ID);
        umP.setOnCompletionListener(
                new MediaPlayer.OnCompletionListener() {
                    @Override
                    public void onCompletion(MediaPlayer mp) {
                        buildNotification(ID);
                        if(getSharedPreferences("InternalSettings",Context.MODE_PRIVATE).getInt("Repeat",0)==2)
                            umP.start();
                        else {
                            nextSong();
                        }
                    }
                }

        );
        if(!started) {
            started=true;
            Timer t = new Timer();
            t.scheduleAtFixedRate(new TimerTask() {
                @Override
                public void run() {
                    if (umP.isPlaying()) {
                        NotificationCompat.Builder builder = notifbuilder();
                        if(builder!=null)
                        NotificationManagerCompat.from(PlayerService.this).notify(ID, builder.build());
                    }
                }
            }, 500, 500);
        }
    }

    public void nextSong(){
        Log.i("ryd","Old Index "+currentIndex);
        Log.i("ryd","Old streamurl "+streamInfo.getAudioStreams().get(0).getUrl());
        if(currentIndex!=queue.size()-1) {
                if(getSharedPreferences("InternalSettings",Context.MODE_PRIVATE).getInt("Shuffle",0)==1)
                    currentIndex=new Random().nextInt()%queue.size();
                else
                    currentIndex++;
                playfromQueue(true);
        }
        else if(getSharedPreferences("InternalSettings",Context.MODE_PRIVATE).getInt("Repeat",0)==1 ||
                (getSharedPreferences("InternalSettings",Context.MODE_PRIVATE).getInt("Repeat",0)==2) ||
                (getSharedPreferences("InternalSettings",Context.MODE_PRIVATE).getInt("Shuffle",0)==1)){
            if(getSharedPreferences("InternalSettings",Context.MODE_PRIVATE).getInt("Shuffle",0)==1)
                currentIndex=abs(new Random().nextInt())%queue.size();
            else
                currentIndex=0;
            playfromQueue(true);
        }
    }
    public void previousSong(){
        if(currentIndex>0){
            currentIndex--;
            playfromQueue(true);
        }
    }

    public  void playfromQueue(final boolean start){
        if( !(queue.size()>0) || !(currentIndex<queue.size())) {
            return;
        }
        else {
            Log.i("ryd", "QUEUE LENGTH " + queue.size());
            streamInfo = queue.get(currentIndex);
            umP.reset();
            isuMPready = false;
            try {
                Picasso.get()
                        .load(streamInfo.getThumbnailUrl())
                        .into(thumbStore);
                start();
                new PlayerService.UpdateSongStream().execute();

                umP.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    @Override
                    public void onPrepared(MediaPlayer mp) {

                        isuMPready = true;
                        umP.setVolume(VOLUME, VOLUME);
                        umP.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        core.updateStreaminDB(streamInfo, dbManager);

                        if (start) {
                            if (mainActivity != null) {
                                mainActivity.coremain.toggle();
                                mainActivity.coremain.setLoadingCircle2(false);
                            } else {
                                umP.start();
                            }
                        }


                    }
                });
                if (mainActivity != null) {
                    mainActivity.coremain.start();
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        //launch=intent.getExtras();
        //startService(new Intent(getApplicationContext(),getClass()));
        return mBinder;
    }

    class MusicServiceBinder extends Binder{

        public PlayerService getPlayerService(){
            return  PlayerService.this;
        }



    }
    @Override
    public void onDestroy() {
        super.onDestroy();
        umP.stop();
        umP.release();
        Log.i("ryd", "SERVICE DESTROYED");
        savelastplaying();
    }
    public static void savelastplaying(){
        SharedPreferences sharedPreferences = mainobj.getSharedPreferences(mainobj.getApplication().getPackageName()+mainobj.getString(R.string.LastPlayedShared),Context.MODE_PRIVATE);

        SharedPreferences.Editor editor = sharedPreferences.edit();
        editor.clear();
        editor.putInt("currentIndex",mainobj.currentIndex);
        Set<String> queueSet = new ArraySet<>();
        for(int i=0;i<mainobj.queue.size();i++){
            queueSet.add(i+" "+mainobj.queue.get(i).getUrl());
        }

        editor.putStringSet("queue",queueSet);
        editor.commit();
    }

    public void CreateToast(String toast){
        Toast.makeText(PlayerService.this, toast, Toast.LENGTH_SHORT).show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {

        if(umP==null) {
            umP = new MediaPlayer();
            Log.i("ryd","NEW UMP CREATED");
        }
        else {
            Log.i("ryd","USING OLD UMP");

        }
        if(thumbStore==null)
            thumbStore=new ImageView(this);
        if(audioManager==null) {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if(getSharedPreferences("Settings",Context.MODE_PRIVATE).getBoolean("respectAudioFocus",true))
            audioManager.requestAudioFocus(new AudioManager.OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    if(focusChange==AudioManager.AUDIOFOCUS_LOSS || focusChange==AudioManager.AUDIOFOCUS_LOSS_TRANSIENT){
                        if(umP.isPlaying()){
                            if(mainActivity!=null){
                                mainActivity.coremain.toggle();
                            }
                            else{
                                if(getSharedPreferences("Settings",Context.MODE_PRIVATE).getBoolean("respectAudioFocus",true)) {
                                    Intent i = new Intent(PlayerService.this, PlayerService.ButtonReceiver.class);
                                    i.putExtra("action", "Pause");
                                    sendBroadcast(i);
                                }
                            }

                        }
                    }
                    else if(focusChange==AudioManager.AUDIOFOCUS_GAIN){
                        if(isuMPready){
                            if(mainActivity!=null){
                                mainActivity.coremain.toggle();
                            }
                            else
                                umP.start();
                        }
                    }
                }
            },AudioManager.STREAM_MUSIC,AudioManager.AUDIOFOCUS_GAIN);
        }


        if (queue==null ) {
            queue = new ArrayList<>();
            SharedPreferences sharedPreferences = getSharedPreferences(getApplication().getPackageName()+getString(R.string.LastPlayedShared),Context.MODE_PRIVATE);
            if(sharedPreferences.contains("currentIndex")){
                currentIndex=sharedPreferences.getInt("currentIndex",0);
                Set<String> queueSet = sharedPreferences.getStringSet("queue",new ArraySet<String>());
                dbManager.open();
                StreamInfo tempstrinfo[] = new StreamInfo[queueSet.size()];
                for (Object object : queueSet){
                    String url = (((String)object).split(" "))[1];

                    StreamInfo streamInfo = dbManager.fetchSong(url);
                    tempstrinfo[Integer.parseInt((((String)object).split(" "))[0])]=streamInfo;
                }
                for(StreamInfo x : tempstrinfo){
                    if(x!=null)
                        queue.add(x);
                    else {
                        currentIndex--;
                    }
                }
                dbManager.close();
                try {
                    streamInfo = queue.get(currentIndex);
                }
                catch (Exception e){
                    streamInfo=null;
                }
            }
        }
        else {
            Log.i("ryd","USING OLD QUEUE");
            Log.i("ryd","QUEUE LENGTH "+queue.size());
        }
        //Toast.makeText(this, "Service Created", Toast.LENGTH_SHORT).show();
        Log.i("ryd","Service Started");
        return super.onStartCommand(intent, flags, startId);

    }


    @Override
    public boolean onUnbind(Intent intent) {
        mainActivity=null;
        Log.i("ryd","Service Unbinded");
        return true;
    }



    public void buildNotification( int id){

        // Given a media session and its context (usually the component containing the session)
// Create a NotificationCompat.Builder

// Get the session's metadata

        NotificationCompat.Builder builder = notifbuilder();


                //.setSubText(description.getDescription()
        if(builder!=null)
            startForeground(id, builder.build());
    }

    public NotificationCompat.Builder notifbuilder(){

        if(quitServiceNotif)
            return null;
        Context context = this;
        Random generator = new Random();

        Intent intent=new Intent(context,ButtonReceiver.class);
        intent.putExtra("action","Close");

        PendingIntent closepIntent = PendingIntent.getBroadcast(context,generator.nextInt(),intent,PendingIntent.FLAG_UPDATE_CURRENT);

        Intent intent1= new Intent(context,ButtonReceiver.class);
        intent.putExtra("action","Pause");
        PendingIntent pauseIntent = PendingIntent.getBroadcast(context,generator.nextInt(),intent,PendingIntent.FLAG_UPDATE_CURRENT);

        Intent intent2= new Intent(context,ButtonReceiver.class);
        intent.putExtra("action","Next");
        PendingIntent nextIntent = PendingIntent.getBroadcast(context,generator.nextInt(),intent,PendingIntent.FLAG_UPDATE_CURRENT);

        Intent intent3= new Intent(context,ButtonReceiver.class);
        intent.putExtra("action","Previous");
        PendingIntent previousIntent = PendingIntent.getBroadcast(context,generator.nextInt(),intent,PendingIntent.FLAG_UPDATE_CURRENT);

        RemoteViews contentView = new RemoteViews(getPackageName(), R.layout.notification_layout);

        contentView.setTextViewText(R.id.notifTitle,streamInfo.getName());
        contentView.setTextColor(R.id.notifTitle,Color.DKGRAY);
        contentView.setTextViewText(R.id.notifText,streamInfo.getUploaderName());
        contentView.setTextColor(R.id.notifText,Color.LTGRAY);
        if(thumbStore!=null && thumbStore.getDrawable()!=null){
            BitmapDrawable bitmapDrawable = (BitmapDrawable) thumbStore.getDrawable();
            thumbnail = bitmapDrawable.getBitmap();
        }
        contentView.setImageViewBitmap(R.id.notifThumb,thumbnail);
        contentView.setImageViewResource(R.id.prevButton,android.R.drawable.ic_media_previous);
        contentView.setImageViewResource(R.id.nextButton,android.R.drawable.ic_media_next);
        contentView.setImageViewResource(R.id.closeButton,android.R.drawable.ic_menu_close_clear_cancel);
        contentView.setTextColor(R.id.notifTimer,Color.LTGRAY);

        contentView.setOnClickPendingIntent(R.id.play_pause_notif,pauseIntent);
        contentView.setOnClickPendingIntent(R.id.closeButton,closepIntent);
        contentView.setOnClickPendingIntent(R.id.nextButton,nextIntent);
        contentView.setOnClickPendingIntent(R.id.prevButton,previousIntent);

        if(isuMPready){
            contentView.setTextViewText(R.id.notifTimer,core.sectotime(umP.getCurrentPosition(),true)+"/"+core.sectotime(umP.getDuration(),true));

            contentView.setProgressBar(R.id.notifProgress, (int) umP.getDuration(), umP.getCurrentPosition(), false);
        }
        else {
            contentView.setTextViewText(R.id.notifTimer,"0:00"+"/"+"0:00");

            contentView.setProgressBar(R.id.notifProgress, 0, 0, true);
        }

        if(umP.isPlaying()) {
            contentView.setImageViewResource(R.id.play_pause_notif, android.R.drawable.ic_media_pause);
        }
        else {
            contentView.setImageViewResource(R.id.play_pause_notif, android.R.drawable.ic_media_play);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID);

        builder
                .setSmallIcon(R.drawable.ic_music_note_white_24dp)
                // Add the metadata for the currently playing track
                //.setContentTitle(streamInfo.getName())
                //.setContentText(streamInfo.getUploaderName())
                //.setStyle(new NotificationCompat.DecoratedCustomViewStyle())
                .setContentIntent(launchIntent)
                .setCustomContentView(contentView)
                .setCustomBigContentView(contentView)
                .setOnlyAlertOnce(true);

        return builder;
    }

    public void createNotificationChannel(){
        String NOTIFICATION_CHANNEL_ID = CHANNEL_ID;
        String channelName = "My Background Service";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel chan = new NotificationChannel(NOTIFICATION_CHANNEL_ID, channelName, NotificationManager.IMPORTANCE_NONE);
            chan.setLightColor(Color.BLUE);
            chan.setLockscreenVisibility(Notification.VISIBILITY_PRIVATE);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            assert manager != null;
            manager.createNotificationChannel(chan);
        }
    }

    public static class ButtonReceiver extends BroadcastReceiver{

        public ButtonReceiver(){
            super();
        }

        @Override
        public void onReceive(Context context, Intent intent) {

            Log.i("ryd",intent.getExtras().toString());

            String action = intent.getStringExtra("action");
            Log.i("ryd","ACTION "+action);
            if(action.equals("Close")){
                mainobj.quitServiceNotif=true;

                Intent intent1 = new Intent();
                intent1.putExtra("action","Close");
                intent1.setAction(MainActivity.MAINACTIVITYTBROADCASTACTION);
                mainobj.sendBroadcast(intent1);

                mainobj.stopForeground(true);
                mainobj.stopSelf();
                mainobj.stopForeground(true);
            }
            else if (action.equals("Pause")) {
                if(mainobj.umP.isPlaying()) {
                    mainobj.umP.pause();
                    mainobj.buildNotification(mainobj.ID);
                    mainobj.stopForeground(false);
                }
                else {
                    mainobj.umP.start();
                    mainobj.buildNotification(mainobj.ID);
                }
            }
            else if(action.equals("Next")){
                mainobj.nextSong();
            }
            else if(action.equals("Previous")){
                mainobj.previousSong();
            }

        }
    }



    public static  class CrunchifyGetPingStatus {


        public static String getStatus(String url) throws IOException {

            String result = "";
            int code = 200;
            try {
                URL siteURL = new URL(url);
                HttpURLConnection connection = (HttpURLConnection) siteURL.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(3000);
                connection.connect();

                code = connection.getResponseCode();
                if (code == 200) {
                    result = "-> Green <-\t" + "Code: " + code;
                    ;
                } else {
                    result = "Error";
                }
            } catch (Exception e) {
                //result = "-> Red <-\t" + "Wrong domain - Exception: " + e.getMessage();
                result = "Error";

            }
            System.out.println(url + "\t\tStatus:" + result);

            return result;
        }

    }

    public void  addtoqueue(String url){
        AddtoQueue addtoQueue = new AddtoQueue();
        addtoQueue.execute(url);
    }
    public void  addtoqueue(StreamInfo streamInfo){
        queue.add(streamInfo);
        if(mainActivity!=null){
            mainActivity.coremain.queueListAdaptor.updateQueue(queue);
        }
    }

    static class UpdateSongStream extends AsyncTask<String,String,String>{


        @Override
        protected String doInBackground(String... strings) {
            StreamInfo streamInfo = PlayerService.mainobj.streamInfo;
            String ping_status = null;
            HttpProxyCacheServer proxyCacheServer = ProxyFactory.getProxy(PlayerService.mainobj);
            if(proxyCacheServer.isCached(streamInfo.getAudioStreams().get(0).getUrl())){
                Log.i("ryd","Already cached");
                return "Using cached";
            }
            try {
                ping_status = CrunchifyGetPingStatus.getStatus(streamInfo.getAudioStreams().get(0).getUrl());
            } catch (IOException e) {
                e.printStackTrace();
            }
            if(ping_status!="Error"){
                Log.i("ryd","Using Old Stream");
                return "Using Old stream";
            }
            Log.i("ryd","OldStream Outdated Updating ");
            try{

                if(PlayerService.mainobj.mainActivity!=null)
                    PlayerService.mainobj.mainActivity.coremain.setLoadingCircle1(true);
                Downloader.init(null);
                NewPipe.init(Downloader.getInstance());
                int sid = NewPipe.getIdOfService("YouTube");
                YoutubeService ys= (YoutubeService)NewPipe.getService(sid);


                streamInfo= StreamInfo.getInfo(ys,streamInfo.getUrl());
                PlayerService.mainobj.streamInfo=streamInfo;

                Log.i("ryd","New Stream link received "+streamInfo.getAudioStreams().get(0).getUrl());
            }
            catch (Exception e){
                e.printStackTrace();
            }

            return "Done";
        }

        @Override
        protected void onPostExecute(String string){
            try {

                if(PlayerService.mainobj.mainActivity!=null) {
                    PlayerService.mainobj.mainActivity.coremain.setLoadingCircle1(false);
                    PlayerService.mainobj.mainActivity.coremain.setLoadingCircle2(true);
                }
                PlayerService.mainobj.umP.reset();
                PlayerService.mainobj.isuMPready=false;
                HttpProxyCacheServer proxy  = ProxyFactory.getProxy(PlayerService.mainobj);
                String proxiedurl  = proxy.getProxyUrl(PlayerService.mainobj.streamInfo.getAudioStreams().get(0).url);
                PlayerService.mainobj.umP.setDataSource(proxiedurl);
                PlayerService.mainobj.umP.prepareAsync();
                PlayerService.mainobj.umP.setOnErrorListener(new MediaPlayer.OnErrorListener() {
                    @Override
                    public boolean onError(MediaPlayer mp, int what, int extra) {
                        Log.i("ryd","ERROR LISTENER ENVOKED ");
                        return false;
                    }
                });
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
    class AddtoQueue extends AsyncTask<String,String,String>{

        StreamInfo streamInfo;

        @Override
        protected String doInBackground(String... strings) {

            try{
                streamInfo = dbManager.open().fetchSong(strings[0]);
                dbManager.close();

                if(streamInfo!=null)
                    return "Done";

                if(PlayerService.this.mainActivity!=null)
                    PlayerService.this.mainActivity.coremain.setLoadingCircle1(true);
                Downloader.init(null);
                NewPipe.init(Downloader.getInstance());
                int sid = NewPipe.getIdOfService("YouTube");
                YoutubeService ys= (YoutubeService)NewPipe.getService(sid);

                streamInfo= StreamInfo.getInfo(ys,strings[0]);

                dbManager.open();
                dbManager.addSong(streamInfo.getName(),streamInfo.getUrl(),streamInfo.getUploaderName(),streamInfo.getThumbnailUrl(),streamInfo.getUploaderAvatarUrl(),streamInfo.getUploaderUrl(),streamInfo.getAudioStreams().get(0).getUrl());
                Log.i("ryd","New Stream link received "+streamInfo.getAudioStreams().get(0).getUrl());
            }
            catch (Exception e){
                e.printStackTrace();
            }

            return "Done";
        }

        @Override
        protected void onPostExecute(String string){
            try {

                PlayerService.this.queue.add(streamInfo);
                if(mainActivity!=null){
                    mainActivity.coremain.setLoadingCircle1(false);
                    mainActivity.coremain.queueListAdaptor.updateQueue(mainobj.queue);
                }

            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private File getTempFile(Context context, String fname) {
        File file = null;
        try {
            String fileName = fname;
            file = File.createTempFile(fileName, null, context.getCacheDir());
        } catch (IOException e) {
            // Error while creating file
        }
        return file;
    }
    public static String convertStreamToString(InputStream is) throws Exception {
        BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        StringBuilder sb = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            sb.append(line).append("\n");
        }
        reader.close();
        return sb.toString();
    }

    public static String getStringFromFile (String filename) throws Exception {
        FileInputStream fin = mainobj.openFileInput(filename);
        String ret = convertStreamToString(fin);
        //Make sure you close all streams.
        fin.close();
        return ret;
    }
}


