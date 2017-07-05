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
package org.matrix.androidsdk.util;

import android.content.Context;
import android.text.TextUtils;
import android.util.Pair;


import org.matrix.androidsdk.adapters.MessageRow;
import org.matrix.androidsdk.data.RoomState;
import org.matrix.androidsdk.rest.model.Event;
import org.matrix.androidsdk.rest.model.EventContent;
import org.matrix.androidsdk.rest.model.RoomMember;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manage the membership events merge
 */
public class MelsDisplay {
    private static final String LOG_TAG = MelsDisplay.class.getSimpleName();

    // membership transition
    private static final String TRANSITION_INVITED = "invited";
    private static final String TRANSITION_BANNED = "banned";
    private static final String TRANSITION_JOINED = "joined";
    private static final String TRANSITION_INVITE_REJECT = "invite_reject";
    private static final String TRANSITION_LEFT = "invite_left";
    private static final String TRANSITION_INVITE_WITHDRAWAL = "invite_withdrawal";
    private static final String TRANSITION_UNBANNED = "unbanned";
    private static final String TRANSITION_KICKED = "kicked";
    private static final String TRANSITION_JOINED_AND_LEFT = "joined_and_left";
    private static final String TRANSITION_LEFT_AND_JOINED = "left_and_joined";
    private static final String TRANSITION_SPLIT_PATTERN = ",";
    private static final String TRANSITION_UNDEFINED = "undefined";

    /**
     * Build a memberships notice text from a message rows list.
     * @param messageRows the message rows list
     * @return the memberships notice text
     */
    public static String getMembershipsNotice(Context context, List<MessageRow> messageRows) {
        if ((null == messageRows) || (0 == messageRows.size())) {
            return null;
        } else if (1 == messageRows.size()) {
            return EventDisplay.getMembershipNotice(context, messageRows.get(0).getEvent(), messageRows.get(0).getRoomState());
        }

        return getNotice(context, getAggregate(UserEvent.toUserEventsMap(messageRows)));
    }

    /**
     * Build a notice for an aggregated events map.
     *
     * @param eventAggregates the aggregated events map
     * @return the notice text
     */
    private static String getNotice(Context context, List<Pair<String, List<String>>> eventAggregates) {
        List<String> summaries = new ArrayList<>();

        for(Pair<String, List<String>> eventAggregate : eventAggregates) {
            String transitions = eventAggregate.first;
            List<String> userNames = eventAggregate.second;

            String nameList = renderNameList(context, userNames);
            boolean plural = userNames.size() > 1;

            String[] splitTransitions = transitions.split(TRANSITION_SPLIT_PATTERN);
            List<String> canonicalTransitions = getCanonicalTransitions(splitTransitions);

            List<Pair<String, Integer>> coalescedTransitions = coalesceRepeatedTransitions(canonicalTransitions);

            List<String> descs = new ArrayList<>();

            for (Pair<String, Integer> coalesceRepeatedTransition : coalescedTransitions) {
                String desc = getDescriptionForTransition(context, coalesceRepeatedTransition.first, plural, coalesceRepeatedTransition.second);

                if (null != desc) {
                    descs.add(desc);
                }
            }

            summaries.add(nameList + " " + TextUtils.join(", ", descs));
        }

        return TextUtils.join(", ", summaries);
    }

    /**
     * define
     */
    private static class UserEvent {
        // The original event
        Event mEvent;

        // The display name of the user (if not, then user ID)
        String mDisplayName;

        // The original index of in the events list
        int mIndex;

        /**
         * Constructor
         * @param messageRow the message row
         * @param index the index in the events list
         */
        public UserEvent(MessageRow messageRow, int index) {
            mEvent = messageRow.getEvent();
            mDisplayName = EventDisplay.senderDisplayNameForEvent(mEvent, mEvent.getEventContent(), mEvent.getPrevContent(), messageRow.getRoomState());
            mIndex = index;
        }

        /**
         * Convert a messageRows list into userEvent map.
         * The map is indexed by userId.
         * @param messageRows the messageRows to convert
         * @return the userEvents map
         */
        public static Map<String, List<UserEvent>> toUserEventsMap(List<MessageRow> messageRows) {
            Map<String, List<UserEvent>> userEventsMap = new HashMap<>();
            int index = 0;

            for(MessageRow row : messageRows) {
                String userId = row.getEvent().userId;
                List<UserEvent> userEvents = userEventsMap.get(userId);

                if (null == userEvents) {
                    userEvents = new ArrayList<>();
                    userEventsMap.put(userId,userEvents);
                }

                userEvents.add(new UserEvent(row, index));
                index++;
            }

            return userEventsMap;
        }
    }

