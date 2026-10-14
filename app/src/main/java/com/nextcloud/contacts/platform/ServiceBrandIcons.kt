package com.nextcloud.contacts.platform

import androidx.annotation.DrawableRes
import com.nextcloud.contacts.R

/**
 * Bundled brand icons for instant messaging and social profile rows when the
 * corresponding app is not installed (or has no resolvable package).
 */
object ServiceBrandIcons {

    @DrawableRes
    fun imDrawableRes(imType: String): Int? = when (imType.lowercase()) {
        "whatsapp" -> R.drawable.ic_brand_whatsapp
        "signal" -> R.drawable.ic_brand_signal
        "telegram" -> R.drawable.ic_brand_telegram
        "threema" -> R.drawable.ic_brand_threema
        "discord" -> R.drawable.ic_brand_discord
        "matrix" -> R.drawable.ic_brand_matrix
        "xmpp" -> R.drawable.ic_brand_xmpp
        "kakaotalk" -> R.drawable.ic_brand_kakaotalk
        "wechat" -> R.drawable.ic_brand_wechat
        "line" -> R.drawable.ic_brand_line
        "zoom" -> R.drawable.ic_brand_zoom
        "irc" -> R.drawable.ic_brand_irc
        "kik" -> R.drawable.ic_brand_kik
        "qq" -> R.drawable.ic_brand_qq
        "nextcloud" -> R.drawable.ic_brand_nextcloud
        else -> null
    }

    @DrawableRes
    fun socialDrawableRes(socialType: String): Int? = when (socialType.lowercase()) {
        "facebook" -> R.drawable.ic_brand_facebook
        "github" -> R.drawable.ic_brand_github
        "instagram" -> R.drawable.ic_brand_instagram
        "linkedin" -> R.drawable.ic_brand_linkedin
        "xing" -> R.drawable.ic_brand_xing
        "pinterest" -> R.drawable.ic_brand_pinterest
        "qzone" -> R.drawable.ic_brand_qzone
        "tumblr" -> R.drawable.ic_brand_tumblr
        "twitter" -> R.drawable.ic_brand_twitter
        "wechat" -> R.drawable.ic_brand_wechat
        "youtube" -> R.drawable.ic_brand_youtube
        "mastodon" -> R.drawable.ic_brand_mastodon
        "diaspora" -> R.drawable.ic_brand_diaspora
        "nextcloud" -> R.drawable.ic_brand_nextcloud
        else -> null
    }
}
