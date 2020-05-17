package com.cpnir.servicebestpractice;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Environment;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

/**
 * AsyncTask
 * String 表示在执行AsyncTask的时候需要传入一个字符串参数给后台任务
 * Integer 表示使用整形数据来作为进度显示单位
 * Integer 表示使用整形数据来反馈执行结果
 */
public class DownLoadTask extends AsyncTask<String, Integer ,Integer> {

    public static final int TYPE_SUCCESS = 0;
    public static final int TYPE_FAILED = 1;
    public static final int TYPE_PAUSED = 2;
    public static final int TYPE_CANCELED = 3;

    public static final int TYPE_FAILED_URL_NULL = 10;
    public static final int TYPE_FAILED_FILENAME_NULL = 11;
    public static final int TYPE_FAILED_IS_NEW = 12;
    public static final int TYPE_FAILED_FILE_ERROR = 13;

    private DownLoadListener listener;
    private boolean isCanceled = false;
    private boolean isPaused = false;
    private int lastProgress;
    private String fileName = null;

    public DownLoadTask(DownLoadListener listener){
        this.listener = listener;
    }
    @Override
    protected Integer doInBackground(String... params) {
        String downloadUrl = params[0];
        String url = null;
        try {
            url = getUrl(downloadUrl);
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (url != null) {
            InputStream is = null;
            RandomAccessFile saveFile = null;
            File file = null;
            try {
                long downloadedLength = 0;//记录已下载文件长度
                fileName = url.substring(url.lastIndexOf("/"));
                //可以指定别的路径
                String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
                if (fileName.isEmpty()){
                    return TYPE_FAILED_FILENAME_NULL;
                }else {
                    if (compareVersion(getVersion(fileName),packageName(MyApplication.getInstance())) != 1){
                        //APP 不需要更新
                        return TYPE_FAILED_IS_NEW;
                    }
                    File fileList = new File(directory + "/");
                    List<File> f = getFileList(fileList.listFiles());
                    int i = 0;
                    while (i < f.size()) {
                        File apkFile = f.get(i);
                        if (fileName.contains(apkFile.getName())){
                            return TYPE_FAILED_IS_NEW;
                        }else if (apkFile.getName().contains(".part")){
                            if (compareVersion(getVersion(fileName),packageName(MyApplication.getInstance())) == 1){
                                apkFile.delete();
                            }
                        }else {
                            apkFile.delete();
                        }
                        i ++;
                    }
                    fileName = fileName.substring(0,fileName.length()-3);//去掉apk
                    fileName = fileName + "part";//加上part
                }
                file = new File(directory + fileName);
                if (file.exists()) {
                    downloadedLength = file.length();
                }
                long contentLength = getContentLength(url);
                if (contentLength == 0) {
                    return TYPE_FAILED_FILE_ERROR;
                } else if (contentLength == downloadedLength) {
                    return TYPE_SUCCESS;
                    //已下载字节和文件总字节相等，说明已经下载完成了
                }
                OkHttpClient client = new OkHttpClient.Builder()
                        .connectTimeout(5,TimeUnit.SECONDS)
                        .readTimeout(5,TimeUnit.SECONDS)
                        .build();
                Request request = new Request.Builder()
                        .addHeader("RANGE", "bytes=" + downloadedLength + "-")
                        .url(url)
                        .build();
                //断点下载，指定从哪个字节开始下载
                Response response = client.newCall(request).execute();
                if (response.body() != null) {

                    is = response.body().byteStream();
                    saveFile = new RandomAccessFile(file, "rw");
                    saveFile.seek(downloadedLength);//跳过已下载的字节
                    byte[] b = new byte[1024];
                    int total = 0, len;
                    while ((len = is.read(b)) != -1) {
                        if (isCanceled) {
                            return TYPE_CANCELED;
                        } else if (isPaused) {
                            return TYPE_PAUSED;
                        } else {
                            total += len;
                            saveFile.write(b, 0, len);
                            int progress = (int) ((total + downloadedLength) * 100 / contentLength);
                            publishProgress(progress);
                        }
                    }
                    response.body().close();
                    fileName = fileName.substring(0,fileName.length()-4);//去掉part
                    fileName = fileName + "apk";//加上apk
                    File newFile = new File(directory + fileName);
                    if (file.renameTo(newFile)) {
                        return TYPE_SUCCESS;
                    }else {
                        return TYPE_FAILED;
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                try {
                    if (is != null) {
                        is.close();
                    }
                    if (saveFile != null) {
                        saveFile.close();
                    }
                    if (isCanceled && file != null) {
                        file.delete();
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }else
        {
            return TYPE_FAILED_URL_NULL;
        }
        return TYPE_FAILED;
    }

    @Override
    protected void onPostExecute(Integer status) {
        switch (status){
            case TYPE_SUCCESS:
                listener.onSuccess();
                break;
            case TYPE_FAILED:
                listener.onFailed(TYPE_FAILED);
                break;
            case TYPE_PAUSED:
                listener.onPaused();
                break;
            case TYPE_CANCELED:
                listener.onCanceled();
                break;
            case TYPE_FAILED_URL_NULL:
                listener.onFailed(TYPE_FAILED_URL_NULL);
                break;
            case TYPE_FAILED_FILE_ERROR:
                listener.onFailed(TYPE_FAILED_FILE_ERROR);
                break;
            case TYPE_FAILED_FILENAME_NULL:
                listener.onFailed(TYPE_FAILED_FILENAME_NULL);
            case TYPE_FAILED_IS_NEW:
                listener.onFailed(TYPE_FAILED_IS_NEW);
                break;
            default:
                break;
        }
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
        int progress = values[0];
        if (progress > lastProgress){
            listener.onProgress(progress);
            lastProgress = progress;
        }
    }

    public void pauseDownload(){
        isPaused = true;
    }

    public void cancelDownload(){
        if (fileName != null){
            String directory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS).getPath();
            //ArcFaceGo应该改为指定路径
            File file = new File(directory + fileName);
            if (file.exists()){
                file.delete();
            }
        }
        isCanceled = true;
    }

    private long getContentLength(String downloadUrl) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new  Request.Builder()
                .url(downloadUrl)
                //.url("http://192.168.1.150:20081/contentLength")
                .build();
        Response response = client.newCall(request).execute();
        if (response.body() != null && response.isSuccessful()){
            //long contentLength = Long.parseLong(response.body().string());
            long contentLength = response.body().contentLength();
            response.body().close();
            return contentLength;
        }
        return 0;
    }

    private String getUrl(String downloadUrl) throws IOException {
        OkHttpClient client = new OkHttpClient();
        Request request = new  Request.Builder()
                .url(downloadUrl)
                .build();
        Response response = client.newCall(request).execute();
        if (response.body() != null && response.isSuccessful()){
            String url = response.body().string();
            response.body().close();
            return url;
        }
        return null;
    }

    private String getVersion(String name) {
        int index=name.indexOf('_');
        if (index==-1) return "";
        int index2= name.indexOf('_',index+1);
        if (index2 == -1){
            return name.substring(index+1);
        }else if (index2>index+1)
            return name.substring(index+2,index2);
        else
            return "";
    }

    private String packageName(Context context) {
        PackageManager manager = context.getPackageManager();
        String name = null;
        try {
            PackageInfo info = manager.getPackageInfo(context.getPackageName(), 0);
            name = info.versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        return name;
    }

    public int compareVersion(String v1,String v2){
        int i=0,j=0,x=0,y=0;
        int v1Len=v1.length();
        int v2Len=v2.length();
        char c;
        do {
            while(i<v1Len){//计算出V1中的点之前的数字
                c=v1.charAt(i++);
                if(c>='0' && c<='9'){
                    x=x*10+(c-'0');//c-‘0’表示两者的ASCLL差值
                }else if(c=='.'){
                    break;//结束
                }else{
                    //无效的字符
                }
            }
            while(j<v2Len){//计算出V2中的点之前的数字
                c=v2.charAt(j++);
                if(c>='0' && c<='9'){
                    y=y*10+(c-'0');
                }else if(c=='.'){
                    break;//结束
                }else{
                    //无效的字符
                }
            }
            if(x<y){
                return -1;
            }else if(x>y){
                return 1;
            }else{
                x=0;y=0;
                continue;
            }

        } while ((i<v1Len) || (j<v2Len));
        return 0;
    }

    /**
     * 遍历获取该文件夹下所有文件
     * @param oriFile
     * @return
     */
    public List<File> getFileList(File[] oriFile) {
        List<File> fileList = new ArrayList<>();
        for (File file : oriFile) {
            if (file.isDirectory()) {
                File[] childFileArr = file.listFiles();
                Collections.addAll(fileList, childFileArr);
            } else if (file.isFile()) {
                Collections.addAll(fileList, file);
            }
        }
        return fileList;
    }
}
