package myfirstapp.mobipeer.com.myfirstapp;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.SurfaceTexture;
import android.media.MediaPlayer;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.Semaphore;

import myfirstapp.mobipeer.com.myfirstapp.gl.SurfaceView;
import myfirstapp.mobipeer.com.myfirstapp.hw.EncoderDebugger;
import myfirstapp.mobipeer.com.myfirstapp.rtsp.RtspClient;
import myfirstapp.mobipeer.com.myfirstapp.rtsp.RtspServer;
import myfirstapp.mobipeer.com.myfirstapp.video.VideoQuality;

import static java.lang.Thread.sleep;
import static myfirstapp.mobipeer.com.myfirstapp.MainActivity.ParentIP;
import static myfirstapp.mobipeer.com.myfirstapp.MainActivity.TAG;
import static myfirstapp.mobipeer.com.myfirstapp.MainActivity.myconf;
import static myfirstapp.mobipeer.com.myfirstapp.MainActivity.sendheartbeatasdestination;
import static myfirstapp.mobipeer.com.myfirstapp.MainActivity.sendheartbeatassource;
import static myfirstapp.mobipeer.com.myfirstapp.listeningThread.rejoin;

public class MainActivity extends Activity implements RtspClient.Callback, Session.Callback, TextureView.SurfaceTextureListener, MediaPlayer.OnPreparedListener, SurfaceHolder.Callback, View.OnClickListener {

    public static String TAG = "MAIN ACTIVITY";
    public static String SourceIP;     // IP Address of the main source of live stream
    public static String ParentIP;     // IP Address of the source in the tree.
    public static String LeftchildIP;  // IP Address of the left child in the tree.
    public static String RightchildIP; // IP Address of the right child in the tree.
    public static String LeaderIP;     // IP Address of the leader of the tree.
    public static Integer leftdepth=-1;   // Depth of the tree subrooted at left child.
    public static Integer rightdepth=-1;  // Depth of the tree subrooted at right child.
    public static Integer depth;       // Depth of the tree subrooted at this node.
    public static int port = 14541;
    public static AlertDialog.Builder builder; // Alert Dialog object for errors
    public static Handler warningHandler;
    public static Boolean joined = false;
    public static boolean iamsource = false;
    public static boolean iamleader = false;
    public static MainActivity mainobj = null;
    public static Map<Configuration, String> leaderlist;
    public static Configuration myconf;
    public static boolean inScheduling = false;
    public static String MyIP = "";
    public static int leftbattery = -1;
    public static int rightbattery = -1;
    public static String electedIP = "";
    public static String swapIP = "";
    public static boolean faultswap = false;
    public static String leadererrorrecoveryIP = "";
    public static SurfaceView mSurfaceView;
    public static Session mSession;
    public static MediaPlayer mMediaPlayer;
    public static Semaphore joingate = new Semaphore(1);
    public static ServerSocket listeningSocket;
    public static Client rtspclient;
    public static boolean duplicatestream = false;

