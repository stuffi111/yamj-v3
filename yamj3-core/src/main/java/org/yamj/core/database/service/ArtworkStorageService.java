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
package org.yamj.core.database.service;

import org.yamj.core.database.model.type.ArtworkCategory;
import org.yamj.core.database.model.type.ArtworkType;

import java.util.List;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.support.DataAccessUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.yamj.common.type.StatusType;
import org.yamj.core.database.dao.ArtworkDao;
import org.yamj.core.database.model.Artwork;
import org.yamj.core.database.model.ArtworkGenerated;
import org.yamj.core.database.model.ArtworkLocated;
import org.yamj.core.database.model.ArtworkProfile;
import org.yamj.core.database.model.dto.QueueDTO;

@Service("artworkStorageService")
public class ArtworkStorageService {

    private static final Logger LOG = LoggerFactory.getLogger(ArtworkStorageService.class);

    @Autowired
    private ArtworkDao artworkDao;

    @Transactional
    public void storeArtworkProfile(ArtworkProfile newProfile) { 
        ArtworkProfile profile = artworkDao.getArtworkProfile(newProfile.getProfileName(), newProfile.getArtworkType());
        if (profile == null) {
            this.artworkDao.saveEntity(newProfile);
            LOG.info("Stored new artwork profile {}", newProfile);
        } else {           
            // TODO what to do if profile changes in size and height?
            
            if (profile.hasRelevantChanges(newProfile)) {
                LOG.warn("Artwork profile update with relevant changes: {}", profile.getProfileName());
            }
            
            profile.setHeight(newProfile.getHeight());
            profile.setWidth(newProfile.getWidth());
            profile.setApplyToMovie(newProfile.isApplyToMovie());
            profile.setApplyToSeries(newProfile.isApplyToSeries());
            profile.setApplyToSeason(newProfile.isApplyToSeason());
            profile.setApplyToEpisode(newProfile.isApplyToEpisode());
            profile.setPreProcess(newProfile.isPreProcess());
            this.artworkDao.saveEntity(profile);
            LOG.info("Updated artwork profile {}", profile);
        }
    }

    @Transactional(readOnly = true)
    public List<ArtworkProfile> getPreProcessArtworkProfiles(ArtworkLocated located) {
        ArtworkCategory category = null;

        ArtworkType artworkType = located.getArtwork().getArtworkType();
        if (ArtworkType.PHOTO == artworkType) {
            // nothing
        } else if (ArtworkType.VIDEOIMAGE == artworkType) {
            category = ArtworkCategory.EPISODE;
        } else if (ArtworkType.BANNER == artworkType) {
            if (located.getArtwork().getSeries() != null) {
                category = ArtworkCategory.SERIES;
            } else {
                category = ArtworkCategory.SEASON;
            }
        } else if (ArtworkType.POSTER == artworkType || (ArtworkType.FANART == artworkType)) {
            if (located.getArtwork().getSeries() != null) {
                category = ArtworkCategory.SERIES;
            } else if (located.getArtwork().getSeason() != null) {
                category = ArtworkCategory.SEASON;
            } else {
                category = ArtworkCategory.MOVIE;
            }
        }
        
       return this.artworkDao.getPreProcessArtworkProfiles(artworkType, category);
    }
    
    @Transactional
    public void saveArtwork(Artwork artwork) {
        this.artworkDao.saveEntity(artwork);
    }

    @Transactional
    public void updateArtwork(Artwork artwork, List<ArtworkLocated> located) {
        // TODO update of existing artwork needed?
        this.artworkDao.storeAll(located);
        this.artworkDao.updateEntity(artwork);
    }

