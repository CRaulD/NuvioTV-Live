package com.nuvio.tv.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "config")
data class ConfigEntity(
    @PrimaryKey
    val id: Int = 1,
    val m3uUrl: String = "",
    val epgUrl: String = "",
    val lastRefreshTs: Long = 0L
)

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey
    val channelId: String
)

@Entity(
    tableName = "epg_programs",
    indices = [androidx.room.Index(value = ["channelTvgId"])]
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val channelTvgId: String,
    val title: String,
    val description: String? = null,
    val startTime: Long,
    val endTime: Long
) {
    fun toDomain(): com.nuvio.tv.domain.model.EpgProgram = com.nuvio.tv.domain.model.EpgProgram(
        channelTvgId = channelTvgId,
        title = title,
        description = description,
        startTime = startTime,
        endTime = endTime
    )

    companion object {
        fun fromDomain(program: com.nuvio.tv.domain.model.EpgProgram): EpgProgramEntity = EpgProgramEntity(
            channelTvgId = program.channelTvgId,
            title = program.title,
            description = program.description,
            startTime = program.startTime,
            endTime = program.endTime
        )
    }
}