    public MainActivity() {
        SourceIP = "";
        ParentIP = "";
        LeftchildIP = "";
        RightchildIP = "";
        LeaderIP = "";          // Will be assigned when it will join a tree.
        depth = 0;              // Initial Depth = 0 (Leaf.)
        leftdepth = -1;         // No left child
        rightdepth = -1;        // No right child
        mainobj = this;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSurfaceView = (SurfaceView) findViewById(R.id.mSurfaceView);
        mSurfaceView.getHolder().addCallback(this);
        SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
        editor.putString(RtspServer.KEY_PORT, String.valueOf(12345));
        editor.commit();

        warningHandler = new Handler();
        SourceIP = "";
        ParentIP = "";
        LeftchildIP = "";
        RightchildIP = "";
        LeaderIP = "";          // Will be assigned when it will join a tree.
        depth = 0;              // Initial Depth = 0 (Leaf.)
        leftdepth = -1;         // No left child
        rightdepth = -1;        // No right child
        mainobj = this;
        leaderlist = new HashMap<Configuration, String>();
        WifiManager wifiMan = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        WifiInfo wifiInf = wifiMan.getConnectionInfo();
        int ipAddress = wifiInf.getIpAddress();
        MyIP = String.format("%d.%d.%d.%d", (ipAddress & 0xff), (ipAddress >> 8 & 0xff), (ipAddress >> 16 & 0xff), (ipAddress >> 24 & 0xff));
        myconf = new Configuration("abc");
        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (!pref.contains("test_done")) {

            final AlertDialog.Builder dialog = new AlertDialog.Builder(this).setTitle("Configuring").setMessage("Getting to know you device for the first time ! This takes less than a cup of coffee !");
            final AlertDialog alert = dialog.create();
            alert.show();

            // Hide after some seconds
            final Handler handler = new Handler();
            final Runnable runnable = new Runnable() {
                @Override
                public void run() {
                    if (alert.isShowing()) {
                        alert.dismiss();
                    }
                }
            };

            alert.setOnDismissListener(new DialogInterface.OnDismissListener() {
                @Override
                public void onDismiss(DialogInterface dialog) {
                    handler.removeCallbacks(runnable);
                }
            });

            EncoderDebugger debugger = EncoderDebugger.debug(PreferenceManager.getDefaultSharedPreferences(this), 1920, 1080);
            debugger = EncoderDebugger.debug(PreferenceManager.getDefaultSharedPreferences(this), 1280, 720);
            debugger = EncoderDebugger.debug(PreferenceManager.getDefaultSharedPreferences(this), 640, 480);
            debugger = EncoderDebugger.debug(PreferenceManager.getDefaultSharedPreferences(this), 480, 360);
            debugger = EncoderDebugger.debug(PreferenceManager.getDefaultSharedPreferences(this), 426, 240);

            SharedPreferences.Editor editor2 = PreferenceManager.getDefaultSharedPreferences(this).edit();
            editor2.putString("test_done", "");
            editor2.commit();

            handler.post(runnable);
        }
        final File file = new File(Environment.getExternalStorageDirectory() + File.separator + "logvalues.txt");
        try {
            file.createNewFile();
        }catch(Exception e){}
        OutputStream fo;
        PrintWriter pw = null;
        try {
            fo = new FileOutputStream(file);
            pw = new PrintWriter(fo, true);
        }catch(Exception e){}
        final PrintWriter pwf = pw;
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    while(true){
                        sleep(15000);
                        IntentFilter ifilter = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
                        Intent batteryStatus = MainActivity.mainobj.registerReceiver(null, ifilter);
                        int level = batteryStatus.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                        int scale = batteryStatus.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                        pwf.println("B : "+level+" "+scale);
                        Client.updateStatsLabel(pwf);
                    }
                }
                catch (Exception e){

                }
            }
        }).start();
    }

    public void init_rtsp_client(final String IP) {
        Log.e(TAG, "client starting with source IP :" + IP);
        if(MainActivity.iamleader) {
            this.startService(new Intent(this, RtspServer.class));
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        Client.ServerIPAddr = IP;
                        Client.VideoFileName = "rtsp://" + IP + ":12345/video";
                    } catch (Exception e) {
                        Log.e(TAG, "error");
                    }
//                    duplicatestream = true;
//                    rtspclient = new Client();
//                    rtspclient.sendDescribe();
//                    rtspclient.sendSetup();
//                    rtspclient.sendPlay();
                    try {
                        mMediaPlayer = new MediaPlayer();
                        mMediaPlayer.setDataSource("rtsp://" + IP + ":12345/video");
                        mMediaPlayer.setSurface(((android.view.SurfaceView) mainobj.findViewById(R.id.justSurface)).getHolder().getSurface());
                        mMediaPlayer.setOnPreparedListener(mainobj);
                        mMediaPlayer.prepareAsync();
                    } catch (Exception e) {
                        Log.d("Player", e.getMessage());
                    }
                }
            }).start();
        }
        else {
            SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
            SharedPreferences.Editor editor = mPrefs.edit();
            editor.putString("password", "abc");
            editor.putString("username", "abc");
            editor.commit();
            mSession = SessionBuilder.getInstance()
                    .setSurfaceView(mSurfaceView)
                    .setPreviewOrientation(90)
                    .setContext(getApplicationContext())
                    .setAudioEncoder(SessionBuilder.AUDIO_NONE)
                    .setVideoEncoder(SessionBuilder.VIDEO_H264)
                    .setVideoQuality(new VideoQuality(426, 240, 30, 300000))
                    .setCallback(this).build();

            mSurfaceView.startGLThread();
            this.startService(new Intent(this, RtspServer.class));
            mMediaPlayer = new MediaPlayer();
            try {
                mMediaPlayer.setDataSource("rtsp://" + IP + ":12345/video");
                mMediaPlayer.setSurface(new Surface(mSurfaceView.getSurfaceTexture()));
                mMediaPlayer.setOnPreparedListener(this);
                mMediaPlayer.prepare();
                mSession.syncConfigure();
            } catch (Exception e) {
                Log.e(TAG, "MediaPlayer exception");
                e.printStackTrace();
            }
        }
    }

    public void init_rtsp_server() {
        listenSourceControlChannel();
        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        SharedPreferences.Editor editor = mPrefs.edit();
        editor.putString("password", "abc");
        editor.putString("username", "abc");
        editor.commit();

        iamsource = true;

        mSession = SessionBuilder.getInstance()
                .setPreviewOrientation(90)
                .setSurfaceView(mSurfaceView)
                .setContext(getApplicationContext())
                .setAudioEncoder(SessionBuilder.AUDIO_NONE)
                .setVideoEncoder(SessionBuilder.VIDEO_H264)
                .setVideoQuality(new VideoQuality(640, 480, 30, 1200000))
                .setCallback(this).build();
        try {
            mSession.syncConfigure();
        } catch (Exception e) {
        }
        this.startService(new Intent(this, RtspServer.class));
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.client:
                EditText et = findViewById(R.id.IPBox);
                if (et.getVisibility() == View.INVISIBLE) {
                    et.setVisibility(View.VISIBLE);
                    Button serv = findViewById(R.id.server);
                    serv.setEnabled(false);
                    serv.setVisibility(View.INVISIBLE);
                } else {
                    Button clien = findViewById(R.id.client);
                    clien.setEnabled(false);
                    clien.setVisibility(View.INVISIBLE);
                    et.setVisibility(View.INVISIBLE);
                    et.setEnabled(false);
                    SourceIP = "192.168.1.126";
//                    joinStreamRequest("192.168.0.3");
                    try {
                        joingate.acquire();
                        init_rtsp_client("192.168.1.126");
                        joingate.release();
                    } catch (Exception e) {
                    }
                }
                break;
            case R.id.server:
                Button serv = findViewById(R.id.server);
                serv.setEnabled(false);
                serv.setVisibility(View.INVISIBLE);
                Button client = findViewById(R.id.client);
                client.setEnabled(false);
                client.setVisibility(View.INVISIBLE);
                init_rtsp_server();
                break;
        }
    }

    @Override
    public void onPrepared(MediaPlayer mp) {
        mMediaPlayer.start();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.e(TAG, "Surface available for the texture view");
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        return false;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
//        mSession.release();
//        mSurfaceView.getHolder().removeCallback(this);
//        Log.d("Mobipeer","On Destroy Method");
    }

    @Override
    public void onBitrateUpdate(long bitrate) {
        Log.e("Bitrate",bitrate/1000+" kbps");
    }

    @Override
    public void onPreviewStarted() {
        logError("Preview Started");
    }

    @Override
    public void onSessionConfigured() {
        Log.d("Mobipeer", "Session Configured");
    }

    @Override
    public void onSessionStarted() {
        Log.d("Mobipeer", "On Session Started");
//        enableUI();
//        mButtonStart.setImageResource(R.drawable.ic_switch_video_active);
//        mProgressBar.setVisibility(View.GONE);
    }

    @Override
    public void onSessionStopped() {
        Log.d("Mobipeer", "On Session stopped");
//        enableUI();
//        mButtonStart.setImageResource(R.drawable.ic_switch_video);
//        mProgressBar.setVisibility(View.GONE);
    }

    @Override
    public void onSessionError(int reason, int streamType, Exception e) {
//        mProgressBar.setVisibility(View.GONE);
        Log.d("Mobipeer", "On Session Error");
        switch (reason) {
            case Session.ERROR_CAMERA_ALREADY_IN_USE:
                break;
            case Session.ERROR_INVALID_SURFACE:
                break;
            case Session.ERROR_STORAGE_NOT_READY:
                break;
            case Session.ERROR_CONFIGURATION_NOT_SUPPORTED:
                VideoQuality quality = mSession.getVideoTrack().getVideoQuality();
                logError("The following settings are not supported on this phone: " +
                        quality.toString() + " " +
                        "(" + e.getMessage() + ")");
                e.printStackTrace();
                return;
            case Session.ERROR_OTHER:
                break;
        }

        if (e != null) {
            logError(e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public void onRtspUpdate(int message, Exception e) {
        switch (message) {
            case RtspClient.ERROR_CONNECTION_FAILED:
            case RtspClient.ERROR_WRONG_CREDENTIALS:
//                mProgressBar.setVisibility(View.GONE);
//                enableUI();
                logError(e.getMessage());
                e.printStackTrace();
                break;
        }
    }


    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        Log.d("Mobipeer", "On Surface Changed");
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.d("Mobipeer", "On Surface Created");
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.d("Mobipeer", "On Surface Destroyed");
    }

    private void logError(final String msg) {
        final String error = (msg == null) ? "Error unknown" : msg;
        // Displays a popup to report the eror to the user
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage(msg).setPositiveButton("OK", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
            }
        });
        AlertDialog dialog = builder.create();
        dialog.show();
    }


    /*
       Function executed when a mobile wants to join the system
       for live stream. A connection request is sent to Source
       using SourceIP address :
       a) Request Message Code : 1
       b) Response Type :
            (i)  "Appointed" : To indicate the device is appointed as a leader.
            (ii)  IP Address : IP address of the leader to contact when not appointed as leader.
     */
    public void joinStreamRequest(final String IP) {
        Log.e(TAG, "Join Stream Request");
        try {
            joingate.tryAcquire();
        } catch (Exception e) {
        }
        Log.e(TAG,"GOT SEMAPHORE");
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Socket requestSourceSocket = new Socket(IP, port);
                    PrintWriter dos = new PrintWriter(requestSourceSocket.getOutputStream(), true);
                    BufferedReader dis = new BufferedReader(new InputStreamReader(requestSourceSocket.getInputStream()));
                    dos.println(1); // REQUEST MESSAGE.
                    dos.println(myconf.tostring());
                    String response = dis.readLine();
                    Log.e(TAG, "Response for join stream request : " + response);
                    if (response.equals("Appointed")) {
                        iamleader = true;
                        response = dis.readLine();
                        Log.e(TAG, "Response for appointed : " + response);
                        if (response.equals("NONE")) {
                            ParentIP = SourceIP;
                            LeftchildIP = "";
                            RightchildIP = "";
                            joined = true;
                            joingate.release();
                            listenLeaderControlChannel();
                            sendheartbeatasdestination(requestSourceSocket, dos, dis);
                        } else
                            joinAsLeader(response);
                    } else {
                        LeaderIP = response;
                        contactLeader();
                    }
                    dis.close();
                    dos.close();
                    requestSourceSocket.close();
                } catch (Exception e) {
                    // What to do here ??
                }
            }
        }).start();
    }

    /*
        Function executed when a device receives a Leader IP
        from the source to join the system. Request is sent to
        leader to join the tree :
        a) Request Message Code : 1
        b) Response Type :
            (i)  "Joined" : Indicates that node is the Parent.
            (ii) IP Address : Next node to reach in the tree.
     */
    public void contactLeader() {
        Log.e(TAG, "Contact Leader function");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String IP = LeaderIP;
                Stack<String> ontheway = new Stack<>();
                ontheway.add(IP);
                while (!joined) {
                    try {
                        Socket requestSourceSocket = new Socket(IP, port);
                        PrintWriter dos = new PrintWriter(requestSourceSocket.getOutputStream(), true);
                        BufferedReader dis = new BufferedReader(new InputStreamReader(requestSourceSocket.getInputStream()));
                        dos.println(1); // REQUEST TO JOIN MESSAGE.
                        String response = dis.readLine();
                        Log.e(TAG, "Response for contact leader : " + response);
                        if (!response.equals("Joined")) {
                            IP = response;
                            ontheway.push(IP);
                        } else {
                            joined = true;
                            ParentIP = IP;
                            depth = 0;
                            leftdepth = -1;
                            rightdepth = -1;
                            dos.println(depth); // Reply with the depth message.
                            joingate.release();
                            listenControlChannel(); // Start listening.
                            sendheartbeatasdestination(requestSourceSocket, dos, dis);
                        }
                        dos.close();
                        dis.close();
                        requestSourceSocket.close();
                    } catch (Exception e) {
                        if (ontheway.size() == 0) {
                            joinStreamRequest(SourceIP);
                            break;
                        }
                        IP = ontheway.pop();
                    }
                }
            }
        }).start();
    }

    /*
        Function executed when a device is appointed as Leader
        from the source to join the system. Request is sent to
        leaders to traverse through forest.
        a) Request Message Code : 3
        b) Response Type :
            (i)  "JoinBetween" : Indicates to become the parent of that leader.
            (ii) "JoinAfter"   : Indicates that it is the end of forest.
            (iii) IP Address   : Next leader to reach in the forest.
     */
    public void joinAsLeader(String leaderIP) {
        Log.e(TAG, "Join as Leader");
        final String LIP = leaderIP;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String IP = LIP;
                Stack<String> ontheway = new Stack<>();
                ontheway.add(IP);
                while (!joined) {
                    try {
                        Socket requestSourceSocket = new Socket(IP, port);
                        PrintWriter dos = new PrintWriter(requestSourceSocket.getOutputStream(), true);
                        BufferedReader dis = new BufferedReader(new InputStreamReader(requestSourceSocket.getInputStream()));
                        dos.println(3); // REQUEST TO JOIN AS LEADER.
                        dos.println(myconf.resolutionh * myconf.resolutionw); // Sending my configuration.
                        String response = dis.readLine();
                        Log.e(TAG, "Response for join as leader : " + response);
                        if (response.equals("JoinBetween")) {
                            joined = true;
                            LeftchildIP = "";
                            RightchildIP = ontheway.pop();
                            if (ontheway.size() > 0)
                                ParentIP = ontheway.pop();
                            else
                                ParentIP = SourceIP;
                            joingate.release();
                            dos.println(4);
                            dos.close();
                            dis.close();
                            requestSourceSocket.close();
                            requestSourceSocket = new Socket(ParentIP, port);
                            dos = new PrintWriter(requestSourceSocket.getOutputStream(), true);
                            dis = new BufferedReader(new InputStreamReader(requestSourceSocket.getInputStream()));
                            dos.println(5);
                            listenLeaderControlChannel();
                            sendheartbeatasdestination(requestSourceSocket, dos, dis);
                        } else if (response.equals("JoinAfter")) {
                            joined = true;
                            ParentIP = IP;
                            LeftchildIP = "";
                            RightchildIP = "";
                            depth = 0;
                            leftdepth = -1;
                            rightdepth = -1;
                            dos.println(5);
                            joingate.release();
                            listenLeaderControlChannel();
                            sendheartbeatasdestination(requestSourceSocket, dos, dis);
                        } else {
                            IP = response;
                            ontheway.push(IP);
                        }
                        dos.close();
                        dis.close();
                        requestSourceSocket.close();
                    } catch (Exception e) {
                        if (ontheway.size() == 0) {
                            joinStreamRequest(SourceIP);
                            break;
                        }
                        IP = ontheway.pop();
                    }
                }
            }
        }).start();
    }

    /*
        Server to listen for non Leader and non Source devices
        in the system. Incoming Message Types :
        (a) 1 : Request to join, reply as said in contactLeader method
        (b) 2 : Depth update message from child. Will propagate up.
        (c) 3 : Scheduling Initiation.
        (d) 6 : Scheduling Response.
        (e) 7 : Swap message from leader in scheduling.
        (f) 8 : Update that the parent is changed.
        (g) 9 : Update that the child is changed.
        (h) 0 : HEART BEAT
        (i) 10 : Message to identify the new leader for Fault-tolerance, when the root of subtree is appointed as leader.
        (j) 11 : Message to be sent to the newly elected leader to become the parent.
        (k) 12 : Confirmation to be the parent of newly elected leader in Fault-tolerance
     */
    public static void listenControlChannel() {
        Log.e(TAG, "Listen Control Channel");
        new Thread(new Runnable() {
            @Override
            public void run() {
                Socket clientsocket;
                try {
                    listeningSocket = new ServerSocket(port);
                    while (true) {
                        try {
                            clientsocket = listeningSocket.accept();
                            new listeningThread(clientsocket).start();
                        } catch (IOException e) {
                            // What to do when IO Exception here ?
                        }
                    }
                } catch (IOException e) {
                    // What to do when IO Exception occurs here ?
                }
            }
        }).start();
    }

    /*
        Server to listen for Leaders in the system. Incoming Message Types :
        (a) 1 : Request to join, reply as said in contactLeader method
        (b) 2 : Depth update message from child. Just update depth variable.
        (c) 3 : Request to propagate through forest by another leader.
        (d) 4 : Notification that the parent of the leader is changed.
        (e) 5 : Notification that the Right child of the leader is changed.
        (f) 6 : Scheduling Response.
        (g) 0 : HEARTBEAT
        (h) 7 : IP of the higher conf leader just before this.
     */
    public static void listenLeaderControlChannel() {
        Log.e(TAG, "Listen Leader Control Channel");
        new Thread(new Runnable() {
            @Override
            public void run() {
                Socket clientsocket;
                try {
                    listeningSocket = new ServerSocket(port);
                    while (true) {
                        try {
                            clientsocket = listeningSocket.accept();
                            new listeningLeaderThread(clientsocket).start();
                        } catch (IOException e) {
                            // What to do when IO Exception here ?
                        }
                    }
                } catch (IOException e) {
                    // What to do when IO Exception occurs here ?
                }
            }
        }).start();
    }

    /*
        Server to listen at Source in the system. Incoming Message Types :
        (a) 1 : Request to join, reply as said in contactLeader method
        (b) 5 : Notification that the Right child of the source is changed.
        (c) 6 : Scheduling Response.
        (d) 0 : HEARTBEAT
        (e) 4 : Give the IP of the just higher conf leader (asked by another leader).
     */
    public void listenSourceControlChannel() {
        Log.e(TAG, "Listen Source Control Channel");
        new Thread(new Runnable() {
            @Override
            public void run() {
                Socket clientsocket;
                try {
                    listeningSocket = new ServerSocket(port);
                    while (true) {
                        try {
                            clientsocket = listeningSocket.accept();
                            new listeningSourceThread(clientsocket).start();
                        } catch (IOException e) {
                            // What to do when IO Exception here ?
                        }
                    }
                } catch (IOException e) {
                    // What to do when IO Exception occurs here ?
                }
            }
        }).start();
    }

    /*
        Method to initiate scheduling in a tree by the leader
        The leader sends a message '3' to the children to indicate initiation of scheduling process.
     */
    public static void initiateSchedulingPhase() {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    if (LeftchildIP.equals(""))
                        return;
                    else {
                        Socket socket = new Socket(LeftchildIP, port);
                        PrintWriter dos = new PrintWriter(socket.getOutputStream(), true);
                        dos.println(3);
                        dos.close();
                        socket.close();
                    }
                } catch (Exception e) {

                }
            }
        }).start();
    }

    public static void sendheartbeatasdestination(Socket socket, PrintWriter dos, BufferedReader dis) {
        try {
            String senderaddress = socket.getInetAddress().toString().substring(1);
            socket.setSoTimeout(500);
            while (true) {
                dos.println(0);
                sleep(500);
                try {
                    String s = dis.readLine();
                    Log.e(TAG, "received heart beat" + s);
                    if (!s.equals("0"))
                        throw new Exception();
                } catch (Exception e) {
                    Log.e(TAG, "lost heart beat ! " + MainActivity.ParentIP);
                    if (senderaddress.equals(MainActivity.ParentIP)) {
                        // I should rejoin as the leader of the system
                        MainActivity.joined = false;
                        if (iamleader == true)
                            listeningLeaderThread.rejoinasleader();
                        else
                            rejoin();
                    }
                    break;
                }
            }
        } catch (Exception e) {
        }
    }

    public static boolean sendheartbeatassource(Socket socket, PrintWriter dos, BufferedReader dis, boolean senddepth) {
        try {
            String senderaddress = socket.getInetAddress().toString().substring(1);
            socket.setSoTimeout(500);
            while (true) {
                dos.println(0);
                sleep(500);
                try {
                    String s = dis.readLine();
                    Log.e(TAG, "received heart beat" + s);
                    if (!s.equals("0"))
                        throw new Exception();
                } catch (Exception e) {
                    Log.e(TAG, "lost heartbeat");
                    if (senderaddress.equals(MainActivity.LeftchildIP)) {
                        // Consider left child gone.
                        MainActivity.depth = 0;
                        MainActivity.leftdepth = -1;
                        MainActivity.LeftchildIP = "";
                        senddepth = true;
                    } else if (senderaddress.equals(MainActivity.RightchildIP)) {
                        // Consider right child gone.
                        MainActivity.depth = 0;
                        MainActivity.rightdepth = -1;
                        MainActivity.RightchildIP = "";
                        senddepth = true;
                    }
                    break;
                }
            }
        } catch (Exception e) {
        }
        return senddepth;
    }

    public static void changemediaplayersource(final String IP){
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Log.e(TAG,"Changing MediaPlayer Source to : " + IP);
                    mMediaPlayer.reset();
                    mMediaPlayer.setDataSource("rtsp://" + IP + ":12345/video");
                    mMediaPlayer.setOnPreparedListener(MainActivity.mainobj);
                    mMediaPlayer.setSurface(new Surface(mSurfaceView.getSurfaceTexture()));
                    mMediaPlayer.prepareAsync();
                } catch (Exception e) {
                    // TODO Auto-generated catch block
                    Log.e(TAG, "MediaPlayer exception");
                    changemediaplayersource(IP);
                }
            }
        }).start();
    }
}

