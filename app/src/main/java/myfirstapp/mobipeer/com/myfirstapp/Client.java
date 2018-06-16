package myfirstapp.mobipeer.com.myfirstapp;

/**
 * Created by PRADEEP DOGGA on 4/28/2018.
 */

import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.InterruptedIOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.net.Socket;
import java.net.SocketException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import myfirstapp.mobipeer.com.myfirstapp.rtsp.RtspServer;

import static java.lang.Math.abs;

public class Client {

    //RTP variables:
    //----------------
    DatagramPacket rcvdp;            //UDP packet received from the server
    DatagramSocket RTPsocket;        //socket to be used to send and receive UDP packets
    static int RTP_RCV_PORT = 25000; //port where the client will receive the RTP packets

    Timer timer; //timer used to receive data from the UDP socket
    byte[] buf;  //buffer used to store data received from the server

    //RTSP variables
    //----------------
    //rtsp states
    final static int INIT = 0;
    final static int READY = 1;
    final static int PLAYING = 2;
    static int state;            //RTSP state == INIT or READY or PLAYING
    static Socket RTSPsocket;           //socket used to send/receive RTSP messages
    static String ServerIPAddr;

    //input and output stream filters
    static BufferedReader RTSPBufferedReader;
    static BufferedWriter RTSPBufferedWriter;
    static String VideoFileName; //video file to request to the server
    int RTSPSeqNb = 0;           //Sequence number of RTSP messages within the session
    String RTSPid;              // ID of the RTSP session (given by the RTSP Server)

    final static String CRLF = "\r\n";
    final static String DES_FNAME = "session_info.txt";
    public static ArrayList<String> describebody = new ArrayList<>();
    public static ArrayList<String> setupbody = new ArrayList<>();
    public static ArrayList<String> playbody = new ArrayList<>();


    //Video constants:
    //------------------
    static int MJPEG_TYPE = 26; //RTP payload type for MJPEG video

    //Statistics variables:
    //------------------
    public static double statDataRate;        //Rate of video data received in bytes/s
    public static int statTotalBytes;         //Total number of bytes received in a session
    public static double statStartTime;       //Time in milliseconds when start is pressed
    public static double statTotalPlayTime;   //Time in milliseconds of video playing since beginning
    public static float statFractionLost;     //Fraction of RTP data packets from sender lost since the prev packet was sent
    public static int statCumLost;            //Number of packets lost
    public static int statExpRtpNb;           //Expected Sequence number of RTP messages within the session
    public static int statHighSeqNb;          //Highest sequence number received in session

    public static boolean forward = false;
    public static MulticastSocket duplicatesocket;

    //--------------------------
    //Constructor
    //--------------------------
    public Client() {
        Log.e("CLIENT", "constructor");
        //allocate enough memory for the buffer used to receive data from the server
        buf = new byte[15000];
        Log.e("CLIENT", "constructor");
        try {
            //Establish a TCP connection with the server to exchange RTSP messages
            //------------------
            Log.e("CLIENT", "before socket");
            RTSPsocket = new Socket(ServerIPAddr, 12345);
            Log.e("CLIENT", "after socket");
            //Set input and output stream filters:
            RTSPBufferedReader = new BufferedReader(new InputStreamReader(RTSPsocket.getInputStream()));
            RTSPBufferedWriter = new BufferedWriter(new OutputStreamWriter(RTSPsocket.getOutputStream()));

            //init RTSP state:
            state = INIT;

            duplicatesocket = new MulticastSocket();

        } catch (Exception e) {
            Log.e("CLIENT", "THERE IS AN EXCEPTION");
            e.printStackTrace();
        }
    }

    public void sendSetup(){

        Log.e("Client","Setup Button pressed !");
        if (state == INIT) {
            //Init non-blocking RTPsocket that will be used to receive data
            try {
                //construct a new DatagramSocket to receive RTP packets from the server, on port RTP_RCV_PORT
                RTPsocket = new DatagramSocket(RTP_RCV_PORT);
                //set TimeOut value of the socket to 64msec. (This is the TTL of the UDP packets)
                RTPsocket.setSoTimeout(64);
            }
            catch (SocketException se)
            {
                Log.e("Client","Socket exception: "+se);
                System.exit(0);
            }

            //init RTSP sequence number
            RTSPSeqNb = 1;

            //Send SETUP message to the server
            sendRequest("SETUP");

            //Wait for the response
            try {
                Response response;
                if ((response = Response.parseResponse(RTSPBufferedReader)).status != 200)
                    Log.e("Client", "Invalid Server Response");
                else {
                    //change RTSP state and print new state
                    state = READY;
                    RTSPBufferedReader = new BufferedReader(new InputStreamReader(RTSPsocket.getInputStream()));
                    RTSPid = response.headers.get("session");
                    Log.e("Client", "New RTSP state: READY  " + RTSPid);
                }
            }
            catch(Exception e){
                Log.e("CLIENT","error in parsing SETUP : " + e.getMessage());
            }
        }
        //else if state != INIT then do nothing
    }

