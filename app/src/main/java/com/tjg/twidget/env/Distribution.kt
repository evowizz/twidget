package com.tjg.twidget.env

/** Where this build is distributed. Each flavor sets its own in [BuildVars.DISTRIBUTION]. */
enum class Distribution {
    GITHUB,
    PLAY,
    ;

    fun isGithub(): Boolean = this == GITHUB
}