class listeningThread extends Thread{
    private Socket socket;

    public listeningThread(Socket s){
        socket = s;
    }

    public void run(){
        boolean listenforheartbeat=false;
        try{
            PrintWriter dos = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader dis = new BufferedReader( new InputStreamReader(socket.getInputStream()));
            String messagetype = dis.readLine();
            Log.e(TAG,"Control Channel received message : " + messagetype);
            boolean senddepth = false;
            if(messagetype.equals("1")) // Request for Joining
            {
                if(MainActivity.depth == 0 || MainActivity.leftdepth == -1 || MainActivity.rightdepth == -1)
                {
                    // Make it the child and send a "Joined" message back
                    dos.println("Joined");
                    if(MainActivity.leftdepth == -1)
                    {
                        MainActivity.LeftchildIP = socket.getInetAddress().toString().substring(1);
                        MainActivity.leftdepth = 0;
                        MainActivity.depth = MainActivity.leftdepth>=MainActivity.rightdepth?MainActivity.rightdepth:MainActivity.leftdepth + 1;
                    }
                    else{
                        MainActivity.RightchildIP = socket.getInetAddress().toString().substring(1);
                        MainActivity.rightdepth = 0;
                        MainActivity.depth = MainActivity.leftdepth>=MainActivity.rightdepth?MainActivity.rightdepth:MainActivity.leftdepth + 1;
                    }
                    senddepth = true;
                    listenforheartbeat = true;
                }
                else{
                    // Reply with the child IP.
                    if(MainActivity.leftdepth <= MainActivity.rightdepth)
                        dos.println(MainActivity.LeftchildIP);
                    else
                        dos.println(MainActivity.RightchildIP);
                }
            }
            else if(messagetype.equals("2"))  // Update a child's depth
            {
                if(MainActivity.LeftchildIP == socket.getInetAddress().toString().substring(1))
                    MainActivity.leftdepth = Integer.parseInt(dis.readLine());
                else
                    MainActivity.rightdepth = Integer.parseInt(dis.readLine());
                MainActivity.depth = MainActivity.leftdepth>=MainActivity.rightdepth?MainActivity.rightdepth:MainActivity.leftdepth + 1;
                senddepth = true;
            }
            else if(messagetype.equals("3")) // Scheduling initiation
            {
                MainActivity.inScheduling = true;
                String parent = MainActivity.ParentIP;
                String leftchild = MainActivity.LeftchildIP;
                String rightchild = MainActivity.RightchildIP;
                if(leftchild.equals("") && rightchild.equals(""))
                {
                    dos.close();
                    dis.close();
                    Socket replysocket = new Socket(parent, MainActivity.port);
                    dos = new PrintWriter(replysocket.getOutputStream(), true);
                    dis = new BufferedReader( new InputStreamReader(replysocket.getInputStream()));
                    dos.println(6);
                    Intent batteryIntent = MainActivity.mainobj.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                    int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                    dos.println(MainActivity.MyIP + "space" + level);
                    MainActivity.inScheduling = false;
                    replysocket.close();
                }
                else{
                    if(!leftchild.equals("")) {
                        dos.close();
                        dis.close();
                        Socket leftchildsocket = new Socket(leftchild, MainActivity.port);
                        dos = new PrintWriter(leftchildsocket.getOutputStream(), true);
                        dis = new BufferedReader( new InputStreamReader(leftchildsocket.getInputStream()));
                        dos.println(3);
                        leftchildsocket.close();
                    }
                    if(!rightchild.equals("")) {
                        dos.close();
                        dis.close();
                        Socket rightchildsocket = new Socket(leftchild, MainActivity.port);
                        dos = new PrintWriter(rightchildsocket.getOutputStream(), true);
                        dis = new BufferedReader( new InputStreamReader(rightchildsocket.getInputStream()));
                        dos.println(3);
                        rightchildsocket.close();
                    }
                }
            }
            else if(messagetype.equals("6"))  //Scheduling reply message
            {
                int battery;
                String ip = dis.readLine();
                battery = Integer.parseInt(ip.substring(ip.indexOf("space")+5));
                ip = ip.substring(0,ip.indexOf("space"));
                Intent batteryIntent = MainActivity.mainobj.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                int level = batteryIntent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                if(socket.getInetAddress().toString().substring(1).equals(MainActivity.LeftchildIP))
                {
                    MainActivity.leftbattery = battery;
                    if(!MainActivity.RightchildIP.equals("") && MainActivity.electedIP.equals(""))
                    {
                        MainActivity.electedIP = ip;
                        dos.close();
                        dis.close();
                        socket.close();
                        return;
                    }
                    else if(!MainActivity.RightchildIP.equals("") && MainActivity.electedIP.equals(""))
                    {
                        // means Received input from both
                        if (level >= MainActivity.leftbattery)
                        {
                           if(level >= MainActivity.rightbattery)
                           {
                                battery = level;
                                ip = MainActivity.MyIP;
                           }
                           else{
                                battery = MainActivity.rightbattery;
                                ip = MainActivity.electedIP;
                           }
                        }
                        else{
                            if(MainActivity.leftbattery >= MainActivity.rightbattery)
                            {
                                battery = MainActivity.leftbattery;
                            }
                            else{
                                battery = MainActivity.rightbattery;
                                ip = MainActivity.electedIP;
                            }
                        }
                    }
                    else
                    {
                        // Just compare received level and self-battery level and send up.
                        if(level >= MainActivity.leftbattery)
                        {
                            battery = level;
                            ip = MainActivity.MyIP;
                        }
                        else{
                            battery = MainActivity.leftbattery;
                        }
                    }
                    dos.close();
                    dis.close();
                    Socket parentsocket = new Socket(MainActivity.ParentIP, MainActivity.port);
                    dos = new PrintWriter(parentsocket.getOutputStream(), true);
                    dis = new BufferedReader( new InputStreamReader(parentsocket.getInputStream()));
                    dos.println(6);
                    dos.println(ip + "space" + battery);
                    parentsocket.close();
                }
                else{
                    MainActivity.rightbattery = battery;
                    if(!MainActivity.LeftchildIP.equals("") && MainActivity.electedIP.equals(""))
                    {
                        MainActivity.electedIP = ip;
                        dos.close();
                        dis.close();
                        socket.close();
                        return;
                    }
                    else if(!MainActivity.LeftchildIP.equals("") && !MainActivity.electedIP.equals(""))
                    {
                        // means Received input from both
                        if (level >= MainActivity.leftbattery)
                        {
                            if(level >= MainActivity.rightbattery)
                            {
                                battery = level;
                                ip = MainActivity.MyIP;
                            }
                            else{
                                battery = MainActivity.rightbattery;
                            }
                        }
                        else{
                            if(MainActivity.leftbattery >= MainActivity.rightbattery)
                            {
                                battery = MainActivity.leftbattery;
                                ip = MainActivity.electedIP;
                            }
                            else{
                                battery = MainActivity.rightbattery;
                            }
                        }
                    }
                    else
                    {
                        // Just compare received level and self-battery level and send up.
                        if(level >= MainActivity.rightbattery)
                        {
                            battery = level;
                            ip = MainActivity.MyIP;
                        }
                        else{
                            battery = MainActivity.rightbattery;
                        }
                    }
                    dos.close();
                    dis.close();
                    Socket parentsocket = new Socket(MainActivity.ParentIP, MainActivity.port);
                    dos = new PrintWriter(parentsocket.getOutputStream(), true);
                    dis = new BufferedReader( new InputStreamReader(parentsocket.getInputStream()));
                    dos.println(6);
                    dos.println(ip + "space" + battery);
                    parentsocket.close();
                }
                MainActivity.inScheduling = false;
            }
            else if(messagetype.equals("7"))
            {
                MainActivity.swapIP = socket.getInetAddress().toString().substring(1);
                final String leftChild = dis.readLine();
                final String rightChild = dis.readLine();
                final String parent = dis.readLine();
                dos.println(MainActivity.LeftchildIP);
                dos.println(MainActivity.RightchildIP);
                dos.println(MainActivity.ParentIP);
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            Socket messagesocket = new Socket(parent, MainActivity.port);
                            PrintWriter dos = new PrintWriter(messagesocket.getOutputStream(), true);
                            dos.println(5);
                            dos.close();
                            messagesocket.close();
                        }
                        catch(Exception e)
                        {

                        }
                    }
                });
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            Socket messagesocket = new Socket(rightChild, MainActivity.port);
                            PrintWriter dos = new PrintWriter(messagesocket.getOutputStream(), true);
                            dos.println(4);
                            dos.close();
                            messagesocket.close();
                        }
                        catch(Exception e)
                        {

                        }
                    }
                });
                if(leftChild != MainActivity.MyIP)
                    new Thread(new Runnable() {
                        @Override
                        public void run() {
                            try{
                                Socket messagesocket = new Socket(leftChild, MainActivity.port);
                                PrintWriter dos = new PrintWriter(messagesocket.getOutputStream(), true);
                                dos.println(8);
                                dos.close();
                                messagesocket.close();
                            }
                            catch(Exception e)
                            {

                            }
                        }
                    });
                MainActivity.ParentIP = parent;
                if(MainActivity.MyIP != leftChild)
                    MainActivity.LeftchildIP = leftChild;
                else
                    MainActivity.LeftchildIP = MainActivity.swapIP;
                MainActivity.RightchildIP = rightChild;
                MainActivity.iamleader = true;
            }
            else if(messagetype.equals("8"))
            {
                MainActivity.ParentIP = socket.getInetAddress().toString().substring(1);
            }
            else if(messagetype.equals("9"))
            {
                String child = dis.readLine();
                if(child == MainActivity.LeftchildIP){
                    MainActivity.LeftchildIP = socket.getInetAddress().toString().substring(1);
                }
                else{
                    MainActivity.RightchildIP = socket.getInetAddress().toString().substring(1);
                }
            }
            else if(messagetype.equals("10"))
            {
                String targetIP = dis.readLine();
                if(MainActivity.LeftchildIP.equals("") && MainActivity.RightchildIP.equals(""))
                {
                    // Leaf, so vote to become the parent of the assigned leader.
                    MainActivity.faultswap = true;
                    Socket leadersocket = new Socket(targetIP,MainActivity.port);
                    PrintWriter dos2 = new PrintWriter(leadersocket.getOutputStream(), true);
                    dos2.println(11);
                    dos2.close();
                    leadersocket.close();
                }
                if(!MainActivity.LeftchildIP.equals(""))
                {
                    // Propagate the message down
                    Socket leftchildsocket = new Socket(MainActivity.LeftchildIP,MainActivity.port);
                    PrintWriter dos2 = new PrintWriter(leftchildsocket.getOutputStream(), true);
                    dos2.println(10);
                    dos2.println(targetIP);
                    dos2.close();
                    leftchildsocket.close();
                }
                if(!MainActivity.RightchildIP.equals(""))
                {
                    // Propagate the message down.
                    Socket rightchildsocket = new Socket(MainActivity.RightchildIP,MainActivity.port);
                    PrintWriter dos2 = new PrintWriter(rightchildsocket.getOutputStream(), true);
                    dos2.println(10);
                    dos2.println(targetIP);
                    dos2.close();
                    rightchildsocket.close();
                }

            }
            else if(messagetype.equals("11"))
            {
                if(MainActivity.faultswap)
                {
                    MainActivity.faultswap = false;
                    MainActivity.ParentIP = socket.getInetAddress().toString().substring(1);
                    Socket leadersocket = new Socket(MainActivity.ParentIP,MainActivity.port);
                    PrintWriter dos2 = new PrintWriter(leadersocket.getOutputStream(), true);
                    dos2.println("12");
                    dos2.println(MainActivity.leadererrorrecoveryIP);
                    dos2.close();
                    leadersocket.close();
                }
            }
            else if(messagetype.equals("12"))
            {
                MainActivity.LeftchildIP = socket.getInetAddress().toString().substring(1);
                String response = dis.readLine();
                if(!response.equals("NONE"))
                    rejoinasleader(response);
            }
            if(senddepth == true)
            {
                // Update your parent about the changes in depth.
                Socket parentSocket = new Socket(MainActivity.ParentIP, MainActivity.port);
                PrintWriter dos2 = new PrintWriter(parentSocket.getOutputStream(), true);
                dos2.println(2);
                dos2.println(MainActivity.depth);
                dos2.close();
                parentSocket.close();
            }
            senddepth = false;
            if(listenforheartbeat)
            {
                senddepth = sendheartbeatassource(socket,dos,dis,senddepth);
            }
            dis.close();
            socket.close();
            if(senddepth == true)
            {
                // Update your parent about the changes in depth.
                Socket parentSocket = new Socket(MainActivity.ParentIP, MainActivity.port);
                PrintWriter dos2 = new PrintWriter(parentSocket.getOutputStream(), true);
                dos2.println(2);
                dos2.println(MainActivity.depth);
                dos2.close();
                parentSocket.close();
            }
        }
        catch(Exception e)
        {
            // What to do here ??
        }
    }

    public static void rejoin(){
        Log.e(TAG,"Rejoin procedure !");
        try {
            Socket requestSourceSocket = new Socket(MainActivity.SourceIP, MainActivity.port);
            PrintWriter dos = new PrintWriter(requestSourceSocket.getOutputStream(), true);
            BufferedReader dis = new BufferedReader(new InputStreamReader(requestSourceSocket.getInputStream()));
            dos.println(1); // REQUEST MESSAGE.
            dos.println(myconf.tostring());
            String response = dis.readLine();
            if(response.equals("Appointed")){
                response = dis.readLine();
                if(MainActivity.RightchildIP.equals(""))
                {
                    MainActivity.iamleader = true;
                    if(response.equals("NONE")) {
                        MainActivity.ParentIP = MainActivity.SourceIP;
                        MainActivity.joined = true;
                        MainActivity.listeningSocket.close();
                        MainActivity.iamleader = true;
                        Log.e(TAG,"I am Re appointed as leader ! --------------------");
                        MainActivity.changemediaplayersource(ParentIP);
                        MainActivity.listenLeaderControlChannel();
                        sendheartbeatasdestination(requestSourceSocket,dos,dis);
                    }
                    else
                        rejoinasleader(response);
                }
                else{
                    // Initiate the process of selecting a leaf to be the leader to eliminate the problem of right child.
                    MainActivity.leadererrorrecoveryIP = response;
                    MainActivity.faultswap = true;
                    Socket selectionsocket = new Socket(MainActivity.RightchildIP,MainActivity.port);
                    PrintWriter os = new PrintWriter(selectionsocket.getOutputStream(), true);
                    os.println(10);
                    os.println(MainActivity.MyIP);
                    os.close();
                    selectionsocket.close();
                    if(!MainActivity.LeftchildIP.equals("")) {
                        Socket selectionsocket2 = new Socket(MainActivity.LeftchildIP, MainActivity.port);
                        PrintWriter os2 = new PrintWriter(selectionsocket2.getOutputStream(), true);
                        os2.println(10);
                        os2.println(MainActivity.MyIP);
                        os2.close();
                        selectionsocket2.close();
                    }
                }
            }
            else {
                MainActivity.LeaderIP = response;
                recontactLeader();
            }
            dis.close();
            requestSourceSocket.close();
        }
        catch(Exception e){
            e.printStackTrace();
            Log.e(TAG,"Exception in rejoin procedure !");
        }
    }

    public static void rejoinasleader(String LIP){
//        if(LIP.equals("None")) {
//            MainActivity.ParentIP = MainActivity.SourceIP;
//            MainActivity.joined = true;
//            sendheartbeatasdestination(requestSourceSocket,dos,dis);
//            return;
//        }
        String IP = LIP;
        Stack<String> ontheway = new Stack<>();
        ontheway.add(IP);
        while(!MainActivity.joined)
        {
            try {
                Socket requestSourceSocket = new Socket(IP, MainActivity.port);
                PrintWriter dos = new PrintWriter(requestSourceSocket.getOutputStream(), true);
                BufferedReader dis = new BufferedReader(new InputStreamReader(requestSourceSocket.getInputStream()));
                dos.println(3); // REQUEST TO JOIN AS LEADER.
                dos.println("Configuration here");
                String response = dis.readLine();
                if(response.equals("JoinBetween"))
                {
                    MainActivity.joined = true;
                    MainActivity.iamleader = true;
                    MainActivity.RightchildIP = ontheway.pop();
                    if(ontheway.size() > 0)
                        MainActivity.ParentIP = ontheway.pop();
                    else
                        MainActivity.ParentIP = MainActivity.SourceIP;
                    dos.println(4);
                    requestSourceSocket = new Socket(MainActivity.ParentIP, MainActivity.port);
                    dos = new PrintWriter(requestSourceSocket.getOutputStream(), true);
                    dos.println(5);
                    MainActivity.listeningSocket.close();
                    MainActivity.changemediaplayersource(ParentIP);
                    MainActivity.listenLeaderControlChannel();
                    sendheartbeatasdestination(requestSourceSocket,dos,dis);
                }
                else if(response.equals("JoinAfter")){
                    MainActivity.joined = true;
                    MainActivity.ParentIP = IP;
                    MainActivity.RightchildIP = "";
                    MainActivity.depth = 0;
                    MainActivity.rightdepth = -1;
                    dos.println(5);
                    MainActivity.iamleader = true;
                    MainActivity.listeningSocket.close();
                    MainActivity.changemediaplayersource(ParentIP);
                    MainActivity.listenLeaderControlChannel();
                    sendheartbeatasdestination(requestSourceSocket,dos,dis);
                }
                else{
                    IP = response;
                    ontheway.push(IP);
                }
                dos.close();
                requestSourceSocket.close();
            }
            catch (Exception e){
                if(ontheway.size() == 0)
                {
                    MainActivity.mainobj.joinStreamRequest(MainActivity.SourceIP);
                    break;
                }
                IP = ontheway.pop();
            }
        }
    }

    public static void recontactLeader() {
        Log.e(TAG, "Contact Leader function");
        new Thread(new Runnable() {
            @Override
            public void run() {
                String IP = MainActivity.LeaderIP;
                Stack<String> ontheway = new Stack<>();
                ontheway.add(IP);
                IP = ontheway.pop();
                while (!MainActivity.joined) {
                    try {
                        Log.e(TAG,"Trying to contact : " + IP);
                        Socket requestSourceSocket = new Socket(IP, MainActivity.port);
                        requestSourceSocket.setSoTimeout(500);
                        PrintWriter dos = new PrintWriter(requestSourceSocket.getOutputStream(), true);
                        BufferedReader dis = new BufferedReader(new InputStreamReader(requestSourceSocket.getInputStream()));
                        Log.e(TAG,"Sending recontact");
                        dos.println(1); // REQUEST TO JOIN MESSAGE.
                        Log.e(TAG,"Sent recontact");
                        String response = dis.readLine();
                        Log.e(TAG, "Response for contact leader : " + response);
                        if (!response.equals("Joined")) {
                            IP = response;
                            ontheway.push(IP);
                        } else {
                            MainActivity.joined = true;
                            MainActivity.ParentIP = IP;
//                            MainActivity.depth = 0;
//                            MainActivity.leftdepth = -1;
//                            MainActivity.rightdepth = -1;
                            dos.println(2);
                            dos.println(MainActivity.depth); // Reply with the depth message.
                            MainActivity.joingate.release();
                            MainActivity.changemediaplayersource(ParentIP);
//                            MainActivity.listenControlChannel(); // Start listening.
                            MainActivity.sendheartbeatasdestination(requestSourceSocket, dos, dis);
                        }
                        dos.close();
                        dis.close();
                        requestSourceSocket.close();
                    } catch (Exception e) {
                        if (ontheway.size() == 0) {
                            rejoin();
                            break;
                        }
                        IP = ontheway.pop();
                    }
                }
            }
        }).start();
    }
}

