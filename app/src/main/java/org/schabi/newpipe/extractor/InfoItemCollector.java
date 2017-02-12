package org.schabi.newpipe.extractor;

import org.schabi.newpipe.extractor.exceptions.ExtractionException;

import java.util.List;
import java.util.Vector;

/**
 * Created by Christian Schabesberger on 12.02.17.
 *
 * Copyright (C) Christian Schabesberger 2017 <chris.schabesberger@mailbox.org>
 * InfoItemCollector.java is part of NewPipe.
 *
 * NewPipe is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * NewPipe is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with NewPipe.  If not, see <http://www.gnu.org/licenses/>.
 */

public class InfoItemCollector {
    private List<InfoItem> itemList = new Vector<>();
    private List<Throwable> errors = new Vector<>();
    private UrlIdHandler urlIdHandler;
    private int serviceId = -1;

    public InfoItemCollector(UrlIdHandler handler, int serviceId) {
        urlIdHandler = handler;
        this.serviceId = serviceId;
    }

    public List<InfoItem> getItemList() {
        return itemList;
    }
    public List<Throwable> getErrors() {
        return errors;
    }
    public void addFromCollector(InfoItemCollector otherC) throws ExtractionException {
        if(serviceId != otherC.serviceId) {
            throw new ExtractionException("Service Id does not equal: "
                    + NewPipe.getNameOfService(serviceId)
                    + " and " + NewPipe.getNameOfService(otherC.serviceId));
        }
        errors.addAll(otherC.errors);
        itemList.addAll(otherC.itemList);
    }
    protected void addError(Exception e) {
        errors.add(e);
    }
    protected void addItem(InfoItem item) {
        itemList.add(item);
    }
    protected int getServiceId() {
        return serviceId;
    }
    protected UrlIdHandler getUrlIdHandler() {
        return urlIdHandler;
    }
}
