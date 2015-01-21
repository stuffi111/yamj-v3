/*
 *      Copyright (c) 2004-2015 YAMJ Members
 *      https://github.com/organizations/YAMJ/teams
 *
 *      This file is part of the Yet Another Media Jukebox (YAMJ).
 *
 *      YAMJ is free software: you can redistribute it and/or modify
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
 *      along with YAMJ.  If not, see <http://www.gnu.org/licenses/>.
 *
 *      Web: https://github.com/YAMJ/yamj-v3
 *
 */
package org.yamj.core.service.metadata.online;

import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.annotation.Resource;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.yamj.core.tools.LRUTimedCache;
import org.yamj.core.tools.web.ResponseTools;
import org.yamj.core.tools.web.TemporaryUnavailableException;

import com.moviejukebox.allocine.AllocineApi;
import com.moviejukebox.allocine.AllocineException;
import com.moviejukebox.allocine.model.EpisodeInfos;
import com.moviejukebox.allocine.model.FilmographyInfos;
import com.moviejukebox.allocine.model.Movie;
import com.moviejukebox.allocine.model.MovieInfos;
import com.moviejukebox.allocine.model.PersonInfos;
import com.moviejukebox.allocine.model.Search;
import com.moviejukebox.allocine.model.ShortPerson;
import com.moviejukebox.allocine.model.TvSeasonInfos;
import com.moviejukebox.allocine.model.TvSeries;
import com.moviejukebox.allocine.model.TvSeriesInfos;

@Service("allocineApiWrapper")
public class AllocineApiWrapper {

    private static final Logger LOG = LoggerFactory.getLogger(AllocineApiWrapper.class);

    private final Lock searchMoviesLock = new ReentrantLock(true);
    private final Lock searchSeriesLock = new ReentrantLock(true);
    private final Lock searchPersonLock = new ReentrantLock(true);
    // make maximal 20 seachMovie objects maximal 30 minutes accessible
    private final LRUTimedCache<String, Search> searchMoviesCache = new LRUTimedCache<>(20, 1800);
    // make maximal 20 seachMovie objects maximal 30 minutes accessible
    private final LRUTimedCache<String, Search> searchSeriesCache = new LRUTimedCache<>(20, 1800);
    // make maximal 10 seachPerson objects maximal 30 minutes accessible
    private final LRUTimedCache<String, Search> searchPersonCache = new LRUTimedCache<>(10, 1800);
    // make maximal 30 movies maximal 30 minutes accessible
    private final LRUTimedCache<String, MovieInfos> moviesCache = new LRUTimedCache<>(30, 1800);
    // make maximal 30 movies maximal 30 minutes accessible
    private final LRUTimedCache<String, TvSeriesInfos> tvSeriesCache = new LRUTimedCache<>(30, 1800);

    @Resource(name="allocineApi")
    private AllocineApi allocineApi;

    public String getAllocineMovieId(String title, int year, boolean throwTempError) {
        // get from cache or retrieve online
        searchMoviesLock.lock();
        Search search;
        try {
            search = searchMoviesCache.get(title);
            if (search == null) {
                search = allocineApi.searchMovies(title);
                searchMoviesCache.put(title, search);
            }
        } catch (AllocineException ex) {
            if (throwTempError && ResponseTools.isTemporaryError(ex)) {
                throw new TemporaryUnavailableException("Allocine service temporary not available: " + ex.getResponseCode(), ex);
            }
            LOG.error("Failed retrieving Allocine id for movie: {}", title);
            LOG.warn("Allocine error" , ex);
            return null;
        } finally {
            searchMoviesLock.unlock();
        }
        
        if (!search.isValid()) {
            return null;
        }
        
        // if we have a valid year try to find the first movie that match
        if (search.getTotalResults() > 1 && year > 0) {
            for (Movie movie : search.getMovies()) {
                if (movie != null) {
                    int movieProductionYear = movie.getProductionYear();
                    if (movieProductionYear <= 0) {
                        continue;
                    }
                    if (movieProductionYear == year) {
                        return String.valueOf(movie.getCode());
                    }
                }
            }
        }
        
        // we don't find a movie or there only one result, return the first
        if (!search.getMovies().isEmpty()) {
            Movie movie = search.getMovies().get(0);
            if (movie != null) {
                return String.valueOf(movie.getCode());
            }
        }
        
        // no id found
        return null;
    }

