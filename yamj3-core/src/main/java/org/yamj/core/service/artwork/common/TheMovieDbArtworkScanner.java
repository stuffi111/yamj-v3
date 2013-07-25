/*
 *      Copyright (c) 2004-2013 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ).
 *
 *      The YAMJ is free software: you can redistribute it and/or modify
 *      it under the terms of the GNU General Public License as published by
 *      the Free Software Foundation, either version 3 of the License, or
 *      any later version.
 *
 *      YAMJ is distributed in the hope that it will be useful,
 *      but WITHOUT ANY WARRANTY; without even the implied warranty of
 *      MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *      GNU General Public License for more details.
 *
 *      You should have received a copy of the GNU General Public License
 *      along with the YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3
 *
 */
package org.yamj.core.service.artwork.common;

import com.omertron.themoviedbapi.MovieDbException;
import com.omertron.themoviedbapi.TheMovieDbApi;
import com.omertron.themoviedbapi.model.Artwork;
import com.omertron.themoviedbapi.model.ArtworkType;
import com.omertron.themoviedbapi.model.MovieDb;
import com.omertron.themoviedbapi.results.TmdbResultsList;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yamj.core.configuration.ConfigService;
import org.yamj.core.database.model.IMetadata;
import org.yamj.core.service.artwork.ArtworkDetailDTO;
import org.yamj.core.service.artwork.ArtworkScannerService;
import org.yamj.core.service.artwork.fanart.IMovieFanartScanner;
import org.yamj.core.service.artwork.poster.IMoviePosterScanner;
import org.yamj.core.service.plugin.ImdbScanner;
import org.yamj.core.service.plugin.TheMovieDbScanner;

@Service("tmdbArtworkScanner")
public class TheMovieDbArtworkScanner implements
        IMoviePosterScanner, IMovieFanartScanner, InitializingBean {

    private static final Logger LOG = LoggerFactory.getLogger(TheMovieDbArtworkScanner.class);
    private static final String DEFAULT_POSTER_SIZE = "original";
    private static final String DEFAULT_FANART_SIZE = "original";
    @Autowired
    private ConfigService configService;
    @Autowired
    private ArtworkScannerService artworkScannerService;
    @Autowired
    private TheMovieDbApi tmdbApi;
    @Autowired
    private TheMovieDbScanner tmdbScanner;

    @Override
    public String getScannerName() {
        return TheMovieDbScanner.TMDB_SCANNER_ID;
    }

    @Override
    public void afterPropertiesSet() {
        // register this scanner
        artworkScannerService.registerMoviePosterScanner(this);
        artworkScannerService.registerMovieFanartScanner(this);
    }

    @Override
    public String getId(String title, int year) {
        return tmdbScanner.getMovieId(title, year);
    }

    @Override
    public List<ArtworkDetailDTO> getPosters(String title, int year) {
        String id = this.getId(title, year);
        return this.getPosters(id);
    }

    @Override
    public List<ArtworkDetailDTO> getFanarts(String title, int year) {
        String id = this.getId(title, year);
        return this.getFanarts(id);
    }

    @Override
    public List<ArtworkDetailDTO> getPosters(String id) {
        String defaultLanguage = configService.getProperty("themoviedb.language", "en");
        return getFilteredArtwork(id, defaultLanguage, ArtworkType.POSTER, DEFAULT_POSTER_SIZE);
    }

    @Override
    public List<ArtworkDetailDTO> getFanarts(String id) {
        String defaultLanguage = configService.getProperty("themoviedb.language", "en");
        return getFilteredArtwork(id, defaultLanguage, ArtworkType.BACKDROP, DEFAULT_FANART_SIZE);
    }

    /**
     * Get a list of the artwork for a movie.
     *
     * This will get all the artwork for a specified language and the blank languages as well
     *
     * @param id
     * @param language
     * @param artworkType
     * @param artworkSize
     * @return
     */
    private List<ArtworkDetailDTO> getFilteredArtwork(String id, String language, ArtworkType artworkType, String artworkSize) {
        List<ArtworkDetailDTO> dtos = new ArrayList<ArtworkDetailDTO>();
        // TODO retrieve more than one fanart info

        if (StringUtils.isNumeric(id)) {
            try {
                // Use an empty language to get all artwork and then filter it.
                TmdbResultsList<Artwork> results = tmdbApi.getMovieImages(Integer.parseInt(id), "");
                List<Artwork> artworkList = results.getResults();
                for (Artwork artwork : artworkList) {
                    if (artwork.getArtworkType() == artworkType
                            && (StringUtils.isBlank(artwork.getLanguage())
                            || StringUtils.equalsIgnoreCase(artwork.getLanguage(), language))) {
                        URL artworkURL = tmdbApi.createImageUrl(artwork.getFilePath(), artworkSize);
                        if (artworkURL == null || artworkURL.toString().endsWith("null")) {
                            LOG.warn("{} URL is invalid and will not be used: {}", artworkType, artworkURL);
                        } else {
                            dtos.add(new ArtworkDetailDTO(getScannerName(), artworkURL.toString()));
                        }
                    }
                }
                LOG.debug("Found {} {} artworks for TMDB ID '{}' and language '{}'", dtos.size(), artworkType, id, language);
            } catch (MovieDbException error) {
                LOG.warn("Failed to get the {} URL for TMDb ID {}", artworkType, id, error);
            }
        }
        return dtos;
    }

    @Override
    public List<ArtworkDetailDTO> getPosters(IMetadata metadata) {
        String id = getId(metadata);
        if (StringUtils.isNotBlank(id)) {
            return getPosters(id);
        }
        return null;
    }

    @Override
    public List<ArtworkDetailDTO> getFanarts(IMetadata metadata) {
        String id = getId(metadata);
        if (StringUtils.isNotBlank(id)) {
            return getFanarts(id);
        }
        return null;
    }

    @Override
    public String getId(IMetadata metadata) {
        // First look to see if we have a TMDb ID as this will make looking the film up easier
        String tmdbID = metadata.getSourceDbId(getScannerName());
        if (StringUtils.isNumeric(tmdbID)) {
            return tmdbID;
        }

        // Search based on IMDb ID
        String imdbID = metadata.getSourceDbId(ImdbScanner.IMDB_SCANNER_ID);
        if (StringUtils.isNotBlank(imdbID)) {
            MovieDb moviedb = null;
            try {
                String defaultLanguage = configService.getProperty("themoviedb.language", "en");
                moviedb = tmdbApi.getMovieInfoImdb(imdbID, defaultLanguage);
            } catch (MovieDbException ex) {
                LOG.warn("Failed to get TMDb ID for {}-{}", imdbID, ex.getMessage());
            }

            if (moviedb != null) {
                tmdbID = String.valueOf(moviedb.getId());
                if (StringUtils.isNumeric(tmdbID)) {
                    metadata.setSourceDbId(getScannerName(), tmdbID);
                    return tmdbID;
                }
            }
        }

        // Search based on title and year
        String title = StringUtils.isBlank(metadata.getTitleOriginal()) ? metadata.getTitle() : metadata.getTitleOriginal();
        tmdbID = getId(title, metadata.getYear());
        if (StringUtils.isNumeric(tmdbID)) {
            metadata.setSourceDbId(getScannerName(), tmdbID);
            return tmdbID;
        }

        LOG.warn("No TMDb id found for movie");
        return null;
    }
}
