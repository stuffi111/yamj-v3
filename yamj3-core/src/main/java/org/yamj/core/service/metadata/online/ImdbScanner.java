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

import com.omertron.imdbapi.model.*;
import java.io.IOException;
import java.util.*;
import javax.annotation.PostConstruct;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yamj.core.config.ConfigServiceWrapper;
import org.yamj.core.config.LocaleService;
import org.yamj.core.database.model.*;
import org.yamj.core.database.model.dto.CreditDTO;
import org.yamj.core.database.model.type.JobType;
import org.yamj.core.service.metadata.nfo.InfoDTO;
import org.yamj.core.tools.MetadataTools;
import org.yamj.core.tools.OverrideTools;
import org.yamj.core.tools.PersonNameDTO;
import org.yamj.core.web.HTMLTools;
import org.yamj.core.web.apis.ImdbApiWrapper;
import org.yamj.core.web.apis.ImdbEpisodeDTO;
import org.yamj.core.web.apis.ImdbSearchEngine;

@Service("imdbScanner")
public class ImdbScanner implements IMovieScanner, ISeriesScanner, IPersonScanner {

    public static final String SCANNER_ID = "imdb";

    private static final Logger LOG = LoggerFactory.getLogger(ImdbScanner.class);
    private static final String HTML_DIV_END = "</div>";
    private static final String HTML_A_END = "</a>";
    private static final String HTML_H4_END = ":</h4>";
    private static final String HTML_TABLE_END = "</table>";
    private static final String HTML_TD_END = "</td>";
    private static final String LITERAL_NORMAL = "normal";
    
    @Autowired
    private ImdbSearchEngine imdbSearchEngine;
    @Autowired
    private OnlineScannerService onlineScannerService;
    @Autowired
    private ConfigServiceWrapper configServiceWrapper;
    @Autowired
    private LocaleService localeService;
    @Autowired
    private ImdbApiWrapper imdbApiWrapper;
    
    @Override
    public String getScannerName() {
        return SCANNER_ID;
    }

    @PostConstruct
    public void init() {
        LOG.trace("Initialize IMDb scanner");
        
        // register this scanner
        onlineScannerService.registerMetadataScanner(this);
    }

    @Override
    public String getMovieId(VideoData videoData) {
        return getMovieId(videoData, false);
    }

    private String getMovieId(VideoData videoData, boolean throwTempError) {
        String imdbId = videoData.getSourceDbId(SCANNER_ID);
        if (StringUtils.isBlank(imdbId)) {
            imdbId = imdbSearchEngine.getImdbId(videoData.getTitle(), videoData.getPublicationYear(), false, throwTempError);
            videoData.setSourceDbId(SCANNER_ID, imdbId);
        }
        return imdbId;
    }

    @Override
    public String getSeriesId(Series series) {
        return getSeriesId(series, false);
    }

    private String getSeriesId(Series series, boolean throwTempError) {
        String imdbId = series.getSourceDbId(SCANNER_ID);
        if (StringUtils.isBlank(imdbId)) {
            imdbId = imdbSearchEngine.getImdbId(series.getTitle(), series.getStartYear(), true, throwTempError);
            series.setSourceDbId(SCANNER_ID, imdbId);
        }
        return imdbId;
    }

    @Override
    public String getPersonId(Person person) {
        return getPersonId(person, false);
    }

    private String getPersonId(Person person, boolean throwTempError) {
        String imdbId = person.getSourceDbId(SCANNER_ID);
        if (StringUtils.isNotBlank(imdbId)) {
            return imdbId;
        }
        if (StringUtils.isNotBlank(person.getName())) {
            imdbId = this.imdbSearchEngine.getImdbPersonId(person.getName(), throwTempError);
            person.setSourceDbId(SCANNER_ID, imdbId);
        }
        return imdbId;
    }
    
    private boolean throwTempError() {
        return configServiceWrapper.getBooleanProperty("imdb.throwError.tempUnavailable", Boolean.TRUE);
    }
    