    public String getAllocineSeriesId(String title, int year, boolean throwTempError) {
        // get from cache or retrieve online
        searchSeriesLock.lock();
        Search search;
        try {
            search = searchSeriesCache.get(title);
            if (search == null) {
                search = allocineApi.searchTvSeries(title);
                searchSeriesCache.put(title, search);
            }
        } catch (AllocineException ex) {
            if (throwTempError && ResponseTools.isTemporaryError(ex)) {
                throw new TemporaryUnavailableException("Allocine service temporary not available: " + ex.getResponseCode(), ex);
            }
            LOG.error("Failed retrieving Allocine id for series: {}", title);
            LOG.warn("Allocine error" , ex);
            return null;
        } finally {
            searchSeriesLock.unlock();
        }

        if (!search.isValid()) {
            return null;
        }

        // if we have a valid year try to find the first series that match
        if (search.getTotalResults() > 1 && year > 0) {
            for (TvSeries serie : search.getTvSeries()) {
                if (serie != null) {
                    int serieStart = serie.getYearStart();
                    if (serieStart <= 0) {
                        continue;
                    }
                    int serieEnd = serie.getYearEnd();
                    if (serieEnd <= 0) {
                        serieEnd = serieStart;
                    }
                    if (year >= serieStart && year <= serieEnd) {
                        return String.valueOf(serie.getCode());
                    }
                }
            }
        }
        
        // we don't find a series or there only one result, return the first
        if (!search.getTvSeries().isEmpty()) {
            TvSeries serie = search.getTvSeries().get(0);
            if (serie != null) {
                return String.valueOf(serie.getCode());
            }
        }
        
        // no id found
        return null;
    }

    public String getAllocinePersonId(String name, boolean throwTempError) {
        // get from cache or retrieve online
        searchPersonLock.lock();
        Search search;
        try {
            search = searchPersonCache.get(name);
            if (search == null) {
                search = allocineApi.searchPersons(name);
                searchPersonCache.put(name, search);
            }
        } catch (AllocineException ex) {
            if (throwTempError && ResponseTools.isTemporaryError(ex)) {
                throw new TemporaryUnavailableException("Allocine service temporary not available: " + ex.getResponseCode(), ex);
            }
            LOG.error("Failed retrieving Allocine id for person: {}", name);
            LOG.warn("Allocine error" , ex);
            return null;
        } finally {
            searchPersonLock.unlock();
        }
        
        if (!search.isValid()) {
            return null;
        }
        
        // find for matching person
        if (search.getTotalResults() > 1) {
            for (ShortPerson person : search.getPersons()) {
                if (person != null) {
                    // find exact name (ignoring case)
                    if (StringUtils.equalsIgnoreCase(name, person.getName())) {
                        return String.valueOf(person.getCode());
                    }
                }
            }
        }
        
        // we don't find a person or there only one result, return the first
        if (!search.getPersons().isEmpty()) {
            ShortPerson person = search.getPersons().get(0);
            if (person != null) {
                return String.valueOf(person.getCode());
            }
        }
        
        // no id found
        return null;
    }

    public MovieInfos getMovieInfos(String allocineId, boolean throwTempError) {
        MovieInfos movieInfos = moviesCache.get(allocineId);
        if (movieInfos == null) {
            try {
                movieInfos = allocineApi.getMovieInfos(allocineId);
                if (movieInfos != null && movieInfos.isValid()) {
                    // add to the cache
                    moviesCache.put(allocineId, movieInfos);
                }
            } catch (AllocineException ex) {
                if (throwTempError && ResponseTools.isTemporaryError(ex)) {
                    throw new TemporaryUnavailableException("Allocine service temporary not available: " + ex.getResponseCode(), ex);
                }
                LOG.error("Failed retrieving Allocine infos for movie: {}", allocineId);
                LOG.warn("Allocine error" , ex);
            }
        }
        return movieInfos;
    }

