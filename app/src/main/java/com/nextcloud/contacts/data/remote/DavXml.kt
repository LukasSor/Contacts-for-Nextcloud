package com.nextcloud.contacts.data.remote

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParser.END_DOCUMENT
import org.xmlpull.v1.XmlPullParser.END_TAG
import org.xmlpull.v1.XmlPullParser.START_TAG

internal object DavXml {
    fun XmlPullParser.tagName(): String {
        val name = name ?: return ""
        return if (name.contains(':')) name.substringAfter(':') else name
    }

    fun XmlPullParser.isStart(tag: String): Boolean =
        eventType == START_TAG && tagName() == tag

    fun XmlPullParser.isEnd(tag: String): Boolean =
        eventType == END_TAG && tagName() == tag

    fun XmlPullParser.readTextValue(): String = nextText().trim()

    fun parseInnerHref(xml: String, containerTag: String): String? {
        val parser = org.xmlpull.v1.XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(java.io.StringReader(xml))
        var event = parser.eventType
        var inContainer = false
        while (event != END_DOCUMENT) {
            when {
                parser.isStart(containerTag) -> inContainer = true
                parser.isEnd(containerTag) -> inContainer = false
                parser.isStart("href") && inContainer -> return parser.readTextValue()
            }
            event = parser.next()
        }
        return null
    }
}