    @Override
    public ScanResult scanMovie(VideoData videoData) {
        try {
            final boolean throwTempError = this.throwTempError();
            String imdbId = getMovieId(videoData, throwTempError);
            
            if (StringUtils.isBlank(imdbId)) {
                LOG.debug("IMDb id not available : {}", videoData.getTitle());
                return ScanResult.MISSING_ID;
            }

            LOG.debug("IMDb id available ({}), updating movie", imdbId);
            return updateMovie(videoData, imdbId, throwTempError);
            
        } catch (TemporaryUnavailableException tue) { //NOSONAR
            // check retry
            int maxRetries = this.configServiceWrapper.getIntProperty("imdb.maxRetries.movie", 0);
            if (videoData.getRetries() < maxRetries) {
                LOG.info("IMDb service temporary not available; trigger retry: '{}'", videoData.getTitle());
                return ScanResult.RETRY;
            }
            LOG.warn("IMDb service temporary not available; no retry: '{}'", videoData.getTitle());
            return ScanResult.ERROR;
            
        } catch (IOException ioe) {
            LOG.error("IMDb service error: '" + videoData.getTitle() + "'", ioe);
            return ScanResult.ERROR;
        }
    }

    private ScanResult updateMovie(VideoData videoData, String imdbId, boolean throwTempError) throws IOException {
        Locale imdbLocale = localeService.getLocaleForConfig(SCANNER_ID);
        ImdbMovieDetails movieDetails = imdbApiWrapper.getMovieDetails(imdbId, imdbLocale);
        if (movieDetails == null || StringUtils.isBlank(movieDetails.getImdbId())) {
            return ScanResult.NO_RESULT;
        }

        // check type change
        if (!"feature".equals(movieDetails.getType())) {
            return ScanResult.TYPE_CHANGE;
        }
        
        // movie details XML is still needed for some parts
        final String xml = imdbApiWrapper.getMovieDetailsXML(imdbId, throwTempError);
        
        // update common values for movie and episodes
        updateCommonMovieEpisode(videoData, movieDetails, imdbId, imdbLocale);
        
        // ORIGINAL TITLE
        if (OverrideTools.checkOverwriteOriginalTitle(videoData, SCANNER_ID)) {
            // get header tag
            String headerXml = HTMLTools.extractTag(xml, "<h1 class=\"header\">", "</h1>");
            videoData.setTitleOriginal(parseOriginalTitle(headerXml), SCANNER_ID);
        }

        // YEAR
        if (OverrideTools.checkOverwriteYear(videoData, SCANNER_ID)) {
            videoData.setPublicationYear(movieDetails.getYear(), SCANNER_ID);
        }

        // TOP250
        Integer rank = this.imdbApiWrapper.getTop250().get(imdbId);
        if (rank != null) {
            videoData.setTopRank(rank.intValue());
        }

        // GENRES
        if (OverrideTools.checkOverwriteGenres(videoData, SCANNER_ID)) {
            videoData.setGenreNames(movieDetails.getGenres(), SCANNER_ID);
        }

        // COUNTRIES
        if (OverrideTools.checkOverwriteCountries(videoData, SCANNER_ID)) {
            videoData.setCountryCodes(parseCountryCodes(xml), SCANNER_ID);
        }

        // STUDIOS
        if (OverrideTools.checkOverwriteStudios(videoData, SCANNER_ID)) {
            videoData.setStudioNames(imdbApiWrapper.getProductionStudios(imdbId), SCANNER_ID);
        }

        // RELEASE INFO
        parseReleasedTitles(videoData, imdbId, imdbLocale);

        // AWARDS
        if (configServiceWrapper.getBooleanProperty("imdb.movie.awards", Boolean.FALSE)) {
            videoData.addAwardDTOS(imdbApiWrapper.getAwards(imdbId));
        }
        
        return ScanResult.OK;
    }


    private void updateCommonMovieEpisode(VideoData videoData, ImdbMovieDetails movieDetails, String imdbId, Locale imdbLocale) {
        // TITLE
        if (OverrideTools.checkOverwriteTitle(videoData, SCANNER_ID)) {
            videoData.setTitle(movieDetails.getTitle(), SCANNER_ID);
        }

        // RELEASE DATE
        if (MapUtils.isNotEmpty(movieDetails.getReleaseDate()) && OverrideTools.checkOverwriteReleaseDate(videoData, SCANNER_ID)) {
            final Date releaseDate = MetadataTools.parseToDate(movieDetails.getReleaseDate().get(LITERAL_NORMAL));
            videoData.setRelease(releaseDate, SCANNER_ID);
        }

        // PLOT
        if (movieDetails.getBestPlot() != null && OverrideTools.checkOverwritePlot(videoData, SCANNER_ID)) {
            videoData.setPlot(MetadataTools.cleanPlot(movieDetails.getBestPlot().getSummary()), SCANNER_ID);
        }

        // OUTLINE
        if (movieDetails.getPlot() != null && OverrideTools.checkOverwriteOutline(videoData, SCANNER_ID)) {
            videoData.setOutline(MetadataTools.cleanPlot(movieDetails.getPlot().getOutline()), SCANNER_ID);
        }

        // TAGLINE
        if (OverrideTools.checkOverwriteTagline(videoData, SCANNER_ID)) {
            videoData.setTagline(movieDetails.getTagline(), SCANNER_ID);
        }

        // QUOTE
        if (movieDetails.getQuote() != null &&
            CollectionUtils.isNotEmpty(movieDetails.getQuote().getLines()) &&
            OverrideTools.checkOverwriteQuote(videoData, SCANNER_ID))
        {
            videoData.setQuote(MetadataTools.cleanPlot(movieDetails.getQuote().getLines().get(0).getQuote()), SCANNER_ID);
        }
        
        // RATING
        videoData.addRating(SCANNER_ID, MetadataTools.parseRating(movieDetails.getRating()));

        // CERTIFICATIONS
        videoData.setCertificationInfos(imdbApiWrapper.getCertifications(imdbId, imdbLocale, movieDetails));

        // CAST/CREW
        parseCastCrew(videoData, imdbId);
    }
    
