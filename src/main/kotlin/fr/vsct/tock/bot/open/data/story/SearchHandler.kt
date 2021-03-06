/*
 *  This file is part of the tock-bot-open-data distribution.
 *  (https://github.com/voyages-sncf-technologies/tock-bot-open-data)
 *  Copyright (c) 2017 VSCT.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU Affero General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU Affero General Public License for more details.
 *
 *   You should have received a copy of the GNU Affero General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package fr.vsct.tock.bot.open.data.story

import fr.vsct.tock.bot.connector.ConnectorMessage
import fr.vsct.tock.bot.connector.ga.GAHandler
import fr.vsct.tock.bot.connector.ga.carouselItem
import fr.vsct.tock.bot.connector.ga.gaFlexibleMessageForCarousel
import fr.vsct.tock.bot.connector.ga.gaImage
import fr.vsct.tock.bot.connector.messenger.MessengerHandler
import fr.vsct.tock.bot.connector.messenger.flexibleListTemplate
import fr.vsct.tock.bot.connector.messenger.listElement
import fr.vsct.tock.bot.connector.messenger.model.send.ListElementStyle.compact
import fr.vsct.tock.bot.definition.ConnectorDef
import fr.vsct.tock.bot.definition.HandlerDef
import fr.vsct.tock.bot.definition.ParameterKey
import fr.vsct.tock.bot.definition.storyDef
import fr.vsct.tock.bot.engine.BotBus
import fr.vsct.tock.bot.open.data.OpenDataConfiguration.trainImage
import fr.vsct.tock.bot.open.data.SecondaryIntent.indicate_location
import fr.vsct.tock.bot.open.data.SecondaryIntent.indicate_origin
import fr.vsct.tock.bot.open.data.client.sncf.SncfOpenDataClient
import fr.vsct.tock.bot.open.data.client.sncf.model.Journey
import fr.vsct.tock.bot.open.data.client.sncf.model.Place
import fr.vsct.tock.bot.open.data.client.sncf.model.Section
import fr.vsct.tock.bot.open.data.story.MessageFormat.datetimeFormat
import fr.vsct.tock.bot.open.data.story.MessageFormat.timeFormat
import fr.vsct.tock.bot.open.data.story.SearchDef.SearchParameter.proposal
import fr.vsct.tock.translator.by
import java.time.LocalDateTime

/**
 * The search intent handler.
 */
val search = storyDef<SearchDef>(
    "search",
    setOf(indicate_origin),
    setOf(indicate_location)
) {
    //handle generic location intent
    if (isIntent(indicate_location) && location != null) {
        if (destination == null || origin != null) {
            destination = returnsAndRemoveLocation()
        } else {
            origin = returnsAndRemoveLocation()
        }
    }

    //check mandatory entities
    when {
        destination == null -> end("For which destination?")
        origin == null -> end("For which origin?")
        departureDate == null -> end("When?")
    }
}


/**
 * The search handler definition.
 */
@GAHandler(GASearchConnector::class)
@MessengerHandler(MessengerSearchConnector::class)
class SearchDef(bus: BotBus) : HandlerDef<SearchConnector>(bus) {

    enum class SearchParameter : ParameterKey {
        proposal
    }

    private val d: Place = bus.destination!!
    private val o: Place = bus.origin!!
    private val date: LocalDateTime = bus.departureDate!!

    override fun answer() {
        send("From {0} to {1}", o, d)
        send("Departure on {0}", date by datetimeFormat)
        val journeys = SncfOpenDataClient.journey(o, d, date)
        if (journeys.isEmpty()) {
            end("Sorry, no routes found :(")
        } else {
            send("Here is the first proposal:")
            connector?.sendFirstJourney(journeys.first())
            end()
        }
    }
}

/**
 * Connector specific behaviour.
 */
sealed class SearchConnector(context: SearchDef) : ConnectorDef<SearchDef>(context) {

    fun Section.title(): CharSequence = i18n("{0} - {1}", from, to)

    fun Section.content(): CharSequence =
        i18n(
            "Departure at {0}, arrival at {1}",
            stopDateTimes.first().departureDateTime by timeFormat,
            stopDateTimes.last().arrivalDateTime by timeFormat
        )


    fun sendFirstJourney(journey: Journey) = withMessage(sendFirstJourney(journey.publicTransportSections()))

    abstract fun sendFirstJourney(sections: List<Section>): ConnectorMessage

}

/**
 * Messenger specific behaviour.
 */
class MessengerSearchConnector(context: SearchDef) : SearchConnector(context) {

    override fun sendFirstJourney(sections: List<Section>): ConnectorMessage =
        flexibleListTemplate(
            sections.map { section ->
                with(section) {
                    listElement(
                        title(),
                        content(),
                        trainImage
                    )
                }
            },
            compact
        )
}

/**
 * Google Assistant specific behaviour.
 */
class GASearchConnector(context: SearchDef) : SearchConnector(context) {

    override fun sendFirstJourney(sections: List<Section>): ConnectorMessage =
        gaFlexibleMessageForCarousel(
            sections.mapIndexed { i, section ->
                with(section) {
                    carouselItem(
                        search,
                        title(),
                        content(),
                        gaImage(trainImage, "train"),
                        proposal[i]
                    )
                }
            }
        )
}