    public TvSeriesInfos getTvSeriesInfos(String allocineId, boolean throwTempError) {
        TvSeriesInfos tvSeriesInfos = tvSeriesCache.get(allocineId);
        if (tvSeriesInfos == null) {
            try {
                tvSeriesInfos = allocineApi.getTvSeriesInfos(allocineId);
                if (tvSeriesInfos != null && tvSeriesInfos.isValid()) {
                    // add to the cache
                    tvSeriesCache.put(allocineId, tvSeriesInfos);
                }
            } catch (AllocineException ex) {
                if (throwTempError && ResponseTools.isTemporaryError(ex)) {
                    throw new TemporaryUnavailableException("Allocine service temporary not available: " + ex.getResponseCode(), ex);
                }
                LOG.error("Failed retrieving Allocine infos for series: {}", allocineId);
                LOG.warn("Allocine error" , ex);
            }
        }
        return tvSeriesInfos;
    }
     
    public TvSeasonInfos getTvSeasonInfos(TvSeriesInfos tvSeriesInfos, int season, boolean throwTempError) {
        if (season  > tvSeriesInfos.getSeasonCount()) {
            // invalid season
            return null;
        }
        
        TvSeasonInfos tvSeasonInfos = null;
        int seasonCode = tvSeriesInfos.getSeasonCode(season);

        try {
            tvSeasonInfos = allocineApi.getTvSeasonInfos(seasonCode);
        } catch (AllocineException ex) {
            if (throwTempError && ResponseTools.isTemporaryError(ex)) {
                throw new TemporaryUnavailableException("Allocine service temporary not available: " + ex.getResponseCode(), ex);
            }
            LOG.error("Failed retrieving Allocine infos for season: {}", seasonCode);
            LOG.warn("Allocine error" , ex);
        }
        
        return tvSeasonInfos;
    }

    public PersonInfos getPersonInfos(String allocineId, boolean throwTempError) {
        PersonInfos personInfos = null;
        try {
            personInfos = allocineApi.getPersonInfos(allocineId);
        } catch (AllocineException ex) {
            if (throwTempError && ResponseTools.isTemporaryError(ex)) {
                throw new TemporaryUnavailableException("Allocine service temporary not available: " + ex.getResponseCode(), ex);
            }
            LOG.error("Failed retrieving Allocine filmography for person: {}", allocineId);
            LOG.warn("Allocine error" , ex);
        }
        return personInfos;
    }

    public FilmographyInfos getFilmographyInfos(String allocineId, boolean throwTempError) {
        FilmographyInfos filmographyInfos = null;
        try {
            filmographyInfos = allocineApi.getPersonFilmography(allocineId);
        } catch (AllocineException ex) {
            if (throwTempError && ResponseTools.isTemporaryError(ex)) {
                throw new TemporaryUnavailableException("Allocine service temporary not available: " + ex.getResponseCode(), ex);
            }
            LOG.error("Failed retrieving Allocine filmography for person: {}", allocineId);
            LOG.warn("Allocine error" , ex);
        }
        return filmographyInfos;
    }

    public EpisodeInfos getEpisodeInfos(String allocineId, boolean throwTempError) {
        EpisodeInfos episodeInfos = null;
        try {
            episodeInfos = allocineApi.getEpisodeInfos(allocineId);
        } catch (AllocineException ex) {
            if (throwTempError && ResponseTools.isTemporaryError(ex)) {
              throw new TemporaryUnavailableException("Allocine service temporary not available: " + ex.getResponseCode(), ex);
            }
            LOG.error("Failed retrieving Allocine filmography for episode: {}", allocineId);
            LOG.warn("Allocine error" , ex);
        }
        return episodeInfos;
    }
}   