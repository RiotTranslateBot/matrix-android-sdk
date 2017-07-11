/*
 * Copyright 2017 Vector Creations Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.matrix.androidsdk.adapters;

import android.content.Context;
import android.widget.ArrayAdapter;

import org.matrix.androidsdk.rest.model.Event;

/**
 * Abstract implementation of messages list
 */
public abstract class AbstractMessagesAdapter extends ArrayAdapter<MessageRow> {

    // default constructor
    public AbstractMessagesAdapter(Context context, int view) {
        super(context, view);
    }

    /*
     * *********************************************************************************************
     * Items getter / setter
     * *********************************************************************************************
     */

    /**
     * Add a row and refresh the adapter if it is required.
     *
     * @param row     the row to append
     * @param refresh tru to refresh the display.
     */
    public abstract void add(MessageRow row, boolean refresh);

    /**
     * Add a message row to the top.
     *
     * @param row     the row to append
     */
    public abstract void addToFront(MessageRow row);

    /**
     * Provides the messageRow from an event Id.
     *
     * @param eventId the event Id.
     * @return the message row.
     */
    public abstract MessageRow getMessageRow(String eventId);

    /**
     * Get the closest row after the given event
     * Used when we need to jump to an event that is not displayed
     *
     * @param event
     * @return closest row
     */
    public abstract MessageRow getClosestRow(Event event);

    /**
     * Get the closest row after the given event id/ts
     * Used when we need to jump to an event that is not displayed
     *
     * @param eventId
     * @param eventTs
     * @return closest row
     */
    public abstract MessageRow getClosestRowFromTs(final String eventId, final long eventTs);

    /**
     * Get the closest row before the given event id/ts
     *
     * @param eventId
     * @param eventTs
     * @return closest row
     */
    public abstract MessageRow getClosestRowBeforeTs(final String eventId, final long eventTs);

    /**
     * Update the message row to a new event id.
     *
     * @param event      the new event
     * @param oldEventId the old message row event id.
     */
    public abstract void updateEventById(Event event, String oldEventId);

    /**
     * Remove an event by an eventId
     *
     * @param eventId the event id.
     */
    public abstract void removeEventById(String eventId);

    /*
     * *********************************************************************************************
     * Display modes
     * *********************************************************************************************
     */

    /**
     * Update the preview mode status
     *
     * @param isPreviewMode true to display the adapter in preview mode
     */
    public abstract void setIsPreviewMode(boolean isPreviewMode);

    /**
     * Set whether we are ine preview mode to show unread messages
     *
     * @param isUnreadViewMode
     */
    public abstract void setIsUnreadViewMode(boolean isUnreadViewMode);

    /**
     * Get whether we are in preview mode to show unread messages
     *
     * @return true if preview to show unread messages
     */
    public abstract boolean isUnreadViewMode();

    /*
     * *********************************************************************************************
     * Preview mode
     * *********************************************************************************************
     */

    /**
     * Defines the search pattern.
     *
     * @param pattern the pattern to search.
     */
    public abstract void setSearchPattern(String pattern);

    /*
     * *********************************************************************************************
     * Read markers
     * *********************************************************************************************
     */

    /**
     * Reset the read marker event so read marker view will not be displayed again on same event
     */
    public abstract void resetReadMarker();

    /**
     * Specify the last read message (to display read marker line)
     *
     * @param readMarkerEventId
     */
    public abstract void updateReadMarker(final String readMarkerEventId, final String readReceiptEventId);

    /*
     * *********************************************************************************************
     * Others
     * *********************************************************************************************
     */

    /**
     * @return the max thumbnail width
     */
    public abstract int getMaxThumbnailWith();

    /**
     * @return the max thumbnail height
     */
    public abstract int getMaxThumbnailHeight();

    /*
     * @return true if some messages have been sent from this user
     */
    public abstract boolean containsMessagesFrom(String userId);

    /**
     * Notify that some bing rules could have been updated.
     */
    public abstract void onBingRulesUpdate();
}