    /**
     * Compute the transition sequences from the events list.
     * eg joined, left, joined.
     *
     * @param userEvents the events list
     * @return the transition sequence
     */
    private static String getTransitionSequence(List<UserEvent> userEvents) {
        List<String> sequenceItems = new ArrayList<>();

        for(UserEvent userEvent : userEvents) {
            sequenceItems.add(getTransition(userEvent.mEvent));
        }

        return TextUtils.join(TRANSITION_SPLIT_PATTERN, sequenceItems);
    }

    /**
     * Convert an userEvents map into a transition list.
     * The first item is the transition description eg "joined, left".
     * The second item is the user ids list.
     *
     * @param userEvents the userEvents map to convert
     * @return the transitions list
     */
    private static List<Pair<String, List<String>>> getAggregate(Map<String, List<UserEvent>> userEvents) {
        // A map of aggregate type to arrays of display names. Each aggregate type
        // is a comma-delimited string of transitions, e.g. "joined,left,kicked".
        // The array of display names is the array of users who went through that
        // sequence during eventsToRender.
        HashMap<String, List<String>> aggregate = new HashMap<>();

        // A map of aggregate types to the indices that order them (the index of
        // the first event for a given transition sequence)
        HashMap<String, Integer> aggregateIndices = new HashMap<>();

        Set<String> userIds = userEvents.keySet();

        for(String userId : userIds) {
            UserEvent firstEvent = userEvents.get(userId).get(0);
            String displayName = userEvents.get(userId).get(0).mDisplayName;

            String seq = getTransitionSequence(userEvents.get(userId));

            if (aggregate.containsKey(seq)) {
                aggregate.put(seq, new ArrayList<String>());
                aggregateIndices.put(seq, -1);
            }

            aggregate.get(seq).add(displayName);

            if ((-1 == aggregateIndices.get(seq)) || (firstEvent.mIndex < aggregateIndices.get(seq))) {
                aggregateIndices.put(seq, firstEvent.mIndex);
            }
        }

        List<Pair<String, Integer>> indices = new ArrayList<>();

        for(String transition : aggregateIndices.keySet()) {
                indices.add(new Pair<>(transition, aggregateIndices.get(transition)));
        }

        Collections.sort(indices, new Comparator<Pair<String, Integer>>() {
            @Override
            public int compare(Pair<String, Integer> pair1, Pair<String, Integer> pair2) {
                return pair2.second - pair1.second;
            }
        });

        List<Pair<String, List<String>>> sortedTransitionsList = new ArrayList<>();

        for(Pair<String, Integer> indice : indices) {
            sortedTransitionsList.add(new Pair<>(indice.first, aggregate.get(indice.first)));
        }

        return sortedTransitionsList;
    }

    /**
     * Provides a transition description from an event
     *
     * @param event the events
     * @return the transition
     */
    private static String getTransition(Event event) {
        EventContent eventContent = JsonUtils.toEventContent(event.getContentAsJsonObject());
        EventContent prevEventContent = event.getPrevContent();

        String membership = null;
        String prevMembership = null;

        if (null != eventContent) {
            membership = eventContent.membership;
        }

        if (null != prevEventContent) {
            prevMembership = prevEventContent.membership;
        }

        if (TextUtils.equals(membership, RoomMember.MEMBERSHIP_INVITE)) {
            return TRANSITION_INVITED;
        } else if (TextUtils.equals(membership, RoomMember.MEMBERSHIP_BAN)) {
            return TRANSITION_BANNED;
        } else if (TextUtils.equals(membership, RoomMember.MEMBERSHIP_JOIN)) {
            return TRANSITION_JOINED;
        } else if (TextUtils.equals(membership, RoomMember.MEMBERSHIP_LEAVE)) {
            if (TextUtils.equals(event.getSender(), event.stateKey)) {
                if (TextUtils.equals(prevMembership, RoomMember.MEMBERSHIP_INVITE)) {
                    return TRANSITION_INVITE_REJECT;
                }
                return TRANSITION_LEFT;
            }

            if (TextUtils.equals(prevMembership, RoomMember.MEMBERSHIP_INVITE)) {
                return TRANSITION_INVITE_WITHDRAWAL;
            } else if (TextUtils.equals(prevMembership, RoomMember.MEMBERSHIP_BAN)) {
                return TRANSITION_UNBANNED;
            } else if (TextUtils.equals(membership, RoomMember.MEMBERSHIP_JOIN)) {
                return TRANSITION_KICKED;
            }

            return TRANSITION_LEFT;
        }
        return TRANSITION_UNDEFINED;
    }

