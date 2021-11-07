/*
 * This file is part of Airsonic.
 *
 *  Airsonic is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Airsonic is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with Airsonic.  If not, see <http://www.gnu.org/licenses/>.
 *
 *  Copyright 2014 (C) Sindre Mehus
 */

package org.airsonic.player.service;

import org.airsonic.player.dao.ArtistDao;
import org.airsonic.player.dao.MediaFileDao;
import org.airsonic.player.domain.*;
import org.airsonic.player.security.GlobalSecurityConfig;
import org.airsonic.player.security.PasswordDecoder;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.*;

/**
 * Provides services from the Last.fm REST API.
 *
 * @author Sindre Mehus
 * @version $Id$
 */
@Service
public class ListenBrainzService {

    private static final Logger LOG = LoggerFactory.getLogger(ListenBrainzService.class);

    private static final String BASE_API_URL = "https://api.listenbrainz.org";

    @Autowired
    private MediaFileDao mediaFileDao;
    @Autowired
    private MediaFileService mediaFileService;
    @Autowired
    private ArtistDao artistDao;
    @Autowired
    private SettingsService settingsService;
    @Autowired
    private SecurityService securityService;

    private final RequestConfig requestConfig = RequestConfig.custom()
            .setConnectTimeout(15000)
            .setSocketTimeout(15000)
            .build();


    public String[] getToken(String username) {
        UserSettings userSettings = settingsService.getUserSettings(username);
        EnumSet<UserCredential.App> enabledApps = EnumSet.noneOf(UserCredential.App.class);
        if (userSettings.getListenBrainzEnabled()) {
            enabledApps.add(UserCredential.App.LISTENBRAINZ);
        }
        Map<UserCredential.App, UserCredential> creds = securityService.getDecodableCredsForApps(username, enabledApps.toArray(new UserCredential.App[0]));
        UserCredential cred = creds.get(UserCredential.App.LISTENBRAINZ);
        if (cred != null) {
            String decoded = decode(cred);
            if (decoded != null) {
                return new String[] {cred.getAppUsername(), decoded };
            }
        }
        return null;
    }
    private static String decode(UserCredential uc) {
        PasswordDecoder decoder = (PasswordDecoder) GlobalSecurityConfig.ENCODERS.get(uc.getEncoder());
        try {
            return decoder.decode(uc.getCredential());
        } catch (Exception e) {
            LOG.warn("Could not decode credentials for user {}, app {}", uc.getUsername(), uc.getApp(), e);
            return null;
        }
    }

    private static String streamToString(InputStream stream) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
        } catch (IOException e) {
            LOG.error("Error while streaming to string:", e);
        }

        return sb.toString();
    }

    public List<MediaFile> getRecommendedSongs(int count, String artist_type, String userName, List<MusicFolder> musicFolders) {
        String[] creds = getToken(userName);
        if (creds == null) {
            LOG.error("Unable to get API token for ListenBrainz API. Is ListenBrainz scrobbling username and API key configured ?");
            return Collections.emptyList();
        }
        String appUserName = creds[0];
        String token = creds[1];

        //GET /1/cf/recommendation/user/(user_name)/recording
        String apiUrl = BASE_API_URL + "/1/cf/recommendation/user/" + appUserName + "/recording" + "?artist_type=" + artist_type + "&count=" + count + "&offset=0";

        HttpGet request = new HttpGet(apiUrl);
        request.setHeader("Authorization", "token " + token);
        //request.setHeader("Content-type", "application/json; charset=utf-8");
        String json;
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse resp = client.execute(request)) {
            boolean ok = resp.getStatusLine().getStatusCode() == 200;
            boolean notFound = resp.getStatusLine().getStatusCode() == 404;
            if (!ok) {
                LOG.warn("Failed to execute ListenBrainz request: {}", resp.getEntity().toString());
                if (notFound) {
                    LOG.warn("Verify that Listenbrainz app username is correct");
                }
                return Collections.emptyList();
            }
            try (InputStream is = resp.getEntity().getContent()) {
                json = streamToString(is);
            }

            LOG.error(json);

        } catch (IOException e) {
            LOG.error("Failed to load recommendations from ListenBrainz ", e);
        }


        /*
        try {
            if (StringUtils.isBlank(artistName) || count <= 0) {
                return Collections.emptyList();
            }

            List<MediaFile> result = new ArrayList<MediaFile>();
            for (Track topTrack : Artist.getTopTracks(artistName, LAST_FM_KEY)) {
                MediaFile song = mediaFileDao.getSongByArtistAndTitle(artistName, topTrack.getName(), musicFolders);
                if (song != null) {
                    result.add(song);
                    if (result.size() == count) {
                        return result;
                    }
                }
            }
            return result;
        } catch (Throwable x) {
            LOG.warn("Failed to find top songs for " + artistName, x);
            return Collections.emptyList();
        }

         */
        return Collections.emptyList();
    }

}