    public void sendPlay() {

        Log.e("Client","Play Button pressed!");

        //Start to save the time in stats
        statStartTime = System.currentTimeMillis();

        if (state == READY) {
            //increase RTSP sequence number
            RTSPSeqNb++;

            //Send PLAY message to the server
            sendRequest("PLAY");

            //Wait for the response
            try{
            if (Response.parseResponse(RTSPBufferedReader).status != 200) {
                Log.e("Client","Invalid Server Response");
            }
            else {
                //change RTSP state and print out new state
                state = PLAYING;
                Log.e("Client","New RTSP state: PLAYING");
                //start the timer
                timer = new Timer();
                timer.scheduleAtFixedRate(new TimerTask() {
                    @Override
                    public void run() {
                        timerHandler();
                    }
                },0,5);
            }
            }catch(Exception e){
                Log.e("CLIENT","Error in parsing PLAY");
            }
        }
        //else if state != READY then do nothing
    }

    public void sendTearDown(){

        Log.e("Client","Teardown Button pressed !");

        //increase RTSP sequence number
        RTSPSeqNb++;

        //Send TEARDOWN message to the server
        sendRequest("TEARDOWN");

        //Wait for the response
        try{
        if (Response.parseResponse(RTSPBufferedReader).status != 200)
            Log.e("Client","Invalid Server Response");
        else {
            //change RTSP state and print out new state
            state = INIT;
            Log.e("Client","New RTSP state: INIT");

            //stop the timer
            timer.cancel();
        }
        }catch(Exception e){
            Log.e("CLIENT","error in parsing TEARDOWN");
        }
    }

    public void sendDescribe() {
        Log.e("Client","Sending DESCRIBE request");

        //increase RTSP sequence number
        RTSPSeqNb++;

        //Send DESCRIBE message to the server
        sendRequest("DESCRIBE");

        //Wait for the response
        try{
        if (Response.parseResponse(RTSPBufferedReader).status != 200) {
            Log.e("Client","Invalid Server Response");
        }
        else {
            Log.e("Client","Received response for DESCRIBE");
            // Parsing headers of the request
            String line;
            while ( (line = RTSPBufferedReader.readLine()) != null) {
                Log.e("CLIENT","parsing body : "+line);
                describebody.add(line);
                if(line.contains("a=control:trackID")){
                    break;
                }
            }
            RTSPBufferedReader = new BufferedReader(new InputStreamReader(RTSPsocket.getInputStream()));
        }
        }
        catch (Exception e){
            Log.e("CLIENT","Error in parsing Describe");
        }
    }

    public void timerHandler() {

        //Construct a DatagramPacket to receive data from the UDP socket
        rcvdp = new DatagramPacket(buf, buf.length);

        try {
            //receive the DP from the socket, save time for stats
            RTPsocket.receive(rcvdp);

            double curTime = System.currentTimeMillis();
            statTotalPlayTime += curTime - statStartTime;
            statStartTime = curTime;

            //create an RTPpacket object from the DP
            RTPpacket rtp_packet = new RTPpacket(rcvdp.getData(), rcvdp.getLength());
            int seqNb = rtp_packet.getsequencenumber();

            //print header bitstream:
            rtp_packet.printheader();

            //get the payload bitstream from the RTPpacket object
            int payload_length = rtp_packet.getpayload_length();
            byte [] payload = new byte[payload_length];
            rtp_packet.getpayload(payload);

            //compute stats and update the label in GUI
            statExpRtpNb++;
            if (seqNb > statHighSeqNb) {
                statHighSeqNb = seqNb;
            }
            if (statExpRtpNb != seqNb) {
                statCumLost += abs(statExpRtpNb - seqNb);
                statExpRtpNb = seqNb;
            }
            statDataRate = statTotalPlayTime == 0 ? 0 : (statTotalBytes / (statTotalPlayTime / 1000.0));
            statFractionLost = (float)statCumLost / statHighSeqNb;
            statTotalBytes += payload_length;


            if(forward && MainActivity.duplicatestream){
                rcvdp.setPort(RtspServer.playerport);
                rcvdp.setAddress(InetAddress.getByName(MainActivity.MyIP));
                duplicatesocket.send(rcvdp);
            }
            if(RtspServer.sendleft && !MainActivity.LeftchildIP.equals(""))
            {
                rcvdp.setPort(RtspServer.ports[0]);
                rcvdp.setAddress(InetAddress.getByName(MainActivity.LeftchildIP));
                duplicatesocket.send(rcvdp);
            }
            if(RtspServer.sendright && !MainActivity.RightchildIP.equals(""))
            {
                rcvdp.setPort(RtspServer.ports[2]);
                rcvdp.setAddress(InetAddress.getByName(MainActivity.RightchildIP));
                duplicatesocket.send(rcvdp);
            }


        }
        catch (InterruptedIOException iioe) {
//            Log.e("Client", iioe.getMessage());
        }
        catch (IOException ioe) {
            Log.e("Client","Exception caught: "+ioe);
        }
    }