    @Override
    public ScanResult scanSeries(Series series) {
        try {
            final boolean throwTempError = this.throwTempError();
            String imdbId = getSeriesId(series, throwTempError);
            
            if (StringUtils.isBlank(imdbId)) {
                LOG.debug("IMDb id not available: {}", series.getIdentifier());
                return ScanResult.MISSING_ID;
            }

            LOG.debug("IMDb id available ({}), updating series", imdbId);
            return updateSeries(series, imdbId, throwTempError);
            
        } catch (TemporaryUnavailableException tue) { //NOSONAR
            // check retry
            int maxRetries = this.configServiceWrapper.getIntProperty("imdb.maxRetries.tvshow", 0);
            if (series.getRetries() < maxRetries) {
                LOG.info("IMDb service temporary not available; trigger retry: '{}'", series.getIdentifier());
                return ScanResult.RETRY;
            }
            LOG.warn("IMDb service temporary not available; no retry: '{}'", series.getIdentifier());
            return ScanResult.ERROR;
            
        } catch (IOException ioe) {
            LOG.error("IMDb service error: '" + series.getIdentifier() + "'", ioe);
            return ScanResult.ERROR;
        }
    }

    private ScanResult updateSeries(Series series, String imdbId, boolean throwTempError) throws IOException {
        Locale imdbLocale = localeService.getLocaleForConfig(SCANNER_ID);
        ImdbMovieDetails movieDetails = imdbApiWrapper.getMovieDetails(imdbId, imdbLocale);
        if (movieDetails == null || StringUtils.isBlank(movieDetails.getImdbId())) {
            return ScanResult.NO_RESULT;
        }
        
        // check type change
        if (!"tv_series".equals(movieDetails.getType())) {
            return ScanResult.TYPE_CHANGE;
        }

        // movie details XML is still needed for some parts
        final String xml = imdbApiWrapper.getMovieDetailsXML(imdbId, throwTempError);
        // get header tag
        final String headerXml = HTMLTools.extractTag(xml, "<h1 class=\"header\">", "</h1>");

        // TITLE
        final String title = movieDetails.getTitle(); 
        if (OverrideTools.checkOverwriteTitle(series, SCANNER_ID)) {
            series.setTitle(title, SCANNER_ID);
        }

        // START YEAR and END YEAR
        if (OverrideTools.checkOverwriteYear(series, SCANNER_ID)) {
            series.setStartYear(movieDetails.getYear(), SCANNER_ID);
            series.setEndYear(NumberUtils.toInt(movieDetails.getYearEnd(), -1), SCANNER_ID);
        }

        // PLOT
        final String plot = (movieDetails.getBestPlot() == null) ? null : MetadataTools.cleanPlot(movieDetails.getBestPlot().getSummary());
        if (OverrideTools.checkOverwritePlot(series, SCANNER_ID)) {
            series.setPlot(plot, SCANNER_ID);
        }

        // OUTLINE
        final String outline = (movieDetails.getPlot() == null) ? null : MetadataTools.cleanPlot(movieDetails.getPlot().getOutline());
        if (OverrideTools.checkOverwriteOutline(series, SCANNER_ID)) {
            series.setOutline(outline, SCANNER_ID);
        }
        
        // ORIGINAL TITLE
        final String titleOriginal = parseOriginalTitle(headerXml);
        if (OverrideTools.checkOverwriteOriginalTitle(series, SCANNER_ID)) {
            series.setTitleOriginal(titleOriginal, SCANNER_ID);
        }

        // GENRES
        if (OverrideTools.checkOverwriteGenres(series, SCANNER_ID)) {
            series.setGenreNames(movieDetails.getGenres(), SCANNER_ID);
        }

        // STUDIOS
        if (OverrideTools.checkOverwriteStudios(series, SCANNER_ID)) {
            series.setStudioNames(imdbApiWrapper.getProductionStudios(imdbId), SCANNER_ID);
        }

        // COUNTRIES
        if (OverrideTools.checkOverwriteCountries(series, SCANNER_ID)) {
            series.setCountryCodes(parseCountryCodes(xml), SCANNER_ID);
        }

        // CERTIFICATIONS
        series.setCertificationInfos(imdbApiWrapper.getCertifications(imdbId, imdbLocale, movieDetails));

        // RELEASE INFO
        parseReleasedTitles(series, imdbId, imdbLocale);

        // AWARDS
        if (configServiceWrapper.getBooleanProperty("imdb.tvshow.awards", Boolean.FALSE)) {
            series.addAwardDTOS(imdbApiWrapper.getAwards(imdbId));
        }

        // scan seasons
        this.scanSeasons(series, imdbId, title, titleOriginal, plot, outline, imdbLocale);

        return ScanResult.OK;
    }

