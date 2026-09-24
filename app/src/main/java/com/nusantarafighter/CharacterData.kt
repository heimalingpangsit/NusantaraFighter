package com.nusantarafighter

enum class FighterId {
    PRORORO, MEGACHAN, MR_ETANOL, MR_YOUTUBE, FUFU, RAJA_SOLO, PURBANKYA
}

data class FighterData(
    val id: FighterId,
    val name: String,
    val title: String,
    val hp: Int,
    val attack: Int,
    val speed: Float,
    val skill1: String,
    val skill2: String,
    val ultimate: String
)

object Fighters {
    val all = listOf(
        FighterData(FighterId.PRORORO, "PRORORO", "HEAVY BRUISER", 140, 16, 1.8f,
            "TARIAN GEMOY", "KAVALERI KUDA PUTIH", "MAKAN SIANG GRATIS"),
        FighterData(FighterId.MEGACHAN, "MEGACHAN", "RED COMMANDER", 125, 14, 2.0f,
            "MONOLOG PANJANG", "KONGRES BESAR", "PETUGAS PARTAI"),
        FighterData(FighterId.MR_ETANOL, "MR. ETANOL", "RUSH / PRESSURE", 115, 15, 2.6f,
            "TAMBANG IZIN CEPAT", "PEMBAGIAN ORMAS", "GELAR KILAT"),
        FighterData(FighterId.MR_YOUTUBE, "MR. YOUTUBE", "RANGED / UTILITY", 108, 13, 2.7f,
            "REC & UPLOAD", "CLICKBAIT SLAM", "VIRAL 1 MILIAR VIEW"),
        FighterData(FighterId.FUFU, "FUFU", "YOUNG RUSHER", 100, 15, 3.2f,
            "SAMSUL ASAM SULFAT", "CELINGAK CELINGUK", "SOLO TECHNO PARK"),
        FighterData(FighterId.RAJA_SOLO, "RAJA SOLO", "TANK / SUSTAIN", 135, 12, 1.9f,
            "SABETAN KERATON", "MODE TENANG", "TITAH RAJA"),
        FighterData(FighterId.PURBANKYA, "PURBANKYA", "ANCIENT WARRIOR", 130, 15, 2.0f,
            "PUKULAN BATU", "TARIAN PURBA", "PERISAI PURBA")
    )
}