class listeningLeaderThread extends Thread{
    private Socket socket;

    public listeningLeaderThread(Socket s){
        socket = s;
    }

    public void run(){
        boolean listenforheartbeat = false;
        try{
            PrintWriter dos = new PrintWriter(socket.getOutputStream(), true);
            BufferedReader dis = new BufferedReader( new InputStreamReader(socket.getInputStream()));
            String messagetype = dis.readLine();
            Log.e(TAG,"Leader Received message : " + messagetype);
            if(messagetype.equals("1")) // Request for Joining in the same tree.
            {
                if(MainActivity.leftdepth == -1)
                {
                    // Make it the child and send a "Joined" message back
                    dos.println("Joined");
                    MainActivity.LeftchildIP = socket.getInetAddress().toString().substring(1);
                    MainActivity.leftdepth = 0;
                    MainActivity.depth = MainActivity.leftdepth>=MainActivity.rightdepth?MainActivity.rightdepth:MainActivity.leftdepth + 1;
                    listenforheartbeat = true;
                }
                else{
                    // Reply with the child IP.
                    dos.println(MainActivity.LeftchildIP);
                }
            }
            else if(messagetype.equals("2"))  // Update a child's depth
            {
                if(MainActivity.LeftchildIP == socket.getInetAddress().toString().substring(1)) {
                    MainActivity.leftdepth = Integer.parseInt(dis.readLine());
                    MainActivity.depth = MainActivity.leftdepth + 1;
                }
            }
            else if(messagetype.equals("3"))
            {
                int configuration = Integer.parseInt(dis.readLine());
                int myconfiguration = myconf.resolutionh* myconf.resolutionw;
                if(configuration - myconfiguration > 0)
                {
                    if(MainActivity.RightchildIP.equals("")){
                        dos.println("JoinAfter");
                    }
                    else{
                        dos.println(MainActivity.RightchildIP);
                    }
                }
                else{
                    dos.println("JoinBetween");
                }
            }
            else if(messagetype.equals("4")) // Parent of the Leader is changed
            {
                MainActivity.ParentIP = socket.getInetAddress().toString().substring(1);
                String senderaddress = socket.getInetAddress().toString().substring(1);
                socket.setSoTimeout(500);
                while(true)
                {
                    dos.println(0);
                    sleep(500);
                    try {
                        String s = dis.readLine();
                        Log.e(TAG,"Leader received heart beat" + s);
                        if(!s.equals("0"))
                            throw new Exception();
                    }
                    catch(Exception e)
                    {
                        Log.e(TAG,"Leader lost heart beat !");
                        if(senderaddress == MainActivity.ParentIP)
                        {
                            // I should rejoin as the leader of the system
                            MainActivity.joined = false;
                            listeningLeaderThread.rejoinasleader();
                        }
                        break;
                    }
                }
            }
            else if(messagetype.equals("5")) // Right child of the Leader is changed.
            {
                MainActivity.RightchildIP = socket.getInetAddress().toString().substring(1);
                listenforheartbeat = true;
            }
            else if(messagetype.equals("6"))
            {
                String response = dis.readLine();
                response = response.substring(0,response.indexOf("space"));
                MainActivity.swapIP = response;
                dos.close();
                dis.close();
                Socket swapsocket = new Socket(response, MainActivity.port);
                dos = new PrintWriter(swapsocket.getOutputStream(), true);
                dis = new BufferedReader( new InputStreamReader(swapsocket.getInputStream()));
                dos.println(7);
                dos.println(MainActivity.LeftchildIP);
                dos.println(MainActivity.RightchildIP);
                dos.println(MainActivity.ParentIP);
                final String leftChild = dis.readLine();
                final String rightChild = dis.readLine();
                final String parent = dis.readLine();
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            Socket messagesocket = new Socket(parent, MainActivity.port);
                            PrintWriter dos = new PrintWriter(messagesocket.getOutputStream(), true);
                            dos.println(9);
                            dos.println(MainActivity.swapIP);
                            dos.close();
                            messagesocket.close();
                        }
                        catch(Exception e)
                        {

                        }
                    }
                });
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            Socket messagesocket = new Socket(leftChild, MainActivity.port);
                            PrintWriter dos = new PrintWriter(messagesocket.getOutputStream(), true);
                            dos.println(8);
                            dos.close();
                            messagesocket.close();
                        }
                        catch(Exception e)
                        {

                        }
                    }
                });
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        try{
                            Socket messagesocket = new Socket(rightChild, MainActivity.port);
                            PrintWriter dos = new PrintWriter(messagesocket.getOutputStream(), true);
                            dos.println(8);
                            dos.close();
                            messagesocket.close();
                        }
                        catch(Exception e)
                        {

                        }
                    }
                });
                MainActivity.LeftchildIP = leftChild;
                MainActivity.RightchildIP = rightChild;
                MainActivity.ParentIP = parent;
                MainActivity.iamleader = false;
                dos.close();
                dis.close();
                swapsocket.close();
            }
            else if(messagetype.equals("7"))
            {
                String IP = dis.readLine();
                MainActivity.ParentIP = IP;
                dos.close();
                dis.close();
                // send a message to update the right child info for that leader.
                Socket newparent = new Socket(MainActivity.ParentIP,MainActivity.port);
                dos = new PrintWriter(newparent.getOutputStream(), true);
                dis = new BufferedReader( new InputStreamReader(newparent.getInputStream()));
                dos.println(5);
                newparent.close();
            }
            if(listenforheartbeat)
            {
                sendheartbeatassource(socket,dos,dis,false);
            }
            dos.close();
            dis.close();
            socket.close();
        }
        catch(Exception e)
        {
            // What to do here ??
        }
    }

    public static void rejoinasleader(){
        // Send a message to Source.
        try{
            Socket swapsocket = new Socket(MainActivity.SourceIP, MainActivity.port);
            PrintWriter dos = new PrintWriter(swapsocket.getOutputStream(), true);
            BufferedReader dis = new BufferedReader( new InputStreamReader(swapsocket.getInputStream()));
            dos.println(4);

            dos.close();
            dis.close();
            swapsocket.close();
        }
        catch(Exception e)
        {

        }
    }
}