    private void scanSeasons(Series series, String imdbId, String title, String titleOriginal, String plot, String outline, Locale imdbLocale) {
        for (Season season : series.getSeasons()) {

            // get the episodes
            Map<Integer, ImdbEpisodeDTO> episodes = getEpisodes(imdbId, season.getSeason(), imdbLocale);

            if (!season.isTvSeasonDone(SCANNER_ID)) {

                // use values from series
                if (OverrideTools.checkOverwriteTitle(season, SCANNER_ID)) {
                    season.setTitle(title, SCANNER_ID);
                }
                if (OverrideTools.checkOverwriteOriginalTitle(season, SCANNER_ID)) {
                    season.setTitleOriginal(titleOriginal, SCANNER_ID);
                }
                if (OverrideTools.checkOverwritePlot(season, SCANNER_ID)) {
                    season.setPlot(plot, SCANNER_ID);
                }
                if (OverrideTools.checkOverwriteOutline(season, SCANNER_ID)) {
                    season.setOutline(outline, SCANNER_ID);
                }

                if (OverrideTools.checkOverwriteYear(season, SCANNER_ID)) {
                    Date publicationYear = null;
                    for (ImdbEpisodeDTO episode : episodes.values()) {
                        if (publicationYear == null) {
                            publicationYear = episode.getReleaseDate();
                        } else if (episode.getReleaseDate() != null && publicationYear.after(episode.getReleaseDate())) {
                            // previous episode
                            publicationYear = episode.getReleaseDate();
                        }
                    }
                    season.setPublicationYear(MetadataTools.extractYearAsInt(publicationYear), SCANNER_ID);
                }

                // mark season as done
                season.setTvSeasonDone();

                // scan episodes
                for (VideoData videoData : season.getVideoDatas()) {
                    this.scanEpisode(videoData, episodes, imdbLocale);
                }
            }
        }
    }

    private void scanEpisode(VideoData videoData, Map<Integer, ImdbEpisodeDTO> episodes, Locale imdbLocale) {
        if (videoData.isTvEpisodeDone(SCANNER_ID)) {
            // episode already done
            return;
        }
        
        ImdbEpisodeDTO dto = episodes.get(Integer.valueOf(videoData.getEpisode()));
        if (dto == null) {
            // mark episode as not found
            videoData.removeOverrideSource(SCANNER_ID);
            videoData.removeSourceDbId(SCANNER_ID);
            videoData.setTvEpisodeNotFound();
            return;
        }

        videoData.setSourceDbId(SCANNER_ID, dto.getImdbId());

        // set other values
        if (OverrideTools.checkOverwriteTitle(videoData, SCANNER_ID)) {
            videoData.setTitle(dto.getTitle(), SCANNER_ID);
        }
        if (OverrideTools.checkOverwriteReleaseDate(videoData, SCANNER_ID)) {
            videoData.setRelease(dto.getReleaseCountry(), dto.getReleaseDate(), SCANNER_ID);
        }

        // get movie details from IMDB
        ImdbMovieDetails movieDetails = imdbApiWrapper.getMovieDetails(dto.getImdbId(), imdbLocale);
        if (movieDetails == null || StringUtils.isBlank(movieDetails.getImdbId())) {
            videoData.setTvEpisodeNotFound();
            return;
        }
        
        // update common values for movie and episodes
        updateCommonMovieEpisode(videoData, movieDetails, dto.getImdbId(), imdbLocale);
        
        // ORIGINAL TITLE
        if (OverrideTools.checkOverwriteOriginalTitle(videoData, SCANNER_ID)) {
            // no original title present; so always get the title
            videoData.setTitleOriginal(movieDetails.getTitle(), SCANNER_ID);
        }
    }

