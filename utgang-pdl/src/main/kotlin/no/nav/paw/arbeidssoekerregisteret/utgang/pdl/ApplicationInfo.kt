package no.nav.paw.arbeidssoekerregisteret.utgang.pdl

object ApplicationInfo {
    val id = System.getenv("IMAGE_WITH_VERSION")
        ?: "paw-arbeidssoekerregisteret-utgang-pdl:LOCAL"
}