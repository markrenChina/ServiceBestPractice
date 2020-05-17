package com.cpnir.servicebestpractice;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.Environment;
import android.os.IBinder;
import android.widget.Switch;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
//import android.support.v4.app.NotificationCompat;

import java.io.File;

public class DownLoadService extends Service {

    private DownLoadTask downLoadTask;

    private String downloadUrl;

    @Override
    public void onCreate() {
        super.onCreate();
        startForeground(1,getNotification("Waiting...",-1));
    }

    private DownLoadListener listener = new DownLoadListener() {
        @Override
        public void onProgress(int progress) {
            getNotificationManager().notify(1,getNotification("DownLoading...",progress));
        }

        @Override
        public void onSuccess() {
            downLoadTask = null;
            //下载成功时将前台服务通知关闭，并创建一个下载成功的通知
            stopForeground(true);
            getNotificationManager().notify(1,getNotification("Download Success", -1));
            Toast.makeText(DownLoadService.this,"DownLoad Success",Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onFailed(int cause) {
            downLoadTask = null;
            //下载失败时将前台服务通知关闭，并创建一个下载失败的通知
            stopForeground(true);
            String tip = "Download Failed";
            switch (cause) {
                case DownLoadTask.TYPE_FAILED_IS_NEW:
                    tip = "不需要更新";
                    break;
                case DownLoadTask.TYPE_FAILED:
                    tip = "Download Failed";
                    break;
                case DownLoadTask.TYPE_FAILED_URL_NULL:
                    tip = "失败，无法获取更新地址";
                    break;
                case DownLoadTask.TYPE_FAILED_FILE_ERROR:
                    tip = "失败，资源错误";
                    break;
                case DownLoadTask.TYPE_FAILED_FILENAME_NULL:
                    tip = "失败，获取不到文件名";
                    break;
                default:
                    break;
            }
            getNotificationManager().notify(1, getNotification(tip, -1));
            Toast.makeText(DownLoadService.this, tip, Toast.LENGTH_SHORT).show();
            if (cause == DownLoadTask.TYPE_FAILED_IS_NEW) {
                stopSelf();
            }else {
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            Thread.sleep(3600000);
                            //Thread.sleep(5000);
                            downLoadTask = new DownLoadTask(listener);
                            downLoadTask.execute(downloadUrl);
                            startForeground(1,getNotification("Downloading...",0));
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }).start();
            }
        }

        @Override
        public void onPaused() {
            downLoadTask = null;
            Toast.makeText(DownLoadService.this,"Download paused",Toast.LENGTH_SHORT).show();
        }

        @Override
        public void onCanceled() {
            downLoadTask = null;
            stopForeground(true);
            Toast.makeText(DownLoadService.this,"Download Canceled",Toast.LENGTH_SHORT).show();
        }
    };

    private DownloadBinder mBinder = new DownloadBinder();

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    /**
     * 服务与活动绑定
     */
    public class DownloadBinder extends Binder{
        public void startDownload(String url){
            if (downLoadTask == null){
                downloadUrl = url;
                downLoadTask = new DownLoadTask(listener);
                downLoadTask.execute(downloadUrl);
                startForeground(1,getNotification("Downloading...",0));
                //在系统状态栏创建一个持续运行的通知
                Toast.makeText(DownLoadService.this,"Download...",Toast.LENGTH_SHORT).show();
            }
        }

        public void pauseDownload(){
            if (downLoadTask != null){
                downLoadTask.pauseDownload();
            }
        }

        public void cancelDownload(){
            if (downLoadTask != null){
                downLoadTask.cancelDownload();
            }
            if (downloadUrl != null){
                /*//取消下载时需将文件删除，并关闭通知
                //String fileName = downloadUrl.substring(downloadUrl.lastIndexOf("/"));
                String fileName = "/test.apk";
                */
                getNotificationManager().cancel(1);
                stopForeground(true);
                Toast.makeText(DownLoadService.this,"cancel",Toast.LENGTH_SHORT).show();
            }
        }
    }

    private NotificationManager getNotificationManager(){
        return (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
    }

    /**
     * 构建一个用于显示下载进度的通知
     * @param title
     * @param progress
     * @return
     */
    private Notification getNotification(String title,int progress){
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this,0,intent,0);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            NotificationChannel notificationChannel = new NotificationChannel("channelid1","channelname",NotificationManager.IMPORTANCE_HIGH);
            getNotificationManager().createNotificationChannel(notificationChannel);
        }
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this,"channelid1");
        builder.setSmallIcon(R.mipmap.ic_launcher);
        builder.setLargeIcon(BitmapFactory.decodeResource(getResources(),R.mipmap.ic_launcher));
        builder.setContentIntent(pi);
        builder.setContentTitle(title);
        if (progress >= 0 ){
            builder.setContentText(progress + "%");
            builder.setProgress(100,progress,false);
            //setProgress，第一个参数 通知的最大进度，第二个参数 当前进度 第三个参数表示是否使用模糊进度条
        }
        return builder.build();
    }
}
