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
package org.yamj.core.service.nfo;

import org.yamj.common.type.MetaDataType;

import org.yamj.core.database.model.dto.QueueDTO;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NfoScannerRunner implements Runnable {

    private static final Logger LOG = LoggerFactory.getLogger(NfoScannerRunner.class);
    private final BlockingQueue<QueueDTO> queue;
    private final NfoScannerService service;

    public NfoScannerRunner(BlockingQueue<QueueDTO> queue, NfoScannerService service) {
        this.queue = queue;
        this.service = service;
    }

    @Override
    public void run() {
        QueueDTO queueElement = queue.poll();
        while (queueElement != null) {

            try {
                if (queueElement.isMetadataType(MetaDataType.MOVIE)) {
                    service.scanMovieNfo(queueElement);
                } else if (queueElement.isMetadataType(MetaDataType.SERIES)) {
                    service.scanSerieseNfo(queueElement);
                } else {
                    LOG.error("No valid element for scanning nfo '{}'", queueElement);
                }
            } catch (Exception error) {
                LOG.error("Failed to process nfo {}-{}", queueElement.getId(), queueElement.getMetadataType());
                LOG.warn("Scanning error", error);
                
                try {
                    service.processingError(queueElement);
                } catch (Exception ignore) {
                    // ignore this error
                }
            }
            queueElement = queue.poll();
        }
    }
}