class listeningSourceThread extends Thread{
    private Socket socket;

    public listeningSourceThread(Socket s){
        socket = s;
    }

    public void run(){
        Configuration senderconf = new Configuration();
        boolean listenforheartbeat = false;
        try{
            BufferedReader dis = new BufferedReader( new InputStreamReader(socket.getInputStream()));
            PrintWriter dos = new PrintWriter(socket.getOutputStream(), true);
            String messagetype = dis.readLine();
            Log.e("SourceServer","received message : " + messagetype);
            if(messagetype.equals("1")) // Request for Joining in the system
            {
                String confstr = dis.readLine();
                senderconf = new Configuration(confstr);
                if(MainActivity.leaderlist.containsKey(senderconf))
                {
                    dos.println(MainActivity.leaderlist.get(senderconf));
                    Log.e("SourceServer","Sent a leader IP : " + MainActivity.leaderlist.get(senderconf));
                }
                else{
                    dos.println("Appointed");
                    MainActivity.leaderlist.put(senderconf,socket.getInetAddress().toString().substring(1));
                    Log.e("SourceServer","Appointing as leader, storing IP : " + socket.getInetAddress().toString().substring(1));
                    if(MainActivity.leaderlist.size() == 1)
                    {
                        dos.println("NONE");
                        Log.e("SourceServer","Currently no other leaders exist");
                        listenforheartbeat = true;
                        MainActivity.RightchildIP=socket.getInetAddress().toString().substring(1);
                    }
                    else{
                        dos.println(MainActivity.leaderlist.get(Collections.max(MainActivity.leaderlist.keySet(),Configuration.confcmp)));
                        Log.e("SourceServer","Returning IP of Highest configuration leader : " + MainActivity.leaderlist.get(Collections.max(MainActivity.leaderlist.keySet(),Configuration.confcmp)));
                    }
                }
            }
            else if(messagetype.equals("5")) // Right child of the Source is changed.
            {
                MainActivity.RightchildIP = socket.getInetAddress().toString().substring(1);
                listenforheartbeat = true;
            }
            else if(messagetype.equals("4"))
            {

            }
            if(listenforheartbeat){
                sendheartbeatassource(socket,dos,dis,false);
                MainActivity.leaderlist.remove(senderconf);
            }
            dis.close();
            dos.close();
            socket.close();
        }
        catch(Exception e)
        {
            // What to do here ??
        }
    }
}

class Configuration{
    public int resolutionh;
    public int resolutionw;

    public Configuration(){
        resolutionh = 480;
        resolutionw = 640;
    }

    public Configuration(int w, int h){
        resolutionw = w;
        resolutionh = h;
    }

    public Configuration(String conf){
        resolutionh = 480;
        resolutionw = 640;
    }

    public static Comparator<Configuration> confcmp = new Comparator<Configuration>() {
        @Override
        public int compare(Configuration o1, Configuration o2) {
            return o1.isbetterthan(o2);
        }
    };

    public int isbetterthan(Configuration second){
        if(this.resolutionh > second.resolutionh || this.resolutionw > second.resolutionw)
            return 1;
        else if(this.resolutionw == second.resolutionw && this.resolutionh == second.resolutionh)
            return 0;
        else
            return -1;
    }

    public String tostring(){
        // TO BE FILLED
        return "abc";
    }

    @Override
    public boolean equals(Object o){
        if (o == null || o.getClass() != getClass()) { // << this is important
            return false;
        }
        final Configuration other = (Configuration)o;
        return other.resolutionh == resolutionh && other.resolutionw == resolutionw;
    }

    @Override
    public int hashCode(){
        return resolutionw*resolutionh;
    }

}