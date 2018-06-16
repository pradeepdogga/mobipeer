package myfirstapp.mobipeer.com.myfirstapp.rtsp;

import java.io.IOException;

import myfirstapp.mobipeer.com.myfirstapp.MainActivity;
import myfirstapp.mobipeer.com.myfirstapp.Session;

/**
 * This class parses URIs received by the RTSP server and configures a Session accordingly.
 */
public class UriParser {

	public final static String TAG = "UriParser";

	/**
	 * Configures a Session according to the given URI.
	 * Here are some examples of URIs that can be used to configure a Session:
	 * <ul><li>rtsp://xxx.xxx.xxx.xxx:8086?h264&flash=on</li>
	 * <li>rtsp://xxx.xxx.xxx.xxx:8086?h263&camera=front&flash=on</li>
	 * <li>rtsp://xxx.xxx.xxx.xxx:8086?h264=200-20-320-240</li>
	 * <li>rtsp://xxx.xxx.xxx.xxx:8086?aac</li></ul>
	 * @param uri The URI
	 * @throws IllegalStateException
	 * @throws IOException
	 * @return A Session configured according to the URI
	 */
	public static Session parse(String uri) throws IllegalStateException, IOException {
//		SessionBuilder builder = SessionBuilder.getInstance().clone();
//		byte audioApi = 0, videoApi = 0;
//
//		if (builder.getVideoEncoder()==VIDEO_NONE && builder.getAudioEncoder()==AUDIO_NONE) {
//			SessionBuilder b = SessionBuilder.getInstance();
//			builder.setVideoEncoder(b.getVideoEncoder());
//			builder.setAudioEncoder(b.getAudioEncoder());
//		}
//
//		Session session = builder.build();
//
//		if (videoApi>0 && session.getVideoTrack() != null) {
//			session.getVideoTrack().setStreamingMethod(videoApi);
//		}
//
//		if (audioApi>0 && session.getAudioTrack() != null) {
//			session.getAudioTrack().setStreamingMethod(audioApi);
//		}

//		Session session = SessionBuilder.getInstance()
//				.setPreviewOrientation(90)
//				.setSurfaceView(MainActivity.mSurfaceView)
//				.setContext(MainActivity.mainobj.getApplicationContext())
//				.setAudioEncoder(SessionBuilder.AUDIO_NONE)
//				.setVideoEncoder(SessionBuilder.VIDEO_H264)
//				.setVideoQuality(new VideoQuality(426,240,30,300000))
//				.setCallback(MainActivity.mainobj).build();

		return MainActivity.mSession;

	}

}