    private Map<Integer, ImdbEpisodeDTO> getEpisodes(String imdbId, int season, Locale imdbLocale) {
        Map<Integer, ImdbEpisodeDTO> episodes = new HashMap<>();
        
        List<ImdbEpisodeDTO> episodeList = imdbApiWrapper.getTitleEpisodes(imdbId, imdbLocale).get(Integer.valueOf(season));
        if (episodeList != null) {
            for (ImdbEpisodeDTO episode : episodeList) {
                episodes.put(Integer.valueOf(episode.getEpisode()), episode);
            }
        }
        return episodes;
    }

    private void parseReleasedTitles(AbstractMetadata metadata, String imdbId, Locale locale) {
        
        // get the AKS
        Map<String, String> akas = getAkaMap(imdbId);
        if (MapUtils.isEmpty(akas)) {
            return;
        }
        
        // ORIGINAL TITLE
        if (OverrideTools.checkOverwriteOriginalTitle(metadata, SCANNER_ID)) {
            // get the AKAs from release info XML
            for (Map.Entry<String, String> aka : akas.entrySet()) {
                if (StringUtils.indexOfIgnoreCase(aka.getKey(), "original title") > 0) {
                    metadata.setTitleOriginal(aka.getValue().trim(), SCANNER_ID);
                    break;
                }
            }
        }

        // TITLE for preferred country from AKAS
        boolean akaScrapeTitle = configServiceWrapper.getBooleanProperty("imdb.aka.scrape.title", Boolean.FALSE);
        if (!akaScrapeTitle || !OverrideTools.checkOverwriteTitle(metadata, SCANNER_ID)) {
            return;
        }
        
        List<String> akaIgnoreVersions = configServiceWrapper.getPropertyAsList("imdb.aka.ignore.versions", "");

        // build countries to search for within AKA list
        Set<String> akaMatchingCountries = new TreeSet<>(localeService.getCountryNames(locale.getCountry()));
        for (String fallback : configServiceWrapper.getPropertyAsList("imdb.aka.fallback.countries", "")) {
            String countryCode = localeService.findCountryCode(fallback);
            akaMatchingCountries.addAll(localeService.getCountryNames(countryCode));
        }

        String foundValue = null;
        // NOTE: First matching country is the preferred country
        outerLoop: for (String matchCountry : akaMatchingCountries) {
            innerLoop: for (Map.Entry<String, String> aka : akas.entrySet()) {
                int startIndex = aka.getKey().indexOf(matchCountry);
                if (startIndex < 0) {
                    continue innerLoop;
                }

                String extracted = aka.getKey().substring(startIndex);
                int endIndex = extracted.indexOf('/');
                if (endIndex > -1) {
                    extracted = extracted.substring(0, endIndex);
                }

                if (isNotIgnored(extracted, akaIgnoreVersions)) {
                    foundValue = aka.getValue().trim();
                    break outerLoop;
                }
            }
        }
        metadata.setTitle(foundValue, SCANNER_ID);
    }
    
    private static final boolean isNotIgnored(String value, List<String> ignoreVersions) {
        for (String ignore : ignoreVersions) {
            if (StringUtils.isNotBlank(ignore) && StringUtils.containsIgnoreCase(value, ignore.trim())) {
                return false;
            }
        }
        return true;
    }
    
    private Map<String, String> getAkaMap(String imdbId) {
        String releaseInfoXML = imdbApiWrapper.getReleasInfoXML(imdbId);
        if (releaseInfoXML != null) {
            // Just extract the AKA section from the page
            List<String> akaList = HTMLTools.extractTags(releaseInfoXML, "<a id=\"akas\" name=\"akas\">", HTML_TABLE_END, "<td>", HTML_TD_END, Boolean.FALSE);
            return buildAkaMap(akaList);
        }
        return null;
    }

    private static String parseOriginalTitle(String xml) {
       return HTMLTools.extractTag(xml, "<span class=\"title-extra\">", "</span>")
                       .replaceFirst("<i>(original title)</i>", StringUtils.EMPTY)
                       .replace("\"", StringUtils.EMPTY)
                       .trim();
    }