    public static void updateStatsLabel(PrintWriter pw) {
        DecimalFormat formatter = new DecimalFormat("###,###.##");
        pw.println("R : " + statTotalBytes);
        pw.println("L : "+ formatter.format(statFractionLost));
        pw.println("D : " + formatter.format(statDataRate));
    }

    //------------------------------------
    //Send RTSP Request
    //------------------------------------

    private void sendRequest(String request_type) {
        try {
            //Use the RTSPBufferedWriter to write to the RTSP socket

            //write the request line:
            if(request_type == "SETUP") {
                RTSPBufferedWriter.write(request_type + " " + VideoFileName + "/trackID=1" + " RTSP/1.0" + CRLF);
            }
            else{
                RTSPBufferedWriter.write(request_type + " " + VideoFileName + " RTSP/1.0" + CRLF);
            }

            //write the CSeq line:
            RTSPBufferedWriter.write("CSeq: " + RTSPSeqNb + CRLF);

            //check if request_type is equal to "SETUP" and in this case write the
            //Transport: line advertising to the server the port used to receive
            //the RTP packets RTP_RCV_PORT
            if (request_type == "SETUP") {
                RTSPBufferedWriter.write("Transport: RTP/AVP/UDP;unicast;client_port=" + RTP_RCV_PORT +"-" + (RTP_RCV_PORT+1) +  CRLF);
                RTSPBufferedWriter.write("User-Agent: stagefright/1.2 (Linux;Android 7.1.2)" + CRLF);
            }
            else if (request_type == "DESCRIBE") {
                RTSPBufferedWriter.write("Accept: application/sdp" + CRLF);
                RTSPBufferedWriter.write("User-Agent: stagefright/1.2 (Linux;Android 7.1.2)" + CRLF);
            }
            else {
                //otherwise, write the Session line from the RTSPid field
                RTSPBufferedWriter.write("Session: " + RTSPid + CRLF);
                RTSPBufferedWriter.write("Range: npt=0-" + CRLF);
                RTSPBufferedWriter.write("User-Agent: stagefright/1.2 (Linux;Android 7.1.2)" + CRLF);
            }
            RTSPBufferedWriter.write(CRLF);
            RTSPBufferedWriter.flush();
        } catch(Exception ex) {
            Log.e("Client","Exception caught: "+ex);
            System.exit(0);
        }
    }

    static class Response {

        // Parses method & uri
        public static final Pattern regexStatus = Pattern.compile("RTSP/\\d.\\d (\\d+) (\\w+)",Pattern.CASE_INSENSITIVE);
        // Parses a request header
        public static final Pattern rexegHeader = Pattern.compile("(\\S+):(.+)",Pattern.CASE_INSENSITIVE);
        // Parses a WWW-Authenticate header
        public static final Pattern rexegAuthenticate = Pattern.compile("realm=\"(.+)\",\\s+nonce=\"(\\w+)\"",Pattern.CASE_INSENSITIVE);
        // Parses a Session header
        public static final Pattern rexegSession = Pattern.compile("(\\d+)",Pattern.CASE_INSENSITIVE);
        // Parses a Transport header
        public static final Pattern rexegTransport = Pattern.compile("client_port=(\\d+)-(\\d+).+server_port=(\\d+)-(\\d+)",Pattern.CASE_INSENSITIVE);


        public int status;
        public HashMap<String,String> headers = new HashMap<String,String>();

        /** Parse the method, URI & headers of a RTSP request */
        public static Response parseResponse(BufferedReader input) throws IOException, IllegalStateException, SocketException {
            Response response = new Response();
            String line;
            Matcher matcher;
            // Parsing request method & URI
            if ((line = input.readLine())==null) throw new SocketException("Connection lost");
            matcher = regexStatus.matcher(line);
            matcher.find();
            response.status = Integer.parseInt(matcher.group(1));

            // Parsing headers of the request
            while ( (line = input.readLine()) != null) {
                //Log.e(TAG,"l: "+line.length()+", c: "+line);
                Log.e("CLIENT","Parsing Line : " + line);
                if (line.length()>3) {
                    matcher = rexegHeader.matcher(line);
                    matcher.find();
                    response.headers.put(matcher.group(1).toLowerCase(Locale.US),matcher.group(2));
                } else {
                    break;
                }
            }
            if (line==null) throw new SocketException("Connection lost");
            Log.d("CLIENT", "Response from server: "+response.status);

            return response;
        }
    }
}