    @Transactional(readOnly = true)
    public List<QueueDTO> getArtworkQueueForScanning(final int maxResults) {
        final StringBuilder sql = new StringBuilder();
        sql.append("select distinct art.id,art.artwork_type,art.create_timestamp,art.update_timestamp ");
        sql.append("from artwork art ");
        sql.append("left outer join videodata vd on vd.id=art.videodata_id ");
        sql.append("left outer join season sea on sea.id=art.season_id ");
        sql.append("left outer join series ser on ser.id=art.series_id ");
        sql.append("where art.status = 'NEW' ");
        sql.append("and (vd.status is null or vd.status='DONE') ");
        sql.append("and (sea.status is null or sea.status='DONE') ");
        sql.append("and (ser.status is null or ser.status='DONE') ");

        return artworkDao.getArtworkQueue(sql, maxResults);
    }

    @Transactional(readOnly = true)
    public Artwork getRequiredArtwork(Long id) {
        final StringBuilder sb = new StringBuilder();
        sb.append("from Artwork art ");
        sb.append("left outer join fetch art.videoData ");
        sb.append("left outer join fetch art.season sea ");
        sb.append("left outer join fetch art.series ser ");
        sb.append("where art.id = :id");

        @SuppressWarnings("unchecked")
        List<Artwork> objects = this.artworkDao.findById(sb, id);
        Artwork artwork = DataAccessUtils.requiredUniqueResult(objects);

        if (artwork.getSeason() != null) {
            // also initialize series
            if (!Hibernate.isInitialized(artwork.getSeason().getSeries())) {
                Hibernate.initialize(artwork.getSeason().getSeries());
            }
        } else if (artwork.getVideoData() != null && artwork.getVideoData().getSeason() != null) {
            // also initialize season and series
            if (!Hibernate.isInitialized(artwork.getVideoData().getSeason())) {
                Hibernate.initialize(artwork.getVideoData().getSeason());
            }
            if (!Hibernate.isInitialized(artwork.getVideoData().getSeason().getSeries())) {
                Hibernate.initialize(artwork.getVideoData().getSeason().getSeries());
            }
        }
        return artwork;
    }

    @Transactional
    public void storeLocatedArtworks(Artwork artwork, List<String> urls) {
        for (String url : urls) {
            ArtworkLocated located = new ArtworkLocated();
            located.setArtwork(artwork);
            located.setUrl(url);
            located.setStatus(StatusType.NEW);
            artworkDao.saveEntity(located);
        }
    }
    
    @Transactional
    public void errorArtwork(Long id) {
         Artwork artwork = artworkDao.getArtwork(id);
         if (artwork != null) {
            artwork.setStatus(StatusType.ERROR);
            artworkDao.updateEntity(artwork);
        }
    }

    @Transactional(readOnly = true)
    public List<QueueDTO> getArtworLocatedQueue(final int maxResults) {
        final StringBuilder sql = new StringBuilder();
        sql.append("select distinct loc.id,loc.create_timestamp,loc.update_timestamp ");
        sql.append("from artwork_located loc ");
        sql.append("where loc.status = 'NEW' ");

        return artworkDao.getArtworkLocatedQueue(sql, maxResults);
    }

    @Transactional(readOnly = true)
    public ArtworkLocated getRequiredArtworkLocated(Long id) {
        final StringBuilder sb = new StringBuilder();
        sb.append("from ArtworkLocated loc ");
        sb.append("join fetch loc.artwork art ");
        sb.append("left outer join fetch art.videoData ");
        sb.append("left outer join fetch art.season ");
        sb.append("left outer join fetch art.series ");
        sb.append("where loc.id = :id");

        @SuppressWarnings("unchecked")
        List<ArtworkLocated> objects = this.artworkDao.findById(sb, id);
        return DataAccessUtils.requiredUniqueResult(objects);
    }

    @Transactional
    public void errorArtworkLocated(Long id) {
        ArtworkLocated located = artworkDao.getArtworkLocated(id);
         if (located != null) {
             located.setStatus(StatusType.ERROR);
            artworkDao.updateEntity(located);
        }
    }

    @Transactional
    public void updateArtworkLocated(ArtworkLocated located) {
        this.artworkDao.updateEntity(located);
    }

    @Transactional
    public void storeArtworkGenerated(ArtworkGenerated generated) {
        this.artworkDao.saveEntity(generated);
    }
}