    private Set<String> parseCountryCodes(String xml) {
        Set<String> countryCodes = new HashSet<>();
        for (String country : HTMLTools.extractTags(xml, "Country" + HTML_H4_END, HTML_DIV_END, "<a href=\"", HTML_A_END)) {
            final String countryCode = localeService.findCountryCode(HTMLTools.removeHtmlTags(country));
            if (countryCode != null) {
                countryCodes.add(countryCode);
            }
        }
        return countryCodes;
    }

    /**
     * Create a map of the AKA values
     *
     * @param list
     * @return
     */
    private static Map<String, String> buildAkaMap(List<String> list) {
        Map<String, String> map = new LinkedHashMap<>();
        int i = 0;
        do {
            try {
                String key = list.get(i++);
                String value = list.get(i++);
                map.put(key, value);
            } catch (Exception ignore) { //NOSONAR
                i = -1;
            }
        } while (i != -1);
        return map;
    }

    private void parseCastCrew(VideoData videoData, String imdbId) {
        List<ImdbCredit> fullCast = imdbApiWrapper.getFullCast(imdbId);
        
        if (CollectionUtils.isEmpty(fullCast)) {
            LOG.info("No cast for imdb ID: {}", imdbId);
            return;
        }

        // build jobs map
        EnumMap<JobType,List<ImdbCast>> jobs = getJobs(fullCast);
        // get configuration parameters
        boolean skipFaceless = configServiceWrapper.getBooleanProperty("yamj3.castcrew.skip.faceless", Boolean.FALSE);
        boolean skipUncredited = configServiceWrapper.getBooleanProperty("yamj3.castcrew.skip.uncredited", Boolean.TRUE);
        
        // add credits
        addCredits(videoData, JobType.DIRECTOR, jobs, skipUncredited, skipFaceless);
        addCredits(videoData, JobType.WRITER, jobs, skipUncredited, skipFaceless);
        addCredits(videoData, JobType.ACTOR, jobs, skipUncredited, skipFaceless);
        addCredits(videoData, JobType.PRODUCER, jobs, skipUncredited, skipFaceless);
        addCredits(videoData, JobType.CAMERA, jobs, skipUncredited, skipFaceless);
        addCredits(videoData, JobType.EDITING, jobs, skipUncredited, skipFaceless);
        addCredits(videoData, JobType.ART, jobs, skipUncredited, skipFaceless);
        addCredits(videoData, JobType.SOUND, jobs, skipUncredited, skipFaceless);
        addCredits(videoData, JobType.EFFECTS, jobs, skipUncredited, skipFaceless);
        addCredits(videoData, JobType.LIGHTING, jobs, skipUncredited, skipFaceless);
        addCredits(videoData, JobType.COSTUME_MAKEUP, jobs, skipUncredited, skipFaceless);
        addCredits(videoData, JobType.CREW, jobs, skipUncredited, skipFaceless);
        addCredits(videoData, JobType.UNKNOWN, jobs, skipUncredited, skipFaceless);
    }

    private static EnumMap<JobType,List<ImdbCast>>  getJobs(List<ImdbCredit> credits) {
        EnumMap<JobType,List<ImdbCast>> result = new EnumMap<>(JobType.class);
        
        for (ImdbCredit credit : credits) {
            if (CollectionUtils.isEmpty(credit.getCredits())) {
                continue;
            }
            
            switch (credit.getToken()) {
                case "cast":
                    result.put(JobType.ACTOR, credit.getCredits());
                    break;
                case "writers":
                    result.put(JobType.WRITER, credit.getCredits());
                    break;
                case "directors":
                    result.put(JobType.DIRECTOR, credit.getCredits());
                    break;
                case "cinematographers":
                    result.put(JobType.CAMERA, credit.getCredits());
                    break;
                case "editors":
                    result.put(JobType.EDITING, credit.getCredits());
                    break;
                case "producers":
                case "casting_directors":
                    if (result.containsKey(JobType.PRODUCER)) {
                        result.get(JobType.PRODUCER).addAll(credit.getCredits());
                    } else {
                        result.put(JobType.PRODUCER, credit.getCredits());
                    }
                    break;
                case "music_original":
                    result.put(JobType.SOUND, credit.getCredits());
                    break;
                case "production_designers":
                case "art_directors":
                case "set_decorators":
                    if (result.containsKey(JobType.ART)) {
                        result.get(JobType.ART).addAll(credit.getCredits());
                    } else {
                        result.put(JobType.ART, credit.getCredits());
                    }
                    break;
                case "costume_designers":
                    if (result.containsKey(JobType.COSTUME_MAKEUP)) {
                        result.get(JobType.COSTUME_MAKEUP).addAll(credit.getCredits());
                    } else {
                        result.put(JobType.COSTUME_MAKEUP, credit.getCredits());
                    }
                    break;
                case "assistant_directors":
                case "production_managers":
                case "art_department":
                case "sound_department":
                case "special_effects_department":
                case "visual_effects_department":
                case "stunts":
                case "camera_department":
                case "animation_department":
                case "casting_department":
                case "costume_department":
                case "editorial_department":
                case "music_department":
                case "transportation_department":
                case "make_up_department":
                case "miscellaneous":
                    if (result.containsKey(JobType.CREW)) {
                        result.get(JobType.CREW).addAll(credit.getCredits());
                    } else {
                        result.put(JobType.CREW, credit.getCredits());
                    }
                    break;
                default:
                    if (result.containsKey(JobType.UNKNOWN)) {
                        result.get(JobType.UNKNOWN).addAll(credit.getCredits());
                    } else {
                        result.put(JobType.UNKNOWN, credit.getCredits());
                    }
                    break;
            }
        }
        
        return result;
    }
    
