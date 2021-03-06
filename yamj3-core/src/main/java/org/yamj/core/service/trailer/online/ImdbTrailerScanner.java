/*
 *      Copyright (c) 2004-2015 YAMJ Members
 *      https://github.com/orgs/YAMJ/people
 *
 *      This file is part of the Yet Another Movie Jukebox (YAMJ) project.
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
package org.yamj.core.service.trailer.online;

import com.omertron.imdbapi.model.ImdbEncodingFormat;
import com.omertron.imdbapi.model.ImdbMovieDetails;
import java.util.Collections;
import java.util.List;
import javax.annotation.PostConstruct;
import org.apache.commons.collections.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.yamj.core.database.model.Series;
import org.yamj.core.database.model.VideoData;
import org.yamj.core.database.model.dto.TrailerDTO;
import org.yamj.core.database.model.type.ContainerType;
import org.yamj.core.service.metadata.online.ImdbScanner;
import org.yamj.core.service.trailer.TrailerScannerService;
import org.yamj.core.web.apis.ImdbApiWrapper;

@Service("imdbTrailerScanner")
public class ImdbTrailerScanner implements IMovieTrailerScanner, ISeriesTrailerScanner {

    private static final Logger LOG = LoggerFactory.getLogger(ImdbTrailerScanner.class);

    @Autowired
    private TrailerScannerService trailerScannerService;
    @Autowired
    private ImdbScanner imdbScanner;
    @Autowired
    private ImdbApiWrapper imdbApiWrapper;
    
    @Override
    public String getScannerName() {
        return imdbScanner.getScannerName();
    }
    
    @PostConstruct
    public void init() {
        LOG.trace("Initialize IMDb trailer scanner");
        
        // register this scanner
        trailerScannerService.registerTrailerScanner(this);
    }

    @Override
    public List<TrailerDTO> getTrailers(VideoData videoData) {
        String imdbId = imdbScanner.getMovieId(videoData);
        return getTrailerDTOS(imdbId);
   }

    @Override
    public List<TrailerDTO> getTrailers(Series series) {
        String imdbId = imdbScanner.getSeriesId(series);
        return getTrailerDTOS(imdbId);
    }
    
    private List<TrailerDTO> getTrailerDTOS(String imdbId) {
        if (StringUtils.isBlank(imdbId)) { 
            return Collections.emptyList();
        }
        
        ImdbMovieDetails movieDetails = imdbApiWrapper.getMovieDetails(imdbId);
        if (movieDetails == null || movieDetails.getTrailer() == null || MapUtils.isEmpty(movieDetails.getTrailer().getEncodings())) {
            return Collections.emptyList();
        }
        
        String url = null;
        int prio = 1000;
        
        for (ImdbEncodingFormat format : movieDetails.getTrailer().getEncodings().values()) {
            switch(format.getFormat()) {
                case "HD 720":
                   if (prio > 10) {
                       prio = 10;
                       url = format.getUrl();
                   }
                   break;
                case "HD 480p":
                    if (prio > 20) {
                        prio = 20;
                        url = format.getUrl();
                    }
                    break;
                case "H.264 Fire 600":
                    if (prio > 30) {
                        prio = 30;
                        url = format.getUrl();
                    }
                    break;
                default:
                    if (prio > 100) {
                        prio = 100;
                        url = format.getUrl();
                    }
                    break;
            }
        }

        if (url == null) {
            return Collections.emptyList();
        }
        
        TrailerDTO dto = new TrailerDTO(ImdbScanner.SCANNER_ID, ContainerType.MP4, url, movieDetails.getTrailer().getTitle(), imdbId); 
        return Collections.singletonList(dto);
    }
}
