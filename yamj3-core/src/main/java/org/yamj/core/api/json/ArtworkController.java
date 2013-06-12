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
package org.yamj.core.api.json;

import java.util.Collections;
import java.util.List;
import org.hibernate.QueryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.yamj.core.api.model.ApiStatus;
import org.yamj.core.api.model.ApiWrapperList;
import org.yamj.core.api.model.ApiWrapperSingle;
import org.yamj.core.api.model.ParameterType;
import org.yamj.core.api.model.Parameters;
import org.yamj.core.database.model.Artwork;
import org.yamj.core.database.service.JsonApiStorageService;

@Controller
@RequestMapping("/api/artwork/**")
public class ArtworkController {

    private static final Logger LOG = LoggerFactory.getLogger(ArtworkController.class);
    @Autowired
    private JsonApiStorageService jsonApiStorageService;

    @RequestMapping(value = "/{id}", method = RequestMethod.GET)
    @ResponseBody
    public ApiWrapperSingle<Artwork> getArtworkById(@PathVariable String id) {
        LOG.info("Getting artwork with ID '{}'", id);
        Artwork artwork = jsonApiStorageService.getEntityById(Artwork.class, Long.parseLong(id));
        ApiWrapperSingle<Artwork> wrapper = new ApiWrapperSingle<Artwork>(artwork);
        wrapper.setStatusCheck();
        return wrapper;
    }

    @RequestMapping(value = "/list", method = RequestMethod.GET)
    @ResponseBody
    public ApiWrapperList<Artwork> getSeriesList(
            @RequestParam(required = false, defaultValue = "") String artwork,
            @RequestParam(required = false, defaultValue = "") String type,
            @RequestParam(required = false, defaultValue = "-1") Integer id,
            @RequestParam(required = false, defaultValue = "-1") Integer start,
            @RequestParam(required = false, defaultValue = "-1") Integer max) {

        Parameters p = new Parameters();
        p.add(ParameterType.ARTWORK_TYPE, artwork);
        p.add(ParameterType.VIDEO_TYPE, type);
        p.add(ParameterType.ID, id);
        p.add(ParameterType.START, start);
        p.add(ParameterType.MAX, max);

        LOG.info("Getting artwork list with {}", p.toString());
        ApiWrapperList<Artwork> wrapper = new ApiWrapperList<Artwork>();
        try {
            List<Artwork> results = jsonApiStorageService.getArtworkList(p);
            wrapper.setResults(results);
            wrapper.setStatusCheck();
        } catch (QueryException ex) {
            wrapper.setResults(Collections.EMPTY_LIST);
            wrapper.setStatus(new ApiStatus(400, "Error with query"));
            LOG.error("Exception: {}", ex.getMessage());
        }
        wrapper.setParameters(p);
        return wrapper;
    }
}