    private void addCredits(VideoData videoData, JobType jobType, EnumMap<JobType,List<ImdbCast>> jobs, boolean skipUncredited, boolean skipFaceless) {
        if (CollectionUtils.isEmpty(jobs.get(jobType))) {
            return;
        }
        if (!this.configServiceWrapper.isCastScanEnabled(jobType)) {
            return;
        }
            
        for (ImdbCast cast : jobs.get(jobType)) {
            final ImdbPerson person = cast.getPerson();
            if (person == null || StringUtils.isBlank(person.getName())) {
                continue; //NOSONAR
            }
            
            if (skipUncredited && StringUtils.contains(cast.getAttr(), "(uncredited")) {
                continue; //NOSONAR
            }

            final String photoURL = (person.getImage() == null) ? null : person.getImage().getUrl();
            if (skipFaceless && JobType.ACTOR.equals(jobType) && StringUtils.isEmpty(photoURL)) {
                // skip faceless actors only
                continue; //NOSONAR
            }
            
            CreditDTO creditDTO = new CreditDTO(SCANNER_ID, person.getActorId(), jobType, person.getName());
            creditDTO.setRole(MetadataTools.cleanRole(cast.getCharacter()));
            creditDTO.setVoice(MetadataTools.isVoiceRole(cast.getAttr()));
            videoData.addCreditDTO(creditDTO);
        }
    }

    @Override
    public boolean scanNFO(String nfoContent, InfoDTO dto, boolean ignorePresentId) {
        return scanImdbID(nfoContent, dto, ignorePresentId);
    }

    public static boolean scanImdbID(String nfoContent, InfoDTO dto, boolean ignorePresentId) {
        // if we already have the ID, skip the scanning of the NFO file
        if (!ignorePresentId && StringUtils.isNotBlank(dto.getId(SCANNER_ID))) {
            return Boolean.TRUE;
        }

        LOG.trace("Scanning NFO for IMDb ID");

        try {
            int beginIndex = nfoContent.indexOf("/tt");
            if (beginIndex != -1) {
                StringTokenizer st = new StringTokenizer(nfoContent.substring(beginIndex + 1), "/ \n,:!&Ã©\"'(--Ã¨_Ã§Ã )=$");
                String sourceId = st.nextToken();
                LOG.debug("IMDb ID found in NFO: {}", sourceId);
                dto.addId(SCANNER_ID, sourceId);
                return Boolean.TRUE;
            }
        } catch (Exception ex) {
            LOG.trace("NFO scanning error", ex);
        }

        try {
            int beginIndex = nfoContent.indexOf("/Title?");
            if (beginIndex != -1 && beginIndex + 7 < nfoContent.length()) {
                StringTokenizer st = new StringTokenizer(nfoContent.substring(beginIndex + 7), "/ \n,:!&Ã©\"'(--Ã¨_Ã§Ã )=$");
                String sourceId = "tt" + st.nextToken();
                LOG.debug("IMDb ID found in NFO: {}", sourceId);
                dto.addId(SCANNER_ID, sourceId);
                return Boolean.TRUE;
            }
        } catch (Exception ex) {
            LOG.trace("NFO scanning error", ex);
        }

        LOG.debug("No IMDb ID found in NFO");
        return Boolean.FALSE;
    }

