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
package org.yamj.core.api.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import java.util.*;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.yamj.core.database.model.Studio;
import org.yamj.core.database.model.type.JobType;

/**
 * @author stuart.boston
 */
@JsonInclude(Include.NON_DEFAULT) 
public class ApiEpisodeDTO extends AbstractApiIdentifiableDTO {

    private Long seriesId = -1L;
    private Long seasonId = -1L;
    private Long season = -1L;
    private Long episode = -1L;
    private String title;
    private String originalTitle;
    private String outline;
    private String plot;
    private Date firstAired;
    private Boolean watched;
    private String cacheFilename;
    private String cacheDir;
    private String videoimage;
    private List<ApiGenreDTO> genres = Collections.emptyList();
    private List<Studio> studios = Collections.emptyList();
    private List<ApiCountryDTO> countries = Collections.emptyList();
    private List<ApiCertificationDTO> certifications = Collections.emptyList();
    private List<ApiRatingDTO> ratings = Collections.emptyList();
    private List<ApiAwardDTO> awards = Collections.emptyList();
    private List<ApiFileDTO> files = Collections.emptyList();
    private final Map<JobType,List<ApiPersonDTO>> cast = new EnumMap<>(JobType.class);

    //<editor-fold defaultstate="collapsed" desc="Setter Methods">
    public void setSeriesId(Long seriesId) {
        this.seriesId = seriesId;
    }

    public void setSeasonId(Long seasonId) {
        this.seasonId = seasonId;
    }

    public void setSeason(Long season) {
        this.season = season;
    }

    public void setEpisode(Long episode) {
        this.episode = episode;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public void setOriginalTitle(String originalTitle) {
        this.originalTitle = originalTitle;
    }

    public void setOutline(String outline) {
        this.outline = outline;
    }

    public void setPlot(String plot) {
        this.plot = plot;
    }
    
    public void setFirstAired(Date firstAired) {
        this.firstAired = firstAired;
    }

    public Boolean getWatched() {
        return watched;
    }

    public void setWatched(Boolean watched) {
        this.watched = watched;
    }

    @JsonIgnore
    public void setCacheFilename(String cacheFilename) {
        this.cacheFilename = cacheFilename;
    }

    @JsonIgnore
    public void setCacheDir(String cacheDir) {
        this.cacheDir = cacheDir;
    }

    public void setGenres(List<ApiGenreDTO> genres) {
        this.genres = genres;
    }

    public void setStudios(List<Studio> studios) {
        this.studios = studios;
    }

    public void setCountries(List<ApiCountryDTO> countries) {
        this.countries = countries;
    }

    public void setCertifications(List<ApiCertificationDTO> certifications) {
        this.certifications = certifications;
    }
    
    public void setRatings(List<ApiRatingDTO> ratings) {
        this.ratings = ratings;
    }

    public void setAwards(List<ApiAwardDTO> awards) {
        this.awards = awards;
    }

    public void setFiles(List<ApiFileDTO> files) {
        this.files = files;
    }
    
    public void setCast(List<ApiPersonDTO> castList) {
        for (ApiPersonDTO acdto : castList) {
            addCast(acdto);
        }
    }
    //</editor-fold>

    //<editor-fold defaultstate="collapsed" desc="Getter Methods">
    public Long getSeriesId() {
        return seriesId;
    }

    public Long getSeasonId() {
        return seasonId;
    }

    public Long getSeason() {
        return season;
    }

    public Long getEpisode() {
        return episode;
    }

    public String getTitle() {
        return title;
    }

    public String getOriginalTitle() {
        return originalTitle;
    }

    public String getOutline() {
        return outline;
    }

    public String getPlot() {
        return plot;
    }

    public Date getFirstAired() {
        return firstAired;
    }

    public String getVideoimage() {
        if (StringUtils.isBlank(videoimage) && (StringUtils.isNotBlank(cacheDir) && StringUtils.isNotBlank(cacheFilename))) {
            this.videoimage = FilenameUtils.normalize(FilenameUtils.concat(this.cacheDir, this.cacheFilename), Boolean.TRUE);
        }
        return videoimage;
    }

    public List<ApiGenreDTO> getGenres() {
        return genres;
    }

    public List<Studio> getStudios() {
        return studios;
    }
    
    public List<ApiCountryDTO> getCountries() {
        return countries;
    }
    
    public List<ApiCertificationDTO> getCertifications() {
        return certifications;
    }
    
    public List<ApiRatingDTO> getRatings() {
        return ratings;
    }

    public List<ApiAwardDTO> getAwards() {
        return awards;
    }

    public List<ApiFileDTO> getFiles() {
        return files;
    }
    
    public Map<JobType, List<ApiPersonDTO>> getCast() {
        return cast;
    }
    //</editor-fold>

    public void addCast(ApiPersonDTO newCast) {
        // Add a blank list if it doesn't already exist
        if (!cast.containsKey(newCast.getJob())) {
            cast.put(newCast.getJob(), new ArrayList<ApiPersonDTO>(1));
        }
        this.cast.get(newCast.getJob()).add(newCast);
    }
}
