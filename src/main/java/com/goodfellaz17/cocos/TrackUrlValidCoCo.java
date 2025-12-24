package com.goodfellaz17.cocos;

import com.goodfellaz17.cocos.order.OrderContext;

import java.util.regex.Pattern;

/**
 * CoCo: Track URL must be a valid Spotify URL.
 * 
 * Manual Ch.10: Syntactic validation at semantic level.
 * Validates Spotify track URL format.
 */
public class TrackUrlValidCoCo implements CoCo<OrderContext> {
    
    public static final String ERROR_CODE = "0xGFL04";
    
    /**
     * Spotify track URL patterns.
     * Spotify IDs are typically 22 alphanumeric characters, but we allow flexibility.
     */
    private static final Pattern SPOTIFY_TRACK_PATTERN = Pattern.compile(
            "^(https?://)?(open\\.)?spotify\\.com/track/[a-zA-Z0-9]{10,30}(\\?.*)?$"
    );
    
    private static final Pattern SPOTIFY_ALBUM_PATTERN = Pattern.compile(
            "^(https?://)?(open\\.)?spotify\\.com/album/[a-zA-Z0-9]{10,30}(\\?.*)?$"
    );
    
    private static final Pattern SPOTIFY_PLAYLIST_PATTERN = Pattern.compile(
            "^(https?://)?(open\\.)?spotify\\.com/playlist/[a-zA-Z0-9]{10,30}(\\?.*)?$"
    );
    
    private static final Pattern SPOTIFY_URI_PATTERN = Pattern.compile(
            "^spotify:(track|album|playlist):[a-zA-Z0-9]{10,30}$"
    );
    
    @Override
    public void check(OrderContext context) throws CoCoViolationException {
        String url = context.trackUrl();
        
        if (url == null || url.isBlank()) {
            throw new CoCoViolationException(
                    ERROR_CODE,
                    "Track URL is required",
                    "link",
                    null
            );
        }
        
        boolean isValidUrl = SPOTIFY_TRACK_PATTERN.matcher(url).matches()
                || SPOTIFY_ALBUM_PATTERN.matcher(url).matches()
                || SPOTIFY_PLAYLIST_PATTERN.matcher(url).matches()
                || SPOTIFY_URI_PATTERN.matcher(url).matches();
        
        if (!isValidUrl) {
            throw new CoCoViolationException(
                    ERROR_CODE,
                    "Invalid Spotify URL format. Expected: https://open.spotify.com/track/... or spotify:track:...",
                    "link",
                    url
            );
        }
    }
    
    @Override
    public String getErrorCode() {
        return ERROR_CODE;
    }
    
    @Override
    public String getDescription() {
        return "Track URL must be a valid Spotify URL";
    }
}
