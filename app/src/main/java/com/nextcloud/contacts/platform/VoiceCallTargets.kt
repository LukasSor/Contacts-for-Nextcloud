package com.nextcloud.contacts.platform

import android.content.Context

/**
 * Voice-capable launch targets for a contact: the system phone app plus Connected-apps
 * rows whose MIME type is a voice call.
 */
object VoiceCallTargets {

    fun collect(
        context: Context,
        phones: List<String>,
        disabledAccountKeys: Set<String>,
    ): List<VideoCallTargets.Target> = VideoCallTargets.collect(
        context = context,
        phones = phones,
        disabledAccountKeys = disabledAccountKeys,
        mode = VideoCallTargets.Mode.Voice,
    )

    fun talkTarget(context: Context): VideoCallTargets.Target = VideoCallTargets.talkTarget(context)

    fun launch(
        context: Context,
        target: VideoCallTargets.Target,
    ): MessagingContactsIntents.LaunchResult =
        VideoCallTargets.launch(context, target)
}