    /**
     * Canonicalise an array of transitions such that some pairs of transitions become
     * single transitions. For example an input ['joined','left'] would result in an output
     * ['joined_and_left'].
     * @param {string[]} transitions an array of transitions.
     * @returns {string[]} an array of transitions.
     */
    private static List<String> getCanonicalTransitions(String[] transitions) {
        ArrayList<String> res = new ArrayList<>();

        for(int i = 0; i < transitions.length; i++) {
            String t =  transitions[i];
            String t2 = null;

            if (i < transitions.length) {
                t2 = transitions[i+1];
            }

            String transition = t;

            if (null != t2) {
                if (TextUtils.equals(t, TRANSITION_JOINED) && TextUtils.equals(t2, TRANSITION_LEFT)) {
                    transition = TRANSITION_JOINED_AND_LEFT;
                    i++;
                } else if (TextUtils.equals(t, TRANSITION_LEFT) && TextUtils.equals(t2, TRANSITION_JOINED)) {
                    transition = TRANSITION_LEFT_AND_JOINED;
                    i++;
                }
            }

            res.add(transition);
        }

        return res;
    }

    /**
     * Transform a list of transitions into an array of transitions and how many times
     * they are repeated consecutively.
     *
     * @param transitions the list of transitions to transform.
     * @returns {object[]} an array of coalesced transitions.
     */
    private static List<Pair<String, Integer>> coalesceRepeatedTransitions(List<String> transitions) {
        List<Pair<String, Integer>> res = new ArrayList<>();
        String curTransition = null;
        int count = 0;

        for(String transition : transitions) {
            if (null == curTransition) {
                curTransition = transition;
                count = 1;
            } else if (TextUtils.equals(curTransition, transition)) {
                count++;
            } else {
                res.add(new Pair<>(curTransition, count));
                curTransition = null;
            }
        }

        if (null != curTransition) {
            res.add(new Pair<>(curTransition, count));
        }

        return res;
    }

    /**
     * Build a description for the transitions
     *
     * @param context the context
     * @param t the transition
     * @param plural true if there were multiple users undergoing the same transition
     * @param repeats repeats the number of times the transition was repeated in a row.
     * @return the description
     */
     private static String getDescriptionForTransition(Context context, String t, boolean plural, int repeats) {
        String beConjugated = plural ? "were" : "was";
        String invitation = "their invitation" + (plural || (repeats > 1) ? "s" : "");

        String res = null;

         if (TextUtils.equals(t, TRANSITION_JOINED)) {
             res = "joined";
         } else if (TextUtils.equals(t, TRANSITION_LEFT)) {
             res = "left";
         } else if (TextUtils.equals(t, TRANSITION_JOINED_AND_LEFT)) {
             res = "joined and left";
         } else if (TextUtils.equals(t, TRANSITION_LEFT_AND_JOINED)) {
             res = "left and joined";
         } else if (TextUtils.equals(t, TRANSITION_INVITE_REJECT)) {
             res = "rejected " + invitation;
         } else if (TextUtils.equals(t, TRANSITION_INVITE_WITHDRAWAL)) {
             res = "had " + invitation + " withdrawn";
         } else if (TextUtils.equals(t, TRANSITION_INVITED)) {
             res = beConjugated + " invited";
         } else if (TextUtils.equals(t, TRANSITION_BANNED)) {
             res = beConjugated + " banned";
         } else if (TextUtils.equals(t, TRANSITION_UNBANNED)) {
             res = beConjugated + " unbanned";
         } else if (TextUtils.equals(t, TRANSITION_KICKED)) {
             res = beConjugated + " kicked";
         }

         if (repeats > 1) {
             res += " " + repeats + " times";
         }

        return res;
    }

    /**
     * Build a string representing the userNames list
     *
     * @param context the context
     * @param userNames the user names list
     * @return a string
     */
    private static String renderNameList(Context context, List<String> userNames) {
        String res = "";

        if ((null != userNames) && !userNames.isEmpty()) {
            if (1 == userNames.size()) {
                res = userNames.get(0);
            } else {
                int otherCount = userNames.size() - 1;

                res = userNames.get(0) + " and " +  otherCount + " other";

                if (otherCount > 1) {
                    res += "s";
                }
            }
        }

        return res;
    }
}