    @Override
    public ScanResult scanPerson(Person person) {
        try {
            final boolean throwTempError = this.throwTempError();
            String imdbId = getPersonId(person, throwTempError);
            
            if (StringUtils.isBlank(imdbId)) {
                LOG.debug("IMDb id not available: {}", person.getName());
                return ScanResult.MISSING_ID;
            }

            LOG.debug("IMDb id available ({}), updating person", imdbId);
            return updatePerson(person, imdbId, throwTempError);
            
        } catch (TemporaryUnavailableException tue) { //NOSONAR
            // check retry
            int maxRetries = this.configServiceWrapper.getIntProperty("imdb.maxRetries.person", 0);
            if (person.getRetries() < maxRetries) {
                LOG.info("IMDb service temporary not available; trigger retry: '{}'", person.getName());
                return ScanResult.RETRY;
            }
            LOG.warn("IMDb service temporary not available; no retry: '{}'", person.getName());
            return ScanResult.ERROR;
            
        } catch (IOException ioe) {
            LOG.error("IMDb service error: '" + person.getName() + "'", ioe);
            return ScanResult.ERROR;
        }
    }

    private ScanResult updatePerson(Person person, String imdbId, boolean throwTempError) throws IOException {
        Locale imdbLocale = localeService.getLocaleForConfig(SCANNER_ID);
        ImdbPerson imdbPerson = imdbApiWrapper.getPerson(imdbId, imdbLocale);
        if (StringUtils.isBlank(imdbPerson.getActorId())) {
            return ScanResult.NO_RESULT;
        }
        
        // split person names
        PersonNameDTO nameDTO = MetadataTools.splitFullName(imdbPerson.getName());
        if (OverrideTools.checkOverwriteName(person, SCANNER_ID)) {
            person.setName(nameDTO.getName(), SCANNER_ID);
        }
        if (OverrideTools.checkOverwriteFirstName(person, SCANNER_ID)) {
            person.setFirstName(nameDTO.getFirstName(), SCANNER_ID);
        }
        if (OverrideTools.checkOverwriteLastName(person, SCANNER_ID)) {
            person.setLastName(nameDTO.getLastName(), SCANNER_ID);
        }
        if (OverrideTools.checkOverwriteBirthName(person, SCANNER_ID)) {
            person.setBirthName(imdbPerson.getRealName(), SCANNER_ID);
        }
        
        if (OverrideTools.checkOverwriteBiography(person, SCANNER_ID)) {
            final String apiBio = MetadataTools.cleanBiography(imdbPerson.getBiography());
            if (StringUtils.isNotBlank(apiBio)) {
                person.setBiography(apiBio, SCANNER_ID);
            } else {
                // try biography from web site
                final String bio = imdbApiWrapper.getPersonBioXML(imdbId, throwTempError);
                if (bio.contains(">Mini Bio (1)</h4>")) {
                    String biography = HTMLTools.extractTag(bio, ">Mini Bio (1)</h4>", "<em>- IMDb Mini Biography");
                    if (StringUtils.isBlank(biography) && bio.contains("<a name=\"trivia\">")) {
                        biography = HTMLTools.extractTag(bio, ">Mini Bio (1)</h4>", "<a name=\"trivia\">");
                    }
                    person.setBiography(HTMLTools.removeHtmlTags(biography), SCANNER_ID);
                }
            }
        }
        
        if (imdbPerson.getBirth() != null) {
            if (imdbPerson.getBirth().getDate() != null && OverrideTools.checkOverwriteBirthDay(person, SCANNER_ID)) {
                final String birthDay = imdbPerson.getBirth().getDate().get(LITERAL_NORMAL);
                person.setBirthDay(MetadataTools.parseToDate(birthDay), SCANNER_ID);
            }

            if (OverrideTools.checkOverwriteBirthPlace(person, SCANNER_ID)) {
                person.setBirthPlace(imdbPerson.getBirth().getPlace(), SCANNER_ID);
            }
        }

        if (imdbPerson.getDeath() != null) {
            if (imdbPerson.getDeath().getDate() != null && OverrideTools.checkOverwriteDeathDay(person, SCANNER_ID)) {
                final String deathDay = imdbPerson.getDeath().getDate().get(LITERAL_NORMAL);
                person.setDeathDay(MetadataTools.parseToDate(deathDay), SCANNER_ID);
            }

            if (OverrideTools.checkOverwriteDeathPlace(person, SCANNER_ID)) {
                person.setDeathPlace(imdbPerson.getDeath().getPlace(), SCANNER_ID);
            }
        }

        return ScanResult.OK;
    }
}
