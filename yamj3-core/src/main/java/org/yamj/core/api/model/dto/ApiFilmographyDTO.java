/*
 *      Copyright (c) 2004-2014 YAMJ Members
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

import java.util.Date;
import org.apache.commons.lang3.StringUtils;
import org.yamj.core.database.model.type.ParticipationType;

public class ApiFilmographyDTO extends AbstractApiDTO {

    private ParticipationType type;
    private String job;
    private String role = "";
    private String title = "";
    private String originalTitle = "";
    private Integer year;
    private Integer yearEnd;
    private Date releaseDate;
    private String releaseState = "";
    private String description = "";
    private Long videoDataId = -1L;
    private Long seriesId = -1L;

    public ParticipationType getType() {
        return type;
    }

    public void setType(ParticipationType type) {
        this.type = type;
    }

    public void setTypeString(String type) {
        setType(ParticipationType.fromString(type));
    }

    public String getJob() {
        return job;
    }

    public void setJob(String job) {
        this.job = job;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        if (StringUtils.isBlank(role)) {
            this.role = "";
        } else {
            this.role = role;
        }
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        if (StringUtils.isBlank(title)) {
            this.title = "";
        } else {
            this.title = title;
        }
    }

    public String getOriginalTitle() {
        return originalTitle;
    }

    public void setOriginalTitle(String originalTitle) {
        if (StringUtils.isBlank(originalTitle)) {
            this.originalTitle = "";
        } else {
            this.originalTitle = originalTitle;
        }
    }

    public Integer getYear() {
        return year;
    }

    public void setYear(Integer year) {
        this.year = year;
    }

    public Integer getYearEnd() {
        return yearEnd;
    }

    public void setYearEnd(Integer yearEnd) {
        this.yearEnd = yearEnd;
    }

    public Date getReleaseDate() {
        return releaseDate;
    }

    public void setReleaseDate(Date releaseDate) {
        this.releaseDate = releaseDate;
    }

    public String getReleaseState() {
        return releaseState;
    }

    public void setReleaseState(String releaseState) {
        if (StringUtils.isBlank(releaseState)) {
            this.releaseState = "";
        } else {
            this.releaseState = releaseState;
        }
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        if (StringUtils.isBlank(description)) {
            this.description = "";
        } else {
            this.description = description;
        }
    }

    public Long getVideoDataId() {
        return videoDataId;
    }

    public void setVideoDataId(Long videoDataId) {
        if (videoDataId == null) {
            videoDataId = Long.valueOf(0);
        } else {
            this.videoDataId = videoDataId;
        }
    }

    public Long getSeriesId() {
        return seriesId;
    }

    public void setSeriesId(Long seriesId) {
        if (seriesId == null) {
            seriesId = Long.valueOf(0);
        } else {
            this.seriesId = seriesId;
        }
    }
}