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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import javax.annotation.Resource;
import org.junit.Test;
import org.yamj.core.AbstractTest;
import org.yamj.core.database.model.*;

public class ImdbScannerTest extends AbstractTest {

    @Resource(name = "imdbScanner")
    private ImdbScanner imdbScanner;

    @Test
    public void testMovie() {
        VideoData videoData = new VideoData();
        
        videoData.setSourceDbId(imdbScanner.getScannerName(), "tt0499549");
        ScanResult scanResult = imdbScanner.scanMovie(videoData);

        assertEquals(ScanResult.OK, scanResult);
        assertEquals("Avatar - Aufbruch nach Pandora", videoData.getTitle());
        assertEquals("Avatar", videoData.getTitleOriginal());
        assertEquals(2009, videoData.getPublicationYear());
        assertNotNull(videoData.getOutline());
        assertTrue(videoData.getGenreNames().contains("Abenteuer"));
        assertTrue(videoData.getGenreNames().contains("Action"));
        assertTrue(videoData.getGenreNames().contains("Fantasy"));
        assertTrue(videoData.getStudioNames().contains("Twentieth Century Fox Film Corporation"));
        assertTrue(videoData.getStudioNames().contains("Lightstorm Entertainment"));

        logCredits(videoData, getClass());
        logAwards(videoData, getClass());
    }

    @Test
    public void testSeries() {
        Series series = new Series();
        series.setSourceDbId(imdbScanner.getScannerName(), "tt0944947");
        
        Season season = new Season();
        season.setSeason(1);
        season.setSeries(series);
        series.getSeasons().add(season);
        
        VideoData episode1 = new VideoData("GOT_1");
        episode1.setEpisode(1);
        episode1.setSeason(season);
        season.getVideoDatas().add(episode1);

        VideoData episode2 = new VideoData("GOT_2");
        episode2.setEpisode(2);
        episode2.setSeason(season);
        season.getVideoDatas().add(episode2);
        
        imdbScanner.scanSeries(series);

        assertEquals("Game of Thrones - Das Lied von Eis und Feuer", series.getTitle());
        assertEquals("Game of Thrones", series.getTitleOriginal());
        assertEquals(2011, series.getStartYear());
        assertEquals(-1, series.getEndYear());
        
        for (VideoData videoData : season.getVideoDatas()) {
            assertNotNull(videoData.getTitle());
            assertNotNull(videoData.getReleaseDate());
            assertNotNull(videoData.getPlot());
        }
        
        logCredits(season, getClass());
    }

    @Test
    public void testPerson() {
        Person person = new Person();
        person.setSourceDbId(imdbScanner.getScannerName(), "nm0001352");
        imdbScanner.scanPerson(person);

        assertEquals("Terence Hill", person.getName());
        assertEquals("Mario Girotti", person.getBirthName());
        assertNotNull(person.getBiography());
        assertEquals("Venice, Veneto, Italy", person.getBirthPlace());
    }
}